package dev.expedition.arcane.compat;

import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Shared validation and progression rules; never mutates the supplied stacks. */
public final class NativeScrollRules {
    public static final int FIRST_INDUSTRIAL_LEVEL = 5;
    public static final int PRINTING_FLUID_COST = 50;

    private NativeScrollRules() {
    }

    public static boolean requiresIndustrialUpgrade(int targetLevel) {
        return targetLevel >= FIRST_INDUSTRIAL_LEVEL;
    }

    /** Zero means an invalid level or a recipe that cannot fit in this printer. */
    public static int printingFluidCost(int spellLevel, int tankCapacity) {
        return spellLevel > 0 && tankCapacity >= PRINTING_FLUID_COST ? PRINTING_FLUID_COST : 0;
    }

    public static Optional<SpellData> readValidScroll(ItemStack stack) {
        return readSingleScroll(stack).filter(data -> data.getLevel() <= data.getSpell().getMaxLevel());
    }

    /** Reads the native container without granting the printer permission to copy breakthrough scrolls. */
    private static Optional<SpellData> readSingleScroll(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IScroll)
                || !ISpellContainer.isSpellContainer(stack)) {
            return Optional.empty();
        }
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty() || container.getActiveSpellCount() != 1) {
            return Optional.empty();
        }
        SpellData data = container.getActiveSpells().get(0).spellData();
        AbstractSpell spell = data.getSpell();
        if (spell.equals(SpellRegistry.none())
                || data.getLevel() < spell.getMinLevel()) {
            return Optional.empty();
        }
        return Optional.of(data);
    }

    public static boolean blocksNativeAnvilUpgrade(ItemStack base, ItemStack modifier) {
        return modifier.getItem() instanceof InkItem
                && readSingleScroll(base).map(data -> data.getLevel() >= data.getSpell().getMaxLevel()
                        || requiresIndustrialUpgrade(data.getLevel() + 1))
                .orElse(false);
    }

    public static boolean blocksNativeWriting(AbstractSpell spell, ItemStack ink) {
        return spell != null && !spell.equals(SpellRegistry.none())
                && ink.getItem() instanceof InkItem inkItem
                && requiresIndustrialUpgrade(spell.getMinLevelForRarity(inkItem.getRarity()));
    }

    /** Can be called by a lightweight build harness without bootstrapping spell registries. */
    public static void verifyBoundaryRules() {
        for (int level = 1; level <= 10; level++) {
            if (requiresIndustrialUpgrade(level) != (level >= 5)) {
                throw new IllegalStateException("Incorrect native scroll gate at level " + level);
            }
        }
        if (printingFluidCost(1, 1000) != 50 || printingFluidCost(10, 1000) != 50
                || printingFluidCost(10, 49) != 0 || printingFluidCost(0, 1000) != 0
                || printingFluidCost(1, 50) != 50) {
            throw new IllegalStateException("Invalid printer cost or tank-capacity boundary");
        }
    }
}
