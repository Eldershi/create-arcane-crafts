package dev.expedition.arcane.chamber;

import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Live registry/config-driven research; optional add-ons are discovered through Iron's public APIs. */
public final class RandomResearchRules {
    public static final String OPERATION = "random_research";
    public static final int SOLUTION_COST_MB = 500;

    private RandomResearchRules() {}

    public record Pool(SchoolType school, List<List<AbstractSpell>> tiers) {
        public Pool {
            tiers = tiers.stream().map(List::copyOf).toList();
        }
        public boolean canResearch() { return tiers.size() == 5 && !tiers.get(0).isEmpty(); }
        public List<AbstractSpell> candidates(SpellRarity rarity) { return tiers.get(rarity.ordinal()); }
    }

    public record Draw(Pool pool, AbstractSpell spell, int drawnTier, int actualTier, ItemStack output) {
        public Draw { output = output.copy(); }
    }

    public static boolean isBlankScroll(ItemStack stack) { return UninkedScrollItem.isBlankNativeScroll(stack); }

    public static boolean isResearchFocus(ItemStack stack) {
        return !stack.isEmpty() && SchoolRegistry.REGISTRY.get().getValues().stream()
                .anyMatch(school -> allowedSchool(school) && school.isFocus(stack));
    }

    public static boolean isCandidate(AbstractSpell spell) {
        return spell != null && !spell.equals(SpellRegistry.none()) && spell.isEnabled()
                && spell.allowCrafting() && !spell.requiresLearning()
                && spell.getMinLevel() <= 1 && spell.getMaxLevel() >= 1
                && allowedSchool(spell.getSchoolType());
    }

    private static boolean allowedSchool(SchoolType school) {
        return school != null && !SchoolRegistry.ELDRITCH_RESOURCE.equals(school.getId());
    }

    /** No cached catalog: registration, enabled, crafting and learning settings are checked live. */
    public static Pool pool(SchoolType school) {
        List<List<AbstractSpell>> tiers = new ArrayList<>();
        for (int tier = 0; tier < 5; tier++) tiers.add(new ArrayList<>());
        if (allowedSchool(school)) {
            SpellRegistry.REGISTRY.get().getValues().stream().filter(RandomResearchRules::isCandidate)
                    .filter(spell -> school.equals(spell.getSchoolType()))
                    .sorted(Comparator.comparing(spell -> spell.getSpellResource().toString()))
                    .forEach(spell -> tiers.get(spell.getRarity(1).ordinal()).add(spell));
        }
        return new Pool(school, tiers);
    }

    /** Useful for JEI: includes empty/commonless schools so their unavailable state can be explained. */
    public static List<Pool> pools() {
        return SchoolRegistry.REGISTRY.get().getValues().stream().filter(RandomResearchRules::allowedSchool)
                .sorted(Comparator.comparing(school -> school.getId().toString()))
                .map(RandomResearchRules::pool).toList();
    }

    /** An ambiguous multi-school focus deterministically selects the first eligible school by ID. */
    public static Optional<Pool> poolForFocus(ItemStack focus) {
        return pools().stream().filter(pool -> pool.school().isFocus(focus) && pool.canResearch()).findFirst();
    }

    public static int[] weights(boolean crimson, boolean headwear) { return ResearchLottery.weights(crimson, headwear); }

    /** Exactly one rarity ticket, followed by exactly one uniform choice within the selected tier. */
    public static Optional<Draw> draw(Pool pool, boolean crimson, boolean headwear, RandomSource random) {
        if (!pool.canResearch()) return Optional.empty();
        int drawn = ResearchLottery.rollTier(random.nextInt(ResearchLottery.TICKETS), crimson, headwear);
        boolean[] nonempty = new boolean[5];
        for (int tier = 0; tier < 5; tier++) nonempty[tier] = !pool.tiers().get(tier).isEmpty();
        int actual = ResearchLottery.fallbackTier(drawn, nonempty);
        if (actual < 0) return Optional.empty();
        List<AbstractSpell> candidates = pool.tiers().get(actual);
        AbstractSpell spell = candidates.get(random.nextInt(candidates.size()));
        ItemStack output = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, 1, output);
        return Optional.of(new Draw(pool, spell, drawn, actual, output));
    }
}
