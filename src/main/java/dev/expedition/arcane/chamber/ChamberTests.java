package dev.expedition.arcane.chamber;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.compat.ScrollAscensionRecipes;
import dev.expedition.arcane.compat.ScrollInkingRecipe;
import dev.expedition.arcane.compat.AscensionAssemblyRecipe;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import dev.expedition.arcane.item.UninkedScrollItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/** Exercises actual registered blocks, native scroll codecs and basin inventories in a disposable test area. */
public final class ChamberTests {
    private ChamberTests() {}

    public static Map<String, Object> run(ServerLevel level, BlockPos origin) {
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("spell_config", initializeSpellConfig(level));
        BlockPos pos = origin.immutable();
        Set<UUID> oldBolts = boltIds(level);
        try {
            require(SpellRegistry.FIREBOLT_SPELL.get().getMaxLevel() == 10,
                    "fixture_firebolt_max_is_10; diagnostics=" + results.get("spell_config"));
            AscensionChamberBlockEntity chamber = fresh(level, pos, 4, false, 1000);
            chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT).getOrCreateTag()
                    .putString("arcane_industry_test_marker", "preserve me");
            chamber.tickServer(); // Establish the manual transaction at t=0.
            for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME - 1; i++) chamber.tickServer();
            require(chamber.isManualProcessing() && chamber.getManualProgress() == 69
                    && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT).isEmpty(),
                    "manual_waits_70_ticks_after_start");
            chamber.tickServer();
            ItemStack produced = chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT);
            require(scrollLevel(produced) == 5, "ordinary_4_to_5_result");
            require(chamber.sourceTank.getFluidAmount() == 750 && chamber.getNormalFuelTicks() == 0, "ordinary_success_pays_exactly_250mb");
            require(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.INK_SLOT).isEmpty(), "bottled_ink_consumed");
            require(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.BOTTLE_SLOT).is(Items.GLASS_BOTTLE)
                    && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.BOTTLE_SLOT).getCount() == 1, "empty_bottle_returned");
            require(!chamber.hasTemplate(), "crafted_chamber_operates_without_installed_template");
            require("preserve me".equals(produced.getOrCreateTag().getString("arcane_industry_test_marker")), "scroll_other_nbt_retained");
            require(ISpellContainer.get(produced).getActiveSpells().get(0).isLocked(), "scroll_lock_retained");
            results.put("ordinary_4_to_5_and_70_tick_manual_cycle", "PASS");
            results.put("bottle_and_nbt_preserved_without_runtime_template", "PASS");

            chamber.inventory.setStackInSlot(7, new ItemStack(CrimsonContent.EMBER_CAKE.get(), 2));
            CompoundTag saved = chamber.saveWithFullMetadata();
            int savedFuel = chamber.getFuelTicks();
            level.removeBlockEntity(pos);
            AscensionChamberBlockEntity restored = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
            restored.load(saved);
            level.setBlockEntity(restored);
            require(restored.sourceTank.getFluidAmount() == 750
                    && restored.inventory.getStackInSlot(7).getCount() == 2 && restored.getFuelTicks() == savedFuel
                    && !restored.hasTemplate() && scrollLevel(restored.inventory.getStackInSlot(3)) == 5
                    && restored.inventory.getStackInSlot(4).is(Items.GLASS_BOTTLE), "nbt_reload_retains_real_state");
            results.put("save_load_roundtrip", "PASS");

            chamber = fresh(level, pos, 7, false, 1000);
            require(!chamber.processOneCycle(), "ordinary_7_to_8_refused");
            unchangedInputs(chamber, 7, 1000, "ordinary_7_to_8");
            results.put("ordinary_rejects_7_to_8_without_material_loss", "PASS");

            chamber = fresh(level, pos, 7, true, 1000);
            require(chamber.processOneCycle(), "transcendent_7_to_8_accepts");
            require(scrollLevel(chamber.inventory.getStackInSlot(3)) == 8
                    && chamber.sourceTank.getFluidAmount() == 937 && chamber.getCrimsonCreditQuarterMb() == 2, "transcendent_pays_exactly_62_5mb");
            results.put("transcendent_7_to_8", "PASS");

            chamber = fresh(level, pos, 10, true, 1000);
            require(!chamber.processOneCycle(), "level_10_needs_breakthrough_cake");
            unchangedInputs(chamber, 10, 1000, "max_level");
            results.put("breakthrough_needs_cake_without_material_loss", "PASS");

            chamber = fresh(level, pos, 4, false, 1000);
            SpellRarity target = SpellRegistry.FIREBOLT_SPELL.get().getRarity(5);
            SpellRarity wrong = target == SpellRarity.COMMON ? SpellRarity.LEGENDARY : SpellRarity.COMMON;
            chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(wrong)));
            require(!chamber.processOneCycle(), "wrong_ink_refused");
            unchangedInputs(chamber, 4, 1000, "wrong_ink");
            require(((InkItem) chamber.inventory.getStackInSlot(2).getItem()).getRarity() == wrong, "wrong_ink_not_replaced");
            results.put("wrong_ink_without_material_loss", "PASS");

            chamber = fresh(level, pos, 4, false, 0);
            require(!chamber.processOneCycle(), "empty_tank_cannot_process");
            unchangedInputs(chamber, 4, 0, "empty_tank");
            results.put("empty_tank_cannot_process", "PASS");

            chamber = fresh(level, pos, 4, false, 1000);
            chamber.inventory.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
            require(!chamber.processOneCycle(), "full_output_refused");
            require(scrollLevel(chamber.inventory.getStackInSlot(1)) == 4
                    && chamber.inventory.getStackInSlot(2).getCount() == 1
                    && chamber.sourceTank.getFluidAmount() == 1000
                    && chamber.inventory.getStackInSlot(3).getCount() == 64
                    && chamber.inventory.getStackInSlot(4).isEmpty(), "full_output_changes_nothing");
            results.put("blocked_output_without_material_loss", "PASS");

            chamber = fresh(level, pos, 4, false, 1000);
            require(chamber.inventory.getStackInSlot(0).isEmpty() && chamber.processOneCycle(),
                    "template_is_a_crafting_part_not_a_runtime_key");
            require(scrollLevel(chamber.inventory.getStackInSlot(3)) == 5
                    && chamber.sourceTank.getFluidAmount() == 750, "template_free_work_pays_real_materials");
            results.put("crafted_chamber_needs_no_runtime_template", "PASS");

            chamber = fresh(level, pos, 4, false, 0);
            CompoundTag noFuel = chamber.saveWithFullMetadata();
            noFuel.putInt("NormalFuel", 0);
            chamber.load(noFuel);
            require(!chamber.processOneCycle(), "missing_fuel_refused");
            unchangedInputs(chamber, 4, 0, "missing_fuel");
            results.put("fuel_required", "PASS");

            chamber = fresh(level, pos, 4, false, 1000);
            chamber.inventory.setStackInSlot(1, ItemStack.EMPTY);
            chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
            level.setBlock(pos.above(), AllBlocks.BASIN.getDefaultState(), 3);
            require(level.getBlockEntity(pos.above()) instanceof BasinBlockEntity, "native_basin_placed");
            BasinBlockEntity basin = (BasinBlockEntity) level.getBlockEntity(pos.above());
            require(basin != null && basin.inputTank != null, "native_basin_initialized");
            require(basin.getInputInventory().insertItem(0, scroll(4), false).isEmpty(), "basin_scroll_inserted");
            IFluidHandler inkTank = basin.inputTank.getCapability().orElse(null);
            require(inkTank != null, "basin_fluid_capability");
            require(inkTank.fill(new FluidStack(InkItem.getInkForRarity(target).fluid().get(), 250),
                    IFluidHandler.FluidAction.EXECUTE) == 250, "basin_250mb_ink_inserted");
            require(chamber.processOneCycle(), "native_basin_upgrade_committed");
            ItemStack basinResult = ItemStack.EMPTY;
            for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) {
                if (scrollLevel(basin.getOutputInventory().getStackInSlot(slot)) == 5) {
                    basinResult = basin.getOutputInventory().getStackInSlot(slot);
                    break;
                }
            }
            require(scrollLevel(basinResult) == 5 && basin.getInputInventory().getStackInSlot(0).isEmpty(), "basin_one_scroll_upgraded");
            require(inkTank.drain(250, IFluidHandler.FluidAction.SIMULATE).isEmpty()
                    && chamber.sourceTank.getFluidAmount() == 750, "basin_pays_250mb_ink_and_250mb_solution");
            require(chamber.inventory.getStackInSlot(4).isEmpty(), "fluid_route_has_no_bottle_output");
            results.put("native_basin_4_to_5_transaction", "PASS");
            runFuelAndContainerTests(level, pos, results);
            runEssenceTopOffTests(level, pos, results);
            runCakeTransactions(level, pos, results);
            runCrimsonPrecisionTests(level, pos, results);
            runSolutionConversionTests(level, pos, results);
            runNativeAscensionSequenceTests(level, pos, results);
            runTieredPrintingInkChecks(level, results);
            runCosmeticAndLightningTests(level, pos.offset(512, 0, 0), results);
            runResidualFireTests(level, pos.offset(1024, 0, 0), results);
            results.put("interaction_rules", ChamberInteractionRulesTests.run(level, pos.offset(1536, 0, 0)));
            results.put("display_hit_rules", ChamberDisplayHitTests.run(level, pos.offset(1600, 0, 0)));
            results.put("status", "PASS");
            return results;
        } catch (RuntimeException failure) {
            results.put("status", "FAILED");
            throw new IllegalStateException("Chamber tests failed; completed=" + results + "; " + failure.getMessage(), failure);
        } finally {
            clear(level, pos);
            for (LightningBolt bolt : bolts(level)) if (!oldBolts.contains(bolt.getUUID())) bolt.discard();
        }
    }

    private static void runFuelAndContainerTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        AscensionChamberBlockEntity chamber = empty(level, pos);
        IFluidHandler fluids = chamber.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(
                () -> new IllegalStateException("missing_side_fluid_capability"));
        require(fluids.getTanks() == 1 && fluids.getTankCapacity(0) == 4000, "single_4000mb_tank");
        require(!chamber.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(), "top_reserved_for_basin");
        for (Direction side : Direction.values()) if (side != Direction.UP) {
            require(chamber.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent(), "fluid_side_" + side);
        }
        require(chamber.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent(), "null_side_fluid_capability");
        FluidStack blue = new FluidStack(ArcaneContent.SOURCE.get(), 1000);
        FluidStack red = new FluidStack(CrimsonContent.SOURCE.get(), 250);
        require(fluids.fill(blue, IFluidHandler.FluidAction.SIMULATE) == 1000
                && fluids.fill(red, IFluidHandler.FluidAction.SIMULATE) == 250
                && chamber.sourceTank.isEmpty(), "simulated_fill_does_not_choose_a_fluid_or_change_amount");
        require(fluids.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                IFluidHandler.FluidAction.EXECUTE) == 0, "wrong_fluid_refused");
        require(fluids.fill(blue, IFluidHandler.FluidAction.EXECUTE) == 1000
                && fluids.fill(red, IFluidHandler.FluidAction.SIMULATE) == 250
                && chamber.sourceTank.getFluidAmount() == 1000 && !chamber.isTranscendent(),
                "foreign_simulation_accepts_without_changing_fluid_or_state");
        require(fluids.fill(red, IFluidHandler.FluidAction.EXECUTE) == 250
                && chamber.sourceTank.getFluidAmount() == 250 && chamber.isTranscendent(),
                "single_tank_replaces_1000_blue_with_only_250_red");
        require(fluids.drain(blue, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                && fluids.drain(100, IFluidHandler.FluidAction.SIMULATE).getAmount() == 100
                && chamber.sourceTank.getFluidAmount() == 250, "typed_and_simulated_drain_do_not_remove_wrong_liquid");
        fluids.getFluidInTank(0).setAmount(1);
        require(chamber.sourceTank.getFluidAmount() == 250 && fluids.getTankCapacity(1) == 0
                && fluids.getFluidInTank(-1).isEmpty() && !fluids.isFluidValid(1, blue), "defensive_query_and_safe_invalid_indices");
        require(fluids.drain(new FluidStack(CrimsonContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE).getAmount() == 250
                && fluids.fill(red, IFluidHandler.FluidAction.EXECUTE) == 250
                && chamber.isTranscendent() && chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == 2,
                "extract_and_fill_updates_mode_immediately");
        require(!chamber.hasTemplate(), "activation_needs_no_template");
        for (int i = 0; i < 300; i++) chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 250 && chamber.getSuperFuelTicks() == 0,
                "idle_red_fluid_does_not_burn_with_time");
        results.put("single_tank_simulation_replacement_and_typed_drain", "PASS");

        chamber = fresh(level, pos, 4, false, 249);
        for (int i = 0; i < 300; i++) chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 249 && chamber.getProgress() == 0
                && chamber.inventory.getStackInSlot(3).isEmpty()
                && chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == 1,
                "under_250mb_keeps_fluid_appearance_but_does_not_process");
        chamber = fresh(level, pos, 4, false, 1000);
        chamber.inventory.setStackInSlot(1, ItemStack.EMPTY);
        for (int i = 0; i < 300; i++) chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 1000, "no_scroll_zero_fluid_cost");
        chamber.inventory.setStackInSlot(1, scroll(4));
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        for (int i = 0; i < 300; i++) chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 1000, "missing_ink_zero_fluid_cost");
        chamber = fresh(level, pos, 7, true, 1000);
        chamber.tickServer();
        for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME; i++) chamber.tickServer();
        require(scrollLevel(chamber.inventory.getStackInSlot(3)) == 8 && chamber.sourceTank.getFluidAmount() == 937
                && chamber.getCrimsonCreditQuarterMb() == 2, "red_70_tick_manual_success_costs_exactly_62_5mb");
        results.put("idle_missing_material_and_under_250mb_zero_cost_red_70_tick_manual_success", "PASS");

        chamber = fresh(level, pos, 4, false, 1000);
        chamber.tickServer();
        for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME - 1; i++) chamber.tickServer();
        require(chamber.sourceTank.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount() == 1000,
                "unfinished_recipe_has_not_precharged_fluid");
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        require(!chamber.isManualProcessing() && chamber.getManualProgress() == 0
                        && chamber.inventory.getStackInSlot(3).isEmpty() && chamber.sourceTank.getFluidAmount() == 1000,
                "changed_fluid_cost_cancels_old_manual_transaction_without_consumption");
        chamber.tickServer();
        require(chamber.isManualProcessing() && chamber.getManualProgress() == 0,
                "changed_fluid_starts_a_new_manual_transaction");
        for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME - 1; i++) chamber.tickServer();
        require(chamber.inventory.getStackInSlot(3).isEmpty() && chamber.sourceTank.getFluidAmount() == 1000,
                "changed_fluid_transaction_waits_its_full_manual_duration");
        chamber.tickServer();
        require(scrollLevel(chamber.inventory.getStackInSlot(3)) == 5 && chamber.sourceTank.getFluidAmount() == 937
                && chamber.getCrimsonCreditQuarterMb() == 2, "restarted_manual_transaction_pays_new_crimson_cost_once");
        results.put("manual_transaction_restarts_when_fluid_cost_signature_changes", "PASS");

        for (int[] route : new int[][]{{4, 0, 4}, {4, 1, 16}, {7, 1, 16}}) {
            int inputLevel = route[0];
            boolean transcendent = route[1] == 1;
            int cycles = route[2];
            chamber = fresh(level, pos, inputLevel, transcendent, 1000);
            for (int i = 0; i < cycles; i++) {
                chamber.inventory.setStackInSlot(1, scroll(inputLevel));
                chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(
                        SpellRegistry.FIREBOLT_SPELL.get().getRarity(inputLevel + 1))));
                chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
                chamber.inventory.setStackInSlot(4, ItemStack.EMPTY);
                require(chamber.processOneCycle() && scrollLevel(chamber.inventory.getStackInSlot(3)) == inputLevel + 1,
                        "bucket_transaction_" + transcendent + "_" + i);
            }
            require(chamber.sourceTank.isEmpty() && chamber.getCrimsonCreditQuarterMb() == 0,
                    "one_bucket_exactly_" + cycles + "_base_upgrades_" + transcendent + "_" + inputLevel);
            chamber.inventory.setStackInSlot(1, scroll(inputLevel));
            chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(
                    SpellRegistry.FIREBOLT_SPELL.get().getRarity(inputLevel + 1))));
            chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
            chamber.inventory.setStackInSlot(4, ItemStack.EMPTY);
            require(!chamber.processOneCycle(), "bucket_has_no_extra_base_upgrade_" + transcendent);
        }
        results.put("blue_bucket_4_and_red_bucket_16_target_5_or_8_upgrades", "PASS");

        int[][] lowTiers = {{1, 100}, {2, 150}, {3, 200}};
        for (int[] tier : lowTiers) {
            chamber = fresh(level, pos, tier[0], false, tier[1] - 1);
            require(!chamber.processOneCycle() && chamber.sourceTank.getFluidAmount() == tier[1] - 1
                    && scrollLevel(chamber.inventory.getStackInSlot(1)) == tier[0]
                    && chamber.inventory.getStackInSlot(2).getCount() == 1,
                    "low_tier_cost_minus_1_refused_without_loss_" + tier[0]);
            chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 1), IFluidHandler.FluidAction.EXECUTE);
            chamber.tickServer();
            for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME - 1; i++) chamber.tickServer();
            require(chamber.sourceTank.getFluidAmount() == tier[1] && chamber.inventory.getStackInSlot(3).isEmpty(),
                    "low_tier_waits_full_70_manual_ticks_without_precharging_" + tier[0]);
            chamber.tickServer();
            require(chamber.sourceTank.isEmpty() && scrollLevel(chamber.inventory.getStackInSlot(3)) == tier[0] + 1
                    && chamber.inventory.getStackInSlot(2).isEmpty()
                    && chamber.inventory.getStackInSlot(4).is(Items.GLASS_BOTTLE),
                    "low_tier_exact_cost_committed_" + tier[0]);
        }
        results.put("low_tier_1_to_4_costs_100_150_200_exact_boundaries_and_70_manual_ticks", "PASS");

        for (int[] tier : new int[][]{{1, 25, 0}, {2, 38, 2}, {3, 50, 0}}) {
            chamber = fresh(level, pos, tier[0], true, tier[1] - 1);
            require(!chamber.processOneCycle() && chamber.getCrimsonCreditQuarterMb() == 0
                    && chamber.sourceTank.getFluidAmount() == tier[1] - 1, "red_low_tier_minus_1_refused_" + tier[0]);
            chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 1), IFluidHandler.FluidAction.EXECUTE);
            chamber.tickServer();
            for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME; i++) chamber.tickServer();
            require(chamber.sourceTank.isEmpty() && chamber.getCrimsonCreditQuarterMb() == tier[2]
                    && scrollLevel(chamber.inventory.getStackInSlot(3)) == tier[0] + 1,
                    "red_low_tier_exact_quarter_cost_" + tier[0]);
        }
        results.put("red_low_tier_costs_25_37_5_50_exact_and_70_manual_ticks", "PASS");

        int[][] tiers = {{4, 250}, {5, 500}, {6, 750}, {7, 63}, {8, 125}, {9, 188}};
        for (int[] tier : tiers) {
            chamber = fresh(level, pos, tier[0], tier[0] >= 7, tier[1] - 1);
            require(!chamber.processOneCycle() && chamber.sourceTank.getFluidAmount() == tier[1] - 1
                    && scrollLevel(chamber.inventory.getStackInSlot(1)) == tier[0], "tier_cost_minus_1_refused_" + tier[0]);
            chamber.sourceTank.fill(new FluidStack(tier[0] >= 7 ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 1),
                    IFluidHandler.FluidAction.EXECUTE);
            chamber.tickServer();
            for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME; i++) chamber.tickServer();
            require(chamber.sourceTank.isEmpty() && scrollLevel(chamber.inventory.getStackInSlot(3)) == tier[0] + 1
                    && chamber.getCrimsonCreditQuarterMb() == (tier[0] == 7 || tier[0] == 9 ? 2 : 0),
                    "tier_exact_cost_committed_" + tier[0]);
        }
        results.put("blue_250_500_750_red_62_5_125_187_5_exact_boundaries", "PASS");

        chamber = empty(level, pos);
        ItemStack essence = new ItemStack(ItemRegistry.ARCANE_ESSENCE.get(), 2);
        require(chamber.insertFromPlayer(essence, false) && chamber.insertFromPlayer(essence, false)
                && essence.isEmpty() && chamber.sourceTank.getFluidAmount() == 500
                && chamber.getNormalFuelTicks() == 0 && chamber.inventory.getStackInSlot(5).isEmpty(),
                "manual_essence_enters_tank_as_whole_250mb_each");
        chamber.sourceTank.drain(500, IFluidHandler.FluidAction.EXECUTE);
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        ItemStack convertedEssence = new ItemStack(ItemRegistry.ARCANE_ESSENCE.get());
        require(chamber.insertFromPlayer(convertedEssence, false) && convertedEssence.isEmpty()
                && chamber.sourceTank.getFluidAmount() == 250 && !chamber.isTranscendent(),
                "essence_replaces_nonfull_red_with_250_blue");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 3750), IFluidHandler.FluidAction.EXECUTE);
        ItemStack rejected = new ItemStack(ItemRegistry.ARCANE_ESSENCE.get());
        require(!chamber.getAutomationInventory().isItemValid(5, rejected)
                && chamber.getAutomationInventory().insertItem(5, rejected.copy(), false).getCount() == 1,
                "physically_full_tank_rejects_new_automatic_essence");
        chamber.inventory.setStackInSlot(5, new ItemStack(ItemRegistry.ARCANE_ESSENCE.get()));
        chamber.tickServer();
        require(chamber.inventory.getStackInSlot(5).getCount() == 1 && chamber.sourceTank.getFluidAmount() == 4000,
                "physically_full_tank_preserves_old_essence_queue");
        chamber.sourceTank.drain(4000, IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        require(chamber.inventory.getStackInSlot(5).isEmpty() && chamber.sourceTank.getFluidAmount() == 250
                && !chamber.isTranscendent(), "queued_essence_converts_after_capacity_opens");
        require(!chamber.insertFromPlayer(new ItemStack(CrimsonContent.EMBER_CAKE.get()), false), "manual_cake_requires_transcendence");
        require(chamber.getAutomationInventory().insertItem(7, new ItemStack(CrimsonContent.EMBER_CAKE.get()), true).isEmpty()
                && !chamber.hasCake(), "cake_insert_simulation_has_no_mutation");
        require(chamber.getAutomationInventory().insertItem(7, new ItemStack(CrimsonContent.EMBER_CAKE.get()), false).isEmpty()
                && chamber.hasCake(), "automation_can_buffer_cake");
        results.put("essence_250mb_replacement_and_full_tank_queue_preservation", "PASS");

        chamber = empty(level, pos);
        chamber.inventory.setStackInSlot(6, new ItemStack(ItemRegistry.CINDER_ESSENCE.get(), 3));
        CompoundTag legacy = chamber.saveWithFullMetadata();
        legacy.getCompound("Inventory").putInt("Size", 7);
        legacy.putInt("NormalFuel", 1234);
        legacy.putInt("SuperFuel", 5678);
        chamber.load(legacy);
        for (int i = 0; i < 300; i++) chamber.tickServer();
        require(chamber.inventory.getSlots() == 8 && chamber.getNormalFuelTicks() == 1234 && chamber.getSuperFuelTicks() == 5678
                && chamber.inventory.getStackInSlot(6).getCount() == 3, "legacy_energy_is_preserved_and_does_not_idle_burn");
        chamber.inventory.setStackInSlot(1, scroll(7));
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(SpellRegistry.FIREBOLT_SPELL.get().getRarity(8))));
        require(chamber.processOneCycle() && chamber.getSuperFuelTicks() == 4418 && chamber.getNormalFuelTicks() == 1234
                && chamber.getCrimsonCreditQuarterMb() == 2, "legacy_super_energy_pays_63mb_and_preserves_half_mb_credit");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        require(!chamber.isTranscendent() && chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == 1,
                "new_tank_fluid_controls_appearance_despite_legacy_red_energy");
        require(!chamber.inventory.isItemValid(6, new ItemStack(ItemRegistry.CINDER_ESSENCE.get()))
                && chamber.getAutomationInventory().extractItem(6, 3, false).getCount() == 3, "legacy_cinder_recoverable_not_new_fuel");
        results.put("legacy_seven_slot_energy_migration_idle_preservation_and_success_cost", "PASS");
        runContainers(level, pos, results);
    }

    private static void runContainers(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        ServerPlayer actor = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("2c3b73c4-f584-461c-8279-ef8eb38afe48"), "ArcaneFuelTest"));
        ListTag inventoryBefore = actor.getInventory().save(new ListTag());
        int selectedBefore = actor.getInventory().selected;
        boolean creativeBefore = actor.getAbilities().instabuild;
        try {
            actor.getAbilities().instabuild = false;
            Item[] full = {ArcaneContent.BOTTLE.get(), ArcaneContent.BUCKET.get(), CrimsonContent.BOTTLE.get(), CrimsonContent.BUCKET.get()};
            for (int i = 0; i < full.length; i++) {
                actor.getInventory().clearContent();
                boolean red = i >= 2, bucket = i % 2 == 1;
                int amount = bucket ? 1000 : 250;
                Item emptyItem = bucket ? Items.BUCKET : Items.GLASS_BOTTLE;
                AscensionChamberBlockEntity chamber = empty(level, pos);
                ItemStack held = new ItemStack(full[i]);
                actor.setItemInHand(InteractionHand.MAIN_HAND, held);
                require(chamber.fillFromContainer(held, actor) && held.isEmpty()
                                && chamber.sourceTank.getFluidAmount() == amount && chamber.isTranscendent() == red
                                && count(actor, emptyItem) == 1,
                        "whole_container_transfers_exact_volume_and_returns_one_empty_" + i);
                chamber.sourceTank.fill(new FluidStack(red ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(),
                        4000 - amount - amount + 1), IFluidHandler.FluidAction.EXECUTE);
                ItemStack shortSpace = new ItemStack(full[i]);
                require(!chamber.fillFromContainer(shortSpace, actor) && shortSpace.getCount() == 1
                                && chamber.sourceTank.getFluidAmount() == 4000 - amount + 1 && count(actor, emptyItem) == 1,
                        "same_solution_requires_space_for_entire_bottle_or_bucket_" + i);
                chamber.sourceTank.drain(1, IFluidHandler.FluidAction.EXECUTE);
                require(chamber.fillFromContainer(shortSpace, actor) && shortSpace.isEmpty()
                                && chamber.sourceTank.getFluidAmount() == 4000 && count(actor, emptyItem) == 2,
                        "same_solution_accepts_exact_whole_container_capacity_" + i);

                chamber = empty(level, pos);
                chamber.sourceTank.fill(new FluidStack(red ? ArcaneContent.SOURCE.get() : CrimsonContent.SOURCE.get(), 4000),
                        IFluidHandler.FluidAction.EXECUTE);
                ItemStack foreign = new ItemStack(full[i]);
                require(chamber.fillFromContainer(foreign, actor) && foreign.isEmpty()
                                && chamber.sourceTank.getFluidAmount() == amount && chamber.isTranscendent() == red
                                && chamber.getCrimsonCreditQuarterMb() == 0 && count(actor, emptyItem) == 3,
                        "foreign_whole_container_discards_full_previous_tank_and_returns_empty_" + i);

                actor.getInventory().clearContent();
                actor.getAbilities().instabuild = true;
                ItemStack creative = new ItemStack(full[i]);
                actor.setItemInHand(InteractionHand.MAIN_HAND, creative);
                chamber = empty(level, pos);
                require(chamber.fillFromContainer(creative, actor) && creative.getCount() == 1
                                && chamber.sourceTank.getFluidAmount() == amount && count(actor, emptyItem) == 0,
                        "creative_fill_keeps_original_and_does_not_create_empty_container_" + i);
                actor.getAbilities().instabuild = false;
            }
            results.put("whole_bottles_buckets_same_type_capacity_and_foreign_replacement", "PASS");
            runContainerDrainChecks(level, pos, actor, results);
        } finally {
            actor.getInventory().clearContent();
            actor.getInventory().load(inventoryBefore);
            actor.getInventory().selected = selectedBefore;
            actor.getAbilities().instabuild = creativeBefore;
        }
    }

    private static void runContainerDrainChecks(ServerLevel level, BlockPos pos, ServerPlayer actor, Map<String, Object> results) {
        for (boolean red : new boolean[]{false, true}) for (boolean bucket : new boolean[]{false, true}) {
            int amount = bucket ? 1000 : 250;
            Item emptyItem = bucket ? Items.BUCKET : Items.GLASS_BOTTLE;
            Item fullItem = bucket ? red ? CrimsonContent.BUCKET.get() : ArcaneContent.BUCKET.get()
                    : red ? CrimsonContent.BOTTLE.get() : ArcaneContent.BOTTLE.get();
            String label = red + "_" + bucket;
            actor.getInventory().clearContent();
            actor.getAbilities().instabuild = false;
            AscensionChamberBlockEntity chamber = empty(level, pos);
            chamber.sourceTank.fill(new FluidStack(red ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), amount - 1),
                    IFluidHandler.FluidAction.EXECUTE);
            ItemStack held = new ItemStack(emptyItem);
            actor.setItemInHand(InteractionHand.MAIN_HAND, held);
            require(!chamber.drainToContainer(held, actor) && held.getCount() == 1
                            && chamber.sourceTank.getFluidAmount() == amount - 1 && count(actor, fullItem) == 0,
                    "short_solution_cannot_consume_empty_container_" + label);
            chamber.sourceTank.fill(new FluidStack(red ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 1),
                    IFluidHandler.FluidAction.EXECUTE);
            if (red) {
                CompoundTag credited = chamber.saveWithFullMetadata();
                credited.putInt("CrimsonCreditQuarterMb", 2);
                chamber.load(credited);
            }
            require(chamber.drainToContainer(held, actor) && actor.getMainHandItem().is(fullItem)
                            && chamber.sourceTank.isEmpty() && chamber.getCrimsonCreditQuarterMb() == 0,
                    "whole_solution_fills_original_hand_and_empty_red_keeps_red_container_" + label);

            actor.getInventory().clearContent();
            chamber = empty(level, pos);
            chamber.sourceTank.fill(new FluidStack(red ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), amount + 17),
                    IFluidHandler.FluidAction.EXECUTE);
            held = new ItemStack(emptyItem, 2);
            actor.setItemInHand(InteractionHand.OFF_HAND, held);
            require(chamber.drainToContainer(held, actor) && actor.getOffhandItem().is(emptyItem)
                            && actor.getOffhandItem().getCount() == 1 && count(actor, fullItem) == 1
                            && chamber.sourceTank.getFluidAmount() == 17,
                    "stacked_offhand_container_returns_one_full_without_losing_remainder_" + label);

            actor.getInventory().clearContent();
            actor.getAbilities().instabuild = true;
            chamber = empty(level, pos);
            chamber.sourceTank.fill(new FluidStack(red ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), amount * 2),
                    IFluidHandler.FluidAction.EXECUTE);
            held = new ItemStack(emptyItem);
            actor.setItemInHand(InteractionHand.MAIN_HAND, held);
            require(chamber.drainToContainer(held, actor) && actor.getMainHandItem() == held && held.getCount() == 1
                            && count(actor, fullItem) == 1 && chamber.sourceTank.getFluidAmount() == amount,
                    "creative_native_pickup_keeps_empty_but_debits_physical_liquid_" + label);
            require(chamber.drainToContainer(held, actor) && count(actor, fullItem) == 1 && chamber.sourceTank.isEmpty(),
                    "creative_native_pickup_does_not_duplicate_existing_filled_kind_" + label);
            actor.getAbilities().instabuild = false;
        }
        results.put("empty_bottle_bucket_full_short_stacked_offhand_red_and_creative_pickup", "PASS");
    }

    private static void runEssenceTopOffTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        for (int initial : new int[]{3800, 3999}) {
            AscensionChamberBlockEntity chamber = empty(level, pos);
            chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), initial), IFluidHandler.FluidAction.EXECUTE);
            ItemStack held = new ItemStack(ItemRegistry.ARCANE_ESSENCE.get(), 2);
            require(chamber.insertFromPlayer(held, false) && held.getCount() == 1
                    && chamber.sourceTank.getFluidAmount() == 4000, "manual_essence_tops_off_and_discards_excess_" + initial);
            require(!chamber.insertFromPlayer(held, false) && held.getCount() == 1
                    && chamber.sourceTank.getFluidAmount() == 4000, "full_tank_refuses_manual_essence_" + initial);
            require(!chamber.inventory.isItemValid(5, held) && !chamber.getAutomationInventory().isItemValid(5, held)
                    && chamber.getAutomationInventory().insertItem(5, held.copy(), true).getCount() == 1
                    && chamber.getAutomationInventory().insertItem(5, held.copy(), false).getCount() == 1
                    && chamber.inventory.getStackInSlot(5).isEmpty(), "full_tank_refuses_new_automatic_essence_" + initial);
            chamber = empty(level, pos);
            chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), initial), IFluidHandler.FluidAction.EXECUTE);
            require(chamber.getAutomationInventory().insertItem(5, new ItemStack(ItemRegistry.ARCANE_ESSENCE.get(), 2), false).isEmpty(),
                    "not_full_tank_accepts_essence_queue_" + initial);
            chamber.tickServer();
            require(chamber.sourceTank.getFluidAmount() == 4000 && chamber.inventory.getStackInSlot(5).getCount() == 1,
                    "automatic_essence_tops_off_with_exactly_one_item_" + initial);
            chamber.tickServer();
            require(chamber.sourceTank.getFluidAmount() == 4000 && chamber.inventory.getStackInSlot(5).getCount() == 1,
                    "full_tank_preserves_existing_queue_" + initial);
            chamber.sourceTank.drain(100, IFluidHandler.FluidAction.EXECUTE);
            chamber.tickServer();
            require(chamber.sourceTank.getFluidAmount() == 4000 && chamber.inventory.getStackInSlot(5).isEmpty(),
                    "old_queue_resumes_partial_topoff_when_space_opens_" + initial);
        }
        AscensionChamberBlockEntity chamber = empty(level, pos);
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 800), IFluidHandler.FluidAction.EXECUTE);
        ItemStack essence = new ItemStack(ItemRegistry.ARCANE_ESSENCE.get());
        require(chamber.insertFromPlayer(essence, false) && essence.isEmpty()
                && chamber.sourceTank.getFluidAmount() == 250 && !chamber.isTranscendent(),
                "manual_essence_discards_800_red_and_adds_only_250_blue");
        chamber = empty(level, pos);
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 800), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.getAutomationInventory().isItemValid(5, new ItemStack(ItemRegistry.ARCANE_ESSENCE.get()))
                && chamber.getAutomationInventory().insertItem(5, new ItemStack(ItemRegistry.ARCANE_ESSENCE.get()), false).isEmpty(),
                "not_full_red_tank_accepts_automatic_essence");
        chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 250 && chamber.inventory.getStackInSlot(5).isEmpty()
                && !chamber.isTranscendent(), "automatic_essence_uses_same_replacement_plan");
        chamber = empty(level, pos);
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        ItemStack fullRedEssence = new ItemStack(ItemRegistry.ARCANE_ESSENCE.get());
        require(!chamber.insertFromPlayer(fullRedEssence, false) && fullRedEssence.getCount() == 1
                && !chamber.getAutomationInventory().isItemValid(5, fullRedEssence)
                && chamber.getAutomationInventory().insertItem(5, fullRedEssence.copy(), false).getCount() == 1
                && chamber.sourceTank.getFluidAmount() == 4000 && chamber.isTranscendent(),
                "physically_full_red_tank_refuses_manual_and_automatic_raw_essence");
        chamber.inventory.setStackInSlot(5, fullRedEssence.copy());
        chamber.tickServer();
        require(chamber.inventory.getStackInSlot(5).getCount() == 1 && chamber.sourceTank.getFluidAmount() == 4000,
                "full_red_tank_preserves_old_queue");
        chamber.sourceTank.drain(1, IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 250 && !chamber.isTranscendent()
                && chamber.inventory.getStackInSlot(5).isEmpty(), "old_queue_replaces_nonfull_red_with_only_250_blue");
        results.put("essence_topoff_replacement_and_physically_full_tank_queue_preservation", "PASS");
    }

    private static void runCakeTransactions(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        AscensionChamberBlockEntity chamber = fresh(level, pos, 10, true, 1000);
        chamber.inventory.setStackInSlot(7, new ItemStack(CrimsonContent.EMBER_CAKE.get(), 2));
        chamber.inventory.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
        require(!chamber.processOneCycle() && chamber.inventory.getStackInSlot(7).getCount() == 2
                && scrollLevel(chamber.inventory.getStackInSlot(1)) == 10
                && chamber.inventory.getStackInSlot(2).getCount() == 1, "blocked_output_keeps_cake_and_inputs");
        chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(SpellRarity.COMMON)));
        require(!chamber.processOneCycle() && chamber.inventory.getStackInSlot(7).getCount() == 2, "wrong_ink_keeps_cake");
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(SpellRarity.LEGENDARY)));
        require(chamber.processOneCycle() && scrollLevel(chamber.inventory.getStackInSlot(3)) == 11
                && chamber.inventory.getStackInSlot(7).getCount() == 1
                && chamber.inventory.getStackInSlot(4).is(Items.GLASS_BOTTLE), "manual_breakthrough_spends_exactly_one_cake");
        chamber = fresh(level, pos, 7, true, 1000);
        chamber.inventory.setStackInSlot(7, new ItemStack(CrimsonContent.EMBER_CAKE.get(), 2));
        require(chamber.processOneCycle() && scrollLevel(chamber.inventory.getStackInSlot(3)) == 8
                && chamber.inventory.getStackInSlot(7).getCount() == 2, "native_level_upgrade_does_not_spend_cake");

        chamber = fresh(level, pos, 10, true, 1000);
        chamber.inventory.setStackInSlot(7, new ItemStack(CrimsonContent.EMBER_CAKE.get(), 3));
        int[] remaining = {750, 437, 62};
        for (int i = 0; i < remaining.length; i++) {
            require(chamber.processOneCycle() && scrollLevel(chamber.inventory.getStackInSlot(3)) == 11 + i
                    && chamber.sourceTank.getFluidAmount() == remaining[i]
                    && chamber.inventory.getStackInSlot(7).getCount() == 2 - i,
                    "breakthrough_dynamic_cost_and_one_cake_" + i);
            ItemStack nextInput = chamber.inventory.getStackInSlot(3).copy();
            chamber.inventory.setStackInSlot(1, nextInput);
            chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(SpellRarity.LEGENDARY)));
            chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
            chamber.inventory.setStackInSlot(4, ItemStack.EMPTY);
        }
        require(chamber.getCrimsonCreditQuarterMb() == 2, "three_breakthroughs_preserve_half_mb_after_938mb_integer_payment");
        results.put("three_breakthroughs_cost_937_5mb_and_three_cakes", "PASS");

        chamber = fresh(level, pos, 10, true, 1000);
        chamber.inventory.setStackInSlot(1, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(7, new ItemStack(CrimsonContent.EMBER_CAKE.get(), 2));
        level.setBlock(pos.above(), AllBlocks.BASIN.getDefaultState(), 3);
        BasinBlockEntity basin = (BasinBlockEntity) level.getBlockEntity(pos.above());
        require(basin != null && basin.inputTank != null, "breakthrough_basin_initialized");
        basin.getInputInventory().insertItem(0, scroll(10), false);
        IFluidHandler ink = basin.inputTank.getCapability().orElseThrow(() -> new IllegalStateException("missing_basin_ink"));
        ink.fill(new FluidStack(InkItem.getInkForRarity(SpellRarity.LEGENDARY).fluid().get(), 250), IFluidHandler.FluidAction.EXECUTE);
        for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) {
            basin.getOutputInventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        require(!chamber.processOneCycle() && chamber.inventory.getStackInSlot(7).getCount() == 2
                && scrollLevel(basin.getInputInventory().getStackInSlot(0)) == 10
                && ink.drain(250, IFluidHandler.FluidAction.SIMULATE).getAmount() == 250,
                "blocked_basin_output_preserves_cake_scroll_and_ink");
        for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) {
            basin.getOutputInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
        require(chamber.processOneCycle() && chamber.inventory.getStackInSlot(7).getCount() == 1
                && ink.drain(250, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "basin_breakthrough_spends_one_cake_and_250mb_ink");
        boolean found = false;
        for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) {
            found |= scrollLevel(basin.getOutputInventory().getStackInSlot(slot)) == 11;
        }
        require(found, "basin_breakthrough_outputs_level_11");
        results.put("manual_and_basin_cake_transactions_no_loss_on_failure", "PASS");
    }

    private static void runCrimsonPrecisionTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        AscensionChamberBlockEntity chamber = fresh(level, pos, 7, true, 125);
        require(chamber.processOneCycle() && chamber.sourceTank.getFluidAmount() == 62
                && chamber.getCrimsonCreditQuarterMb() == 2, "first_62_5mb_upgrade_pays_63_and_keeps_half_mb");
        CompoundTag saved = chamber.saveWithFullMetadata();
        level.removeBlockEntity(pos);
        chamber = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
        chamber.load(saved);
        level.setBlockEntity(chamber);
        require(chamber.sourceTank.getFluidAmount() == 62 && chamber.getCrimsonCreditQuarterMb() == 2,
                "fractional_credit_and_liquid_survive_reload");
        chamber.inventory.setStackInSlot(1, scroll(7));
        chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(4, ItemStack.EMPTY);
        SpellRarity correct = SpellRegistry.FIREBOLT_SPELL.get().getRarity(8);
        SpellRarity wrong = correct == SpellRarity.COMMON ? SpellRarity.LEGENDARY : SpellRarity.COMMON;
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(wrong)));
        require(!chamber.processOneCycle() && chamber.getCrimsonCreditQuarterMb() == 2
                && chamber.sourceTank.getFluidAmount() == 62, "wrong_ink_does_not_spend_fractional_credit");
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(correct)));
        chamber.inventory.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
        require(!chamber.processOneCycle() && chamber.getCrimsonCreditQuarterMb() == 2
                && chamber.sourceTank.getFluidAmount() == 62, "blocked_output_does_not_spend_fractional_credit");
        IFluidHandler fluids = chamber.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new IllegalStateException("missing_precision_test_fluid_capability"));
        require(fluids.fill(new FluidStack(CrimsonContent.SOURCE.get(), 100), IFluidHandler.FluidAction.SIMULATE) == 100
                && fluids.drain(62, IFluidHandler.FluidAction.SIMULATE).getAmount() == 62
                && chamber.getCrimsonCreditQuarterMb() == 2 && chamber.sourceTank.getFluidAmount() == 62,
                "fluid_simulation_does_not_spend_or_reset_fractional_credit");
        for (int i = 0; i < 100; i++) chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 62 && chamber.getCrimsonCreditQuarterMb() == 2,
                "blocked_idle_ticks_do_not_spend_fractional_credit");
        fluids.fill(new FluidStack(CrimsonContent.SOURCE.get(), 1), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.getCrimsonCreditQuarterMb() == 2
                        && fluids.drain(1, IFluidHandler.FluidAction.EXECUTE).getAmount() == 1
                        && chamber.getCrimsonCreditQuarterMb() == 2,
                "same_red_fill_and_partial_external_drain_preserve_processing_credit");
        fluids.drain(62, IFluidHandler.FluidAction.EXECUTE);
        require(chamber.getCrimsonCreditQuarterMb() == 0, "external_drain_to_empty_discards_fractional_credit");
        fluids.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        chamber.inventory.setStackInSlot(1, scroll(4));
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(SpellRegistry.FIREBOLT_SPELL.get().getRarity(5))));
        chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
        require(chamber.sourceTank.getFluidAmount() == 250 && chamber.getCrimsonCreditQuarterMb() == 0,
                "blue_fill_never_converts_old_red_credit_to_extra_liquid");
        require(chamber.processOneCycle() && chamber.sourceTank.isEmpty() && chamber.getCrimsonCreditQuarterMb() == 0,
                "ordinary_upgrade_pays_full_250_after_replacement");
        fluids.fill(new FluidStack(CrimsonContent.SOURCE.get(), 63), IFluidHandler.FluidAction.EXECUTE);
        chamber.inventory.setStackInSlot(1, scroll(7));
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(correct)));
        chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(4, ItemStack.EMPTY);
        require(chamber.processOneCycle() && chamber.sourceTank.isEmpty() && chamber.getCrimsonCreditQuarterMb() == 2,
                "paid_red_processing_recreates_half_mb_credit_even_when_physical_tank_empties");
        results.put("fractional_credit_reload_partial_drain_replacement_and_paid_empty_tank_boundaries", "PASS");

        chamber = empty(level, pos);
        CompoundTag legacy = chamber.saveWithFullMetadata();
        legacy.putInt("NormalFuel", 5000);
        legacy.putInt("SuperFuel", 1260);
        chamber.load(legacy);
        require(chamber.isTranscendent(), "legacy_red_63mb_selects_super_below_old_250mb_threshold");
        chamber.inventory.setStackInSlot(1, scroll(7));
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(correct)));
        require(chamber.processOneCycle() && chamber.getSuperFuelTicks() == 0
                && chamber.getCrimsonCreditQuarterMb() == 2 && chamber.getNormalFuelTicks() == 5000,
                "legacy_red_pays_actual_63mb_and_preserves_normal_balance");
        results.put("legacy_red_minimum_threshold_and_actual_quarter_price", "PASS");
    }

    private static void runSolutionConversionTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        // Old mB, old red, requested mB, incoming red, accepted mB, final mB.
        int[][] examples = {{800, 1, 250, 0, 250, 250}, {1500, 1, 250, 0, 250, 250},
                {1000, 0, 250, 1, 250, 250}, {1, 0, 250, 1, 250, 250},
                {4000, 0, 1000, 1, 1000, 1000}, {4000, 1, 250, 0, 250, 250},
                {3800, 0, 250, 0, 200, 4000}};
        for (int[] example : examples) {
            AscensionChamberBlockEntity chamber = empty(level, pos);
            chamber.sourceTank.fill(new FluidStack(example[1] == 1 ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), example[0]),
                    IFluidHandler.FluidAction.EXECUTE);
            IFluidHandler fluids = chamber.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                    () -> new IllegalStateException("missing_replacement_capability"));
            FluidStack incoming = new FluidStack(example[3] == 1 ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), example[2]);
            int stateBefore = chamber.getBlockState().getValue(AscensionChamberBlock.STATE);
            require(fluids.fill(incoming, IFluidHandler.FluidAction.SIMULATE) == example[4]
                            && chamber.sourceTank.getFluidAmount() == example[0] && chamber.getCrimsonCreditQuarterMb() == 0
                            && chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == stateBefore,
                    "replacement_simulation_leaves_tank_credit_and_state_unchanged_" + example[0] + "_" + example[1]);
            require(fluids.fill(incoming, IFluidHandler.FluidAction.EXECUTE) == example[4]
                            && chamber.sourceTank.getFluidAmount() == example[5] && chamber.getCrimsonCreditQuarterMb() == 0
                            && chamber.isTranscendent() == (example[3] == 1) && incoming.getAmount() == example[2],
                    "replacement_stores_only_new_fluid_and_returns_actual_input_acceptance_" + example[0] + "_" + example[1]);
        }
        results.put("tank_replacement_discards_old_liquid_without_fourfold_exchange", "PASS");

        AscensionChamberBlockEntity chamber = empty(level, pos);
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        CompoundTag credited = chamber.saveWithFullMetadata();
        credited.putInt("CrimsonCreditQuarterMb", 2);
        chamber.load(credited);
        require(chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 0), IFluidHandler.FluidAction.EXECUTE) == 0
                        && chamber.sourceTank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE) == 0
                        && chamber.sourceTank.getFluidAmount() == 250 && chamber.getCrimsonCreditQuarterMb() == 2
                        && chamber.isTranscendent(), "zero_or_invalid_fluid_cannot_erase_old_solution_or_credit");
        FluidStack hugeBlue = new FluidStack(ArcaneContent.SOURCE.get(), 5000);
        require(chamber.sourceTank.fill(hugeBlue, IFluidHandler.FluidAction.SIMULATE) == 4000
                        && chamber.sourceTank.getFluidAmount() == 250 && chamber.getCrimsonCreditQuarterMb() == 2,
                "oversized_foreign_simulation_preserves_original_credit");
        require(chamber.sourceTank.fill(hugeBlue, IFluidHandler.FluidAction.EXECUTE) == 4000
                        && chamber.sourceTank.getFluidAmount() == 4000 && chamber.getCrimsonCreditQuarterMb() == 0
                        && !chamber.isTranscendent() && hugeBlue.getAmount() == 5000,
                "foreign_pipe_stores_only_accepted_new_liquid_and_clears_credit");
        require(chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE) == 0
                        && chamber.sourceTank.getFluidAmount() == 4000, "full_same_solution_pipe_accepts_nothing");
        require(chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 5000), IFluidHandler.FluidAction.EXECUTE) == 4000
                        && chamber.sourceTank.getFluidAmount() == 4000 && chamber.isTranscendent(),
                "full_foreign_pipe_uses_new_solution_capacity");
        for (int i = 0; i < 32; i++) {
            boolean red = i % 2 == 1;
            require(chamber.sourceTank.fill(new FluidStack(red ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 1),
                            IFluidHandler.FluidAction.EXECUTE) == 1 && chamber.sourceTank.getFluidAmount() == 1
                            && chamber.getCrimsonCreditQuarterMb() == 0,
                    "repeated_red_blue_replacement_never_converts_or_retains_old_volume_" + i);
        }
        results.put("zero_invalid_simulated_oversized_and_repeated_pipe_replacement_contract", "PASS");

        chamber = empty(level, pos);
        CompoundTag dryCredit = chamber.saveWithFullMetadata();
        dryCredit.putInt("CrimsonCreditQuarterMb", 3);
        chamber.load(dryCredit);
        require(chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 0), IFluidHandler.FluidAction.EXECUTE) == 0
                        && chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.SIMULATE) == 250
                        && chamber.sourceTank.isEmpty() && chamber.getCrimsonCreditQuarterMb() == 3,
                "dry_red_credit_survives_zero_requests_and_simulation");
        require(chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE) == 250
                        && chamber.sourceTank.getFluidAmount() == 250 && chamber.getCrimsonCreditQuarterMb() == 0,
                "dry_red_credit_is_lost_when_blue_is_actually_added");
        results.put("dry_red_processing_credit_is_not_exchangeable_blue_liquid", "PASS");

        chamber = empty(level, pos);
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 3500), IFluidHandler.FluidAction.EXECUTE);
        CompoundTag oldTwoTank = chamber.saveWithFullMetadata();
        oldTwoTank.put("CrimsonSolution", new FluidStack(CrimsonContent.SOURCE.get(), 1500).writeToNBT(new CompoundTag()));
        oldTwoTank.putInt("SuperFuel", 100);
        chamber.load(oldTwoTank);
        require(chamber.sourceTank.getFluidAmount() == 3500 && !chamber.isTranscendent()
                        && chamber.getSuperFuelTicks() == 30100 && chamber.getCrimsonCreditQuarterMb() == 0,
                "legacy_two_tank_load_preserves_separate_paid_balance_without_exchanging_current_blue");
        CompoundTag migrated = chamber.saveWithFullMetadata();
        require(!migrated.contains("CrimsonSolution"), "legacy_red_migration_is_not_saved_twice");
        chamber.load(migrated);
        require(chamber.getSuperFuelTicks() == 30100 && chamber.sourceTank.getFluidAmount() == 3500,
                "legacy_two_tank_migration_reloads_without_duplicate_energy");
        results.put("legacy_two_tank_separate_paid_balance_remains_compatible", "PASS");
    }

    /** Every represented native level-one rarity must require its own ink, never the template's level. */
    private static void runTieredPrintingInkChecks(ServerLevel level, Map<String, Object> results) {
        var examples = new LinkedHashMap<SpellRarity, io.redspace.ironsspellbooks.api.spells.AbstractSpell>();
        for (var spell : SpellRegistry.getEnabledSpells()) {
            if (UninkedScrollItem.isPrintableSpell(spell)) examples.putIfAbsent(spell.getRarity(1), spell);
        }
        require(!examples.isEmpty(), "tiered_printing_has_at_least_one_enabled_printable_spell");
        var loaded = level.getRecipeManager().byKey(UninkedScrollItem.ASSEMBLY_ID).orElseThrow();
        require(loaded instanceof SequencedAssemblyRecipe, "tiered_printing_remains_native_sequenced_assembly");
        var assembly = (SequencedAssemblyRecipe) loaded;
        require(assembly.getLoops() == 1 && assembly.getSequence().size() == 2
                        && assembly.getSequence().get(0).getRecipe().getFluidIngredients().size() == 1
                        && assembly.getSequence().get(0).getRecipe().getFluidIngredients().get(0).getRequiredAmount() == 50
                        && assembly.getSequence().get(0).getRecipe().getFluidIngredients().get(0)
                        .test(new FluidStack(ArcaneContent.SOURCE.get(), 50))
                        && assembly.getSequence().get(1).getRecipe() instanceof ScrollInkingRecipe,
                "tiered_printing_keeps_50_blue_first_step_and_native_filling_second_step");
        for (var example : examples.entrySet()) {
            var spell = example.getValue();
            var correctInk = InkItem.getInkForRarity(spell.getRarity(1)).fluid().get();
            ItemStack intermediate = UninkedScrollItem.create(spell);
            intermediate = ItemStack.of(intermediate.save(new CompoundTag()));
            require(UninkedScrollItem.isInkingStage(intermediate)
                            && UninkedScrollItem.readRequiredInk(intermediate).filter(correctInk::isSame).isPresent()
                            && FillingBySpout.canItemBeFilled(level, intermediate),
                    "tiered_printing_selects_actual_level_one_ink_" + example.getKey());
            for (SpellRarity rarity : SpellRarity.values()) {
                if (rarity == example.getKey()) continue;
                ItemStack input = intermediate.copy();
                FluidStack wrong = new FluidStack(InkItem.getInkForRarity(rarity).fluid().get(), 250);
                CompoundTag before = input.save(new CompoundTag());
                CompoundTag fluidBefore = wrong.writeToNBT(new CompoundTag());
                require(FillingBySpout.getRequiredAmountForItem(level, input, wrong) < 0
                                && FillingBySpout.fillItem(level, 250, input, wrong).isEmpty()
                                && input.save(new CompoundTag()).equals(before)
                                && wrong.writeToNBT(new CompoundTag()).equals(fluidBefore),
                        "tiered_printing_rejects_other_ink_atomically_" + example.getKey() + "_" + rarity);
            }
            ItemStack shortInput = intermediate.copy();
            FluidStack shortInk = new FluidStack(correctInk, 249);
            CompoundTag beforeShort = shortInput.save(new CompoundTag());
            require(FillingBySpout.getRequiredAmountForItem(level, shortInput, shortInk) < 0
                            && FillingBySpout.fillItem(level, 250, shortInput, shortInk).isEmpty()
                            && shortInput.save(new CompoundTag()).equals(beforeShort) && shortInk.getAmount() == 249,
                    "tiered_printing_short_correct_ink_cannot_consume_anything_" + example.getKey());
            intermediate.setCount(2);
            FluidStack ink = new FluidStack(correctInk, 300);
            require(FillingBySpout.getRequiredAmountForItem(level, intermediate, ink) == 250,
                    "tiered_printing_quotes_exactly_250_correct_ink_" + example.getKey());
            ItemStack output = FillingBySpout.fillItem(level, 250, intermediate, ink);
            ISpellContainer container = ISpellContainer.isSpellContainer(output) ? ISpellContainer.get(output) : null;
            require(UninkedScrollItem.isPrintedScroll(output) && output.getCount() == 1
                            && container != null && container.getActiveSpellCount() == 1
                            && container.getSpellAtIndex(0).getSpell().equals(spell)
                            && container.getSpellAtIndex(0).getLevel() == 1
                            && !output.getTag().contains(UninkedScrollItem.ASSEMBLY_TAG)
                            && intermediate.getCount() == 1 && ink.getAmount() == 50,
                    "tiered_printing_consumes_one_stage_and_250_actual_ink_for_one_level_one_scroll_" + example.getKey());
        }
        results.put("tiered_printing_native_level_one_rarities_wrong_short_and_exact_ink", examples.keySet().toString());
    }

    private static void runNativeAscensionSequenceTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        var registered = level.getRecipeManager().byKey(UnfinishedAscensionScrollItem.ASSEMBLY_ID).orElseThrow();
        require(registered instanceof SequencedAssemblyRecipe, "ascension_is_real_native_sequenced_assembly");
        SequencedAssemblyRecipe assembly = (SequencedAssemblyRecipe) registered;
        require(assembly.getLoops() == 1 && assembly.getSequence().size() == 2
                        && assembly.getTransitionalItem().getItem() instanceof SequencedAssemblyItem,
                "ascension_native_sequence_has_two_steps_one_loop_and_progress_item");

        ItemStack original = scroll(4);
        original.setHoverName(Component.literal("Named printed ascension fixture"));
        original.getOrCreateTag().putBoolean(UninkedScrollItem.PRINTED_TAG, true);
        original.getOrCreateTag().putString("arcane_industry_test_marker", "full source NBT");
        ItemStack prepared = legacyAscensionStage(level, original);
        ItemStack source = UnfinishedAscensionScrollItem.sourceOf(prepared).orElseThrow();
        require(ItemStack.isSameItemSameTags(source, original) && source.getCount() == 1
                        && UninkedScrollItem.isPrintedScroll(source) && source.hasCustomHoverName(),
                "pre_inked_ascension_retains_complete_original_scroll_name_lock_and_printed_source");
        ItemStack expected = ScrollUpgradeRules.prepare(original, false, false).orElseThrow().output();
        var directInventory = new ItemStackHandler(1);
        directInventory.setStackInSlot(0, prepared.copy());
        var directWrapper = new RecipeWrapper(directInventory);
        AscensionAssemblyRecipe directStep = SequencedAssemblyRecipe.getRecipe(level, directWrapper,
                ScrollAscensionRecipes.ASSEMBLY_TYPE.get(), AscensionAssemblyRecipe.class).orElseThrow();
        CompoundTag beforeUnauthorized = directInventory.getStackInSlot(0).save(new CompoundTag());
        require(directStep.rollResults().isEmpty()
                        && directInventory.getStackInSlot(0).save(new CompoundTag()).equals(beforeUnauthorized),
                "native_ascension_final_step_cannot_roll_output_without_chamber_authorization");
        require(ItemStack.isSameItemSameTags(expected, ScrollAscensionRecipes.finishStage(level, prepared, false, false))
                        && UnfinishedAscensionScrollItem.isPreparedStage(prepared),
                "authorized_native_sequence_finalization_is_read_only_and_retains_full_metadata");
        ItemStack reloadedStage = ItemStack.of(prepared.save(new CompoundTag()));
        require(UnfinishedAscensionScrollItem.isPreparedStage(reloadedStage)
                        && ItemStack.isSameItemSameTags(prepared, reloadedStage),
                "ascension_intermediate_native_progress_and_source_survive_item_nbt_reload");

        AscensionChamberBlockEntity chamber = fresh(level, pos, 4, false, 1000);
        chamber.inventory.setStackInSlot(1, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        require(chamber.inventory.isItemValid(1, prepared), "manual_scroll_slot_accepts_valid_native_intermediate");
        CompoundTag beforePreparedInsert = chamber.inventory.serializeNBT().copy();
        require(chamber.getAutomationInventory().insertItem(1, reloadedStage.copy(), true).isEmpty()
                        && chamber.inventory.serializeNBT().equals(beforePreparedInsert),
                "valid_pre_inked_automation_simulation_accepts_without_mutating_inventory");
        require(chamber.getAutomationInventory().insertItem(1, reloadedStage.copy(), false).isEmpty()
                        && ItemStack.matches(chamber.inventory.getStackInSlot(1), reloadedStage),
                "valid_pre_inked_automation_execution_preserves_complete_paid_stage");
        chamber.inventory.setStackInSlot(4, new ItemStack(Items.COBBLESTONE, 64));
        require(!chamber.inventory.isItemValid(1, prepared)
                        && !chamber.getAutomationInventory().insertItem(1, prepared.copy(), true).isEmpty(),
                "unclaimed_secondary_output_blocks_new_inputs_without_invalidating_an_existing_paid_stage");
        for (int tick = 0; tick < 79; tick++) chamber.tickServer();
        require(chamber.getProgress() == 79 && chamber.inventory.getStackInSlot(3).isEmpty()
                        && chamber.sourceTank.getFluidAmount() == 1000,
                "pre_inked_manual_route_still_waits_80_ticks_without_precharging");
        chamber.tickServer();
        require(ItemStack.isSameItemSameTags(expected, chamber.inventory.getStackInSlot(3))
                        && chamber.inventory.getStackInSlot(1).isEmpty() && chamber.inventory.getStackInSlot(2).isEmpty()
                        && chamber.inventory.getStackInSlot(4).is(Items.COBBLESTONE)
                        && chamber.inventory.getStackInSlot(4).getCount() == 64
                        && chamber.sourceTank.getFluidAmount() == 750,
                "pre_inked_manual_upgrade_pays_solution_once_without_second_ink_or_bottle_output");

        chamber = fresh(level, pos, 4, false, 1000);
        chamber.inventory.setStackInSlot(1, prepared.copy());
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        for (int tick = 0; tick < 31; tick++) chamber.tickServer();
        CompoundTag saved = chamber.saveWithFullMetadata();
        level.removeBlockEntity(pos);
        chamber = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
        chamber.load(saved);
        level.setBlockEntity(chamber);
        require(UnfinishedAscensionScrollItem.isPreparedStage(chamber.inventory.getStackInSlot(1))
                        && chamber.inventory.getStackInSlot(2).isEmpty() && chamber.sourceTank.getFluidAmount() == 1000,
                "pre_inked_chamber_reload_keeps_paid_stage_and_needs_no_second_ink");
        for (int tick = 0; tick < 80; tick++) chamber.tickServer();
        require(ItemStack.isSameItemSameTags(expected, chamber.inventory.getStackInSlot(3))
                        && chamber.sourceTank.getFluidAmount() == 750 && chamber.inventory.getStackInSlot(4).isEmpty(),
                "reloaded_pre_inked_chamber_commits_exactly_once");
        chamber = fresh(level, pos, 4, false, 1000);
        chamber.tickServer();
        for (int tick = 0; tick < 40; tick++) chamber.tickServer();
        require(chamber.getProgress() == 40 && chamber.sourceTank.getFluidAmount() == 1000,
                "raw_source_does_not_pay_ink_or_fuel_during_progress");
        chamber.inventory.setStackInSlot(1, prepared.copy());
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        chamber.tickServer();
        require(chamber.getProgress() == 1 && chamber.sourceTank.getFluidAmount() == 1000,
                "changing_raw_source_to_pre_inked_stage_resets_progress_and_does_not_spend_twice");
        for (int tick = 0; tick < 79; tick++) chamber.tickServer();
        require(ItemStack.isSameItemSameTags(expected, chamber.inventory.getStackInSlot(3))
                        && chamber.sourceTank.getFluidAmount() == 750 && chamber.inventory.getStackInSlot(4).isEmpty(),
                "replacement_pre_inked_stage_gets_its_own_complete_80_tick_transaction");
        results.put("legacy_ascension_full_source_nbt_80ticks_and_reload_no_double_ink", "PASS");

        chamber = fresh(level, pos, 4, false, 1000);
        chamber.inventory.setStackInSlot(1, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        level.setBlock(pos.above(), AllBlocks.BASIN.getDefaultState(), 3);
        BasinBlockEntity basin = (BasinBlockEntity) level.getBlockEntity(pos.above());
        require(basin != null && basin.getInputInventory().insertItem(0, prepared.copy(), false).isEmpty(),
                "basin_accepts_actual_pre_inked_intermediate");
        IFluidHandler basinInk = basin.inputTank.getCapability().orElseThrow(IllegalStateException::new);
        FluidStack unrelatedInk = new FluidStack(InkItem.getInkForRarity(SpellRarity.LEGENDARY).fluid().get(), 250);
        basinInk.fill(unrelatedInk, IFluidHandler.FluidAction.EXECUTE);
        require(chamber.processOneCycle() && chamber.sourceTank.getFluidAmount() == 750
                        && basin.getInputInventory().getStackInSlot(0).isEmpty()
                        && basinInk.drain(unrelatedInk, IFluidHandler.FluidAction.SIMULATE).getAmount() == 250,
                "pre_inked_basin_route_does_not_charge_ink_again_or_require_matching_basin_ink");
        boolean found = false;
        for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) {
            found |= ItemStack.isSameItemSameTags(expected, basin.getOutputInventory().getStackInSlot(slot));
        }
        require(found && chamber.inventory.getStackInSlot(4).isEmpty(), "basin_pre_inked_output_keeps_complete_source_nbt");
        results.put("native_ascension_pre_inked_basin_preserves_existing_ink_and_full_result_nbt", "PASS");

        ItemStack highPrepared = legacyAscensionStage(level, scroll(7));
        require(ScrollUpgradeRules.prepare(scroll(7), true, true).orElseThrow().rarity() != SpellRarity.COMMON,
                "ascension_tests_cover_dynamic_non_common_ink_instead_of_generic_json_placeholder");
        chamber = fresh(level, pos, 7, true, 1000);
        chamber.inventory.setStackInSlot(1, highPrepared.copy());
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        for (int tick = 0; tick < 40; tick++) chamber.tickServer();
        chamber.sourceTank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        for (int tick = 0; tick < 100; tick++) chamber.tickServer();
        require(chamber.getProgress() == 0 && chamber.sourceTank.getFluidAmount() == 1000
                        && ItemStack.isSameItemSameTags(highPrepared, chamber.inventory.getStackInSlot(1))
                        && chamber.inventory.getStackInSlot(3).isEmpty(),
                "pre_inked_stage_cannot_bypass_transcendent_mode_and_illegal_mode_spends_nothing");
        chamber.sourceTank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        chamber.sourceTank.fill(new FluidStack(CrimsonContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        for (int tick = 0; tick < 80; tick++) chamber.tickServer();
        require(scrollLevel(chamber.inventory.getStackInSlot(3)) == 8 && chamber.sourceTank.getFluidAmount() == 937
                        && chamber.getCrimsonCreditQuarterMb() == 2 && chamber.inventory.getStackInSlot(4).isEmpty(),
                "pre_inked_transcendent_resume_pays_exact_quarter_cost_once");

        ItemStack breakthrough = legacyAscensionStage(level, scroll(10));
        chamber = fresh(level, pos, 10, true, 1000);
        chamber.inventory.setStackInSlot(1, breakthrough.copy());
        chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
        require(!chamber.processOneCycle() && chamber.sourceTank.getFluidAmount() == 1000,
                "pre_inked_breakthrough_still_requires_cake");
        chamber.inventory.setStackInSlot(7, new ItemStack(CrimsonContent.EMBER_CAKE.get()));
        chamber.inventory.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
        for (int tick = 0; tick < 100; tick++) chamber.tickServer();
        require(chamber.sourceTank.getFluidAmount() == 1000 && chamber.inventory.getStackInSlot(7).getCount() == 1
                        && ItemStack.isSameItemSameTags(breakthrough, chamber.inventory.getStackInSlot(1)),
                "blocked_pre_inked_breakthrough_keeps_stage_cake_and_solution");
        chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
        require(chamber.processOneCycle() && scrollLevel(chamber.inventory.getStackInSlot(3)) == 11
                        && chamber.sourceTank.getFluidAmount() == 750 && chamber.inventory.getStackInSlot(7).isEmpty()
                        && chamber.inventory.getStackInSlot(4).isEmpty(),
                "pre_inked_breakthrough_pays_one_cake_and_250red_without_second_ink");
        results.put("native_ascension_pre_inked_mode_cake_output_gates_and_quarter_price", "PASS");

        for (String corruption : new String[]{"source", "recipe", "step", "negative_step", "progress"}) {
            ItemStack invalid = prepared.copy();
            CompoundTag tag = invalid.getOrCreateTag();
            if (corruption.equals("source")) tag.remove(UnfinishedAscensionScrollItem.SOURCE_TAG);
            if (corruption.equals("recipe")) tag.getCompound("SequencedAssembly").putString("id", "create:sequenced_assembly/precision_mechanism");
            if (corruption.equals("step")) tag.getCompound("SequencedAssembly").putInt("Step", 0);
            if (corruption.equals("negative_step")) tag.getCompound("SequencedAssembly").putInt("Step", -1);
            if (corruption.equals("progress")) tag.getCompound("SequencedAssembly").putFloat("Progress", 1F);
            chamber = fresh(level, pos, 4, false, 1000);
            require(!UnfinishedAscensionScrollItem.isPreparedStage(invalid) && !chamber.inventory.isItemValid(1, invalid),
                    "invalid_native_ascension_payload_rejected_on_insertion_" + corruption);
            CompoundTag beforeInvalidInsert = chamber.inventory.serializeNBT().copy();
            require(ItemStack.matches(chamber.getAutomationInventory().insertItem(1, invalid.copy(), true), invalid)
                            && ItemStack.matches(chamber.getAutomationInventory().insertItem(1, invalid.copy(), false), invalid)
                            && chamber.inventory.serializeNBT().equals(beforeInvalidInsert),
                    "invalid_native_ascension_payload_simulate_and_execute_reject_without_loss_" + corruption);
            chamber.inventory.setStackInSlot(1, invalid.copy());
            chamber.inventory.setStackInSlot(2, ItemStack.EMPTY);
            require(!chamber.processOneCycle() && ItemStack.isSameItemSameTags(invalid, chamber.inventory.getStackInSlot(1))
                            && chamber.sourceTank.getFluidAmount() == 1000 && chamber.inventory.getStackInSlot(3).isEmpty(),
                    "invalid_native_ascension_payload_cannot_upgrade_or_spend_anything_" + corruption);
        }
        results.put("native_ascension_invalid_payload_recipe_step_and_progress_rejected_without_loss", "PASS");
    }

    /** Construct a previously paid legacy save item through the internal read-only sequence. */
    private static ItemStack legacyAscensionStage(ServerLevel level, ItemStack original) {
        var upgrade = ScrollUpgradeRules.prepare(original, true, true).orElseThrow();
        SpellRarity wrongRarity = upgrade.rarity() == SpellRarity.COMMON ? SpellRarity.LEGENDARY : SpellRarity.COMMON;
        for (FluidStack available : new FluidStack[]{
                new FluidStack(upgrade.inkFluid(), 300),
                new FluidStack(upgrade.inkFluid(), 249),
                new FluidStack(InkItem.getInkForRarity(wrongRarity).fluid().get(), 250),
                new FluidStack(ArcaneContent.SOURCE.get(), 250)}) {
            ItemStack input = original.copyWithCount(1);
            CompoundTag inputBefore = input.save(new CompoundTag());
            CompoundTag fluidBefore = available.writeToNBT(new CompoundTag());
            require(!FillingBySpout.canItemBeFilled(level, input)
                            && FillingBySpout.getRequiredAmountForItem(level, input, available) < 0
                            && FillingBySpout.fillItem(level, ScrollUpgradeRules.INK_AMOUNT, input, available).isEmpty()
                            && input.save(new CompoundTag()).equals(inputBefore)
                            && available.writeToNBT(new CompoundTag()).equals(fluidBefore),
                    "raw_upgrade_scroll_cannot_be_preinked_by_external_spout_or_lose_materials");
        }
        CompoundTag before = original.copyWithCount(1).save(new CompoundTag());
        ItemStack stage = ScrollAscensionRecipes.prepareStage(level, original);
        require(UnfinishedAscensionScrollItem.isPreparedStage(stage)
                        && UnfinishedAscensionScrollItem.sourceOf(stage).orElseThrow().save(new CompoundTag()).equals(before)
                        && original.copyWithCount(1).save(new CompoundTag()).equals(before),
                "internal_legacy_fixture_embeds_complete_source_without_mutating_original");
        CompoundTag nativeProgress = stage.getTag().getCompound("SequencedAssembly");
        require(nativeProgress.getString("id").equals(UnfinishedAscensionScrollItem.ASSEMBLY_ID.toString())
                        && nativeProgress.getInt("Step") == 1 && nativeProgress.getFloat("Progress") == 0.5F,
                "legacy_fixture_has_valid_native_half_complete_progress");
        FluidStack ink = new FluidStack(upgrade.inkFluid(), 250);
        CompoundTag stageBefore = stage.save(new CompoundTag());
        require(!FillingBySpout.canItemBeFilled(level, stage)
                        && FillingBySpout.getRequiredAmountForItem(level, stage, ink) < 0
                        && FillingBySpout.fillItem(level, ScrollUpgradeRules.INK_AMOUNT, stage, ink).isEmpty()
                        && stage.save(new CompoundTag()).equals(stageBefore) && ink.getAmount() == 250,
                "legacy_paid_stage_cannot_be_filled_again_by_external_spout");
        return stage;
    }

    private static void runCosmeticAndLightningTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        ServerPlayer actor = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("636d3655-34c1-4b3c-9d62-d65e28f520d0"), "ArcaneCosmetics"));
        actor.getInventory().clearContent();
        actor.getAbilities().instabuild = false;
        Set<UUID> oldBolts = boltIds(level);
        IronGolem victim = null;
        boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        try {
            AscensionChamberBlockEntity chamber = fresh(level, pos, 4, false, 1000);
            actor.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + 2);
            // Explicit legacy-only payload: new chambers neither accept nor need this old slot.
            chamber.inventory.setStackInSlot(0, new ItemStack(ArcaneContent.TEMPLATE.get()));
            for (int tick = 0; tick < 40; tick++) chamber.tickServer();
            CompoundTag inputs = chamber.inventory.serializeNBT().copy();
            int progress = chamber.getProgress();
            ItemStack brass = new ItemStack(AllItems.BRASS_INGOT.get(), 3);
            clickChamber(level, pos, actor, brass, false);
            require(brass.getCount() == 2 && chamber.getSkinKey().equals("brass")
                            && chamber.getBlockState().getValue(AscensionChamberBlock.SKIN) == AscensionChamberBlock.Skin.BRASS,
                    "brass_right_click_changes_native_block_skin_and_pays_one_ingot");
            clickChamber(level, pos, actor, brass, false);
            require(brass.getCount() == 2, "same_skin_does_not_consume_another_ingot");
            Item mithrilItem = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "mithril_ingot"));
            require(mithrilItem != null && mithrilItem != Items.AIR, "native_mithril_ingot_registered");
            ItemStack mithril = new ItemStack(mithrilItem, 2);
            clickChamber(level, pos, actor, mithril, false);
            require(mithril.getCount() == 1 && chamber.getSkinKey().equals("mithril"), "mithril_skin_costs_one_ingot");
            actor.getAbilities().instabuild = true;
            ItemStack copper = new ItemStack(Items.COPPER_INGOT, 2);
            clickChamber(level, pos, actor, copper, false);
            actor.getAbilities().instabuild = false;
            require(copper.getCount() == 2 && chamber.getSkinKey().equals("copper"), "creative_skin_change_is_free");
            require(chamber.getProgress() == progress && chamber.inventory.serializeNBT().equals(inputs)
                            && chamber.sourceTank.getFluidAmount() == 1000,
                    "cosmetic_changes_keep_in_flight_recipe_progress_inventory_and_fluid");

            ItemStack goggles = AllItems.GOGGLES.asStack();
            goggles.setHoverName(Component.literal("Saved engineering goggles"));
            goggles.getOrCreateTag().putString("arcane_test_decoration", "full native item NBT");
            CompoundTag gogglesTag = goggles.save(new CompoundTag());
            clickChamber(level, pos, actor, goggles, false);
            Item crownItem = ForgeRegistries.ITEMS.getValue(ChamberDecorations.NECROMANCER_CROWN_ID);
            require(crownItem != null && crownItem != Items.AIR, "native_crown_item_registered");
            ItemStack crown = new ItemStack(crownItem);
            crown.setHoverName(Component.literal("Saved necromancer crown"));
            crown.getOrCreateTag().putInt("arcane_test_decoration", 42);
            CompoundTag crownTag = crown.save(new CompoundTag());
            clickChamber(level, pos, actor, crown, false);
            require(goggles.isEmpty() && crown.getCount() == 1 && chamber.hasGoggles() && !chamber.hasNecromancerCrown()
                            && crown.save(new CompoundTag()).equals(crownTag),
                    "goggles_and_crown_are_mutually_exclusive_and_rejected_crown_keeps_all_nbt");
            ItemStack spareGoggles = AllItems.GOGGLES.asStack();
            clickChamber(level, pos, actor, spareGoggles, false);
            require(spareGoggles.getCount() == 1, "duplicate_decoration_does_not_consume_the_item");
            clickChamber(level, pos, actor, new ItemStack(mithrilItem), false);
            CompoundTag saved = chamber.saveWithFullMetadata();
            level.removeBlockEntity(pos);
            chamber = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
            chamber.load(saved);
            level.setBlockEntity(chamber);
            require(chamber.isBlazeAlive() && chamber.getSkinKey().equals("mithril") && chamber.hasGoggles()
                            && !chamber.hasNecromancerCrown() && chamber.getDecorationDrops().size() == 1
                            && chamber.getDecorationDrops().get(0).save(new CompoundTag()).equals(gogglesTag),
                    "skin_alive_flag_and_one_complete_decoration_nbt_survive_reload");
            CompoundTag processingInputs = chamber.inventory.serializeNBT().copy();
            actor.getInventory().clearContent();
            clickChamber(level, pos, actor, ItemStack.EMPTY, false);
            require(chamber.isManualProcessing() && chamber.inventory.serializeNBT().equals(processingInputs)
                            && chamber.hasGoggles() && matchingCount(actor, gogglesTag) == 0,
                    "empty_hand_cannot_extract_inputs_or_headpiece_during_manual_processing");
            // End the in-flight fixture before testing independent display extraction.
            ItemStack pendingPrimary = chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT).copy();
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, ItemStack.EMPTY);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, pendingPrimary);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT, scroll(5));
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
            for (int slot : new int[]{AscensionChamberBlockEntity.RESULT_SLOT, AscensionChamberBlockEntity.BOTTLE_SLOT,
                    AscensionChamberBlockEntity.SCROLL_SLOT, AscensionChamberBlockEntity.INK_SLOT}) {
                ItemStack expected = chamber.inventory.getStackInSlot(slot).copy();
                require(!expected.isEmpty(), "extraction_priority_fixture_slot_" + slot);
                actor.getInventory().clearContent();
                require(chamber.takeDisplayedManualItem(actor,
                        slot == AscensionChamberBlockEntity.RESULT_SLOT || slot == AscensionChamberBlockEntity.SCROLL_SLOT ? 0 : 1),
                        "clicked_display_takes_fixture_slot_" + slot);
                require(chamber.inventory.getStackInSlot(slot).isEmpty() && chamber.hasGoggles()
                                && matchingCount(actor, expected.copyWithCount(1).save(new CompoundTag())) == expected.getCount()
                                && matchingCount(actor, gogglesTag) == 0,
                        "clicked_display_takes_only_machine_slot_" + slot + "_before_original_headpiece");
            }
            CompoundTag beforeTake = chamber.inventory.serializeNBT().copy();
            actor.getInventory().clearContent();
            clickChamber(level, pos, actor, ItemStack.EMPTY, false);
            require(!chamber.hasGoggles() && !chamber.hasNecromancerCrown()
                            && matchingCount(actor, gogglesTag) == 1
                            && chamber.inventory.serializeNBT().equals(beforeTake),
                    "plain_empty_hand_returns_original_headpiece_after_regular_items_with_template_retained");
            actor.getInventory().clearContent();
            clickChamber(level, pos, actor, crown, false);
            require(crown.isEmpty() && chamber.hasNecromancerCrown() && !chamber.hasGoggles(),
                    "crown_can_be_added_after_empty_hand_removal_of_goggles");
            ItemStack refusedGoggles = AllItems.GOGGLES.asStack();
            clickChamber(level, pos, actor, refusedGoggles, false);
            require(refusedGoggles.getCount() == 1 && chamber.hasNecromancerCrown() && !chamber.hasGoggles(),
                    "crown_also_rejects_goggles_without_consuming_them");
            actor.getInventory().clearContent();
            clickChamber(level, pos, actor, ItemStack.EMPTY, true);
            require(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.TEMPLATE_SLOT).isEmpty()
                            && matchingCount(actor, new ItemStack(ArcaneContent.TEMPLATE.get()).save(new CompoundTag())) == 1
                            && chamber.hasNecromancerCrown()
                            && matchingCount(actor, crownTag) == 0,
                    "shift_empty_hand_takes_template_before_original_headpiece");
            beforeTake = chamber.inventory.serializeNBT().copy();
            actor.getInventory().clearContent();
            clickChamber(level, pos, actor, ItemStack.EMPTY, true);
            require(matchingCount(actor, crownTag) == 1 && chamber.inventory.serializeNBT().equals(beforeTake)
                            && !chamber.hasNecromancerCrown(),
                    "shift_empty_hand_returns_original_headpiece_after_shift_items");
            actor.getInventory().clearContent();
            actor.getAbilities().instabuild = true;
            ItemStack creativeGoggles = ItemStack.of(gogglesTag);
            clickChamber(level, pos, actor, creativeGoggles, false);
            require(creativeGoggles.isEmpty() && chamber.hasGoggles() && matchingCount(actor, gogglesTag) == 0,
                    "creative_headpiece_attachment_transfers_original_without_keeping_an_extra_copy");
            clickChamber(level, pos, actor, ItemStack.EMPTY, false);
            require(!chamber.hasGoggles() && matchingCount(actor, gogglesTag) == 1,
                    "creative_empty_hand_detachment_returns_exactly_one_full_nbt_original");
            actor.getAbilities().instabuild = false;
            actor.getInventory().clearContent();
            // Preserve both originals in old dual-headpiece saves while showing only one at a time.
            CompoundTag legacyDual = chamber.saveWithFullMetadata();
            legacyDual.put("GogglesDecoration", gogglesTag.copy());
            legacyDual.put("CrownDecoration", crownTag.copy());
            chamber.load(legacyDual);
            require(chamber.hasGoggles() && !chamber.hasNecromancerCrown() && chamber.getDecorationDrops().size() == 2,
                    "legacy_dual_decoration_save_keeps_both_originals_but_renders_only_goggles");
            CompoundTag dualRoundtrip = chamber.saveWithFullMetadata();
            chamber.load(dualRoundtrip);
            clickChamber(level, pos, actor, ItemStack.EMPTY, false);
            require(!chamber.hasGoggles() && chamber.hasNecromancerCrown() && matchingCount(actor, gogglesTag) == 1
                            && chamber.inventory.serializeNBT().equals(beforeTake),
                    "first_legacy_empty_hand_click_returns_goggles_and_reveals_retained_crown");
            CompoundTag pendingCrown = chamber.saveWithFullMetadata();
            chamber.load(pendingCrown);
            // Clear only the fixture player's returned item before presenting its empty hand again.
            actor.getInventory().clearContent();
            clickChamber(level, pos, actor, ItemStack.EMPTY, false);
            require(!chamber.hasGoggles() && !chamber.hasNecromancerCrown() && matchingCount(actor, crownTag) == 1
                            && chamber.getDecorationDrops().isEmpty() && chamber.inventory.serializeNBT().equals(beforeTake),
                    "second_legacy_empty_hand_click_returns_pending_crown_after_reload_without_touching_inputs");
            actor.getInventory().clearContent();
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, scroll(4));
            require(chamber.takeDisplayedManualItem(actor, 0), "clicked_primary_without_headpiece_is_accessible");
            require(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT).isEmpty()
                            && actor.getInventory().items.stream().anyMatch(stack -> scrollLevel(stack) == 4),
                    "empty_hand_without_headpiece_preserves_original_machine_extraction_behavior");
            results.put("three_skins_mutually_exclusive_headpieces_full_nbt_and_items_before_headpieces", "PASS");
            results.put("creative_headpiece_transfer_and_return_conserve_exact_one_original", "PASS");
            results.put("legacy_dual_headpieces_render_one_and_return_both_safely_across_reload", "PASS");

            chamber = fresh(level, pos, 10, true, 1000);
            level.setBlock(pos.east(4), Blocks.AIR.defaultBlockState(), 3);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.CAKE_SLOT, new ItemStack(CrimsonContent.EMBER_CAKE.get(), 2));
            clickChamber(level, pos, actor, AllItems.BRASS_INGOT.asStack(), false);
            clickChamber(level, pos, actor, AllItems.GOGGLES.asStack(), false);
            ItemStack prepared = legacyAscensionStage(level, scroll(10));
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, prepared.copy());
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, ItemStack.EMPTY);
            CompoundTag before = chamber.inventory.serializeNBT().copy();
            int initialBolts = bolts(level).size();
            for (int tick = 0; tick < 79; tick++) chamber.tickServer();
            require(chamber.isBlazeAlive() && bolts(level).size() == initialBolts, "over_max_lightning_waits_for_actual_80_tick_commit");
            chamber.tickServer();
            List<LightningBolt> newBolts = bolts(level).stream().filter(bolt -> !oldBolts.contains(bolt.getUUID())).toList();
            require(!chamber.isBlazeAlive() && newBolts.size() == 1
                            && newBolts.get(0).blockPosition().equals(pos.above())
                            && newBolts.get(0).getPersistentData().getBoolean("ExperienceCharge"),
                    "unprotected_over_max_upgrade_creates_one_real_native_ce_i_bolt_and_kills_the_blaze");
            require(chamber.inventory.serializeNBT().equals(before) && chamber.sourceTank.getFluidAmount() == 1000
                            && chamber.getProgress() == 0 && chamber.hasGoggles() && chamber.getSkinKey().equals("brass")
                            && !chamber.getBlockState().getValue(AscensionChamberBlock.ALIVE)
                            && chamber.hasResidualFire() && chamber.getBlockState().getValue(AscensionChamberBlock.BURNING)
                            && chamber.getBlockState().getLightEmission(level, pos) == 12,
                    "lethal_upgrade_retains_pre_inked_progress_every_input_fluid_decoration_and_skin");
            int deadBolts = bolts(level).size();
            for (int tick = 0; tick < 160; tick++) chamber.tickServer();
            require(!chamber.processOneCycle() && bolts(level).size() == deadBolts
                            && chamber.inventory.serializeNBT().equals(before) && chamber.sourceTank.getFluidAmount() == 1000,
                    "dead_chamber_stays_inactive_without_repeated_lightning_or_resource_use");
            CompoundTag deadSaved = chamber.saveWithFullMetadata();
            level.removeBlockEntity(pos);
            chamber = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
            chamber.load(deadSaved);
            level.setBlockEntity(chamber);
            require(!chamber.isBlazeAlive() && chamber.hasResidualFire() && chamber.hasGoggles() && chamber.getSkinKey().equals("brass"),
                    "dead_state_skin_and_decoration_survive_save_load");
            List<ItemStack> drops = Block.getDrops(chamber.getBlockState(), level, pos, chamber);
            ItemStack portable = drops.stream().filter(stack -> stack.is(ArcaneContent.CHAMBER_ITEM.get())).findFirst().orElseThrow();
            require(portable.getTag().getCompound("BlockEntityTag").getString("Skin").equals("brass")
                            && !portable.getTag().getCompound("BlockEntityTag").getBoolean("BlazeAlive")
                            && portable.getTag().getCompound("BlockEntityTag").getBoolean("ResidualFire")
                            && !portable.getTag().getCompound("BlockEntityTag").contains("Inventory")
                            && !portable.getTag().getCompound("BlockEntityTag").contains("GogglesDecoration"),
                    "portable_chamber_preserves_death_without_duplicating_inventory_or_decoration_drops");
            BlockPos portablePos = pos.west(2);
            level.setBlock(portablePos, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
            require(BlockItem.updateCustomBlockEntityTag(level, actor, portablePos, portable)
                            && level.getBlockEntity(portablePos) instanceof AscensionChamberBlockEntity replaced
                            && !replaced.isBlazeAlive() && replaced.hasResidualFire() && replaced.getSkinKey().equals("brass"),
                    "native_block_item_replacement_cannot_resurrect_the_dead_blaze");
            level.setBlock(portablePos, Blocks.AIR.defaultBlockState(), 3);
            ItemStack emptyCarrier = AllItems.EMPTY_BLAZE_BURNER.asStack();
            require(!chamber.restoreBlaze(emptyCarrier, actor) && emptyCarrier.getCount() == 1,
                    "empty_burner_cannot_restore_a_dead_chamber");
            int emptyBefore = count(actor, AllItems.EMPTY_BLAZE_BURNER.get());
            ItemStack filledCarrier = AllBlocks.BLAZE_BURNER.asStack();
            clickChamber(level, pos, actor, filledCarrier, false);
            require(chamber.isBlazeAlive() && !chamber.hasResidualFire()
                            && !chamber.getBlockState().getValue(AscensionChamberBlock.BURNING) && filledCarrier.isEmpty()
                            && count(actor, AllItems.EMPTY_BLAZE_BURNER.get()) == emptyBefore + 1
                            && chamber.inventory.serializeNBT().equals(before) && chamber.hasGoggles(),
                    "filled_native_burner_restores_blaze_returns_empty_carrier_and_preserves_pending_recipe");
            results.put("over_max_no_rod_fails_atomically_death_persists_and_native_burner_restores", "PASS");

            for (LightningBolt bolt : newBolts) bolt.discard();
            BlockPos rod = placeTestRod(level, pos);
            // An exposed rod inside CEI's full radius intercepts exactly the next committed upgrade.
            int safeBefore = bolts(level).size();
            require(chamber.processOneCycle() && chamber.isBlazeAlive()
                            && scrollLevel(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT)) == 11
                            && chamber.sourceTank.getFluidAmount() == 750
                            && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.CAKE_SLOT).getCount() == 1,
                    "real_exposed_rod_allows_pre_inked_over_max_native_sequence_to_finish_once");
            List<LightningBolt> safeBolts = bolts(level).stream().filter(bolt -> !oldBolts.contains(bolt.getUUID())).toList();
            require(bolts(level).size() == safeBefore + 1 && safeBolts.size() == 1
                            && safeBolts.get(0).blockPosition().equals(rod.above()),
                    "native_helper_redirects_exactly_one_bolt_to_the_exposed_rod");
            victim = EntityType.IRON_GOLEM.create(level);
            require(victim != null, "native_lightning_damage_fixture_created");
            victim.moveTo(rod.getX() + 1.5, rod.getY(), rod.getZ() + 0.5, 0, 0);
            require(level.addFreshEntity(victim), "native_lightning_damage_fixture_added");
            float health = victim.getHealth();
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
            safeBolts.get(0).tick();
            require(level.getBlockState(rod).getValue(LightningRodBlock.POWERED) && victim.getHealth() < health,
                    "redirected_bolt_is_real_lightning_that_powers_rod_and_applies_native_damage");
            victim.discard();
            victim = null;
            results.put("native_ce_i_128_range_exposed_rod_exact_one_real_damage_bolt", "PASS");

            // A covered rod has a POI but is not WORLD_SURFACE's exposed topmost block.
            chamber = fresh(level, pos, 10, true, 1000);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.CAKE_SLOT, new ItemStack(CrimsonContent.EMBER_CAKE.get()));
            level.setBlock(rod.above(), Blocks.STONE.defaultBlockState(), 3);
            require(!chamber.processOneCycle() && !chamber.isBlazeAlive()
                            && chamber.sourceTank.getFluidAmount() == 1000
                            && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.CAKE_SLOT).getCount() == 1,
                    "covered_rod_does_not_intercept_and_failed_cycle_spends_nothing");
            chamber = fresh(level, pos, 7, true, 1000);
            int nativeBefore = bolts(level).size();
            require(chamber.processOneCycle() && chamber.isBlazeAlive() && bolts(level).size() == nativeBefore,
                    "red_solution_native_range_upgrade_never_calls_lightning");
            results.put("covered_rod_rejected_and_native_range_red_upgrade_never_strikes", "PASS");

            chamber = fresh(level, pos, 4, false, 1000);
            CompoundTag legacyDecorations = chamber.saveWithFullMetadata();
            legacyDecorations.put("GogglesDecoration", gogglesTag.copy());
            legacyDecorations.put("CrownDecoration", crownTag.copy());
            chamber.load(legacyDecorations);
            require(chamber.getDecorationDrops().size() == 2 && chamber.hasGoggles() && !chamber.hasNecromancerCrown(),
                    "breaking_legacy_dual_decoration_save_retains_both_full_originals");
            int existingItems = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2)).size();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            List<net.minecraft.world.entity.item.ItemEntity> spawned = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2));
            require(spawned.size() >= existingItems + 2
                            && spawned.stream().anyMatch(item -> AllItems.GOGGLES.isIn(item.getItem()))
                            && spawned.stream().anyMatch(item -> item.getItem().is(crownItem)),
                    "actual_block_removal_drops_native_goggles_and_crown");
            for (var item : spawned) item.discard();
            results.put("actual_block_removal_returns_decorations", "PASS");
        } finally {
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
            if (victim != null) victim.discard();
            actor.setShiftKeyDown(false);
            actor.getInventory().clearContent();
            clear(level, pos);
            level.setBlock(pos.west(2), Blocks.AIR.defaultBlockState(), 3);
            for (LightningBolt bolt : bolts(level)) if (!oldBolts.contains(bolt.getUUID())) bolt.discard();
        }
    }

    private static void runResidualFireTests(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        ServerPlayer actor = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("f1d19cdb-d193-43eb-8aa1-e761bdb6f7b2"), "ArcaneEmptyFire"));
        actor.getInventory().clearContent();
        actor.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 0, 0);
        GameType oldMode = actor.gameMode.getGameModeForPlayer();
        boolean oldInvulnerable = actor.isInvulnerable();
        Set<UUID> oldBolts = boltIds(level);
        boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        BlockPos neighborBase = pos.east();
        var previousNeighborBase = level.getBlockState(neighborBase);
        var previousNeighborFire = level.getBlockState(neighborBase.above());
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> fireCellsBefore = new LinkedHashMap<>();
        for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 2, 2))) {
            fireCellsBefore.put(cell.immutable(), level.getBlockState(cell));
        }
        try {
            actor.setInvulnerable(true);
            actor.setGameMode(GameType.SURVIVAL);
            AscensionChamberBlockEntity chamber = fresh(level, pos, 10, true, 1000);
            level.setBlock(pos.east(4), Blocks.AIR.defaultBlockState(), 3);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.CAKE_SLOT, new ItemStack(CrimsonContent.EMBER_CAKE.get()));
            require(!chamber.processOneCycle() && !chamber.isBlazeAlive() && chamber.hasResidualFire()
                            && chamber.getBlockState().getValue(AscensionChamberBlock.BURNING)
                            && chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == 0
                            && chamber.getBlockState().getLightEmission(level, pos) == 12,
                    "lethal_over_max_upgrade_creates_independent_lit_empty_chamber_not_a_working_state");
            CompoundTag inputBefore = chamber.inventory.serializeNBT().copy();
            int fluidBefore = chamber.sourceTank.getFluidAmount();
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            clickChamber(level, pos, actor, flint, false);
            require(flint.getDamageValue() == 0, "lighting_an_already_lit_empty_chamber_does_not_charge_tool_again");

            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    Direction.UP, level.getMaxBuildHeight(), 1);
            require(level.getBlockEntity(pos) == chamber && !chamber.isBlazeAlive() && !chamber.hasResidualFire()
                            && !chamber.getBlockState().getValue(AscensionChamberBlock.BURNING)
                            && chamber.getBlockState().getLightEmission(level, pos) == 0,
                    "real_survival_left_click_event_extinguishes_without_starting_to_break_the_chamber");
            clickChamber(level, pos, actor, flint, false);
            require(chamber.hasResidualFire() && flint.getDamageValue() == 1 && !chamber.processOneCycle(),
                    "native_flint_and_steel_costs_one_durability_to_relight_without_restoring_processing");

            actor.setGameMode(GameType.CREATIVE);
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    Direction.UP, level.getMaxBuildHeight(), 2);
            require(level.getBlockEntity(pos) == chamber && !chamber.hasResidualFire(),
                    "real_creative_left_click_is_cancelled_before_instant_block_destruction");
            actor.setGameMode(GameType.SURVIVAL);
            clickChamber(level, pos, actor, flint, false);
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
            clickChamber(level, pos, actor, shovel, false);
            require(!chamber.hasResidualFire() && shovel.getDamageValue() == 1 && flint.getDamageValue() == 2
                            && chamber.inventory.serializeNBT().equals(inputBefore) && chamber.sourceTank.getFluidAmount() == fluidBefore,
                    "native_shovel_right_click_costs_one_durability_and_preserves_pending_inputs_and_fluid");

            // Native initial and later flashes can place ordinary fire after the player already put the cage out.
            // Fold only the exact cell above this chamber away; preserve a neighboring normal fire.
            level.setBlock(neighborBase, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(neighborBase.above(), Blocks.FIRE.defaultBlockState(), 3);
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
            List<LightningBolt> localBolts = bolts(level).stream().filter(bolt -> !oldBolts.contains(bolt.getUUID())).toList();
            require(localBolts.size() == 1, "empty_chamber_fixture_contains_the_actual_committed_native_bolt");
            level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
            for (int tick = 0; tick < 80; tick++) {
                for (LightningBolt bolt : localBolts) if (!bolt.isRemoved()) bolt.tick();
                chamber.tickServer();
                require(!(level.getBlockState(pos.above()).getBlock() instanceof BaseFireBlock) && !chamber.hasResidualFire(),
                        "native_lightning_initial_and_delayed_flashes_cannot_reintroduce_unremovable_self_fire_tick_" + tick);
            }
            require(level.getBlockState(neighborBase.above()).is(Blocks.FIRE)
                            && chamber.inventory.serializeNBT().equals(inputBefore) && chamber.sourceTank.getFluidAmount() == fluidBefore,
                    "self_fire_cleanup_preserves_adjacent_normal_fire_and_never_processes_a_dead_chamber");

            CompoundTag extinguished = chamber.saveWithFullMetadata();
            level.removeBlockEntity(pos);
            chamber = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
            chamber.load(extinguished);
            level.setBlockEntity(chamber);
            require(!chamber.isBlazeAlive() && !chamber.hasResidualFire()
                            && chamber.getBlockState().getLightEmission(level, pos) == 0,
                    "unlit_empty_chamber_stays_unlit_after_save_load");
            ItemStack unlitPortable = Block.getDrops(chamber.getBlockState(), level, pos, chamber).stream()
                    .filter(stack -> stack.is(ArcaneContent.CHAMBER_ITEM.get())).findFirst().orElseThrow();
            BlockPos portablePos = pos.west(2);
            level.setBlock(portablePos, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
            require(BlockItem.updateCustomBlockEntityTag(level, actor, portablePos, unlitPortable)
                            && level.getBlockEntity(portablePos) instanceof AscensionChamberBlockEntity replaced
                            && !replaced.isBlazeAlive() && !replaced.hasResidualFire(),
                    "unlit_empty_block_item_keeps_both_death_and_extinguished_fire_on_replacement");
            level.setBlock(portablePos, Blocks.AIR.defaultBlockState(), 3);
            ItemStack filled = AllBlocks.BLAZE_BURNER.asStack();
            clickChamber(level, pos, actor, filled, false);
            level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
            chamber.tickServer();
            require(chamber.isBlazeAlive() && !chamber.hasResidualFire() && filled.isEmpty()
                            && !(level.getBlockState(pos.above()).getBlock() instanceof BaseFireBlock)
                            && level.getBlockState(neighborBase.above()).is(Blocks.FIRE),
                    "restoring_captured_blaze_clears_residual_fire_and_retains_short_native_flash_cleanup_grace");
            CompoundTag legacy = extinguished.copy();
            legacy.remove("ResidualFire");
            chamber.load(legacy);
            level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
            chamber.tickServer();
            require(!chamber.isBlazeAlive() && chamber.hasResidualFire()
                            && !(level.getBlockState(pos.above()).getBlock() instanceof BaseFireBlock),
                    "legacy_dead_chamber_with_external_fire_migrates_to_the_removable_internal_fire_layer");
            results.put("empty_chamber_lit_unlit_left_click_survival_creative_native_tool_durability", "PASS");
            results.put("residual_fire_save_load_portable_empty_state_and_restore", "PASS");
            results.put("native_bolt_all_flashes_self_fire_cleanup_only_adjacent_fire_preserved", "PASS");
        } finally {
            actor.setGameMode(oldMode);
            actor.setInvulnerable(oldInvulnerable);
            actor.getInventory().clearContent();
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
            for (LightningBolt bolt : bolts(level)) if (!oldBolts.contains(bolt.getUUID())) bolt.discard();
            clear(level, pos);
            level.setBlock(pos.west(2), Blocks.AIR.defaultBlockState(), 3);
            // The integration fixture discards only fire it introduced in its own disposable cells.
            // Production cleanup itself remains strictly limited to the chamber's one cell above.
            for (var entry : fireCellsBefore.entrySet()) {
                if (level.getBlockState(entry.getKey()).getBlock() instanceof BaseFireBlock
                        && !(entry.getValue().getBlock() instanceof BaseFireBlock)) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
            }
            level.setBlock(neighborBase.above(), previousNeighborFire, 3);
            level.setBlock(neighborBase, previousNeighborBase, 3);
        }
    }

    private static void clickChamber(ServerLevel level, BlockPos pos, ServerPlayer actor, ItemStack held, boolean sneaking) {
        actor.setShiftKeyDown(sneaking);
        actor.setItemInHand(InteractionHand.MAIN_HAND, held);
        ArcaneContent.CHAMBER.get().use(level.getBlockState(pos), level, pos, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        actor.setShiftKeyDown(false);
    }

    private static BlockPos placeTestRod(ServerLevel level, BlockPos pos) {
        BlockPos rod = pos.east(4);
        level.setBlock(rod.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState(), 3);
        return rod;
    }

    private static List<LightningBolt> bolts(ServerLevel level) {
        List<LightningBolt> result = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) if (entity instanceof LightningBolt bolt && !bolt.isRemoved()) result.add(bolt);
        return result;
    }

    private static Set<UUID> boltIds(ServerLevel level) {
        Set<UUID> result = new HashSet<>();
        for (LightningBolt bolt : bolts(level)) result.add(bolt.getUUID());
        return result;
    }

    private static int count(ServerPlayer player, Item item) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static int matchingCount(ServerPlayer player, CompoundTag originalSingleItem) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.copyWithCount(1).save(new CompoundTag()).equals(originalSingleItem)) count += stack.getCount();
        }
        return count;
    }

    private static AscensionChamberBlockEntity empty(ServerLevel level, BlockPos pos) {
        clear(level, pos);
        level.setBlock(pos, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity, "empty_chamber_placed");
        return (AscensionChamberBlockEntity) level.getBlockEntity(pos);
    }

    /** Runs the same native config build as player login; does not override any spell settings. */
    public static Map<String, Object> initializeSpellConfig(ServerLevel level) {
        int initialMax = SpellRegistry.FIREBOLT_SPELL.get().getMaxLevel();
        int defaultMax = SpellRegistry.FIREBOLT_SPELL.get().getDefaultConfig().maxLevel;
        SpellConfigManager.onDatapackSync(new OnDatapackSyncEvent(level.getServer().getPlayerList(), null));
        return Map.of("fixture", "irons_spellbooks:firebolt", "initial_max", initialMax,
                "default_max", defaultMax, "effective_max", SpellRegistry.FIREBOLT_SPELL.get().getMaxLevel());
    }

    private static AscensionChamberBlockEntity fresh(ServerLevel level, BlockPos pos, int inputLevel,
                                                     boolean transcendent, int solutionAmount) {
        clear(level, pos);
        level.setBlock(pos, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity, "chamber_placed");
        AscensionChamberBlockEntity chamber = (AscensionChamberBlockEntity) level.getBlockEntity(pos);
        chamber.inventory.setStackInSlot(1, scroll(inputLevel));
        SpellRarity rarity = inputLevel >= 10 ? SpellRarity.LEGENDARY : SpellRegistry.FIREBOLT_SPELL.get().getRarity(inputLevel + 1);
        chamber.inventory.setStackInSlot(2, new ItemStack(InkItem.getInkForRarity(rarity)));
        if (solutionAmount > 0) require(chamber.sourceTank.fill(new FluidStack(transcendent ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), solutionAmount),
                IFluidHandler.FluidAction.EXECUTE) == solutionAmount, "solution_filled");
        // Existing over-max transaction fixtures now use a real exposed native rod.
        // The separate isolated lightning tests explicitly remove it to test lethal failure.
        if (inputLevel >= 10 && transcendent) placeTestRod(level, pos);
        return chamber;
    }

    private static ItemStack scroll(int level) {
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(SpellRegistry.FIREBOLT_SPELL.get(), level, stack);
        return stack;
    }

    private static int scrollLevel(ItemStack stack) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) return -1;
        ISpellContainer container = ISpellContainer.get(stack);
        return container == null || container.getActiveSpellCount() != 1 ? -1 : container.getActiveSpells().get(0).getLevel();
    }

    private static void unchangedInputs(AscensionChamberBlockEntity chamber, int inputLevel, int solution, String label) {
        require(scrollLevel(chamber.inventory.getStackInSlot(1)) == inputLevel
                && chamber.inventory.getStackInSlot(1).getCount() == 1
                && chamber.inventory.getStackInSlot(2).getCount() == 1
                && chamber.sourceTank.getFluidAmount() == solution
                && chamber.inventory.getStackInSlot(3).isEmpty()
                && chamber.inventory.getStackInSlot(4).isEmpty(), label + "_inputs_and_fluids_unchanged");
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber) {
            for (int slot = 0; slot < chamber.inventory.getSlots(); slot++) chamber.inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (level.getBlockEntity(pos.above()) instanceof BasinBlockEntity basin) {
            for (int slot = 0; slot < basin.getInputInventory().getSlots(); slot++) basin.getInputInventory().setStackInSlot(slot, ItemStack.EMPTY);
            for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) basin.getOutputInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.east(4).above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.east(4), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("FAILED: " + label);
    }
}
