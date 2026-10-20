package dev.expedition.arcane.chamber;

import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Pair acceptance is independent of power, fuel, cake and the player's pending spell selection. */
public final class ChamberInputRules {
    private ChamberInputRules() {}

    public static boolean isPrimary(ItemStack stack) {
        return ManualUpgradeRecipes.isPrimary(stack) || RandomResearchRules.isBlankScroll(stack)
                || UnfinishedAscensionScrollItem.isPreparedStage(stack);
    }

    public static boolean isSecondary(ItemStack stack) {
        return ManualUpgradeRecipes.isModifier(stack) || RandomResearchRules.isResearchFocus(stack);
    }

    /** Every candidate is a private copy. Native recipe menus never receive a live inventory stack. */
    public static boolean canPair(Level level, ItemStack primary, ItemStack secondary) {
        if (primary.isEmpty() || secondary.isEmpty()) return true;
        if (!isPrimary(primary) || !isSecondary(secondary)) return false;
        if (RandomResearchRules.isBlankScroll(primary)) return RandomResearchRules.isResearchFocus(secondary);
        if (UnfinishedAscensionScrollItem.isPreparedStage(primary)) return false;
        if (level == null) return false;
        // Permissive resource state answers only whether the two objects can ever combine.
        // Work itself still validates the actual liquid, cake, selected slot and native restrictions.
        if (primary.getItem() instanceof ISpellbook && secondary.getItem() instanceof InkItem) {
            var spells = ManualUpgradeRecipes.selectableSpells(primary.copyWithCount(1));
            for (int slot = 0; slot < spells.size(); slot++) {
                if (ManualUpgradeRecipes.find(level, primary.copyWithCount(1), secondary.copyWithCount(1),
                        slot, true, true, true).isPresent()) return true;
            }
            return false;
        }
        return ManualUpgradeRecipes.find(level, primary.copyWithCount(1), secondary.copyWithCount(1),
                -1, false, true, true).isPresent();
    }
}
