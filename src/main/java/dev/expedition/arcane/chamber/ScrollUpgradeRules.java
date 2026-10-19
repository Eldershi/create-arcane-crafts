package dev.expedition.arcane.chamber;

import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.Optional;

/** The same one-level transaction is used by both chamber input arrangements. */
public final class ScrollUpgradeRules {
    public static final int INK_AMOUNT = 250;
    public static final int MAX_BREAKTHROUGH_LEVELS = 3;
    private ScrollUpgradeRules() {}

    /** solutionCost is the ceiling before credit; commits use exact quarter-mB units plus stored credit. */
    public record Upgrade(ItemStack input, ItemStack output, int targetLevel, SpellRarity rarity,
                          boolean requiresCake, int solutionCost, long solutionCostQuarterMb) {
        public Fluid inkFluid() { return InkItem.getInkForRarity(rarity).fluid().get(); }
    }

    public static boolean isScroll(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IScroll;
    }

    public static Optional<Upgrade> prepare(ItemStack input, boolean transcendent) {
        return prepare(input, transcendent, false);
    }

    public static Optional<Upgrade> prepare(ItemStack input, boolean transcendent, boolean hasCake) {
        if (!isScroll(input) || !ISpellContainer.isSpellContainer(input)) return Optional.empty();
        ISpellContainer container = ISpellContainer.get(input);
        if (container == null || container.getActiveSpellCount() != 1) return Optional.empty();
        SpellSlot slot = container.getActiveSpells().get(0);
        if (slot.getSpell().equals(SpellRegistry.none())
                || slot.getLevel() < slot.getSpell().getMinLevel()) return Optional.empty();
        int nativeMax = slot.getSpell().getMaxLevel();
        long nextLevel = (long) slot.getLevel() + 1;
        boolean breakthrough = slot.getLevel() >= nativeMax;
        if (nativeMax < slot.getSpell().getMinLevel() || nextLevel > Integer.MAX_VALUE) return Optional.empty();
        if (breakthrough) {
            // A low-maximum spell can break through below level five, just as a ten-level spell can.
            if (!transcendent || !hasCake || nextLevel > (long) nativeMax + MAX_BREAKTHROUGH_LEVELS) {
                return Optional.empty();
            }
        } else if (nextLevel >= 8 && !transcendent) {
            return Optional.empty();
        }
        int targetLevel = (int) nextLevel;
        long baseCost;
        if (breakthrough) {
            baseCost = (nextLevel - Math.min(7, nativeMax)) * 250L;
        } else if (nextLevel <= 4) {
            baseCost = nextLevel * 50L;
        } else {
            long tier = nextLevel < 8 ? nextLevel - 4 : nextLevel - 7;
            baseCost = tier * 250L;
        }
        if (baseCost <= 0 || baseCost > Long.MAX_VALUE / 4) return Optional.empty();
        // Quarter-mB units retain exact crimson efficiency; the block entity carries fractional credit.
        long quarterMb = transcendent ? baseCost : baseCost * 4L;
        if (quarterMb > (long) Integer.MAX_VALUE * 4L) return Optional.empty();
        int solutionCost = (int) ((quarterMb + 3L) / 4L);
        ISpellContainerMutable upgraded = container.mutableCopy();
        if (!upgraded.removeSpellAtIndex(slot.index())
                || !upgraded.addSpellAtIndex(slot.getSpell(), targetLevel, slot.index(), slot.isLocked())) {
            return Optional.empty();
        }
        ItemStack result = input.copy();
        result.setCount(1);
        ISpellContainer.set(result, upgraded.toImmutable());
        ItemStack singleInput = input.copy();
        singleInput.setCount(1);
        SpellRarity rarity = breakthrough ? SpellRarity.LEGENDARY : slot.getSpell().getRarity(targetLevel);
        return Optional.of(new Upgrade(singleInput, result, targetLevel, rarity, breakthrough, solutionCost, quarterMb));
    }

    public static boolean matchesInk(ItemStack ink, Upgrade upgrade) {
        return !ink.isEmpty() && ink.getItem() instanceof InkItem item && item.getRarity() == upgrade.rarity();
    }
}
