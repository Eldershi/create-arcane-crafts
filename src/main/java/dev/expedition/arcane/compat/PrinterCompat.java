package dev.expedition.arcane.compat;

import com.mojang.serialization.DataResult;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import plus.dragons.createenchantmentindustry.api.registry.CEIRegistries;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrinterBlockEntity;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrintingInput;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrintingRecipe;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviour;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviourProvider;
import plus.dragons.createenchantmentindustry.common.registry.CEIRecipes;

import java.util.List;
import java.util.Optional;

/** Extends CEI's existing printer instead of adding another processing block. */
public final class PrinterCompat {
    public static final int PRINTING_FLUID_AMOUNT = NativeScrollRules.PRINTING_FLUID_COST;
    private static final DeferredRegister<PrintingBehaviourProvider> PROVIDERS = DeferredRegister.create(
            CEIRegistries.PRINTING_BEHAVIOUR_PROVIDER, ArcaneIndustry.MOD_ID);
    private static boolean registered;

    static {
        // Named scrolls must be handled before CEI's generic custom-name provider (priority 1000).
        PROVIDERS.register("spell_scroll", () -> new PrintingBehaviourProvider(2000, PrinterCompat::create));
    }

    private PrinterCompat() {
    }

    /** Call once in the @Mod constructor, before registry events; commonSetup is too late. */
    public static void register() {
        if (!registered) {
            PROVIDERS.register(FMLJavaModLoadingContext.get().getModEventBus());
            registered = true;
        }
    }

    private static Optional<DataResult<PrintingBehaviour>> create(
            Level level, SmartFluidTankBehaviour tank, ItemStack template) {
        if (!(template.getItem() instanceof IScroll)) {
            return Optional.empty();
        }
        Optional<SpellData> spell = readPrintableTemplate(template);
        if (spell.isEmpty()) {
            return Optional.of(DataResult.error(() -> "此样板不是允许印制的原生等级法术卷轴"));
        }
        if (NativeScrollRules.printingFluidCost(spell.get().getLevel(),
                tank.getPrimaryHandler().getCapacity()) == 0) {
            return Optional.of(DataResult.error(() -> "打印机需要容纳50mB奥术原液"));
        }
        return Optional.of(DataResult.success(new ScrollPrintingBehaviour(template.copyWithCount(1), tank)));
    }

    public static Optional<SpellData> readPrintableTemplate(ItemStack template) {
        return NativeScrollRules.readValidScroll(template)
                .filter(data -> UninkedScrollItem.isPrintableSpell(data.getSpell()));
    }

    /** Produces only a spell-ID intermediate; sample level, name and other metadata are not copied. */
    public static ItemStack copyScroll(ItemStack template) {
        return readPrintableTemplate(template).map(data -> UninkedScrollItem.create(data.getSpell()))
                .orElse(ItemStack.EMPTY);
    }

    /** Runtime verification hook: supply an actual registered, populated scroll. */
    public static void verifyScrollCopy(ItemStack template) {
        SpellData source = readPrintableTemplate(template)
                .orElseThrow(() -> new IllegalArgumentException("A populated valid scroll is required"));
        CompoundTag before = template.save(new CompoundTag());
        ItemStack result = copyScroll(template);
        var copied = UninkedScrollItem.readSpell(result)
                .orElseThrow(() -> new IllegalStateException("Printed scroll lost its spell-ID payload"));
        if (!source.getSpell().equals(copied) || result.getCount() != 1 || result == template
                || !template.save(new CompoundTag()).equals(before)
                || result.hasCustomHoverName() || !result.hasTag()
                || !UninkedScrollItem.isInkingStage(result)
                || !result.getTag().getAllKeys().equals(java.util.Set.of(
                        UninkedScrollItem.SPELL_ID_TAG, UninkedScrollItem.ASSEMBLY_TAG))) {
            throw new IllegalStateException("Printed scroll copied more than the spell ID or changed its source");
        }
        result.setHoverName(Component.literal("independent-copy-check"));
        result.getOrCreateTag().putString(UninkedScrollItem.SPELL_ID_TAG, "irons_spellbooks:none");
        if (!template.save(new CompoundTag()).equals(before)) {
            throw new IllegalStateException("Copied scroll shares mutable data with its source");
        }
    }

