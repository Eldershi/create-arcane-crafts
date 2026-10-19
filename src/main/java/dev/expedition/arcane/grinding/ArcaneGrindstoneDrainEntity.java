// SPDX-License-Identifier: LGPL-3.0-or-later
/*
 * Capacity checks and slot-3000 compatibility adapted from Create Enchantment
 * Industry 2.5.4b ProcessingInventory (DragonsPlusMinecraft and contributors).
 * This file is distributed under LGPL-3.0-or-later. Other processing delegates to CEI.
 * See THIRD_PARTY_NOTICES.md and LICENSES/Create-Enchantment-Industry-2.5.4b-LICENSE.txt.
 */
package dev.expedition.arcane.grinding;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.mixin.GrindstoneDrainAccessor;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindingRecipe;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneDrainBlockEntity;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneHelper;
import plus.dragons.createenchantmentindustry.common.registry.CEIBlocks;
import plus.dragons.createenchantmentindustry.common.registry.CEIFluids;
import plus.dragons.createenchantmentindustry.common.registry.CEIRecipes;

/**
 * Native CEI 2.5.4b processor plus one post-processing transaction. super.tick() executes the
 * complete native private applyRecipe; at its appliedRecipe transition it returns before
 * exporting any stack. That boundary is used to strip the actual native results exactly once.
 */
