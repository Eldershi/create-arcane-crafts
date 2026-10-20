package dev.expedition.arcane.chamber;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Actual chamber transactions: presentation cannot expose, repay, reroll or duplicate a pending draw. */
public final class ResearchPresentationTests {
    private ResearchPresentationTests() {}

    public static Map<String, Object> run(ServerLevel level, BlockPos origin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spell_config", ChamberTests.initializeSpellConfig(level));
        BlockPos pos = origin.immutable();
        Snapshot before = snapshot(level, pos), above = snapshot(level, pos.above());
        ServerPlayer actor = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("643e3046-1ef5-4e1b-bb86-c5e67da32efe"), "ResearchTest"));
        try {
            require(AscensionChamberBlockEntity.RESEARCH_PROCESSING_TIME == 161,
                    "approved_research_endpoint_is_161_ticks");
            require(AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME == 70,
                    "ordinary_manual_upgrades_keep_70_ticks");
            result.put("arcane", exercise(level, pos, actor, false));
            result.put("crimson", exercise(level, pos, actor, true));
            result.put("legacy_paid_draw", exerciseLegacyPaidDraw(level, pos));
            result.put("status", "PASS");
            return result;
        } catch (RuntimeException error) {
            throw new IllegalStateException("Research presentation ownership tests failed; completed="
                    + result + "; " + error.getMessage(), error);
        } finally {
            restore(level, pos.above(), above);
            restore(level, pos, before);
        }
    }

    private static Map<String, Object> exercise(ServerLevel level, BlockPos pos, ServerPlayer actor,
                                               boolean crimson) {
        restore(level, pos.above(), new Snapshot(Blocks.AIR.defaultBlockState(), null));
        restore(level, pos, new Snapshot(ArcaneContent.CHAMBER.get().defaultBlockState(), null));
        require(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity, "registered_chamber_created");
        AscensionChamberBlockEntity chamber = (AscensionChamberBlockEntity) level.getBlockEntity(pos);
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, new ItemStack(ItemRegistry.SCROLL.get()));
        ItemStack focus = new ItemStack(Items.BLAZE_ROD);
        require(RandomResearchRules.poolForFocus(focus).isPresent(), "registered_fire_focus_has_research_pool");
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, focus.copy());
        require(chamber.sourceTank.fill(new FluidStack(crimson ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 1000),
                IFluidHandler.FluidAction.EXECUTE) == 1000, "fixture_1000_physical_mb_filled");
        if (crimson) {
            CompoundTag credit = chamber.saveWithFullMetadata();
            credit.putInt("CrimsonCreditQuarterMb", 2);
            chamber.load(credit);
        }
        chamber.tickServer();
        require(chamber.isResearchProcessing() && chamber.getManualProgress() == 0,
                "actual_tick_establishes_paid_research_at_zero");
        ItemStack expected = chamber.getResearchOutput();
        require(!expected.isEmpty() && expected.getCount() == 1, "server_selected_one_pending_scroll");
        long seed = chamber.getResearchVisualSeed();
        CompoundTag transaction = chamber.saveWithFullMetadata().getCompound("ManualTransaction").copy();
        ItemStack remainder = ItemStack.EMPTY;
        assertPaid(chamber, expected, remainder, seed, crimson, "initial_payment");
        require(ItemStack.matches(chamber.getResearchSecondary(), focus)
                        && transaction.getBoolean("GuideConsumed") && !chamber.hasTemplate(),
                "one_guide_consumed_but_its_visual_snapshot_remains_without_runtime_template");
        IItemHandler automation = automation(chamber);

        for (int i = 1; i <= 90; i++) {
            chamber.tickServer();
            require(chamber.getManualProgress() == i, "real_progress_before_pause_" + i);
            cannotTake(chamber, automation, actor, "before_endpoint_" + i);
        }
        require(chamber.getManualProgress() > AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME,
                "research_outlives_old_70_tick_endpoint");

        CompoundTag dead = chamber.saveWithFullMetadata();
        dead.putBoolean("BlazeAlive", false);
        chamber.load(dead);
        for (int i = 0; i < 40; i++) chamber.tickServer();
        require(chamber.getManualProgress() == 90 && !chamber.isResearchVisualRunning(), "dead_blaze_freezes_work_and_visual_clock");
        require(chamber.getResearchVisualTicks(level.getGameTime() + 100) == 90,
                "paused_visual_clock_does_not_extrapolate");
        chamber = reload(level, pos, chamber.saveWithFullMetadata());
        automation = automation(chamber);
        require(chamber.getManualProgress() == 90 && chamber.isResearchProcessing(),
                "nbt_reload_preserves_progress_above_70");
        require(chamber.saveWithFullMetadata().getCompound("ManualTransaction").equals(transaction),
                "nbt_reload_keeps_selected_output_payment_snapshot_and_visual_seed");
        for (int i = 0; i < 30; i++) chamber.tickServer();
        require(chamber.getManualProgress() == 90, "reloaded_paid_draw_stays_paused");
        assertPaid(chamber, expected, remainder, seed, crimson, "paused_reload");
        cannotTake(chamber, automation, actor, "paused_reload");

        CompoundTag living = chamber.saveWithFullMetadata();
        living.putBoolean("BlazeAlive", true);
        chamber.load(living);
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT, new ItemStack(Items.COBBLESTONE, 64));
        for (int i = 0; i < 20; i++) chamber.tickServer();
        require(chamber.getManualProgress() == 90 && !chamber.isResearchVisualRunning(),
                "blocked_output_pauses_instead_of_invalidating_paid_draw");
        require(automation.extractItem(AscensionChamberBlockEntity.RESULT_SLOT, 64, false).isEmpty(),
                "automation_cannot_extract_during_output_blockage");
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT, ItemStack.EMPTY);

        level.setBlock(pos.above(), AllBlocks.BASIN.getDefaultState(), 3);
        for (int i = 0; i < 20; i++) chamber.tickServer();
        require(chamber.getManualProgress() == 90 && chamber.isResearchProcessing() && !chamber.isResearchVisualRunning(),
                "added_working_basin_pauses_without_cancelling_paid_draw");
        level.removeBlockEntity(pos.above());
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        assertPaid(chamber, expected, remainder, seed, crimson, "output_and_basin_pause");

        while (chamber.getManualProgress() < 160) {
            chamber.tickServer();
            cannotTake(chamber, automation, actor, "resumed_before_161");
        }
        require(chamber.isResearchVisualRunning() && chamber.getManualProgress() == 160,
                "resumed_work_waits_through_tick_160");
        chamber = reload(level, pos, chamber.saveWithFullMetadata());
        automation = automation(chamber);
        require(chamber.getManualProgress() == 160 && chamber.getResearchVisualSeed() == seed,
                "late_reload_keeps_endpoint_and_seed");
        cannotTake(chamber, automation, actor, "reloaded_at_160");
        chamber.tickServer();
        require(!chamber.isManualProcessing() && chamber.getManualProgress() == 161,
                "exactly_tick_161_commits_once");
        require(ItemStack.matches(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT), expected),
                "committed_result_is_original_pending_scroll");
        require(chamber.hasResearchPresentation() && !chamber.isResearchProcessing()
                        && chamber.getResearchVisualSeed() == seed && !chamber.getResearchPrimary().isEmpty()
                        && !chamber.getResearchSecondary().isEmpty() && ItemStack.matches(chamber.getResearchOutput(), expected),
                "completed_presentation_retains_input_result_and_seed_metadata");
        require(chamber.getAdditionalReturnDrops().isEmpty() && chamber.getAdditionalReturnDrops().isEmpty(),
                "committed_metadata_does_not_duplicate_inventory_owned_scroll");
        require(chamber.sourceTank.getFluidAmount() == 500
                        && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.INK_SLOT).isEmpty(),
                "all_pauses_reloads_and_commit_charge_blank_guide_and_liquid_only_once");

        chamber = reload(level, pos, chamber.saveWithFullMetadata());
        automation = automation(chamber);
        require(chamber.hasResearchPresentation() && chamber.getAdditionalReturnDrops().isEmpty(),
                "completed_nbt_reload_retains_presentation_without_a_second_owned_result");
        require(ItemStack.matches(automation.extractItem(AscensionChamberBlockEntity.RESULT_SLOT, 1, true), expected)
                        && chamber.hasResearchPresentation(), "simulated_collection_preserves_presentation");
        require(ItemStack.matches(automation.extractItem(AscensionChamberBlockEntity.RESULT_SLOT, 1, false), expected),
                "automation_receives_single_original_result_after_endpoint");
        require(!chamber.hasResearchPresentation() && chamber.getResearchOutput().isEmpty()
                        && chamber.getResearchPrimary().isEmpty() && chamber.getResearchSecondary().isEmpty()
                        && chamber.getResearchVisualSeed() == 0 && chamber.getManualOperation().isEmpty()
                        && chamber.getManualProgress() == 0 && chamber.getManualCompletedAt() == -1,
                "collecting_result_clears_all_reveal_metadata_for_next_upgrade");
        require(automation.extractItem(AscensionChamberBlockEntity.RESULT_SLOT, 1, false).isEmpty()
                        && chamber.getAdditionalReturnDrops().isEmpty(), "cannot_collect_a_second_result");
        return Map.of("status", "PASS", "duration_ticks", 161, "pause_reload_at_tick", 90,
                "late_reload_at_tick", 160, "physical_mb_paid_once", 500,
                "guide_consumed_once", 1, "output_ownership", "one pending -> one result -> one extraction");
    }

    /** A pre-fix paid transaction already owns its result; migration must never charge its retained guide. */
    private static Map<String, Object> exerciseLegacyPaidDraw(ServerLevel level, BlockPos pos) {
        restore(level, pos.above(), new Snapshot(Blocks.AIR.defaultBlockState(), null));
        restore(level, pos, new Snapshot(ArcaneContent.CHAMBER.get().defaultBlockState(), null));
        AscensionChamberBlockEntity chamber = (AscensionChamberBlockEntity) level.getBlockEntity(pos);
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, new ItemStack(ItemRegistry.SCROLL.get()));
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, new ItemStack(Items.BLAZE_ROD));
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        ItemStack original = chamber.getResearchOutput();
        CompoundTag legacy = chamber.saveWithFullMetadata();
        legacy.getCompound("ManualTransaction").remove("GuideConsumed");
        chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, new ItemStack(Items.BLAZE_ROD, 3));
        legacy.put("Inventory", chamber.inventory.serializeNBT());
        chamber = reload(level, pos, legacy);
        for (int i = 0; i < AscensionChamberBlockEntity.RESEARCH_PROCESSING_TIME; i++) chamber.tickServer();
        require(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.INK_SLOT).getCount() == 3
                        && chamber.sourceTank.getFluidAmount() == 500
                        && ItemStack.matches(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT), original)
                        && chamber.getAdditionalReturnDrops().isEmpty(),
                "legacy_paid_transaction_keeps_its_original_payment_output_and_untruncated_guide_stack");
        return Map.of("status", "PASS", "legacy_guide_count_preserved", 3, "additional_payment", 0);
    }

    private static void cannotTake(AscensionChamberBlockEntity chamber, IItemHandler automation,
                                   ServerPlayer actor, String where) {
        require(chamber.isResearchProcessing()
                        && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT).isEmpty()
                        && !chamber.takeManualOutputs(actor) && chamber.extractForPlayer(false).isEmpty()
                        && automation.extractItem(AscensionChamberBlockEntity.RESULT_SLOT, 1, true).isEmpty()
                        && automation.extractItem(AscensionChamberBlockEntity.RESULT_SLOT, 1, false).isEmpty(),
                where + "_no_hand_or_automation_result_before_endpoint");
    }

    private static void assertPaid(AscensionChamberBlockEntity chamber, ItemStack output, ItemStack focus,
                                   long seed, boolean crimson, String where) {
        require(chamber.sourceTank.getFluidAmount() == 500
                        && chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT).isEmpty()
                        && ItemStack.matches(chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.INK_SLOT), focus)
                        && chamber.getResearchVisualSeed() == seed && ItemStack.matches(chamber.getResearchOutput(), output)
                        && chamber.isResearchCrimson() == crimson
                        && chamber.getCrimsonCreditQuarterMb() == (crimson ? 2 : 0),
                where + "_one_blank_and_500mb_paid_without_credit_or_reroll");
        var drops = chamber.getAdditionalReturnDrops();
        require(drops.size() == 1 && ItemStack.matches(drops.get(0), output),
                where + "_destruction_owns_one_pending_output");
    }

    private static IItemHandler automation(AscensionChamberBlockEntity chamber) {
        return chamber.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN)
                .orElseThrow(() -> new IllegalStateException("missing_real_automation_capability"));
    }

    private static AscensionChamberBlockEntity reload(ServerLevel level, BlockPos pos, CompoundTag saved) {
        level.removeBlockEntity(pos);
        AscensionChamberBlockEntity restored = new AscensionChamberBlockEntity(pos, level.getBlockState(pos));
        restored.load(saved);
        level.setBlockEntity(restored);
        return restored;
    }

    private record Snapshot(BlockState state, CompoundTag tag) {}

    private static Snapshot snapshot(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return new Snapshot(level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata());
    }

    private static void restore(ServerLevel level, BlockPos pos, Snapshot saved) {
        // Detach the disposable entity first, so fixture cleanup does not emit owned item drops.
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, saved.state, 3);
        var entity = level.getBlockEntity(pos);
        if (saved.tag != null && entity != null) entity.load(saved.tag);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("FAILED: " + label);
    }
}