    private static final class ScrollPrintingBehaviour implements PrintingBehaviour {
        private final ItemStack template;
        private final SmartFluidTankBehaviour tank;

        private ScrollPrintingBehaviour(ItemStack template, SmartFluidTankBehaviour tank) {
            this.template = template;
            this.tank = tank;
        }

        @Override
        public boolean isValid() {
            return readPrintableTemplate(template).isPresent();
        }

        @Override
        public boolean isSafeNBT() {
            // Match CEI's native copy behaviour when a printer is saved in a schematic.
            return false;
        }

        @Override
        public int getRequiredItemCount(Level level, ItemStack stack) {
            return findAssemblyStep(level, stack, FluidStack.EMPTY).isPresent() ? 1 : 0;
        }

        private Optional<PrintingRecipe> findAssemblyStep(Level level, ItemStack stack, FluidStack fluid) {
            if (!isValid() || !UninkedScrollItem.isBlankNativeScroll(stack)) return Optional.empty();
            PrintingInput input = new PrintingInput(stack, template, fluid);
            return SequencedAssemblyRecipe.getRecipe(level, input, CEIRecipes.PRINTING.getType(), PrintingRecipe.class,
                    recipe -> recipe instanceof AssemblyPrintingRecipe
                            && recipe.getId().toString().equals(UninkedScrollItem.ASSEMBLY_ID + "_step_0")
                            && recipe.matches(input, level));
        }

        @Override
        public int getRequiredFluidAmount(Level level, ItemStack stack, FluidStack fluid) {
            if (!UninkedScrollItem.isBlankNativeScroll(stack) || fluid.isEmpty()
                    || !ArcaneContent.SOURCE.get().isSame(fluid.getFluid())) {
                return 0;
            }
            return findAssemblyStep(level, stack, fluid).isPresent() ? PRINTING_FLUID_AMOUNT : 0;
        }

        @Override
        public ItemStack getResult(Level level, ItemStack stack, FluidStack fluid) {
            int cost = getRequiredFluidAmount(level, stack, fluid);
            if (cost <= 0 || fluid.getAmount() < cost) return ItemStack.EMPTY;
            var step = findAssemblyStep(level, stack, fluid);
            var spell = readPrintableTemplate(template);
            if (step.isEmpty() || spell.isEmpty()) return ItemStack.EMPTY;
            // rollResults executes Create's enforceNextResult/advance, producing real progress NBT.
            var results = step.get().rollResults();
            if (results.size() != 1 || !(results.get(0).getItem() instanceof UninkedScrollItem)) return ItemStack.EMPTY;
            ItemStack result = results.get(0).copyWithCount(1);
            result.getOrCreateTag().putString(UninkedScrollItem.SPELL_ID_TAG,
                    spell.get().getSpell().getSpellResource().toString());
            return UninkedScrollItem.isInkingStage(result) ? result : ItemStack.EMPTY;
        }

        @Override
        public void onFinished(Level level, BlockPos pos, PrinterBlockEntity printer) {
            level.playSound(null, pos.below(), SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, 0.6F, 1.2F);
        }

        @Override
        public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
            readPrintableTemplate(template).ifPresent(data -> tooltip.add(Component.translatable(
                    "gui.arcane_industry.printing.spell", data.getSpell().getDisplayName(null))));
            int cost = readPrintableTemplate(template).map(data ->
                    NativeScrollRules.printingFluidCost(data.getLevel(), tank.getPrimaryHandler().getCapacity()))
                    .orElse(0);
            tooltip.add(Component.translatable("create_enchantment_industry.gui.goggles.printing.cost",
                    cost + " mB ").append(ArcaneContent.SOURCE.get().getFluidType().getDescription()));
            return true;
        }
    }
}