public final class ArcaneGrindstoneDrainEntity extends GrindstoneDrainBlockEntity {
    public static final int TANK_CAPACITY = 4000;
    public static final int UNIT_MB = 250;
    private static final int UNLOCKED = -1, DRY = 0, WATER = 1, BLOOD = 2;
    private final FluidTank reagent = new FluidTank(TANK_CAPACITY, ArcaneGrindstoneDrainEntity::isReagent) {
        @Override protected void onContentsChanged() { changed(); }
    };
    private final FluidTank solution = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() { changed(); }
    };
    private final ItemStackHandler essence = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { changed(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };
    private int mode = UNLOCKED;
    private ItemStack lockedInput = ItemStack.EMPTY;
    private long pendingUnits;
    private boolean destroying;
    private boolean paused;
    private boolean committing;
    private LazyOptional<IItemHandler> safeItems = LazyOptional.of(RecoveryItems::new);
    private LazyOptional<IFluidHandler> inputFluids = LazyOptional.of(() -> new FluidsView(true));
    private LazyOptional<IFluidHandler> outputFluids = LazyOptional.of(() -> new FluidsView(false));
    private LazyOptional<IFluidHandler> allFluids = LazyOptional.of(() -> new FluidsView(null));

    public ArcaneGrindstoneDrainEntity(BlockPos pos, BlockState state) { super(ArcaneGrindingContent.DRAIN_ENTITY.get(), pos, state); }
    @Override protected Block getStressConfigKey() { return CEIBlocks.GRINDSTONE_DRAIN.get(); }
    public long pendingUnits() { return pendingUnits; }
    public int recoveryMode() { return mode; }
    public IFluidHandler reagentTankForDisplay() { return reagent; }
    public IFluidHandler solutionTankForDisplay() { return solution; }

    @Override public void tick() {
        if (level == null || level.isClientSide) { super.tick(); return; }
        if (pendingUnits > 0) {
            if (getRelativeSpeed() != 0) settleOneUnit();
            // Keep Create's base tick/network maintenance and native output animation alive.
            // Native inventory is already applied; it cannot be processed a second time.
            super.tick();
            return;
        }
        ItemStack input = inventory.getStackInSlot(0);
        if (inventory.appliedRecipe || input.isEmpty()) {
            if (inventory.isEmpty()) unlock();
            super.tick();
            return;
        }
        ArcaneSalvage.Result inspection = ArcaneSalvage.strip(input);
        if (!inspection.changed()) { unlock(); super.tick(); return; }
        if (getRelativeSpeed() == 0) { tickPaused(); return; }
        if (!ItemStack.matches(lockedInput, input) || mode == UNLOCKED) {
            lockedInput = input.copy();
            mode = reagent.isEmpty() ? DRY : isBlood(reagent.getFluid()) ? BLOOD : WATER;
            changed();
        }
        if (!canSettleUnit(inspection.units()) || !nativeExperienceHasRoom(input)) { tickPaused(); return; }
        ItemStack before = input.copy();
        boolean wasApplied = inventory.appliedRecipe;
        super.tick();
        if (!wasApplied && inventory.appliedRecipe) commitSalvage(before, inspection.units());
    }

    @Override public float getRelativeSpeed() { return paused ? 0 : super.getRelativeSpeed(); }
    private void tickPaused() {
        paused = true;
        try { super.tick(); } finally { paused = false; }
    }

    private boolean nativeExperienceHasRoom(ItemStack input) {
        int experience = GrindstoneHelper.getExperienceFromItem(input);
        if (experience <= 0) return true;
        FluidStack xp = new FluidStack(CEIFluids.EXPERIENCE.get(), experience);
        return tank.getPrimaryHandler().fill(xp, IFluidHandler.FluidAction.SIMULATE) == experience;
    }

    private void commitSalvage(ItemStack before, long maximum) {
        // A native cancellation or unavailable XP output leaves the enchanted input untouched.
        // Respect it: do not strip that item or accidentally bypass the native event contract.
        boolean unchanged = ItemStack.matches(before, inventory.getStackInSlot(0));
        if (unchanged && GrindstoneHelper.getExperienceFromItem(before) > 0) { unlock(); return; }
        long recovered = 0;
        boolean any = false;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack actual = inventory.getStackInSlot(i);
            // Native recipe/polishing outputs of another item retain their complete native result.
            if (!actual.is(before.getItem())) continue;
            ArcaneSalvage.Result stripped = ArcaneSalvage.strip(actual);
            if (!stripped.changed()) continue;
            inventory.setStackInSlot(i, stripped.stack());
            recovered = Math.addExact(recovered, stripped.units());
            any = true;
        }
        if (any) {
            // A third-party native event may return multiple stacks: it cannot multiply salvage.
            pendingUnits = Math.min(maximum, recovered);
            lockedInput = ItemStack.EMPTY;
            if (pendingUnits > 0) settleOneUnit();
            changed();
        } else unlock();
    }

    private boolean canSettleUnit(long units) {
        if (units <= 0) return true;
        if (mode == DRY) {
            ItemStack stack = essence.getStackInSlot(0);
            return stack.isEmpty() || stack.getCount() < stack.getMaxStackSize();
        }
        Fluid fluid = mode == BLOOD ? bloodFluid() : Fluids.WATER;
        FluidStack cost = new FluidStack(fluid, UNIT_MB);
        FluidStack produced = new FluidStack(mode == BLOOD ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), UNIT_MB);
        return reagent.drain(cost, IFluidHandler.FluidAction.SIMULATE).getAmount() == UNIT_MB
                && solution.fill(produced, IFluidHandler.FluidAction.SIMULATE) == UNIT_MB;
    }

    /** One indivisible ledger unit, never a fractional debit, extra entity, overflow or retry roll. */
    private void settleOneUnit() {
        if (pendingUnits <= 0 || !canSettleUnit(pendingUnits)) return;
        committing = true;
        if (mode == DRY) {
            ItemStack current = essence.getStackInSlot(0);
            essence.setStackInSlot(0, current.isEmpty() ? new ItemStack(ItemRegistry.ARCANE_ESSENCE.get()) : current.copyWithCount(current.getCount() + 1));
        } else {
            FluidStack remaining = reagent.getFluid().copy();
            remaining.shrink(UNIT_MB);
            FluidStack produced = solution.getFluid().isEmpty()
                    ? new FluidStack(mode == BLOOD ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), UNIT_MB)
                    : solution.getFluid().copy();
            if (!solution.getFluid().isEmpty()) produced.grow(UNIT_MB);
            // These are owned tanks, no external callback can change them between validation and commit.
            reagent.setFluid(remaining);
            solution.setFluid(produced);
        }
        pendingUnits--;
        committing = false;
        changed();
        if (pendingUnits == 0) unlock();
    }

    private void unlock() {
        if (mode != UNLOCKED || !lockedInput.isEmpty()) { mode = UNLOCKED; lockedInput = ItemStack.EMPTY; changed(); }
    }
    private void changed() { if (level != null && !level.isClientSide && !destroying && !committing) { setChanged(); sendData(); } }
    private static Fluid bloodFluid() { return ForgeRegistries.FLUIDS.getValue(new ResourceLocation("irons_spellbooks", "blood")); }
    private static boolean isBlood(FluidStack stack) { Fluid blood = bloodFluid(); return blood != null && stack.getFluid().isSame(blood); }
    private boolean acceptsReagent(FluidStack stack) {
        return isReagent(stack) && (mode != WATER && mode != BLOOD || mode == WATER && stack.getFluid().isSame(Fluids.WATER) || mode == BLOOD && isBlood(stack));
    }
    private static boolean isReagent(FluidStack stack) { return !stack.isEmpty() && (stack.getFluid().isSame(Fluids.WATER) || isBlood(stack)); }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return safeItems.cast();
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            if (side == null) return allFluids.cast();
            Direction facing = getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            if (side == facing.getClockWise()) return inputFluids.cast();
            if (side == facing.getCounterClockWise()) return outputFluids.cast();
            return LazyOptional.empty();
        }
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); safeItems.invalidate(); inputFluids.invalidate(); outputFluids.invalidate(); allFluids.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps(); safeItems = LazyOptional.of(RecoveryItems::new);
        inputFluids = LazyOptional.of(() -> new FluidsView(true)); outputFluids = LazyOptional.of(() -> new FluidsView(false)); allFluids = LazyOptional.of(() -> new FluidsView(null));
    }

    @Override protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("ArcaneRecovery", recoveryTag());
        tag.put("RecoveryEssence", essence.serializeNBT());
    }
    @Override protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        CompoundTag recovery = tag.getCompound("ArcaneRecovery");
        reagent.readFromNBT(recovery.getCompound("Reagent")); solution.readFromNBT(recovery.getCompound("Solution"));
        pendingUnits = Math.max(0L, recovery.getLong("PendingUnits"));
        mode = recovery.contains("Mode") ? recovery.getInt("Mode") : UNLOCKED;
        if (mode < UNLOCKED || mode > BLOOD || (pendingUnits > 0 && mode == UNLOCKED)) mode = DRY;
        lockedInput = ItemStack.of(recovery.getCompound("LockedInput"));
        essence.deserializeNBT(tag.getCompound("RecoveryEssence"));
    }
    private CompoundTag recoveryTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Reagent", reagent.writeToNBT(new CompoundTag())); tag.put("Solution", solution.writeToNBT(new CompoundTag()));
        tag.putLong("PendingUnits", pendingUnits); tag.putInt("Mode", mode); tag.put("LockedInput", lockedInput.save(new CompoundTag()));
        return tag;
    }
    /** No native inventory or essence stack is copied: they drop separately, exactly once. */
    public CompoundTag savePortableRecovery() { CompoundTag tag = new CompoundTag(); tag.put("ArcaneRecovery", recoveryTag()); return tag; }

    @Override public void destroy() {
        if (destroying) return;
        destroying = true;
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < inventory.getSlots(); i++) { ItemStack stack = inventory.getStackInSlot(i); if (!stack.isEmpty()) drops.add(stack.copy()); }
        ItemStack extra = essence.getStackInSlot(0); if (!extra.isEmpty()) drops.add(extra.copy());
        inventory.clear(); essence.setStackInSlot(0, ItemStack.EMPTY);
        // Native processedItem is a visual snapshot, not a second owned item.
        ((GrindstoneDrainAccessor) (Object) this).arcane$setProcessedItem(ItemStack.EMPTY);
        super.destroy();
        if (level != null && !level.isClientSide) for (ItemStack drop : drops)
            Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, drop);
    }
    @Override public void clearContent() {
        super.clearContent(); essence.setStackInSlot(0, ItemStack.EMPTY); pendingUnits = 0; unlock(); changed();
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        boolean result = super.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(Component.translatable("tooltip.arcane_industry.grindstone.ledger", pendingUnits));
        tooltip.add(Component.translatable("tooltip.arcane_industry.grindstone.mode." + (mode == BLOOD ? "blood" : mode == WATER ? "water" : mode == DRY ? "dry" : "idle")));
        tooltip.add(Component.translatable("tooltip.arcane_industry.grindstone.reagent", reagent.getFluidAmount(), reagent.getCapacity()));
        tooltip.add(Component.translatable("tooltip.arcane_industry.grindstone.solution", solution.getFluidAmount(), solution.getCapacity()));
        return true;
    }

    private final class RecoveryItems implements IItemHandler {
        @Override public int getSlots() { return inventory.getSlots() + 1; }
        @Override public ItemStack getStackInSlot(int slot) { return slot == inventory.getSlots() ? essence.getStackInSlot(0).copy() : slot >= 0 && slot < inventory.getSlots() ? inventory.getStackInSlot(slot).copy() : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return slot == inventory.getSlots() ? 64 : inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && pendingUnits == 0 && essence.getStackInSlot(0).isEmpty() && inventory.isEmpty() && inventory.isItemValid(slot, stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack) || tank == null) return stack;
            // Native ProcessingInventory's callback starts processing even for SIMULATE. Never call
            // that method for simulation; the native validation and one-item limit are reproduced.
            int space = tank.getPrimaryHandler().getSpace();
            if (GrindstoneHelper.getExperienceFromItem(stack) > space || level == null || GrindstoneHelper.getExperienceFromGrindingRecipe(level, stack) > space) return stack;
            ItemStack remainder = stack.copy(); remainder.shrink(Math.min(1, stack.getCount()));
            if (!simulate) { ItemStack actualRemainder = inventory.insertItem(slot, stack, false); changed(); return actualRemainder; }
            return remainder;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            if (slot == 3000) {
                for (int i = 0; i < getSlots(); i++) if (!getStackInSlot(i).isEmpty()) return extractSafely(i, amount, simulate, true);
                return ItemStack.EMPTY;
            }
            return extractSafely(slot, amount, simulate, false);
        }
        private ItemStack extractSafely(int slot, int amount, boolean simulate, boolean manual) {
            if (slot == inventory.getSlots()) return essence.extractItem(0, amount, simulate);
            if (slot < 0 || slot >= inventory.getSlots()) return ItemStack.EMPTY;
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            // Automation cannot pull an input or an uncommitted native result mid-process.
            if (!manual && !inventory.appliedRecipe && inventory.remainingTime > 0) return ItemStack.EMPTY;
            ItemStack extracted = stack.copyWithCount(Math.min(amount, stack.getCount()));
            if (!simulate) {
                ItemStack rest = stack.copy(); rest.shrink(extracted.getCount()); inventory.setStackInSlot(slot, rest);
                if (inventory.isEmpty()) { inventory.clear(); inventory.remainingTime = -1; if (pendingUnits == 0) unlock(); }
                changed();
            }
            return extracted;
        }
    }

    /** Native XP/recipe tank is preserved as a separate service tank on both pipe faces. */
    private final class FluidsView implements IFluidHandler {
        private final Boolean input;
        FluidsView(Boolean input) { this.input = input; }
        private IFluidHandler nativeTank() { return tank == null ? null : tank.getPrimaryHandler(); }
        @Override public int getTanks() { return input == null ? 3 : 2; }
        private int mappedTank(int index) {
            if (index < 0 || index >= getTanks()) return -1;
            return input == null ? index : index == 1 ? 2 : input ? 0 : 1;
        }
        @Override public FluidStack getFluidInTank(int index) {
            int mapped = mappedTank(index);
            return mapped == 0 ? reagent.getFluid().copy() : mapped == 1 ? solution.getFluid().copy()
                    : mapped == 2 && nativeTank() != null ? nativeTank().getFluidInTank(0).copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int index) {
            int mapped = mappedTank(index);
            return mapped == 0 || mapped == 1 ? TANK_CAPACITY : mapped == 2 && nativeTank() != null ? nativeTank().getTankCapacity(0) : 0;
        }
        @Override public boolean isFluidValid(int index, FluidStack stack) {
            int mapped = mappedTank(index);
            return mapped == 0 ? input != Boolean.FALSE && acceptsReagent(stack)
                    : mapped == 2 && nativeTank() != null && nativeTank().isFluidValid(0, stack);
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (stack.isEmpty()) return 0;
            // Current native recipes, including sequenced assembly, keep their original fluid path.
            if (nativeNeeds(stack)) return nativeTank() == null ? 0 : nativeTank().fill(stack, action);
            if (isReagent(stack)) return input == Boolean.FALSE || !acceptsReagent(stack) ? 0 : reagent.fill(stack, action);
            return nativeTank() == null ? 0 : nativeTank().fill(stack, action);
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            if (input != Boolean.TRUE) { FluidStack result = solution.drain(stack, action); if (!result.isEmpty()) return result; }
            if (input == null) { FluidStack result = reagent.drain(stack, action); if (!result.isEmpty()) return result; }
            return nativeTank() == null ? FluidStack.EMPTY : nativeTank().drain(stack, action);
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            if (input != Boolean.TRUE) { FluidStack result = solution.drain(amount, action); if (!result.isEmpty()) return result; }
            if (input == null) { FluidStack result = reagent.drain(amount, action); if (!result.isEmpty()) return result; }
            return nativeTank() == null ? FluidStack.EMPTY : nativeTank().drain(amount, action);
        }
        private boolean nativeNeeds(FluidStack fluid) {
            if (level == null || inventory.getStackInSlot(0).isEmpty()) return false;
            var container = new SimpleContainer(inventory.getStackInSlot(0));
            var recipe = SequencedAssemblyRecipe.getRecipe(level, container, CEIRecipes.GRINDING.getType(), GrindingRecipe.class);
            if (recipe.isEmpty()) recipe = level.getRecipeManager().getRecipeFor(CEIRecipes.GRINDING.getType(), container, level);
            return recipe.isPresent() && recipe.get().getFluidIngredients().stream().anyMatch(ingredient -> ingredient.test(fluid));
        }
    }
}
