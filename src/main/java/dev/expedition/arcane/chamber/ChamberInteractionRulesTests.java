package dev.expedition.arcane.chamber;

import com.mojang.authlib.GameProfile;
import dev.expedition.arcane.ArcaneContent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.item.UniqueItem;
import io.redspace.ironsspellbooks.item.UpgradeOrbTypeData;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.UpgradeOrbTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Runs production insertion, recipe planning, NBT migration and clicked-display ownership in a real world. */
public final class ChamberInteractionRulesTests {
    private ChamberInteractionRulesTests() {}

    public static Map<String, Object> run(ServerLevel level, BlockPos pos) {
        Snapshot before = snapshot(level, pos), above = snapshot(level, pos.above());
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("fda351d0-8a0b-48c0-8901-c19083013d38"), "ChamberRulesTest"));
        ListTag oldInventory = player.getInventory().save(new ListTag());
        var oldPosition = player.position();
        boolean oldCreative = player.getAbilities().instabuild;
        Map<String, Object> checks = new LinkedHashMap<>();
        try {
            player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + 2);
            player.getInventory().clearContent();
            player.getAbilities().instabuild = false;
            checkInsertion(level, pos, player, checks);
            checkLegacyAndExtraction(level, pos, player, checks);
            checkBookSelection(level, pos, player, checks);
            checkNativeOperations(level, pos, checks);
            checks.put("status", "PASS");
            return checks;
        } finally {
            player.getInventory().clearContent();
            player.getInventory().load(oldInventory);
            player.getAbilities().instabuild = oldCreative;
            player.setPos(oldPosition.x, oldPosition.y, oldPosition.z);
            restore(level, pos.above(), above);
            restore(level, pos, before);
        }
    }

    private static void checkInsertion(ServerLevel level, BlockPos pos, ServerPlayer player, Map<String, Object> checks) {
        AscensionChamberBlockEntity chamber = fresh(level, pos);
        IItemHandler items = chamber.getAutomationInventory();
        ItemStack template = new ItemStack(ArcaneContent.TEMPLATE.get(), 2);
        require(!chamber.inventory.isItemValid(0, template) && !chamber.insertFromPlayer(template, false)
                        && items.insertItem(0, template, true).getCount() == 2
                        && items.insertItem(0, template, false).getCount() == 2 && template.getCount() == 2,
                "runtime_template_rejected_by_player_and_automation_without_loss");
        require(items.getSlotLimit(1) == 1 && items.getSlotLimit(2) == 1, "both_display_inputs_have_limit_one");
        CompoundTag empty = chamber.inventory.serializeNBT();
        ItemStack blanks = new ItemStack(ItemRegistry.SCROLL.get(), 64);
        require(items.insertItem(1, blanks, true).getCount() == 63
                        && chamber.inventory.serializeNBT().equals(empty) && blanks.getCount() == 64,
                "simulated_stack_insert_accepts_only_one_without_mutation");
        require(items.insertItem(1, blanks, false).getCount() == 63
                        && chamber.inventory.getStackInSlot(1).getCount() == 1,
                "executed_stack_insert_accepts_only_one");
        ItemStack incompatible = new ItemStack(InkItem.getInkForRarity(
                io.redspace.ironsspellbooks.api.spells.SpellRarity.COMMON), 6);
        CompoundTag before = chamber.inventory.serializeNBT();
        require(items.insertItem(2, incompatible, true).getCount() == 6
                        && items.insertItem(2, incompatible, false).getCount() == 6
                        && !chamber.insertFromPlayer(incompatible, false)
                        && chamber.inventory.serializeNBT().equals(before) && incompatible.getCount() == 6,
                "blank_scroll_plus_bottled_ink_rejected_in_both_paths_without_mutation");
        ItemStack guides = new ItemStack(Items.BLAZE_ROD, 64);
        require(items.insertItem(2, guides, false).getCount() == 63
                        && chamber.inventory.getStackInSlot(2).getCount() == 1,
                "valid_research_guide_accepts_only_one");
        require(!chamber.processOneCycle() && !chamber.isManualProcessing(), "no_liquid_means_waiting_not_wrong_pair");
        chamber = fresh(level, pos);
        ItemStack heldGuides = new ItemStack(Items.BLAZE_ROD, 3);
        ItemStack heldBlank = new ItemStack(ItemRegistry.SCROLL.get(), 3);
        require(chamber.insertFromPlayer(heldGuides, false) && heldGuides.getCount() == 2
                        && chamber.insertFromPlayer(heldBlank, false) && heldBlank.getCount() == 2,
                "survival_can_place_guide_first_and_transfers_one_of_each");
        require(!chamber.insertFromPlayer(heldGuides, false) && heldGuides.getCount() == 2,
                "second_click_cannot_stack_a_displayed_material");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        require(chamber.isResearchProcessing() && chamber.inventory.getStackInSlot(1).isEmpty()
                        && chamber.inventory.getStackInSlot(2).isEmpty() && chamber.sourceTank.isEmpty()
                        && !chamber.takeDisplayedManualItem(player, 0) && !chamber.takeDisplayedManualItem(player, 1),
                "research_consumes_both_items_once_and_locks_their_paid_displays");
        chamber = fresh(level, pos);
        ItemStack creativeBlank = new ItemStack(ItemRegistry.SCROLL.get(), 4);
        ItemStack creativeGuide = new ItemStack(Items.BLAZE_ROD, 4);
        player.getAbilities().instabuild = true;
        require(chamber.insertFromPlayer(creativeBlank, true) && chamber.insertFromPlayer(creativeGuide, true)
                        && creativeBlank.getCount() == 4 && creativeGuide.getCount() == 4,
                "creative_placement_preserves_held_stack_but_machine_inputs_are_singletons");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        require(chamber.isResearchProcessing() && chamber.inventory.getStackInSlot(1).isEmpty()
                        && chamber.inventory.getStackInSlot(2).isEmpty() && chamber.sourceTank.isEmpty(),
                "creative_mode_does_not_bypass_machine_payment");
        player.getAbilities().instabuild = false;
        checks.put("insertion_and_payment", "PASS: template rejected; cap one; simulation; both orders; survival and creative");
    }

    private static void checkLegacyAndExtraction(ServerLevel level, BlockPos pos, ServerPlayer player,
                                                  Map<String, Object> checks) {
        AscensionChamberBlockEntity chamber = fresh(level, pos);
        chamber.inventory.setStackInSlot(0, new ItemStack(ArcaneContent.TEMPLATE.get(), 3));
        chamber.inventory.setStackInSlot(1, scroll(4).copyWithCount(3));
        chamber.inventory.setStackInSlot(2, ink(5).copyWithCount(6));
        CompoundTag legacy = chamber.saveWithFullMetadata();
        chamber.load(legacy);
        require(chamber.inventory.getStackInSlot(0).getCount() == 3
                        && chamber.inventory.getStackInSlot(1).getCount() == 3
                        && chamber.inventory.getStackInSlot(2).getCount() == 6,
                "legacy_oversized_inputs_and_old_templates_survive_reload_without_truncation");
        int revision = chamber.getManualDisplayRevision();
        require(chamber.takeDisplayedManualItem(player, 1)
                        && chamber.inventory.getStackInSlot(2).getCount() == 5
                        && chamber.inventory.getStackInSlot(1).getCount() == 3
                        && chamber.getManualDisplayRevision() != revision,
                "clicked_secondary_recovers_exactly_one_and_advances_display_revision");
        require(chamber.takeDisplayedManualItem(player, 0) && chamber.inventory.getStackInSlot(1).getCount() == 2
                        && chamber.inventory.getStackInSlot(2).getCount() == 5,
                "clicked_primary_does_not_take_secondary");
        int returnedTemplates = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack returned = chamber.getAutomationInventory().extractItem(0, 3, false);
            require(returned.is(ArcaneContent.TEMPLATE.get()) && returned.getCount() == 1,
                    "legacy_template_extraction_obeys_native_max_stack_one");
            returnedTemplates += returned.getCount();
        }
        require(returnedTemplates == 3 && chamber.inventory.getStackInSlot(0).isEmpty()
                        && chamber.getAutomationInventory().extractItem(0, 3, false).isEmpty(),
                "old_templates_can_be_returned_even_though_new_insertion_is_disabled");
        chamber = fresh(level, pos);
        require(!chamber.takeDisplayedManualItem(player, 1) && !chamber.takeDisplayedManualItem(player, -1)
                        && !chamber.takeDisplayedManualItem(player, 2), "empty_or_unknown_display_does_not_fall_back");
        chamber.inventory.setStackInSlot(1, scroll(4));
        chamber.inventory.setStackInSlot(2, ink(5));
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        chamber.tickServer();
        for (int i = 0; i < AscensionChamberBlockEntity.MANUAL_PROCESSING_TIME; i++) chamber.tickServer();
        require(!chamber.isManualProcessing() && !chamber.getManualPrimary().isEmpty()
                        && chamber.getManualSecondary().is(Items.GLASS_BOTTLE), "finished_outputs_have_two_matching_displays");
        require(chamber.takeDisplayedManualItem(player, 0) && chamber.inventory.getStackInSlot(3).isEmpty()
                        && chamber.getManualSecondary().is(Items.GLASS_BOTTLE)
                        && !chamber.takeDisplayedManualItem(player, 0),
                "taking_primary_once_leaves_secondary_owned_and_cannot_duplicate_primary");
        require(chamber.takeDisplayedManualItem(player, 1) && chamber.inventory.getStackInSlot(4).isEmpty()
                        && !chamber.takeDisplayedManualItem(player, 1) && chamber.getManualOperation().isEmpty(),
                "secondary_can_be_taken_independently_then_complete_transaction_clears_once");
        chamber.inventory.setStackInSlot(3, scroll(5));
        require(!chamber.insertFromPlayer(scroll(4), false)
                        && !chamber.getAutomationInventory().insertItem(1, scroll(4), false).isEmpty(),
                "legacy_output_without_manual_ready_flag_still_blocks_new_hidden_inputs");
        chamber.inventory.setStackInSlot(3, ItemStack.EMPTY);
        chamber.inventory.setStackInSlot(1, scroll(4));
        var close = player.position();
        player.setPos(pos.getX() + 32, pos.getY(), pos.getZ());
        require(!chamber.takeDisplayedManualItem(player, 0) && !chamber.inventory.getStackInSlot(1).isEmpty(),
                "server_rejects_remote_display_extraction");
        player.setPos(close.x, close.y, close.z);
        checks.put("legacy_and_independent_extraction", "PASS: no truncation; safe old templates; per-display ownership; duplicate and reach rejection");
    }

    private static void checkBookSelection(ServerLevel level, BlockPos pos, ServerPlayer player, Map<String, Object> checks) {
        AscensionChamberBlockEntity chamber = fresh(level, pos);
        ItemStack book = book(4), ink = ink(5);
        CompoundTag untouchedBook = book.save(new CompoundTag()), untouchedInk = ink.save(new CompoundTag());
        require(ChamberInputRules.canPair(level, book, ink)
                        && book.save(new CompoundTag()).equals(untouchedBook)
                        && ink.save(new CompoundTag()).equals(untouchedInk),
                "book_pair_filter_waits_for_selection_and_resources_without_mutating_originals");
        require(chamber.insertFromPlayer(book, false) && chamber.insertFromPlayer(ink, false)
                        && !chamber.processOneCycle(), "book_without_confirmed_spell_waits");
        require(chamber.selectBookSpell(player, 0, chamber.getManualSelectionRevision()), "actual_book_selection_accepts_slot_zero");
        CompoundTag ready = chamber.saveWithFullMetadata();
        ready.putLong("ManualSelectedAt", level.getGameTime() - 11);
        chamber.load(ready);
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.processOneCycle() && chamber.inventory.getStackInSlot(1).isEmpty()
                        && chamber.inventory.getStackInSlot(2).isEmpty()
                        && ISpellContainer.get(chamber.inventory.getStackInSlot(3)).getSpellAtIndex(0).getLevel() == 5,
                "confirmed_book_spell_upgrade_consumes_one_ink_without_runtime_template");
        ItemStack firstResult = chamber.inventory.getStackInSlot(3).copy();
        long placement = chamber.getManualItemPlacedAt();
        CompoundTag completed = chamber.saveWithFullMetadata();
        // A migrated/custom native result can retain more than its first return. Promotion
        // must preserve the same queue contract even though normal book ink returns one bottle.
        ListTag refunds = new ListTag();
        refunds.add(new ItemStack(Items.AMETHYST_SHARD).save(new CompoundTag()));
        refunds.add(new ItemStack(Items.GOLD_NUGGET).save(new CompoundTag()));
        completed.put("ManualExtraReturns", refunds);
        completed.putLong("ManualCompletedAt", level.getGameTime() - AscensionChamberBlockEntity.BOOK_REARM_TICKS + 1);
        chamber.load(completed);
        chamber.tickServer();
        require(chamber.inventory.getStackInSlot(1).isEmpty() && ItemStack.matches(chamber.inventory.getStackInSlot(3), firstResult)
                        && !chamber.isBookRearmed(), "book_result_waits_for_complete_24_tick_settle");
        completed = chamber.saveWithFullMetadata();
        completed.putLong("ManualCompletedAt", level.getGameTime() - AscensionChamberBlockEntity.BOOK_REARM_TICKS);
        chamber.load(completed);
        chamber.tickServer();
        require(chamber.isBookRearmed() && chamber.inventory.getStackInSlot(3).isEmpty()
                        && ItemStack.matches(chamber.inventory.getStackInSlot(1), firstResult)
                        && !chamber.isManualBookSelectionLocked() && chamber.getSelectedBookSpellSlot() == -1
                        && chamber.getManualItemPlacedAt() == placement
                        && chamber.getManualCompletedAt() == level.getGameTime() - 24
                        && chamber.inventory.getStackInSlot(4).is(Items.GLASS_BOTTLE)
                        && chamber.getAdditionalReturnDrops().size() == 2,
                "settled_book_moves_result_to_input_once_unlocks_selection_and_preserves_every_return_and_visual_timestamp");
        CompoundTag rearmed = chamber.saveWithFullMetadata();
        chamber.load(rearmed);
        ItemStack secondInk = ink(6);
        require(chamber.isBookRearmed() && !chamber.insertFromPlayer(secondInk, false) && secondInk.getCount() == 1
                        && chamber.selectBookSpell(player, 0, chamber.getManualSelectionRevision()),
                "rearmed_book_survives_reload_and_can_be_selected_but_unclaimed_returns_block_new_ink");
        for (var expectedReturn : new net.minecraft.world.item.Item[]{Items.GLASS_BOTTLE, Items.AMETHYST_SHARD, Items.GOLD_NUGGET}) {
            require(chamber.getManualSecondary().is(expectedReturn) && chamber.takeDisplayedManualItem(player, 1),
                    "rearmed_book_keeps_individual_return_" + expectedReturn);
            require(chamber.isBookRearmed() && ItemStack.matches(chamber.inventory.getStackInSlot(1), firstResult),
                    "collecting_return_does_not_take_or_relock_rearmed_book");
        }
        require(chamber.getAdditionalReturnDrops().isEmpty() && chamber.inventory.getStackInSlot(4).isEmpty()
                        && chamber.insertFromPlayer(secondInk, false) && secondInk.isEmpty(),
                "after_all_returns_new_ink_can_be_added_without_removing_book");
        ready = chamber.saveWithFullMetadata();
        ready.putLong("ManualSelectedAt", level.getGameTime() - 11);
        chamber.load(ready);
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.processOneCycle() && chamber.inventory.getStackInSlot(1).isEmpty()
                        && chamber.inventory.getStackInSlot(2).isEmpty() && chamber.sourceTank.isEmpty()
                        && ISpellContainer.get(chamber.inventory.getStackInSlot(3)).getSpellAtIndex(0).getLevel() == 6
                        && !chamber.processOneCycle(),
                "same_retained_book_upgrades_a_second_time_with_exact_payment_and_no_duplicate_result");

        chamber = fresh(level, pos);
        require(chamber.insertFromPlayer(book(1), false)
                        && chamber.insertFromPlayer(new ItemStack(ItemRegistry.LESSER_SPELL_SLOT_UPGRADE.get()), false),
                "book_slot_upgrade_fixture_inserted");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.processOneCycle(), "book_slot_upgrade_finishes");
        completed = chamber.saveWithFullMetadata();
        completed.putLong("ManualCompletedAt", level.getGameTime() - 24);
        chamber.load(completed);
        chamber.tickServer();
        require(chamber.isBookRearmed() && chamber.inventory.getStackInSlot(3).isEmpty()
                        && ISpellContainer.get(chamber.inventory.getStackInSlot(1)).getMaxSpellCount() == 2,
                "slot_upgraded_book_also_reopens_as_single_owned_input");

        chamber = fresh(level, pos);
        require(chamber.insertFromPlayer(scroll(4), false) && chamber.insertFromPlayer(ink(5), false),
                "non_book_upgrade_fixture_inserted");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.processOneCycle(), "non_book_upgrade_finishes");
        completed = chamber.saveWithFullMetadata();
        completed.putLong("ManualCompletedAt", level.getGameTime() - 100);
        chamber.load(completed);
        chamber.tickServer();
        require(!chamber.isBookRearmed() && chamber.inventory.getStackInSlot(1).isEmpty()
                        && !chamber.inventory.getStackInSlot(3).isEmpty()
                        && !chamber.insertFromPlayer(scroll(5), false),
                "non_book_pending_result_keeps_its_existing_collection_and_input_blockage_contract");
        checks.put("book_selection", "PASS: selection; exact 24 tick result-to-input transfer; all returns; reload; two consecutive upgrades; slot upgrade; non-book unchanged");
    }

    private static void checkNativeOperations(ServerLevel level, BlockPos pos, Map<String, Object> checks) {
        Map<String, Object> nativeChecks = new LinkedHashMap<>();
        ItemStack unique = new ItemStack(ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> item instanceof UniqueItem).findFirst().orElseThrow());
        var container = ISpellContainer.create(1, false, false).mutableCopy();
        require(container.addSpellAtIndex(SpellRegistry.FIREBOLT_SPELL.get(), 1, 0, true), "unique_fixture_spell_installed");
        ISpellContainer.set(unique, container.toImmutable());
        checkNative(level, pos, unique, scroll(2), ManualUpgradeRecipes.UNIQUE_SPELL_UPGRADE, nativeChecks);
        checkNative(level, pos, new ItemStack(Items.DIAMOND_SWORD), scroll(2), ManualUpgradeRecipes.SPELL_IMBUE, nativeChecks);
        ItemStack orb = new ItemStack(ItemRegistry.UPGRADE_ORB.get());
        UpgradeOrbTypeData.set(orb, new UpgradeOrbTypeData(UpgradeOrbTypeRegistry.FIRE_SPELL_POWER));
        ItemStack upgraded = checkNative(level, pos, new ItemStack(Items.DIAMOND_HELMET), orb,
                ManualUpgradeRecipes.ORB_UPGRADE, nativeChecks);
        checkNative(level, pos, upgraded, new ItemStack(ItemRegistry.SHRIVING_STONE.get()),
                ManualUpgradeRecipes.SHRIVING, nativeChecks);
        checkNative(level, pos, book(1), new ItemStack(ItemRegistry.LESSER_SPELL_SLOT_UPGRADE.get()),
                ManualUpgradeRecipes.BOOK_SLOT_UPGRADE, nativeChecks);
        ItemStack ring = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "affinity_ring")));
        checkNative(level, pos, ring, scroll(2), ManualUpgradeRecipes.AFFINITY_BIND, nativeChecks);
        // The dual-purpose scroll was put down first. The subsequent sword assigns it to material role.
        AscensionChamberBlockEntity chamber = fresh(level, pos);
        ItemStack firstScroll = scroll(2), sword = new ItemStack(Items.DIAMOND_SWORD);
        require(chamber.insertFromPlayer(firstScroll, false) && chamber.insertFromPlayer(sword, false)
                        && firstScroll.isEmpty() && sword.isEmpty()
                        && chamber.inventory.getStackInSlot(1).is(Items.DIAMOND_SWORD)
                        && chamber.inventory.getStackInSlot(2).is(ItemRegistry.SCROLL.get()),
                "ambiguous_native_scroll_can_be_placed_before_primary_object");
        chamber = fresh(level, pos);
        IItemHandler automation = chamber.getAutomationInventory();
        ItemStack automatedScroll = scroll(2);
        automatedScroll.getOrCreateTag().putString("role_reassignment_marker", "keep original NBT");
        ItemStack automatedSword = new ItemStack(Items.DIAMOND_SWORD);
        automatedSword.getOrCreateTag().putString("role_reassignment_marker", "keep second NBT");
        require(ItemHandlerHelper.insertItemStacked(automation, automatedScroll, false).isEmpty(),
                "generic_automation_can_place_dual_purpose_scroll_first");
        CompoundTag simulatedBefore = chamber.saveWithFullMetadata();
        require(ItemHandlerHelper.insertItemStacked(automation, automatedSword, true).isEmpty()
                        && chamber.saveWithFullMetadata().equals(simulatedBefore)
                        && automatedSword.getCount() == 1,
                "generic_automation_role_reassignment_simulation_has_no_slot_revision_timestamp_or_input_mutation");
        require(ItemHandlerHelper.insertItemStacked(automation, automatedSword, false).isEmpty()
                        && ItemStack.matches(chamber.inventory.getStackInSlot(1), automatedSword)
                        && ItemStack.matches(chamber.inventory.getStackInSlot(2), automatedScroll)
                        && chamber.getManualMaterialPlacedAt() == level.getGameTime(),
                "generic_automation_role_reassignment_preserves_both_items_and_syncs_material_timestamp");
        require(!ItemHandlerHelper.insertItemStacked(automation, automatedSword, false).isEmpty()
                        && chamber.inventory.getStackInSlot(1).getCount() == 1
                        && chamber.inventory.getStackInSlot(2).getCount() == 1,
                "generic_automation_role_reassignment_still_refuses_second_material_or_primary");
        checks.put("six_native_operations", nativeChecks);
    }

    private static ItemStack checkNative(ServerLevel level, BlockPos pos, ItemStack primary, ItemStack secondary,
                                        String kind, Map<String, Object> checks) {
        CompoundTag beforePrimary = primary.save(new CompoundTag()), beforeSecondary = secondary.save(new CompoundTag());
        var operation = ManualUpgradeRecipes.find(level, primary, secondary, -1, false, false, false).orElseThrow(
                () -> new IllegalStateException("Native fixture rejected: " + kind));
        require(kind.equals(operation.kind()) && ChamberInputRules.canPair(level, primary, secondary)
                        && primary.save(new CompoundTag()).equals(beforePrimary)
                        && secondary.save(new CompoundTag()).equals(beforeSecondary),
                kind + "_native_snapshot_and_pair_filter_leave_originals_untouched");
        AscensionChamberBlockEntity chamber = fresh(level, pos);
        IItemHandler items = chamber.getAutomationInventory();
        require(items.insertItem(2, secondary.copy(), false).isEmpty(), kind + "_secondary_first_accepted");
        CompoundTag simulated = chamber.inventory.serializeNBT();
        require(items.insertItem(1, primary.copy(), true).isEmpty() && chamber.inventory.serializeNBT().equals(simulated),
                kind + "_primary_simulation_is_read_only");
        require(items.insertItem(1, primary.copy(), false).isEmpty(), kind + "_valid_combination_accepted");
        chamber.sourceTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        require(chamber.processOneCycle() && chamber.inventory.getStackInSlot(1).isEmpty()
                        && chamber.inventory.getStackInSlot(2).isEmpty() && chamber.sourceTank.isEmpty()
                        && ItemStack.matches(chamber.inventory.getStackInSlot(3), operation.output()),
                kind + "_actual_transaction_consumes_both_originals_and_matches_native_result");
        checks.put(kind, "PASS");
        return operation.output().copy();
    }

    private static ItemStack book(int spellLevel) {
        ItemStack book = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
        var spells = ISpellContainer.create(1, true, false).mutableCopy();
        require(spells.addSpellAtIndex(SpellRegistry.FIREBOLT_SPELL.get(), spellLevel, 0, false), "book_fixture_spell_installed");
        ISpellContainer.set(book, spells.toImmutable());
        return book;
    }

    private static ItemStack scroll(int spellLevel) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(SpellRegistry.FIREBOLT_SPELL.get(), spellLevel, scroll);
        return scroll;
    }

    private static ItemStack ink(int targetLevel) {
        return new ItemStack(InkItem.getInkForRarity(SpellRegistry.FIREBOLT_SPELL.get().getRarity(targetLevel)));
    }

    private record Snapshot(BlockState state, CompoundTag tag) {}
    private static Snapshot snapshot(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return new Snapshot(level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata());
    }
    private static void restore(ServerLevel level, BlockPos pos, Snapshot snapshot) {
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, snapshot.state, 3);
        var entity = level.getBlockEntity(pos);
        if (snapshot.tag != null && entity != null) entity.load(snapshot.tag);
    }
    private static AscensionChamberBlockEntity fresh(ServerLevel level, BlockPos pos) {
        restore(level, pos.above(), new Snapshot(Blocks.AIR.defaultBlockState(), null));
        restore(level, pos, new Snapshot(ArcaneContent.CHAMBER.get().defaultBlockState(), null));
        return (AscensionChamberBlockEntity) level.getBlockEntity(pos);
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED: " + name);
    }
}
