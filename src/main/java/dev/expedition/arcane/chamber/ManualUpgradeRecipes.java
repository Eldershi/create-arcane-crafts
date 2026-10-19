package dev.expedition.arcane.chamber;

import dev.expedition.arcane.compat.NativeAnvilBridge;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.item.UpgradeData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellSlotUpgradeItem;
import io.redspace.ironsspellbooks.item.UniqueItem;
import io.redspace.ironsspellbooks.item.UpgradeOrbTypeData;
import io.redspace.ironsspellbooks.item.curios.AffinityRing;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only chamber transactions. Scroll and selected-book upgrades use this
 * add-on's industrial rules; the remaining operations ask the installed Iron's
 * Spellbooks menu for a result using isolated input copies.
 */
public final class ManualUpgradeRecipes {
    public static final String SCROLL_UPGRADE = "scroll_upgrade";
    public static final String BOOK_SPELL_UPGRADE = "book_spell_upgrade";
    public static final String UNIQUE_SPELL_UPGRADE = "unique_spell_upgrade";
    public static final String SPELL_IMBUE = "spell_imbue";
    public static final String ORB_UPGRADE = "orb_upgrade";
    public static final String BOOK_SLOT_UPGRADE = "book_slot_upgrade";
    public static final String AFFINITY_BIND = "affinity_bind";
    public static final String SHRIVING = "shriving";
    private static final int NATIVE_SOLUTION_COST_MB = 250;

    private ManualUpgradeRecipes() {}

    /** Byproducts are separate stacks because shriving can refund several orb types. */
    public record Operation(String kind, ItemStack output, ItemStack byproduct, int modifierCount,
                            long solutionCostQuarterMb, boolean requiresCake,
                            List<ItemStack> additionalByproducts) {
        public Operation {
            output = output.copy();
            byproduct = byproduct.copy();
            additionalByproducts = additionalByproducts.stream().map(ItemStack::copy).toList();
        }

        public Operation(String kind, ItemStack output, ItemStack byproduct, int modifierCount,
                         long solutionCostQuarterMb, boolean requiresCake) {
            this(kind, output, byproduct, modifierCount, solutionCostQuarterMb, requiresCake, List.of());
        }
    }

    public static boolean isPrimary(ItemStack stack) {
        return !stack.isEmpty() && (ScrollUpgradeRules.isScroll(stack)
                || stack.getItem() instanceof ISpellbook || stack.getItem() instanceof UniqueItem
                || stack.getItem() instanceof AffinityRing || Utils.canImbue(stack)
                || Utils.canBeUpgraded(stack) || UpgradeData.hasUpgradeData(stack)
                || ISpellContainer.isSpellContainer(stack));
    }

