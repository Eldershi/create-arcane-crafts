package dev.expedition.arcane.testing;

import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.compat.NativeScrollRules;
import dev.expedition.arcane.compat.PrinterCompat;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registered native spell/container tests. Does not cast spells, spawn entities or change world blocks. */
public final class BreakthroughTests {
    private BreakthroughTests() {}

    public static Map<String, Object> run(ServerPlayer actor) {
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> fixtures = new ArrayList<>();
        fixtures.add(checkSequence(SpellRegistry.COUNTERSPELL_SPELL.get(), 1));
        fixtures.add(checkSequence(SpellRegistry.PORTAL_SPELL.get(), 3));
        fixtures.add(checkSequence(SpellRegistry.TELEPORT_SPELL.get(), 5));
        fixtures.add(checkSequence(SpellRegistry.FIREBOLT_SPELL.get(), 10));
        results.put("native_max_1_3_5_10_sequences", fixtures);

        ItemStack ordinary = scroll(SpellRegistry.FIREBOLT_SPELL.get(), 4);
        var ordinaryWithCake = ScrollUpgradeRules.prepare(ordinary, false, true).orElseThrow();
        require(ordinaryWithCake.targetLevel() == 5 && !ordinaryWithCake.requiresCake()
                && ordinaryWithCake.solutionCost() == 250 && ordinaryWithCake.solutionCostQuarterMb() == 1000,
                "native_range_first_upgrade_uses_250mb_and_keeps_cake");
        int[] lowRedRoundedCosts = {25, 38, 50};
        for (int target = 2; target <= 4; target++) {
            var upgrade = ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), target - 1), false, false)
                    .orElseThrow();
            require(upgrade.targetLevel() == target && upgrade.solutionCost() == target * 50
                    && upgrade.solutionCostQuarterMb() == target * 200L
                    && !upgrade.requiresCake(), "native_low_target_" + target + "_uses_target_times_50mb");
            var crimson = ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), target - 1), true, false)
                    .orElseThrow();
            require(crimson.solutionCostQuarterMb() == target * 50L
                    && crimson.solutionCost() == lowRedRoundedCosts[target - 2] && !crimson.requiresCake(),
                    "crimson_low_target_" + target + "_uses_exact_quarter_cost");
        }
        int[] redQuarterCosts = {250, 500, 750, 250, 500, 750};
        int[] redRoundedCosts = {63, 125, 188, 63, 125, 188};
        for (int target = 5; target <= 10; target++) {
            var upgrade = ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), target - 1), true, true)
                    .orElseThrow();
            require(upgrade.solutionCostQuarterMb() == redQuarterCosts[target - 5]
                    && upgrade.solutionCost() == redRoundedCosts[target - 5] && !upgrade.requiresCake(),
                    "crimson_target_" + target + "_exact_arithmetic_fluid_cost");
            if (target <= 7) {
                var blue = ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), target - 1), false, true)
                        .orElseThrow();
                require(blue.solutionCost() == redQuarterCosts[target - 5]
                        && blue.solutionCostQuarterMb() == redQuarterCosts[target - 5] * 4L,
                        "blue_target_" + target + "_keeps_original_fluid_cost");
            }
        }
        require(ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), 7), false, true).isEmpty(),
                "cake_does_not_bypass_transcendent_level_8_gate");
        require(!ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), 7), true, true)
                .orElseThrow().requiresCake(), "native_7_to_8_does_not_spend_cake");
        var lowMaxNative = ScrollUpgradeRules.prepare(scroll(SpellRegistry.PORTAL_SPELL.get(), 2), true, true).orElseThrow();
        require(lowMaxNative.targetLevel() == 3 && lowMaxNative.solutionCostQuarterMb() == 150
                        && lowMaxNative.solutionCost() == 38 && !lowMaxNative.requiresCake(),
                "low_max_native_range_crimson_upgrade_uses_37_5mb_and_keeps_cake");
        require(ScrollUpgradeRules.prepare(scroll(SpellRegistry.FIREBOLT_SPELL.get(), Integer.MAX_VALUE), true, true).isEmpty(),
                "overflow_level_has_no_upgrade");
        results.put("ordinary_routes_and_integer_boundary", "PASS");
        List<Component> tooltip = new ArrayList<>();
        MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(scroll(SpellRegistry.FIREBOLT_SPELL.get(), 13),
                actor, tooltip, TooltipFlag.NORMAL));
        require(tooltip.stream().anyMatch(line -> line.getString().contains("超越 +3/3"))
                && tooltip.stream().anyMatch(line -> line.getString().contains("不可打印")),
                "registered_breakthrough_tooltip_event_on_server");
        results.put("registered_tooltip_event_server_safe", "PASS");
        results.put("registered_spell_calculation_smoke", smokeNativeStatistics(actor));
        results.put("status", "PASS");
        return results;
    }

    private static Map<String, Object> checkSequence(AbstractSpell spell, int expectedMax) {
        int nativeMax = spell.getMaxLevel();
        require(nativeMax == expectedMax, "fixture_native_max_" + spell.getSpellId());
        ItemStack input = scroll(spell, nativeMax);
        int[] expectedQuarterCosts = nativeMax == 10 ? new int[]{1000, 1250, 1500} : new int[]{250, 500, 750};
        int[] expectedRoundedCosts = nativeMax == 10 ? new int[]{250, 313, 375} : new int[]{63, 125, 188};
        input.setHoverName(Component.literal("Breakthrough " + spell.getSpellId()));
        input.getOrCreateTag().putString("arcane_industry_test_marker", "retain scroll metadata");
        for (int stage = 1; stage <= ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS; stage++) {
            CompoundTag before = input.save(new CompoundTag());
            require(ScrollUpgradeRules.prepare(input, true).isEmpty()
                    && ScrollUpgradeRules.prepare(input, true, false).isEmpty(), "breakthrough_requires_cake_stage_" + stage);
            require(ScrollUpgradeRules.prepare(input, false, true).isEmpty(), "breakthrough_requires_super_stage_" + stage);
            require(NativeScrollRules.blocksNativeAnvilUpgrade(input, legendaryInk()),
                    "native_anvil_cannot_break_through_stage_" + stage);
            var upgrade = ScrollUpgradeRules.prepare(input, true, true).orElseThrow();
            require(upgrade.targetLevel() == nativeMax + stage && upgrade.requiresCake()
                    && upgrade.rarity() == SpellRarity.LEGENDARY
                    && upgrade.solutionCostQuarterMb() == expectedQuarterCosts[stage - 1]
                    && upgrade.solutionCost() == expectedRoundedCosts[stage - 1],
                    "one_cake_grants_exactly_one_level_stage_" + stage);
            require(ScrollUpgradeRules.matchesInk(legendaryInk(), upgrade)
                    && !ScrollUpgradeRules.matchesInk(new ItemStack(InkItem.getInkForRarity(SpellRarity.COMMON)), upgrade),
                    "breakthrough_requires_legendary_ink_stage_" + stage);
            require(input.save(new CompoundTag()).equals(before), "query_keeps_input_stage_" + stage);
            ItemStack output = upgrade.output();
            SpellSlot slot = ISpellContainer.get(output).getActiveSpells().get(0);
            require(slot.getSpell().equals(spell) && slot.getLevel() == nativeMax + stage && slot.isLocked()
                    && slot.index() == 0 && output.getCount() == 1 && upgrade.input().getCount() == 1,
                    "native_spell_slot_and_lock_retained_stage_" + stage);
            require(output.getHoverName().equals(input.getHoverName())
                    && "retain scroll metadata".equals(output.getOrCreateTag().getString("arcane_industry_test_marker")),
                    "name_and_other_nbt_retained_stage_" + stage);
            require(NativeScrollRules.readValidScroll(output).isEmpty() && PrinterCompat.copyScroll(output).isEmpty(),
                    "printer_cannot_duplicate_breakthrough_stage_" + stage);
            input = ItemStack.of(output.save(new CompoundTag()));
            require(ISpellContainer.get(input).getActiveSpells().get(0).getLevel() == nativeMax + stage,
                    "native_nbt_roundtrip_stage_" + stage);
        }
        require(ScrollUpgradeRules.prepare(input, true, true).isEmpty(), "native_max_plus_four_refused");
        require(NativeScrollRules.blocksNativeAnvilUpgrade(input, legendaryInk()), "native_anvil_cannot_exceed_plus_three");
        return Map.of("spell", spell.getSpellId(), "native_max", nativeMax,
                "accepted_levels", List.of(nativeMax + 1, nativeMax + 2, nativeMax + 3),
                "solution_costs_mB", List.of(expectedQuarterCosts[0] / 4.0, expectedQuarterCosts[1] / 4.0, expectedQuarterCosts[2] / 4.0),
                "ceil_mB_without_fractional_credit", List.of(expectedRoundedCosts[0], expectedRoundedCosts[1], expectedRoundedCosts[2]),
                "max_plus_four_without_cake_and_without_super", "REJECTED",
                "metadata_roundtrip_and_printing_gate", "PASS");
    }

    /** Exercises the same scalar/description methods used by native mana costs and tooltips, without onCast. */
    private static Map<String, Object> smokeNativeStatistics(ServerPlayer actor) {
        int spells = 0;
        int levels = 0;
        List<String> negativeTimes = new ArrayList<>();
        for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
            if (spell.equals(SpellRegistry.none()) || spell.getMaxLevel() < spell.getMinLevel()) continue;
            int nativeMax = spell.getMaxLevel();
            require(nativeMax <= Integer.MAX_VALUE - ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS,
                    "configured_spell_max_has_room_for_three_levels");
            for (int stage = 1; stage <= ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS; stage++) {
                int level = nativeMax + stage;
                try {
                    require(spell.getManaCost(level) >= 0, "nonnegative_native_mana_cost");
                    require(Float.isFinite(spell.getSpellPower(level, actor)), "finite_native_power");
                    if (spell.getCastTime(level) < 0) negativeTimes.add(spell.getSpellId() + "@" + level);
                    require(spell.getLevelFor(level, actor) >= level, "native_effective_level_not_truncated");
                    require(spell.getRarity(level) == SpellRarity.LEGENDARY || nativeMax == 1,
                            "native_over_max_rarity_is_supported");
                    // getUniqueInfo builds native tooltip statistics and does not execute the spell.
                    require(spell.getUniqueInfo(level, actor) != null, "native_tooltip_statistics_returned");
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("Breakthrough statistics failed for " + spell.getSpellId()
                            + " at level " + level, failure);
                }
                levels++;
            }
            spells++;
        }
        require(spells >= 4, "registered_spells_present_for_smoke_check");
        return Map.of("spells", spells, "max_plus_1_2_3_checks", levels,
                "native_negative_cast_time_results", negativeTimes,
                "scope", "mana/power/cast-time/effective-level/rarity/tooltip-statistics only; no actual casts",
                "status", "PASS");
    }

    private static ItemStack scroll(AbstractSpell spell, int level) {
        ItemStack result = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainerMutable container = ISpellContainer.create(1, false, false).mutableCopy();
        require(container.addSpellAtIndex(spell, level, 0, true), "test_scroll_container_created");
        ISpellContainer.set(result, container.toImmutable());
        return result;
    }

    private static ItemStack legendaryInk() {
        return new ItemStack(InkItem.getInkForRarity(SpellRarity.LEGENDARY));
    }

    private static void require(boolean condition, String check) {
        if (!condition) throw new IllegalStateException("Breakthrough test failed: " + check);
    }
}
