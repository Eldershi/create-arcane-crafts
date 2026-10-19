package dev.expedition.arcane.chamber;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.compat.ScrollAscensionRecipes;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.item.UpgradeOrbTypeData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import plus.dragons.createenchantmentindustry.util.BlazeLightningHelper;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AscensionChamberBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int TEMPLATE_SLOT = 0;
    public static final int SCROLL_SLOT = 1;
    public static final int INK_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    public static final int BOTTLE_SLOT = 4;
    public static final int NORMAL_FUEL_SLOT = 5;
    public static final int SUPER_FUEL_SLOT = 6;
    public static final int CAKE_SLOT = 7;
    public static final int TICKS_PER_MB = 20;
    public static final int BOTTLE_AMOUNT = 250;
    public static final int BUCKET_AMOUNT = 1000;
    public static final int FUEL_DURATION = BOTTLE_AMOUNT * TICKS_PER_MB;
    public static final int PROCESSING_TIME = 80;
    public static final int MANUAL_PROCESSING_TIME = 70;
    private static final int MANUAL_BOOK_CLOSE_TICKS = 11;
    public static final int BASE_SOLUTION_COST = 250;
    public static final int SOLUTION_CAPACITY = 4000;
    /** Matches Create Enchantment Industry's native BlazeLightningHelper. */
    public static final int LIGHTNING_ROD_RANGE = 128;
    private static final ResourceLocation NORMAL_FUEL = new ResourceLocation("irons_spellbooks", "arcane_essence");
    private static final ResourceLocation BRASS_INGOT = new ResourceLocation("create", "brass_ingot");
    private static final ResourceLocation MITHRIL_INGOT = new ResourceLocation("irons_spellbooks", "mithril_ingot");

    public final ItemStackHandler inventory = new ItemStackHandler(8) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            return switch (slot) {
                case TEMPLATE_SLOT -> stack.is(ArcaneContent.TEMPLATE.get());
                case SCROLL_SLOT -> !manualProcessing && !manualResultReady
                        && (ManualUpgradeRecipes.isPrimary(stack) || RandomResearchRules.isBlankScroll(stack)
                        || UnfinishedAscensionScrollItem.isPreparedStage(stack));
                case INK_SLOT -> !manualProcessing && !manualResultReady
                        && (ManualUpgradeRecipes.isModifier(stack) || RandomResearchRules.isResearchFocus(stack));
                case NORMAL_FUEL_SLOT -> isArcaneEssence(stack) && canAcceptEssence();
                // Slot 6 remains readable for old saves; cinder essence is no longer a fuel input.
                case CAKE_SLOT -> stack.is(CrimsonContent.EMBER_CAKE.get());
                default -> false;
            };
        }
        @Override public int getSlotLimit(int slot) {
            return slot == TEMPLATE_SLOT || slot == SCROLL_SLOT ? 1 : 64;
        }
        @Override protected void onContentsChanged(int slot) { inventoryContentsChanged(slot); }
    };

    public final FluidTank sourceTank = new FluidTank(SOLUTION_CAPACITY) {
        @Override public boolean isFluidValid(FluidStack stack) {
            return !stack.isEmpty() && (ArcaneContent.SOURCE.get().isSame(stack.getFluid())
                    || CrimsonContent.SOURCE.get().isSame(stack.getFluid()));
        }
        @Override public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
            if (!isFluidValid(stack)) return 0;
            boolean crimson = isCrimson(stack);
            SolutionConversion.FillPlan plan = SolutionConversion.plan(getFluidAmount(), isCrimson(getFluid()),
                    crimsonCreditQuarterMb, crimson, stack.getAmount(), getCapacity());
            if (action.execute() && plan.acceptedMb() > 0) {
                crimsonCreditQuarterMb = plan.crimsonCreditQuarterMb();
                setFluid(new FluidStack(crimson ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), plan.storedMb()));
                onContentsChanged();
            }
            return plan.acceptedMb();
        }
        @Override public FluidStack drain(int amount, IFluidHandler.FluidAction action) {
            FluidStack drained = super.drain(amount, action);
            if (action.execute() && !drained.isEmpty() && isEmpty() && crimsonCreditQuarterMb != 0) {
                crimsonCreditQuarterMb = 0;
                onContentsChanged();
            }
            return drained;
        }
        @Override protected void onContentsChanged() { contentsChanged(); }
    };

    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return inventory.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == RESULT_SLOT || slot == BOTTLE_SLOT ? stack : inventory.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (manualProcessing) return ItemStack.EMPTY;
            if (slot == INK_SLOT && !manualResultReady && RandomResearchRules.isResearchFocus(inventory.getStackInSlot(slot))) {
                return inventory.extractItem(slot, amount, simulate);
            }
            return slot == RESULT_SLOT || slot == BOTTLE_SLOT || slot == SUPER_FUEL_SLOT
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
    };
    private final IFluidHandler fluidInput = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) {
            FluidTank selected = tankAt(tank);
            return selected == null ? FluidStack.EMPTY : selected.getFluid().copy();
        }
        @Override public int getTankCapacity(int tank) {
            FluidTank selected = tankAt(tank);
            return selected == null ? 0 : selected.getCapacity();
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            FluidTank selected = tankAt(tank);
            return selected != null && selected.isFluidValid(stack);
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return sourceTank.fill(stack, action);
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return sourceTank.drain(stack, action);
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return sourceTank.drain(amount, action);
        }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automation);
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidInput);
    private int normalFuelTicks;
    private int superFuelTicks;
    /** Fractional crimson processing remainder; never exchanged into blue liquid or a bucket. */
    private int crimsonCreditQuarterMb;
    private int progress;
    private ItemStack activeScroll = ItemStack.EMPTY;
    private AscensionChamberBlock.Skin skin = AscensionChamberBlock.Skin.COPPER;
    private boolean blazeAlive = true;
    private boolean residualFire;
    /** Cover the native bolt's short delayed flashes even if the chamber is immediately restored. */
    private int lightningFireCleanupTicks;
    private boolean migrateLegacyExternalFire;
    private ItemStack gogglesDecoration = ItemStack.EMPTY;
    private ItemStack crownDecoration = ItemStack.EMPTY;
    private boolean manualProcessing;
    private int manualProgress;
    private long manualStartedAt = -1;
    private long manualCompletedAt = -1;
    private long manualItemPlacedAt = -1;
    private long manualMaterialPlacedAt = -1;
    private String manualOperation = "";
    private boolean manualResultReady;
    private boolean manualBookSelectionLocked;
    private int selectedBookSpellSlot = -1;
    private int manualSelectionRevision;
    private long manualSelectedAt = -1;
    private ItemStack selectionPrimary = ItemStack.EMPTY;
    /** Immutable transaction description: a reload or input change cannot switch an in-flight recipe. */
    private CompoundTag manualTransaction = new CompoundTag();
    /** First return occupies slot 4; every other native shriving refund is retained here. */
    private final List<ItemStack> manualExtraReturns = new ArrayList<>();
    private boolean manualMutation;

    public AscensionChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ArcaneContent.CHAMBER_ENTITY.get(), pos, state);
        skin = state.getValue(AscensionChamberBlock.SKIN);
        blazeAlive = state.getValue(AscensionChamberBlock.ALIVE);
        residualFire = !blazeAlive && state.getValue(AscensionChamberBlock.BURNING);
    }

    public boolean hasTemplate() { return inventory.getStackInSlot(TEMPLATE_SLOT).is(ArcaneContent.TEMPLATE.get()); }
    public String getSkinKey() { return skin.getSerializedName(); }
    public boolean hasGoggles() { return !gogglesDecoration.isEmpty(); }
    /** Legacy saves may retain both originals; only the goggles render until removed. */
    public boolean hasNecromancerCrown() { return gogglesDecoration.isEmpty() && !crownDecoration.isEmpty(); }
    public boolean isBlazeAlive() { return blazeAlive; }
    public boolean hasResidualFire() { return !blazeAlive && residualFire; }

    /** Create's native empty-burner tools, acting only on the decorative empty-cage fire. */
    public boolean interactResidualFire(ItemStack held, Player player, InteractionHand hand) {
        if (blazeAlive || held.isEmpty() || level == null || level.isClientSide) return false;
        boolean shovel = held.getItem() instanceof ShovelItem
                || held.getItem().canPerformAction(held, LitBlazeBurnerBlock.EXTINGUISH_FLAME_ACTION);
        if (hasResidualFire() && shovel) {
            extinguishResidualFire();
            held.hurtAndBreak(1, player, actor -> actor.broadcastBreakEvent(hand));
            return true;
        }
        if (held.getItem() instanceof FlintAndSteelItem) {
            if (!hasResidualFire()) {
                residualFire = true;
                level.playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                        1, level.random.nextFloat() * 0.4F + 0.8F);
                held.hurtAndBreak(1, player, actor -> actor.broadcastBreakEvent(hand));
                clearExternalSelfFire();
                contentsChanged();
            }
            return true;
        }
        return false;
    }

    /** Empty-hand left click uses this through a cancellable Forge event before block destruction. */
    public boolean extinguishResidualFire() {
        if (!hasResidualFire() || level == null || level.isClientSide) return false;
        residualFire = false;
        clearExternalSelfFire();
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5F, 2);
        contentsChanged();
        return true;
    }

    private void clearExternalSelfFire() {
        if (level != null && !level.isClientSide
                && level.getBlockState(worldPosition.above()).getBlock() instanceof BaseFireBlock) {
            // Only this cage's exact cell above is folded into its own removable fire layer.
            // Adjacent ordinary lightning fire and player structures are untouched.
            level.removeBlock(worldPosition.above(), false);
        }
    }
    public List<ItemStack> getDecorationDrops() {
        return java.util.stream.Stream.of(gogglesDecoration, crownDecoration)
                .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    /** Empty hand removes the visible headpiece after the selected machine slots are empty. */
    public boolean removeDecorationForPlayer(Player player) {
        if (level == null || level.isClientSide) return false;
        ItemStack returned;
        if (!gogglesDecoration.isEmpty()) {
            returned = gogglesDecoration;
            gogglesDecoration = ItemStack.EMPTY;
        } else if (!crownDecoration.isEmpty()) {
            returned = crownDecoration;
            crownDecoration = ItemStack.EMPTY;
        } else return false;
        // Move the stored original, including its complete NBT, rather than creating a replacement.
        ItemHandlerHelper.giveItemToPlayer(player, returned.copy());
        contentsChanged();
        return true;
    }

    /** Reversible cosmetic interactions never alter a recipe's progress or inventory. */
    public boolean interactCosmetic(ItemStack held, Player player) {
        if (held.isEmpty() || level == null || level.isClientSide) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
        AscensionChamberBlock.Skin selected = held.is(Items.COPPER_INGOT) ? AscensionChamberBlock.Skin.COPPER
                : BRASS_INGOT.equals(id) ? AscensionChamberBlock.Skin.BRASS
                : MITHRIL_INGOT.equals(id) ? AscensionChamberBlock.Skin.MITHRIL : null;
        if (selected != null) {
            if (skin != selected) {
                skin = selected;
                if (!player.getAbilities().instabuild) held.shrink(1);
                contentsChanged();
            }
            return true;
        }
        boolean goggles = AllItems.GOGGLES.isIn(held);
        if (!goggles && !ChamberDecorations.NECROMANCER_CROWN_ID.equals(id)) return false;
        if (blazeAlive && gogglesDecoration.isEmpty() && crownDecoration.isEmpty()) {
            if (goggles) gogglesDecoration = held.copyWithCount(1);
            else crownDecoration = held.copyWithCount(1);
            // Headpieces are stored possessions, so transfer one even in creative.
            // Returning it later must not produce an extra copy of its NBT-bearing original.
            held.shrink(1);
            contentsChanged();
        }
        return true;
    }

    /** Transfer the captured blaze into an intact empty chamber and return its empty carrier. */
    public boolean restoreBlaze(ItemStack held, Player player) {
        if (blazeAlive || held.isEmpty() || !held.is(AllBlocks.BLAZE_BURNER.asItem())
                || level == null || level.isClientSide) return false;
        blazeAlive = true;
        residualFire = false;
        clearExternalSelfFire();
        clearProgress();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, AllItems.EMPTY_BLAZE_BURNER.asStack());
        }
        contentsChanged();
        return true;
    }

    public CompoundTag savePortableAppearance() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Skin", getSkinKey());
        tag.putBoolean("BlazeAlive", blazeAlive);
        tag.putBoolean("ResidualFire", hasResidualFire());
        tag.putInt("LightningFireCleanupTicks", lightningFireCleanupTicks);
        return tag;
    }
    public boolean hasCake() { return inventory.getStackInSlot(CAKE_SLOT).is(CrimsonContent.EMBER_CAKE.get()); }
    public boolean isTranscendent() {
        return !sourceTank.isEmpty() ? isCrimson(sourceTank.getFluid())
                : superFuelTicks >= ((BASE_SOLUTION_COST - crimsonCreditQuarterMb + 3) / 4) * TICKS_PER_MB;
    }
    /** Energy equivalent retained for compatibility; it does not tick down while idle. */
    public int getFuelTicks() {
        long available = (isTranscendent() ? superFuelTicks : normalFuelTicks)
                + (long) sourceTank.getFluidAmount() * TICKS_PER_MB;
        return (int) Math.min(Integer.MAX_VALUE, available);
    }
    public int getNormalFuelTicks() { return normalFuelTicks; }
    public int getSuperFuelTicks() { return superFuelTicks; }
    public int getCrimsonCreditQuarterMb() { return crimsonCreditQuarterMb; }
    public long getAvailableNormalFuelTicks() {
        ItemStack essence = inventory.getStackInSlot(NORMAL_FUEL_SLOT);
        long stored = !sourceTank.isEmpty() && !isCrimson(sourceTank.getFluid())
                ? (long) sourceTank.getFluidAmount() * TICKS_PER_MB : 0;
        return (long) normalFuelTicks + stored
                + (isArcaneEssence(essence) ? (long) essence.getCount() * FUEL_DURATION : 0);
    }
    public long getAvailableSuperFuelTicks() {
        return (long) superFuelTicks + (isCrimson(sourceTank.getFluid())
                ? (long) sourceTank.getFluidAmount() * TICKS_PER_MB : 0);
    }
    public int getProgress() { return manualProcessing ? manualProgress : progress; }
    public boolean isManualProcessing() { return manualProcessing; }
    public boolean isResearchProcessing() {
        return manualProcessing && RandomResearchRules.OPERATION.equals(manualOperation)
                && RandomResearchRules.OPERATION.equals(manualTransaction.getString("Kind"));
    }
    public int getManualProgress() { return manualProgress; }
    // Presentation metadata only. Work, payment and the selected result remain server-owned.
    private boolean researchVisualRunning;
    private long researchVisualSampleAt;
    public double getResearchVisualTicks(double now) {
        return Math.min(MANUAL_PROCESSING_TIME, manualProgress
                + (researchVisualRunning ? Math.max(0, Math.min(10, now - researchVisualSampleAt)) : 0));
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(.9, 1.6, .9);
    }
    public long getManualStartedAt() { return manualStartedAt; }
    public long getManualCompletedAt() { return manualCompletedAt; }
    public long getManualItemPlacedAt() { return manualItemPlacedAt; }
    public long getManualMaterialPlacedAt() { return manualMaterialPlacedAt; }
    public String getManualOperation() { return manualOperation; }
    public ItemStack getManualPrimary() {
        if (isResearchProcessing()) return ItemStack.of(manualTransaction.getCompound("Primary"));
        return inventory.getStackInSlot(manualResultReady ? RESULT_SLOT : SCROLL_SLOT).copy();
    }
    public ItemStack getManualSecondary() {
        if (isResearchProcessing()) return ItemStack.of(manualTransaction.getCompound("Modifier"));
        ItemStack stack = inventory.getStackInSlot(manualResultReady ? BOTTLE_SLOT : INK_SLOT);
        if (stack.isEmpty() || stack.getItem() instanceof InkItem || stack.is(Items.GLASS_BOTTLE)) return ItemStack.EMPTY;
        return !manualResultReady || UpgradeOrbTypeData.has(stack) ? stack.copy() : ItemStack.EMPTY;
    }
    public ItemStack getManualResultSecondary() { return inventory.getStackInSlot(BOTTLE_SLOT).copy(); }
    public boolean isManualBookSelectionLocked() { return manualBookSelectionLocked; }
    public int getSelectedBookSpellSlot() { return selectedBookSpellSlot; }
    public int getManualSelectionRevision() { return manualSelectionRevision; }
    public List<SpellData> getManualBookSpells() { return ManualUpgradeRecipes.selectableSpells(getManualPrimary()); }

    /** The packet handler also checks reach and the exact block ray hit before calling this. */
    public boolean selectBookSpell(Player player, int slot, int revision) {
        if (level == null || level.isClientSide || player.level() != level || !player.isAlive() || player.isSpectator()
                || manualProcessing || manualResultReady || manualBookSelectionLocked || getBasin() != null
                || revision != manualSelectionRevision) return false;
        List<SpellData> spells = ManualUpgradeRecipes.selectableSpells(inventory.getStackInSlot(SCROLL_SLOT));
        if (slot < 0 || slot >= spells.size()) return false;
        SpellData selected = spells.get(slot);
        if (selected == null || selected.getSpell().equals(SpellRegistry.none())) return false;
        selectedBookSpellSlot = slot;
        manualBookSelectionLocked = true;
        manualSelectedAt = level.getGameTime();
        contentsChanged();
        return true;
    }

    public List<ItemStack> getAdditionalReturnDrops() {
        List<ItemStack> drops = new ArrayList<>(manualExtraReturns.stream().map(ItemStack::copy).toList());
        // Research is paid at start. Its persisted pending scroll is owned even while the timer is paused.
        // The blank is no longer in the inventory, so destruction drops this result exactly once.
        if (isResearchProcessing()) {
            ItemStack pending = ItemStack.of(manualTransaction.getCompound("Output"));
            if (!pending.isEmpty()) drops.add(pending);
        }
        return List.copyOf(drops);
    }

    /** Clear the complete ownership record before handing any stack to the player. */
    public boolean takeManualOutputs(Player player) {
        if (level == null || level.isClientSide || manualProcessing || !manualResultReady) return false;
        List<ItemStack> returned = new ArrayList<>();
        manualMutation = true;
        try {
            for (int slot : new int[]{RESULT_SLOT, BOTTLE_SLOT}) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) returned.add(stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
            returned.addAll(getAdditionalReturnDrops());
            manualExtraReturns.clear();
            clearManualResult();
        } finally { manualMutation = false; }
        contentsChanged();
        returned.forEach(stack -> ItemHandlerHelper.giveItemToPlayer(player, stack));
        return true;
    }
    public boolean isBasinMode() { return getBasin() != null; }
    /** Visual working pose; tanks do not become valid scroll-processing inputs. */
    public boolean hasWorkBlockAbove() {
        return level != null && (isBasinMode()
                || level.getBlockState(worldPosition.above()).getBlock() instanceof FluidTankBlock);
    }
    public IItemHandler getAutomationInventory() { return automation; }
    public FluidTank getSourceTank() { return sourceTank; }
    @Nullable private FluidTank tankAt(int tank) {
        return tank == 0 ? sourceTank : null;
    }

    public static void serverTicker(Level level, BlockPos pos, BlockState state, AscensionChamberBlockEntity chamber) {
        if (!level.isClientSide) chamber.tickServer();
    }

    /** Ambient magic is ticked once per client game tick, independently of render frequency. */
    public static void clientTicker(Level level, BlockPos pos, BlockState state, AscensionChamberBlockEntity chamber) {
        if (!level.isClientSide) return;
        int mode = state.getValue(AscensionChamberBlock.STATE);
        if (!chamber.isBlazeAlive() || mode == 0 || chamber.isManualProcessing()
                || chamber.getManualCompletedAt() >= 0 || level.random.nextInt(4) != 0) return;
        double angle = level.random.nextDouble() * Math.PI * 2;
        double radius = 0.32 + level.random.nextDouble() * 0.12;
        double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
        double y = pos.getY() + 0.24 + level.random.nextDouble() * 0.26;
        double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
        level.addParticle(mode == 2 ? ArcaneContent.CLEANSE_BLUE.get() : ArcaneContent.CLEANSE_GOLD.get(),
                x, y, z, 0, 0.018 + level.random.nextDouble() * 0.02, 0);
    }

    public void tickServer() {
        if (level == null || level.isClientSide) return;
        if (migrateLegacyExternalFire) {
            if (!blazeAlive && level.getBlockState(worldPosition.above()).getBlock() instanceof BaseFireBlock) residualFire = true;
            migrateLegacyExternalFire = false;
        }
        if (!blazeAlive || lightningFireCleanupTicks > 0) clearExternalSelfFire();
        if (lightningFireCleanupTicks > 0) lightningFireCleanupTicks--;
        if (blazeAlive) convertQueuedEssence();
        if (isResearchProcessing()) {
            // Adding a basin, unloading, headwear changes or output blockage never invalidate a paid draw.
            clearProgress();
            tickResearchWork();
        } else if (getBasin() != null) {
            // The actual powered Mechanical Mixer is the only basin commit clock.
            cancelManualWork();
            clearProgress();
        } else if (UnfinishedAscensionScrollItem.isPreparedStage(inventory.getStackInSlot(SCROLL_SLOT))
                && !manualResultReady) {
            // Preserve already paid intermediate items from earlier versions.
            cancelManualWork();
            Optional<Plan> found = findPlan();
            if (found.isPresent()) {
                Plan plan = found.get();
                if (!ItemStack.isSameItemSameTags(activeScroll, plan.input())) {
                    progress = 0;
                    activeScroll = plan.input().copy();
                }
                if (++progress >= PROCESSING_TIME) { commit(plan); clearProgress(); }
            } else clearProgress();
        } else {
            clearProgress();
            tickManualWork();
        }
        updateMode();
        setChanged();
        if (level.getGameTime() % 10 == 0) sync();
    }

    private void convertQueuedEssence() {
        // Essence replaces any nonfull crimson tank with blue. A physically full tank
        // leaves every queued item untouched; excess blue from one item is discarded.
        ItemStack queued = inventory.getStackInSlot(NORMAL_FUEL_SLOT);
        FluidStack blueBottle = new FluidStack(ArcaneContent.SOURCE.get(), BOTTLE_AMOUNT);
        if (isArcaneEssence(queued) && canAcceptEssence()) {
            if (sourceTank.fill(blueBottle, IFluidHandler.FluidAction.EXECUTE) > 0) {
                inventory.extractItem(NORMAL_FUEL_SLOT, 1, false);
            }
        }
    }

    private long requiredWholeMb(long quarterMb, boolean crimson) {
        long cost = quarterMb - (crimson ? crimsonCreditQuarterMb : 0);
        return Math.max(0, (cost + 3) / 4);
    }
    private long requiredWholeMb(ScrollUpgradeRules.Upgrade upgrade, boolean crimson) {
        return requiredWholeMb(upgrade.solutionCostQuarterMb(), crimson);
    }
    private boolean hasWorkingFuel(long quarterMb, boolean crimson) {
        long legacy = crimson ? superFuelTicks : normalFuelTicks;
        return (long) sourceTank.getFluidAmount() * TICKS_PER_MB + legacy
                >= requiredWholeMb(quarterMb, crimson) * TICKS_PER_MB;
    }
    private boolean hasWorkingFuel(ScrollUpgradeRules.Upgrade upgrade) {
        return hasWorkingFuel(upgrade.solutionCostQuarterMb(), isTranscendent());
    }

    /** Called only after the fully validated item/output transaction succeeds. */
    private void consumeWorkingFuel(long quarterMb, boolean crimson) {
        int oldCredit = crimsonCreditQuarterMb;
        long amount = requiredWholeMb(quarterMb, crimson);
        int fromTank = (int) Math.min(amount, sourceTank.getFluidAmount());
        sourceTank.drain(fromTank, IFluidHandler.FluidAction.EXECUTE);
        int legacyCost = (int) ((amount - fromTank) * TICKS_PER_MB);
        if (crimson) superFuelTicks -= legacyCost;
        else normalFuelTicks -= legacyCost;
        if (crimson) crimsonCreditQuarterMb = (int) (oldCredit + amount * 4 - quarterMb);
    }
    private void consumeWorkingFuel(ScrollUpgradeRules.Upgrade upgrade, boolean crimson) {
        consumeWorkingFuel(upgrade.solutionCostQuarterMb(), crimson);
    }

    private Optional<ManualPlan> findManualPlan() {
        if (level == null || !blazeAlive || !hasTemplate() || getBasin() != null || manualResultReady
                || !inventory.getStackInSlot(RESULT_SLOT).isEmpty()
                || !inventory.getStackInSlot(BOTTLE_SLOT).isEmpty() || !manualExtraReturns.isEmpty()) return Optional.empty();
        ItemStack primary = inventory.getStackInSlot(SCROLL_SLOT);
        ItemStack modifier = inventory.getStackInSlot(INK_SLOT);
        if (primary.getItem() instanceof ISpellbook && modifier.getItem() instanceof InkItem
                && (!manualBookSelectionLocked || manualSelectedAt < 0
                || level.getGameTime() - manualSelectedAt < MANUAL_BOOK_CLOSE_TICKS)) return Optional.empty();
        boolean crimson = isTranscendent();
        Optional<ManualUpgradeRecipes.Operation> recipe = ManualUpgradeRecipes.find(level, primary, modifier,
                selectedBookSpellSlot, manualBookSelectionLocked, crimson, hasCake());
        if (recipe.isEmpty()) return Optional.empty();
        var operation = recipe.get();
        if (operation.output().isEmpty() || operation.output().getCount() != 1
                || operation.modifierCount() < 1 || modifier.getCount() < operation.modifierCount()
                || !hasWorkingFuel(operation.solutionCostQuarterMb(), crimson)) return Optional.empty();
        return Optional.of(new ManualPlan(operation, primary.copyWithCount(1),
                modifier.copyWithCount(operation.modifierCount()), crimson));
    }

    private void tickManualWork() {
        if (!manualProcessing && tryStartResearch()) return;
        Optional<ManualPlan> found = findManualPlan();
        if (manualProcessing) {
            if (found.isEmpty() || !manualTransaction.equals(describeManualPlan(found.get()))) {
                cancelManualWork();
                contentsChanged();
                return;
            }
            if (++manualProgress >= MANUAL_PROCESSING_TIME) {
                commitManual(found.get());
                if (manualProcessing) cancelManualWork();
                contentsChanged();
            }
        } else if (found.isPresent()) {
            manualProcessing = true;
            manualProgress = 0;
            manualStartedAt = level.getGameTime();
            manualCompletedAt = -1;
            manualOperation = found.get().operation().kind();
            manualTransaction = describeManualPlan(found.get());
            contentsChanged();
        }
    }

    /**
     * Atomic ownership transfer: exactly one blank and 500 physical mB become one persisted result.
     * The animation exposes that already-owned result later; it cannot refund, reroll or charge again.
     * Upgrade quarter-mB credit is deliberately not consulted by this new-spell operation.
     */
    private boolean tryStartResearch() {
        if (level == null || level.isClientSide || !blazeAlive || !hasTemplate() || getBasin() != null
                || manualProcessing || manualResultReady || !manualExtraReturns.isEmpty()
                || !inventory.getStackInSlot(RESULT_SLOT).isEmpty()
                || !inventory.getStackInSlot(BOTTLE_SLOT).isEmpty()
                || sourceTank.getFluidAmount() < RandomResearchRules.SOLUTION_COST_MB
                || !sourceTank.isFluidValid(sourceTank.getFluid())) return false;
        ItemStack blank = inventory.getStackInSlot(SCROLL_SLOT);
        ItemStack guide = inventory.getStackInSlot(INK_SLOT);
        if (!RandomResearchRules.isBlankScroll(blank)) return false;
        Optional<RandomResearchRules.Pool> pool = RandomResearchRules.poolForFocus(guide);
        if (pool.isEmpty()) return false;
        boolean crimson = isCrimson(sourceTank.getFluid());
        boolean headwear = hasGoggles() || hasNecromancerCrown();
        Optional<RandomResearchRules.Draw> chosen = RandomResearchRules.draw(pool.get(), crimson, headwear, level.random);
        if (chosen.isEmpty() || chosen.get().output().isEmpty() || chosen.get().output().getCount() != 1) return false;
        var draw = chosen.get();
        CompoundTag transaction = new CompoundTag();
        transaction.putString("Kind", RandomResearchRules.OPERATION);
        transaction.put("Primary", blank.copyWithCount(1).save(new CompoundTag()));
        transaction.put("Modifier", guide.copyWithCount(1).save(new CompoundTag()));
        transaction.put("Output", draw.output().save(new CompoundTag()));
        transaction.putString("School", pool.get().school().getId().toString());
        transaction.putString("Spell", draw.spell().getSpellResource().toString());
        transaction.putInt("DrawnTier", draw.drawnTier());
        transaction.putInt("ActualTier", draw.actualTier());
        transaction.putInt("PhysicalCostMb", RandomResearchRules.SOLUTION_COST_MB);
        transaction.putBoolean("Crimson", crimson);
        transaction.putBoolean("Headwear", headwear);
        transaction.put("Goggles", gogglesDecoration.save(new CompoundTag()));
        transaction.put("Crown", crownDecoration.save(new CompoundTag()));
        manualMutation = true;
        try {
            // Both handlers belong to this block entity and invoke no external capability callbacks.
            inventory.extractItem(SCROLL_SLOT, 1, false);
            sourceTank.drain(RandomResearchRules.SOLUTION_COST_MB, IFluidHandler.FluidAction.EXECUTE);
            manualTransaction = transaction;
            manualOperation = RandomResearchRules.OPERATION;
            manualProcessing = true;
            manualProgress = 0;
            manualStartedAt = level.getGameTime();
            manualCompletedAt = -1;
        } finally { manualMutation = false; }
        contentsChanged();
        return true;
    }

    private void tickResearchWork() {
        if (!isResearchProcessing() || level == null) return;
        // Elapsed render time follows paid work ticks, including after a saved chunk is reloaded.
        manualStartedAt = level.getGameTime() - manualProgress;
        ItemStack output = ItemStack.of(manualTransaction.getCompound("Output"));
        boolean canAdvance = blazeAlive && hasTemplate() && getBasin() == null && !output.isEmpty()
                && fitsOutput(RESULT_SLOT, output) && inventory.getStackInSlot(BOTTLE_SLOT).isEmpty();
        if (researchVisualRunning != canAdvance) {
            researchVisualRunning = canAdvance;
            sync(); // Freeze/resume the visual clock immediately when paid work pauses/resumes.
        }
        if (!canAdvance) return;
        if (++manualProgress < MANUAL_PROCESSING_TIME) return;
        manualMutation = true;
        try {
            putOutput(RESULT_SLOT, output);
            manualTransaction = new CompoundTag();
            manualProcessing = false;
            researchVisualRunning = false;
            manualResultReady = true;
            manualCompletedAt = level.getGameTime();
        } finally { manualMutation = false; }
        contentsChanged();
    }

    private CompoundTag describeManualPlan(ManualPlan plan) {
        CompoundTag tag = new CompoundTag();
        var operation = plan.operation();
        tag.putString("Kind", operation.kind());
        tag.put("Primary", plan.primary().save(new CompoundTag()));
        tag.put("Modifier", plan.modifier().save(new CompoundTag()));
        tag.put("Output", operation.output().save(new CompoundTag()));
        tag.put("Return", operation.byproduct().save(new CompoundTag()));
        ListTag additional = new ListTag();
        operation.additionalByproducts().forEach(stack -> additional.add(stack.save(new CompoundTag())));
        tag.put("Additional", additional);
        tag.putLong("Cost", operation.solutionCostQuarterMb());
        tag.putBoolean("Cake", operation.requiresCake());
        tag.putBoolean("Crimson", plan.crimson());
        return tag;
    }

    private boolean commitManual(ManualPlan plan) {
        Optional<ManualPlan> current = findManualPlan();
        if (current.isEmpty() || !describeManualPlan(plan).equals(describeManualPlan(current.get()))) return false;
        ManualUpgradeRecipes.Operation operation = current.get().operation();
        if (operation.requiresCake() && level instanceof ServerLevel server
                && BlazeLightningHelper.strikeLightning(server, worldPosition)) {
            blazeAlive = false;
            residualFire = true;
            lightningFireCleanupTicks = 100;
            clearExternalSelfFire();
            cancelManualWork();
            contentsChanged();
            return false;
        }
        // No external capability is called while this ownership transfer is in progress.
        manualMutation = true;
        try {
            inventory.extractItem(SCROLL_SLOT, 1, false);
            inventory.extractItem(INK_SLOT, operation.modifierCount(), false);
            inventory.setStackInSlot(RESULT_SLOT, operation.output().copy());
            inventory.setStackInSlot(BOTTLE_SLOT, operation.byproduct().copy());
            manualExtraReturns.clear();
            operation.additionalByproducts().forEach(stack -> manualExtraReturns.add(stack.copy()));
            consumeWorkingFuel(operation.solutionCostQuarterMb(), current.get().crimson());
            if (operation.requiresCake()) inventory.extractItem(CAKE_SLOT, 1, false);
            manualProcessing = false;
            manualProgress = MANUAL_PROCESSING_TIME;
            manualCompletedAt = level.getGameTime();
            manualResultReady = true;
            manualOperation = operation.kind();
            manualTransaction = new CompoundTag();
            // Preserve the confirmed selection for the completed book's visual pose.
        } finally { manualMutation = false; }
        contentsChanged();
        return true;
    }

    private void cancelManualWork() {
        if (!manualProcessing) return;
        if (isResearchProcessing()) return; // A paid draw is never cancelled or selected again.
        manualProcessing = false;
        manualProgress = 0;
        manualStartedAt = -1;
        manualTransaction = new CompoundTag();
    }

    /** Queried by the native mixer recipe; these methods never advance or consume anything. */
    public boolean canProcessBasinMixing(BasinBlockEntity basin) {
        return basin != null && !manualProcessing && findPlan().filter(plan -> plan.basin() == basin).isPresent();
    }
    public ItemStack getBasinMixingInput(BasinBlockEntity basin) {
        return findPlan().filter(plan -> plan.basin() == basin && basin != null)
                .map(plan -> plan.input().copy()).orElse(ItemStack.EMPTY);
    }
    public ItemStack getBasinMixingOutput(BasinBlockEntity basin) {
        return findPlan().filter(plan -> plan.basin() == basin && basin != null)
                .map(plan -> plan.upgrade().output().copy()).orElse(ItemStack.EMPTY);
    }
    public FluidStack getBasinMixingInk(BasinBlockEntity basin) {
        return findPlan().filter(plan -> plan.basin() == basin && basin != null && !plan.prepared())
                .map(plan -> new FluidStack(plan.upgrade().inkFluid(), ScrollUpgradeRules.INK_AMOUNT))
                .orElse(FluidStack.EMPTY);
    }
    /** Caller is the powered mixer at its native commit tick, never this block entity's clock. */
    public boolean processBasinMixingCycle(BasinBlockEntity basin) {
        if (level == null || level.isClientSide || basin == null || manualProcessing) return false;
        Optional<Plan> plan = findPlan().filter(candidate -> candidate.basin() == basin);
        if (plan.isEmpty()) return false;
        boolean committed = commit(plan.get());
        clearProgress();
        return committed;
    }

    /** Executes one fully validated transaction; useful for server-side integration tests. */
    public boolean processOneCycle() {
        if (level == null || level.isClientSide) return false;
        if (manualProcessing) return false;
        if (blazeAlive) convertQueuedEssence();
        if (tryStartResearch()) return true; // Paid pending output is exposed by the normal animation timer.
        Optional<ManualPlan> manual = findManualPlan();
        if (manual.isPresent()) return commitManual(manual.get());
        Optional<Plan> plan = findPlan();
        if (plan.isEmpty()) return false;
        boolean result = commit(plan.get());
        clearProgress();
        return result;
    }

    private Optional<Plan> findPlan() {
        if (!blazeAlive || !hasTemplate() || manualProcessing || manualResultReady) return Optional.empty();
        BasinBlockEntity basin = getBasin();
        if (basin != null) {
            IFluidHandler inkTank = basin.inputTank.getCapability().orElse(null);
            for (int slot = 0; slot < basin.getInputInventory().getSlots(); slot++) {
                ItemStack input = basin.getInputInventory().getStackInSlot(slot);
                Optional<ScrollUpgradeRules.Upgrade> upgrade = ScrollAscensionRecipes.prepareUpgrade(
                        input, isTranscendent(), hasCake());
                if (upgrade.isEmpty() || !hasWorkingFuel(upgrade.get())) continue;
                boolean prepared = UnfinishedAscensionScrollItem.isPreparedStage(input);
                if (!prepared) {
                    if (inkTank == null) continue;
                    FluidStack ink = new FluidStack(upgrade.get().inkFluid(), ScrollUpgradeRules.INK_AMOUNT);
                    if (inkTank.drain(ink, IFluidHandler.FluidAction.SIMULATE).getAmount() != ink.getAmount()) continue;
                }
                if (basin.getFilter() == null || !basin.getFilter().test(upgrade.get().output())
                        || !basin.acceptOutputs(List.of(upgrade.get().output().copy()), List.of(), true)) continue;
                return Optional.of(new Plan(upgrade.get(), basin, slot, input.copyWithCount(1), prepared));
            }
            return Optional.empty();
        }
        ItemStack input = inventory.getStackInSlot(SCROLL_SLOT);
        Optional<ScrollUpgradeRules.Upgrade> upgrade = ScrollAscensionRecipes.prepareUpgrade(
                input, isTranscendent(), hasCake());
        boolean prepared = UnfinishedAscensionScrollItem.isPreparedStage(input);
        if (upgrade.isEmpty() || !hasWorkingFuel(upgrade.get())
                || (!prepared && !ScrollUpgradeRules.matchesInk(inventory.getStackInSlot(INK_SLOT), upgrade.get()))
                || !fitsOutput(RESULT_SLOT, upgrade.get().output())
                || (!prepared && !fitsOutput(BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE)))) return Optional.empty();
        return Optional.of(new Plan(upgrade.get(), null, SCROLL_SLOT, input.copyWithCount(1), prepared));
    }

    private boolean fitsOutput(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack current = inventory.getStackInSlot(slot);
        return current.isEmpty() || ItemStack.isSameItemSameTags(current, stack)
                && current.getCount() + stack.getCount() <= Math.min(current.getMaxStackSize(), inventory.getSlotLimit(slot));
    }

    private boolean commit(Plan plan) {
        // Everything is simulated first and committed on the server thread in this method.
        Optional<Plan> current = findPlan();
        if (current.isEmpty() || current.get().basin() != plan.basin() || current.get().slot() != plan.slot()
                || current.get().prepared() != plan.prepared()
                || !ItemStack.isSameItemSameTags(current.get().input(), plan.input())) return false;
        ScrollUpgradeRules.Upgrade upgrade = current.get().upgrade();
        boolean crimson = isTranscendent();
        // Both routes execute the actual native sequence on local copies before
        // any external item, ink, fuel or cake is committed. A pre-inked stage
        // has already paid for stage one at a spout and goes straight to stage two.
        ItemStack prepared = plan.prepared() ? plan.input().copy()
                : ScrollAscensionRecipes.prepareStage(level, plan.input());
        if (prepared.isEmpty()) return false;
        ItemStack produced = ScrollAscensionRecipes.finishStage(level, prepared, crimson, hasCake());
        if (produced.getCount() != 1 || !ItemStack.isSameItemSameTags(produced, upgrade.output())) return false;
        if (upgrade.requiresCake() && level instanceof ServerLevel server
                && BlazeLightningHelper.strikeLightning(server, worldPosition)) {
            // The native helper returns true if no exposed rod intercepted its real bolt.
            // As in CEI this happens before the transaction; keep every input and fluid.
            blazeAlive = false;
            residualFire = true;
            lightningFireCleanupTicks = 100;
            clearExternalSelfFire();
            clearProgress();
            contentsChanged();
            return false;
        }
        if (plan.basin() == null) {
            inventory.extractItem(SCROLL_SLOT, 1, false);
            if (!plan.prepared()) inventory.extractItem(INK_SLOT, 1, false);
            putOutput(RESULT_SLOT, produced);
            if (!plan.prepared()) putOutput(BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        } else {
            BasinBlockEntity basin = plan.basin();
            IFluidHandler inkTank = basin.inputTank.getCapability().orElse(null);
            if (!plan.prepared() && inkTank == null) return false;
            ItemStack consumedScroll = basin.getInputInventory().extractItem(plan.slot(), 1, false);
            FluidStack consumedInk = plan.prepared() ? FluidStack.EMPTY
                    : inkTank.drain(new FluidStack(upgrade.inkFluid(), ScrollUpgradeRules.INK_AMOUNT),
                    IFluidHandler.FluidAction.EXECUTE);
            boolean validInput = consumedScroll.getCount() == 1
                    && ItemStack.isSameItemSameTags(consumedScroll, plan.input());
            boolean validInk = plan.prepared() || (consumedInk.getAmount() == ScrollUpgradeRules.INK_AMOUNT
                    && consumedInk.getFluid().isSame(upgrade.inkFluid()));
            if (!validInput || !validInk || !basin.acceptOutputs(List.of(produced), List.of(), false)) {
                // A spout destination may have changed during its capability callbacks.
                basin.getInputInventory().insertItem(plan.slot(), consumedScroll, false);
                if (!consumedInk.isEmpty() && inkTank != null) inkTank.fill(consumedInk, IFluidHandler.FluidAction.EXECUTE);
                return false;
            }
            basin.inputTank.sendDataImmediately();
            basin.notifyChangeOfContents();
            basin.setChanged();
            basin.sendData();
        }
        consumeWorkingFuel(upgrade, crimson);
        if (upgrade.requiresCake()) inventory.extractItem(CAKE_SLOT, 1, false);
        contentsChanged();
        return true;
    }

    private void putOutput(int slot, ItemStack produced) {
        ItemStack current = inventory.getStackInSlot(slot);
        if (current.isEmpty()) inventory.setStackInSlot(slot, produced.copy());
        else {
            ItemStack result = current.copy();
            result.grow(produced.getCount());
            inventory.setStackInSlot(slot, result);
        }
    }

    @Nullable private BasinBlockEntity getBasin() {
        if (level == null) return null;
        BlockEntity blockEntity = level.getBlockEntity(worldPosition.above());
        return blockEntity instanceof BasinBlockEntity basin ? basin : null;
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking,
                getCapability(ForgeCapabilities.FLUID_HANDLER));
    }

    public static boolean isSolutionContainer(ItemStack stack) {
        return stack.is(ArcaneContent.BOTTLE.get()) || stack.is(ArcaneContent.BUCKET.get())
                || stack.is(CrimsonContent.BOTTLE.get()) || stack.is(CrimsonContent.BUCKET.get());
    }

    public static boolean isBucketContainer(ItemStack stack) {
        return stack.is(ArcaneContent.BUCKET.get()) || stack.is(CrimsonContent.BUCKET.get());
    }

    public static int containerAmount(ItemStack stack) { return isBucketContainer(stack) ? BUCKET_AMOUNT : BOTTLE_AMOUNT; }

    public static boolean isArcaneEssence(ItemStack stack) {
        return !stack.isEmpty() && NORMAL_FUEL.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private boolean canAcceptEssence() {
        return sourceTank.getFluidAmount() < sourceTank.getCapacity()
                && sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), BOTTLE_AMOUNT),
                IFluidHandler.FluidAction.SIMULATE) > 0;
    }

    private static boolean isCrimson(FluidStack stack) {
        return !stack.isEmpty() && CrimsonContent.SOURCE.get().isSame(stack.getFluid());
    }

    /** A whole container is transferred only when the tank can accept all of its contents. */
    public boolean fillFromContainer(ItemStack held, Player player) {
        if (level == null || level.isClientSide || !isSolutionContainer(held)) return false;
        boolean bucket = isBucketContainer(held);
        int amount = containerAmount(held);
        boolean crimson = held.is(CrimsonContent.BOTTLE.get()) || held.is(CrimsonContent.BUCKET.get());
        FluidStack contents = new FluidStack(crimson ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), amount);
        if (fluidInput.fill(contents, IFluidHandler.FluidAction.SIMULATE) != amount) return false;
        if (fluidInput.fill(contents, IFluidHandler.FluidAction.EXECUTE) != amount) return false;
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE));
        }
        return true;
    }

    /** Match ItemUtils pickup: transfer a whole physical container, never fractional credit. */
    public boolean drainToContainer(ItemStack held, Player player) {
        boolean bucket = held.is(Items.BUCKET);
        int amount = bucket ? BUCKET_AMOUNT : held.is(Items.GLASS_BOTTLE) ? BOTTLE_AMOUNT : 0;
        if (level == null || level.isClientSide || amount == 0 || player.level() != level
                || !sourceTank.isFluidValid(sourceTank.getFluid()) || sourceTank.getFluidAmount() < amount) return false;
        FluidStack request = sourceTank.getFluid().copy();
        request.setAmount(amount);
        if (fluidInput.drain(request, IFluidHandler.FluidAction.SIMULATE).getAmount() != amount) return false;
        boolean crimson = isCrimson(request);
        if (fluidInput.drain(request, IFluidHandler.FluidAction.EXECUTE).getAmount() != amount) return false;
        ItemStack filled = new ItemStack(bucket
                ? crimson ? CrimsonContent.BUCKET.get() : ArcaneContent.BUCKET.get()
                : crimson ? CrimsonContent.BOTTLE.get() : ArcaneContent.BOTTLE.get());
        InteractionHand hand = player.getItemInHand(InteractionHand.MAIN_HAND) == held ? InteractionHand.MAIN_HAND
                : player.getItemInHand(InteractionHand.OFF_HAND) == held ? InteractionHand.OFF_HAND : null;
        ItemStack replacement = ItemUtils.createFilledResult(held, player, filled);
        if (hand != null) player.setItemInHand(hand, replacement);
        else if (replacement != held && !replacement.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, replacement);
        contentsChanged();
        return true;
    }

    public boolean drainToBucket(ItemStack held, Player player) {
        return held.is(Items.BUCKET) && drainToContainer(held, player);
    }

    public boolean insertFromPlayer(ItemStack held, boolean creative) {
        if (held.isEmpty()) return false;
        if (isArcaneEssence(held)) {
            FluidStack bottle = new FluidStack(ArcaneContent.SOURCE.get(), BOTTLE_AMOUNT);
            if (!canAcceptEssence()) return false;
            if (sourceTank.fill(bottle, IFluidHandler.FluidAction.EXECUTE) <= 0) return false;
            if (!creative) held.shrink(1);
            return true;
        }
        if (held.is(CrimsonContent.EMBER_CAKE.get()) && !isTranscendent()) return false;
        int[] inputs = {TEMPLATE_SLOT, SCROLL_SLOT, INK_SLOT, CAKE_SLOT};
        for (int slot : inputs) {
            if (!inventory.isItemValid(slot, held)) continue;
            ItemStack single = held.copy();
            single.setCount(1);
            if (!inventory.insertItem(slot, single, true).isEmpty()) continue;
            inventory.insertItem(slot, single, false);
            if (!creative) held.shrink(1);
            return true;
        }
        return false;
    }

    public boolean acceptsInputType(ItemStack held) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (inventory.isItemValid(slot, held)) return true;
        return false;
    }

    public ItemStack extractForPlayer(boolean sneaking) {
        if (manualProcessing || manualResultReady) return ItemStack.EMPTY;
        int[] slots = sneaking ? new int[]{TEMPLATE_SLOT, CAKE_SLOT, SUPER_FUEL_SLOT, NORMAL_FUEL_SLOT}
                : new int[]{RESULT_SLOT, BOTTLE_SLOT, SCROLL_SLOT, INK_SLOT};
        for (int slot : slots) {
            ItemStack extracted = inventory.extractItem(slot, inventory.getSlotLimit(slot), false);
            if (!extracted.isEmpty()) return extracted;
        }
        return ItemStack.EMPTY;
    }

    private void clearProgress() { progress = 0; activeScroll = ItemStack.EMPTY; }
    private void resetBookSelection() {
        manualBookSelectionLocked = false;
        selectedBookSpellSlot = -1;
        manualSelectedAt = -1;
        manualSelectionRevision = manualSelectionRevision == Integer.MAX_VALUE ? 0 : manualSelectionRevision + 1;
    }
    private void clearManualResult() {
        manualResultReady = false;
        manualProgress = 0;
        manualStartedAt = -1;
        manualCompletedAt = -1;
        manualOperation = "";
        manualItemPlacedAt = inventory.getStackInSlot(SCROLL_SLOT).isEmpty() || level == null ? -1 : level.getGameTime();
        selectionPrimary = inventory.getStackInSlot(SCROLL_SLOT).copy();
        resetBookSelection();
    }
    private void inventoryContentsChanged(int slot) {
        if (manualMutation) return;
        // Input snapshots belong to the paid transaction. Even an internal inventory mutation
        // must not clear its operation key and accidentally turn a pause into a fresh draw.
        if (isResearchProcessing()) {
            contentsChanged();
            return;
        }
        if (slot == SCROLL_SLOT) {
            ItemStack current = inventory.getStackInSlot(SCROLL_SLOT);
            if (!ItemStack.matches(current, selectionPrimary)) {
                selectionPrimary = current.copy();
                cancelManualWork();
                resetBookSelection();
                manualStartedAt = -1;
                manualCompletedAt = -1;
                manualOperation = "";
                manualItemPlacedAt = current.isEmpty() || level == null ? -1 : level.getGameTime();
            }
        }
        if (slot == INK_SLOT && !inventory.getStackInSlot(INK_SLOT).isEmpty() && level != null) {
            manualMaterialPlacedAt = level.getGameTime();
        }
        // Preserve arbitrary native orb refunds even when automation empties slot 4 first.
        if (manualResultReady && (slot == RESULT_SLOT || slot == BOTTLE_SLOT)) {
            manualMutation = true;
            try {
                if (inventory.getStackInSlot(BOTTLE_SLOT).isEmpty() && !manualExtraReturns.isEmpty()) {
                    inventory.setStackInSlot(BOTTLE_SLOT, manualExtraReturns.remove(0));
                }
                if (inventory.getStackInSlot(RESULT_SLOT).isEmpty()
                        && inventory.getStackInSlot(BOTTLE_SLOT).isEmpty() && manualExtraReturns.isEmpty()) clearManualResult();
            } finally { manualMutation = false; }
        }
        contentsChanged();
    }
    private void contentsChanged() {
        if (manualMutation) return;
        setChanged(); updateMode(); sync();
    }
    private void updateMode() {
        if (level == null || level.isClientSide) return;
        int state = !blazeAlive ? 0 : isResearchProcessing()
                ? manualTransaction.getBoolean("Crimson") ? 2 : 1
                : getFuelTicks() <= 0 ? 0 : isTranscendent() ? 2 : 1;
        BlockState current = getBlockState();
        if (current.getValue(AscensionChamberBlock.STATE) != state || current.getValue(AscensionChamberBlock.SKIN) != skin
                || current.getValue(AscensionChamberBlock.ALIVE) != blazeAlive
                || current.getValue(AscensionChamberBlock.BURNING) != hasResidualFire()) {
            level.setBlock(worldPosition, current.setValue(AscensionChamberBlock.STATE, state)
                    .setValue(AscensionChamberBlock.SKIN, skin).setValue(AscensionChamberBlock.ALIVE, blazeAlive)
                    .setValue(AscensionChamberBlock.BURNING, hasResidualFire()), 3);
        }
    }
    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.put("Solution", sourceTank.writeToNBT(new CompoundTag()));
        tag.putInt("NormalFuel", normalFuelTicks);
        tag.putInt("SuperFuel", superFuelTicks);
        tag.putInt("CrimsonCreditQuarterMb", crimsonCreditQuarterMb);
        tag.putInt("Progress", progress);
        tag.putBoolean("ManualProcessing", manualProcessing);
        tag.putInt("ManualProgress", manualProgress);
        tag.putBoolean("ResearchVisualRunning", researchVisualRunning && isResearchProcessing());
        tag.putLong("ResearchVisualSampleAt", level == null ? 0 : level.getGameTime());
        tag.putLong("ManualStartedAt", manualStartedAt);
        tag.putLong("ManualCompletedAt", manualCompletedAt);
        tag.putLong("ManualItemPlacedAt", manualItemPlacedAt);
        tag.putLong("ManualMaterialPlacedAt", manualMaterialPlacedAt);
        tag.putString("ManualOperation", manualOperation);
        tag.putBoolean("ManualResultReady", manualResultReady);
        tag.putBoolean("ManualBookSelectionLocked", manualBookSelectionLocked);
        tag.putInt("SelectedBookSpellSlot", selectedBookSpellSlot);
        tag.putInt("ManualSelectionRevision", manualSelectionRevision);
        tag.putLong("ManualSelectedAt", manualSelectedAt);
        tag.put("ManualTransaction", manualTransaction.copy());
        ListTag returns = new ListTag();
        manualExtraReturns.forEach(stack -> returns.add(stack.save(new CompoundTag())));
        tag.put("ManualExtraReturns", returns);
        tag.putString("Skin", getSkinKey());
        tag.putBoolean("BlazeAlive", blazeAlive);
        tag.putBoolean("ResidualFire", hasResidualFire());
        tag.putInt("LightningFireCleanupTicks", lightningFireCleanupTicks);
        tag.put("GogglesDecoration", gogglesDecoration.save(new CompoundTag()));
        tag.put("CrownDecoration", crownDecoration.save(new CompoundTag()));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        manualMutation = true;
        skin = tag.contains("Skin") ? AscensionChamberBlock.Skin.byKey(tag.getString("Skin"))
                : getBlockState().getValue(AscensionChamberBlock.SKIN);
        blazeAlive = !tag.contains("BlazeAlive") || tag.getBoolean("BlazeAlive");
        residualFire = !blazeAlive && tag.getBoolean("ResidualFire");
        migrateLegacyExternalFire = !blazeAlive && !tag.contains("ResidualFire");
        lightningFireCleanupTicks = Math.max(0, Math.min(100, tag.getInt("LightningFireCleanupTicks")));
        gogglesDecoration = ItemStack.of(tag.getCompound("GogglesDecoration"));
        if (!AllItems.GOGGLES.isIn(gogglesDecoration)) gogglesDecoration = ItemStack.EMPTY;
        else gogglesDecoration.setCount(1);
        crownDecoration = ItemStack.of(tag.getCompound("CrownDecoration"));
        if (!ChamberDecorations.NECROMANCER_CROWN_ID.equals(ForgeRegistries.ITEMS.getKey(crownDecoration.getItem()))) crownDecoration = ItemStack.EMPTY;
        else crownDecoration.setCount(1);
        CompoundTag savedInventory = tag.getCompound("Inventory").copy();
        savedInventory.putInt("Size", 8); // Upgrade old seven-slot saves without changing slot indices.
        inventory.deserializeNBT(savedInventory);
        sourceTank.readFromNBT(tag.getCompound("Solution"));
        normalFuelTicks = Math.max(0, tag.getInt("NormalFuel"));
        superFuelTicks = Math.max(0, tag.getInt("SuperFuel"));
        crimsonCreditQuarterMb = Math.max(0, Math.min(3, tag.getInt("CrimsonCreditQuarterMb")));
        // Recover an intermediate two-tank save without silently dropping its liquid.
        FluidStack legacyRed = FluidStack.loadFluidStackFromNBT(tag.getCompound("CrimsonSolution"));
        if (isCrimson(legacyRed)) {
            // Loading a two-tank save must not convert/overflow an already stored blue tank.
            int recovered = sourceTank.isEmpty() || isCrimson(sourceTank.getFluid())
                    ? sourceTank.fill(legacyRed, IFluidHandler.FluidAction.EXECUTE) : 0;
            superFuelTicks = (int) Math.min(Integer.MAX_VALUE,
                    (long) superFuelTicks + (long) (legacyRed.getAmount() - recovered) * TICKS_PER_MB);
        }
        progress = Math.max(0, Math.min(PROCESSING_TIME - 1, tag.getInt("Progress")));
        activeScroll = ItemStack.EMPTY;
        manualProgress = Math.max(0, Math.min(MANUAL_PROCESSING_TIME, tag.getInt("ManualProgress")));
        researchVisualRunning = tag.getBoolean("ResearchVisualRunning");
        researchVisualSampleAt = tag.getLong("ResearchVisualSampleAt");
        manualStartedAt = tag.contains("ManualStartedAt") ? tag.getLong("ManualStartedAt") : -1;
        manualCompletedAt = tag.contains("ManualCompletedAt") ? tag.getLong("ManualCompletedAt") : -1;
        manualItemPlacedAt = tag.contains("ManualItemPlacedAt") ? tag.getLong("ManualItemPlacedAt") : -1;
        manualMaterialPlacedAt = tag.contains("ManualMaterialPlacedAt") ? tag.getLong("ManualMaterialPlacedAt") : -1;
        manualOperation = tag.getString("ManualOperation");
        manualResultReady = tag.getBoolean("ManualResultReady");
        manualBookSelectionLocked = tag.getBoolean("ManualBookSelectionLocked");
        selectedBookSpellSlot = tag.contains("SelectedBookSpellSlot") ? tag.getInt("SelectedBookSpellSlot") : -1;
        manualSelectionRevision = Math.max(0, tag.getInt("ManualSelectionRevision"));
        manualSelectedAt = tag.contains("ManualSelectedAt") ? tag.getLong("ManualSelectedAt") : -1;
        manualTransaction = tag.getCompound("ManualTransaction").copy();
        manualProcessing = tag.getBoolean("ManualProcessing") && !manualResultReady && !manualTransaction.isEmpty();
        manualExtraReturns.clear();
        for (Tag entry : tag.getList("ManualExtraReturns", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) manualExtraReturns.add(stack);
        }
        selectionPrimary = inventory.getStackInSlot(SCROLL_SLOT).copy();
        if (manualItemPlacedAt < 0 && !selectionPrimary.isEmpty() && level != null) manualItemPlacedAt = level.getGameTime();
        manualMutation = false;
        updateMode();
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        if (capability == ForgeCapabilities.FLUID_HANDLER && side != Direction.UP) {
            return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        fluidCapability.invalidate();
    }

    @Override public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> automation);
        fluidCapability = LazyOptional.of(() -> fluidInput);
    }

    private record ManualPlan(ManualUpgradeRecipes.Operation operation, ItemStack primary,
                              ItemStack modifier, boolean crimson) {}
    private record Plan(ScrollUpgradeRules.Upgrade upgrade, @Nullable BasinBlockEntity basin, int slot,
                        ItemStack input, boolean prepared) {}
}