    public static boolean isModifier(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof InkItem
                || stack.getItem() instanceof Scroll || stack.getItem() instanceof SpellSlotUpgradeItem
                || UpgradeOrbTypeData.has(stack) || stack.is(ItemRegistry.SHRIVING_STONE.get()));
    }

    /** List positions are native slot indices; vacant slots retain SpellData.EMPTY. */
    public static List<SpellData> selectableSpells(ItemStack primary) {
        if (primary.isEmpty() || !(primary.getItem() instanceof ISpellbook)) return List.of();
        ItemStack copy = initializedCopy(primary);
        ISpellContainer container = ISpellContainer.get(copy);
        if (container == null) return List.of();
        List<SpellData> spells = new ArrayList<>();
        for (SpellSlot slot : container.getAllSpells()) {
            spells.add(slot != null && validSpell(slot.spellData()) ? slot.spellData() : SpellData.EMPTY);
        }
        return List.copyOf(spells);
    }

    /** selectedSlot is a native container index; locked means the player confirmed that selection. */
    public static Optional<Operation> find(Level level, ItemStack primary, ItemStack modifier,
                                           int selectedSlot, boolean locked, boolean crimson, boolean cake) {
        if (primary.isEmpty() || modifier.isEmpty()) return Optional.empty();
        ItemStack base = initializedCopy(primary);
        ItemStack material = initializedCopy(modifier);

        if (material.getItem() instanceof InkItem) {
            if (ScrollUpgradeRules.isScroll(base)) {
                return ScrollUpgradeRules.prepare(base, crimson, cake)
                        .filter(upgrade -> ScrollUpgradeRules.matchesInk(material, upgrade))
                        .map(upgrade -> new Operation(SCROLL_UPGRADE, upgrade.output(),
                                new ItemStack(Items.GLASS_BOTTLE), 1,
                                upgrade.solutionCostQuarterMb(), upgrade.requiresCake()));
            }
            if (base.getItem() instanceof ISpellbook && locked) {
                return upgradeBookSpell(base, material, selectedSlot, crimson, cake);
            }
            return Optional.empty();
        }

        // Validate a scroll before passing it to the native menu, which assumes
        // a single initialized spell in its first slot.
        if (material.getItem() instanceof Scroll) {
            Optional<SpellData> spell = singleScroll(material);
            if (spell.isEmpty()) return Optional.empty();
            // Keep accepting a single active spell even if its source container
            // used a sparse slot index; native anvils read slot zero.
            ISpellContainer.createScrollContainer(spell.get().getSpell(), spell.get().getLevel(), material);
        }
        return NativeAnvilBridge.prepare(level, base, material)
                .filter(result -> acceptsNativeSpellWrite(base, material, result.output()))
                .map(result -> {
            List<ItemStack> refunds = result.byproducts();
            ItemStack first = refunds.isEmpty() ? ItemStack.EMPTY : refunds.get(0);
            List<ItemStack> extra = refunds.size() < 2 ? List.of() : refunds.subList(1, refunds.size());
            return new Operation(nativeKind(base, material), result.output(), first, 1,
                    nativeCost(crimson), false, extra);
        });
    }

    /**
     * Native anvil code does not check a failed container write. Keep the chamber's
     * earlier all-or-nothing behavior by validating the result, without rebuilding
     * the dependency's recipe: only the requested slot may change.
     */
    private static boolean acceptsNativeSpellWrite(ItemStack base, ItemStack material, ItemStack output) {
        boolean unique = base.getItem() instanceof UniqueItem;
        if (!(material.getItem() instanceof Scroll) || (!unique && !Utils.canImbue(base))) return true;
        Optional<SpellData> supplied = singleScroll(material);
        if (supplied.isEmpty()) return false;
        ISpellContainer before = unique ? ISpellContainer.get(base) : ISpellContainer.getOrCreate(base.copy());
        ISpellContainer after = ISpellContainer.get(output);
        if (before == null || after == null) return false;
        SpellData incoming = supplied.get();
        int target = unique ? before.getIndexForSpell(incoming.getSpell()) : before.getNextAvailableIndex();
        if (!unique && target < 0) target = 0;
        if (target < 0 || target >= before.getMaxSpellCount()) return false;
        if (unique) {
            SpellData previous = before.getSpellAtIndex(target);
            if (previous == null || !previous.isLocked() || previous.getLevel() >= incoming.getLevel()) return false;
        }
        return matchesSpellWrite(before, after, incoming, target, unique);
    }

    /** A postcondition only: it never performs a spell-container mutation. */
    private static boolean matchesSpellWrite(ISpellContainer before, ISpellContainer after,
                                               SpellData incoming, int target, boolean unique) {
        if (before.getMaxSpellCount() != after.getMaxSpellCount()
                || before.mustEquip() != after.mustEquip() || before.isSpellWheel() != after.isSpellWheel()
                || after.isImproved() != (unique || before.isImproved())) return false;
        for (int index = 0; index < before.getMaxSpellCount(); index++) {
            SpellData actual = after.getSpellAtIndex(index);
            if (index == target) {
                if (actual == null || !actual.getSpell().equals(incoming.getSpell())
                        || actual.getLevel() != incoming.getLevel() || actual.isLocked() != unique) return false;
            } else if (!sameSpell(before.getSpellAtIndex(index), actual)) return false;
        }
        return true;
    }

    private static boolean sameSpell(SpellData a, SpellData b) {
        return a != null && b != null && a.getSpell().equals(b.getSpell())
                && a.getLevel() == b.getLevel() && a.isLocked() == b.isLocked();
    }

    /** Labels affect the chamber animation only; the upstream menu decides acceptance. */
    private static String nativeKind(ItemStack primary, ItemStack material) {
        if (material.getItem() instanceof Scroll) {
            if (primary.getItem() instanceof UniqueItem) return UNIQUE_SPELL_UPGRADE;
            if (Utils.canImbue(primary)) return SPELL_IMBUE;
            if (primary.getItem() instanceof AffinityRing) return AFFINITY_BIND;
        }
        if (material.is(ItemRegistry.SHRIVING_STONE.get())) return SHRIVING;
        if (material.getItem() instanceof SpellSlotUpgradeItem) return BOOK_SLOT_UPGRADE;
        return ORB_UPGRADE;
    }

    private static Optional<Operation> upgradeBookSpell(ItemStack base, ItemStack ink, int selectedSlot,
                                                         boolean crimson, boolean cake) {
        ISpellContainer container = ISpellContainer.get(base);
        if (container == null || selectedSlot < 0 || selectedSlot >= container.getMaxSpellCount()) {
            return Optional.empty();
        }
        SpellData selected = container.getSpellAtIndex(selectedSlot);
        if (!validSpell(selected)) return Optional.empty();
        // Delegate every level, rarity, breakthrough and exact fuel boundary to the same rules as scrolls.
        ItemStack surrogate = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(selected.getSpell(), selected.getLevel(), surrogate);
        var upgrade = ScrollUpgradeRules.prepare(surrogate, crimson, cake);
        if (upgrade.isEmpty() || !ScrollUpgradeRules.matchesInk(ink, upgrade.get())) return Optional.empty();
        ISpellContainerMutable upgraded = container.mutableCopy();
        if (!upgraded.removeSpellAtIndex(selectedSlot)
                || !upgraded.addSpellAtIndex(selected.getSpell(), upgrade.get().targetLevel(),
                selectedSlot, selected.isLocked())) return Optional.empty();
        if (base.getItem() instanceof UniqueItem && selected.isLocked()) upgraded.setImproved(true);
        ISpellContainer.set(base, upgraded.toImmutable());
        return Optional.of(new Operation(BOOK_SPELL_UPGRADE, base, new ItemStack(Items.GLASS_BOTTLE), 1,
                upgrade.get().solutionCostQuarterMb(), upgrade.get().requiresCake()));
    }

    private static long nativeCost(boolean crimson) {
        return NATIVE_SOLUTION_COST_MB * (crimson ? 1L : 4L);
    }

    private static Optional<SpellData> singleScroll(ItemStack stack) {
        if (!(stack.getItem() instanceof Scroll) || !ISpellContainer.isSpellContainer(stack)) return Optional.empty();
        ISpellContainer container = ISpellContainer.get(stack);
        return container != null && container.getActiveSpellCount() == 1
                && validSpell(container.getActiveSpells().get(0).spellData())
                ? Optional.of(container.getActiveSpells().get(0).spellData()) : Optional.empty();
    }

    private static boolean validSpell(SpellData data) {
        return data != null && !data.getSpell().equals(SpellRegistry.none())
                && data.getLevel() >= data.getSpell().getMinLevel();
    }

    private static ItemStack initializedCopy(ItemStack input) {
        ItemStack result = input.copyWithCount(1);
        if (result.getItem() instanceof IPresetSpellContainer preset && !ISpellContainer.isSpellContainer(result)) {
            preset.initializeSpellContainer(result);
        }
        return result;
    }
}
