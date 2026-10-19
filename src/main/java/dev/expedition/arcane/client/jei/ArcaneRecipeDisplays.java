package dev.expedition.arcane.client.jei;

import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.compat.PrinterCompat;
import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Display data made from the same rules and items used by the real machines; no world simulation. */
public final class ArcaneRecipeDisplays {
    private ArcaneRecipeDisplays() {}

    public record Ascension(ResourceLocation id, ScrollUpgradeRules.Upgrade upgrade, boolean crimson) {}
    public record Printing(ResourceLocation id, List<ItemStack> samples, ItemStack blank,
                           ItemStack uninked, ItemStack finished) {
        /** Printing always makes level one; the sample's own level never chooses its ink. */
        public Fluid inkFluid() {
            return UninkedScrollItem.readRequiredInk(uninked)
                    .orElseThrow(() -> new IllegalStateException("Printing display has no valid level-one spell"));
        }
    }
    public record Snapshot(List<Ascension> ascension, List<Printing> printing,
                           List<ItemStack> uninkedVariants, List<ItemStack> breakthroughVariants) {}

    public static Snapshot create() {
        List<Ascension> ascensions = new ArrayList<>();
        List<Printing> printing = new ArrayList<>();
        List<ItemStack> uninkedVariants = new ArrayList<>();
        List<ItemStack> breakthroughVariants = new ArrayList<>();
        List<AbstractSpell> spells = new ArrayList<>(SpellRegistry.getEnabledSpells());
        spells.sort(Comparator.comparing(AbstractSpell::getSpellId));
        for (AbstractSpell spell : spells) {
            int min = Math.max(1, spell.getMinLevel());
            int max = spell.getMaxLevel();
            if (spell.equals(SpellRegistry.none()) || max < min
                    || max > Integer.MAX_VALUE - ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS) continue;
            List<ItemStack> samples = new ArrayList<>();
            int ceiling = max + ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS;
            for (int level = min; level <= ceiling; level++) {
                ItemStack input = scroll(spell, level);
                if (level <= max && PrinterCompat.readPrintableTemplate(input).isPresent()) samples.add(input.copy());
                if (level == ceiling) continue;
                for (boolean crimson : new boolean[]{false, true}) {
                    // Passing a cake permits the rule to describe breakthroughs; requiresCake
                    // then tells the display whether the transaction really consumes one.
                    var upgrade = ScrollUpgradeRules.prepare(input, crimson, true);
                    if (upgrade.isEmpty()) continue;
                    String path = spell.getSpellResource().getNamespace() + "/" + spell.getSpellResource().getPath()
                            + "/" + level + "/" + (crimson ? "crimson" : "arcane");
                    ascensions.add(new Ascension(id("ascension/" + path), upgrade.get(), crimson));
                    if (crimson && upgrade.get().requiresCake()) breakthroughVariants.add(upgrade.get().output().copy());
                }
            }
            ItemStack uninked = UninkedScrollItem.create(spell);
            ItemStack finished = UninkedScrollItem.finishInking(uninked);
            if (uninked.isEmpty() || finished.isEmpty()) continue;
            String path = spell.getSpellResource().getNamespace() + "/" + spell.getSpellResource().getPath();
            printing.add(new Printing(id("scroll_printing/" + path), List.copyOf(samples),
                    new ItemStack(ItemRegistry.SCROLL.get()), uninked.copy(), finished.copy()));
            uninkedVariants.add(uninked.copy());
        }
        return new Snapshot(List.copyOf(ascensions), List.copyOf(printing),
                List.copyOf(uninkedVariants), List.copyOf(breakthroughVariants));
    }

    public static ItemStack scroll(AbstractSpell spell, int level) {
        ItemStack result = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, level, result);
        return result;
    }

    static String scrollIdentity(ItemStack stack) {
        if (!ISpellContainer.isSpellContainer(stack)) return "";
        var container = ISpellContainer.get(stack);
        if (container == null || container.getActiveSpellCount() != 1) return "";
        var slot = container.getActiveSpells().get(0);
        return slot.getSpell().getSpellId() + ":" + slot.getLevel();
    }

    public static String millibuckets(long quarterUnits) {
        long whole = quarterUnits / 4;
        return whole + switch ((int) (quarterUnits % 4)) {
            case 1 -> ".25";
            case 2 -> ".5";
            case 3 -> ".75";
            default -> "";
        };
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(ArcaneIndustry.MOD_ID, path); }
}
