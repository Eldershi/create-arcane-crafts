package dev.expedition.arcane.testing;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.compat.NativeScrollRules;
import dev.expedition.arcane.compat.PrintedBookProvenance;
import dev.expedition.arcane.compat.PrinterCompat;
import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.block.scroll_forge.ScrollForgeTile;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeMenu;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrinterBehaviour;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrinterBlockEntity;
import plus.dragons.createenchantmentindustry.common.fluids.printer.behaviour.PrintingBehaviour;
import plus.dragons.createenchantmentindustry.common.registry.CEIBlocks;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Real server-side CEI processing and Iron menu tests in a restored temporary area. */
public final class FactoryCompatTests {
    private static final GameProfile TEST_PROFILE = new GameProfile(
            UUID.fromString("69a45564-1e55-4c30-b13c-70211e4556cd"), "ArcaneTest");

    private FactoryCompatTests() {
    }

    public static Map<String, Object> run(ServerLevel level, BlockPos origin) {
        Map<String, Object> results = new LinkedHashMap<>();
        NativeScrollRules.verifyBoundaryRules();
        results.put("level_and_capacity_boundaries", "PASS");
        var nativeRarities = new LinkedHashMap<String, Integer>();
        var nonCommonExamples = new ArrayList<String>();
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            SpellRarity rarity = spell.getRarity(1);
            nativeRarities.merge(rarity.name(), 1, Integer::sum);
            if (rarity != SpellRarity.COMMON) nonCommonExamples.add(spell.getSpellId() + "=" + rarity.name());
        }
        results.put("native_level_one_rarity_distribution", nativeRarities);
        results.put("native_level_one_non_common_spells", nonCommonExamples);
        runPrinter(level, origin.immutable(), results);
        ServerPlayer fakePlayer = FakePlayerFactory.get(level, TEST_PROFILE);
        results.put("breakthrough", BreakthroughTests.run(fakePlayer));
        runMenus(level, origin.east(2), fakePlayer, results);
        results.put("menu_test_actor", "Forge FakePlayer; actual server menu classes; no client GUI exercised");
        return results;
    }

    /** Optional real-player entry. Uses independent menu inventories, never the player's inventory. */
    public static Map<String, Object> runPlayer(ServerPlayer player) {
        Map<String, Object> results = new LinkedHashMap<>();
        runMenus(player.serverLevel(), player.blockPosition().offset(24, 4, 0), player, results);
        results.put("menu_test_actor", "ServerPlayer with independent test Inventory; no client GUI exercised");
        return results;
    }

    private static void runPrinter(ServerLevel level, BlockPos pos, Map<String, Object> results) {
        Snapshot previous = Snapshot.capture(level, pos);
        try {
            level.setBlock(pos, CEIBlocks.PRINTER.getDefaultState(), 3);
            require(level.getBlockEntity(pos) instanceof PrinterBlockEntity, "registered_printer_block_entity");
            PrinterBlockEntity printer = (PrinterBlockEntity) level.getBlockEntity(pos);
            SmartFluidTankBehaviour tank = printer.getBehaviour(SmartFluidTankBehaviour.TYPE);
            PrinterBehaviour filter = printer.getBehaviour(PrinterBehaviour.TYPE);
            require(tank != null && filter != null, "native_printer_behaviours_available");

            ItemStack template = scroll(10);
            template.setHoverName(Component.literal("Factory Firebolt X"));
            CompoundTag templateBefore = template.save(new CompoundTag());
            PrinterCompat.verifyScrollCopy(template);
            PrintingBehaviour behaviour = PrintingBehaviour.create(level, tank, template).result()
                    .orElseThrow(() -> new IllegalStateException("Registered scroll printing provider was not selected"));
            require(behaviour.getClass().getName().startsWith(PrinterCompat.class.getName()),
                    "named_scroll_provider_has_priority_over_name_printing");
            ItemStack blanks = new ItemStack(ItemRegistry.SCROLL.get(), 2);
            FluidStack solution = new FluidStack(ArcaneContent.SOURCE.get(), 1000);
            FluidStack water = new FluidStack(Fluids.WATER, 1000);
            require(behaviour.getRequiredItemCount(level, blanks) == 1
                    && behaviour.getRequiredFluidAmount(level, blanks, solution) == 50,
                    "firebolt_10_cost_one_blank_scroll_50mb");
            ItemStack result = behaviour.getResult(level, blanks, solution);
            require(uninkedFirebolt(result) && result.getCount() == 1 && !result.hasCustomHoverName()
                            && result.hasTag() && result.getTag().getAllKeys().equals(java.util.Set.of(
                            UninkedScrollItem.SPELL_ID_TAG, UninkedScrollItem.ASSEMBLY_TAG))
                            && UninkedScrollItem.isInkingStage(result)
                            && result.getItem() instanceof SequencedAssemblyItem
                            && ((SequencedAssemblyItem) result.getItem()).getProgress(result) == 0.5F,
                    "dynamic_uninked_result_has_native_half_complete_progress_and_spell_identity_without_level_or_name");
            require(behaviour.getRequiredFluidAmount(level, blanks, water) == 0
                    && behaviour.getResult(level, blanks, water).isEmpty(), "wrong_fluid_has_no_recipe");
            require(behaviour.getResult(level, blanks, new FluidStack(ArcaneContent.SOURCE.get(), 49)).isEmpty(),
                    "insufficient_fluid_has_no_result");
            for (ItemStack rejected : new ItemStack[]{new ItemStack(Items.PAPER), scroll(1), scroll(10),
                    new ItemStack(Items.STONE), result.copy()}) {
                require(behaviour.getRequiredItemCount(level, rejected) == 0
                                && behaviour.getResult(level, rejected, solution).isEmpty(),
                        "only_empty_native_scroll_is_accepted_" + rejected.getItem());
            }
            require(PrintingBehaviour.create(level, tank, new ItemStack(ItemRegistry.SCROLL.get())).result().isEmpty(),
                    "empty_scroll_template_rejected");
            require(PrintingBehaviour.create(level, tank, scroll(11)).result().isEmpty()
                    && PrinterCompat.copyScroll(scroll(11)).isEmpty(),
                    "breakthrough_scroll_template_cannot_bypass_cake_cost_by_printing");
            for (int templateLevel : new int[]{1, 10}) {
                ItemStack levelTemplate = scroll(templateLevel);
                CompoundTag before = levelTemplate.save(new CompoundTag());
                PrintingBehaviour levelBehaviour = PrintingBehaviour.create(level, tank, levelTemplate).result().orElseThrow();
                ItemStack intermediate = levelBehaviour.getResult(level, blanks, solution);
                require(levelBehaviour.getRequiredFluidAmount(level, blanks, solution) == 50
                                && uninkedFirebolt(intermediate)
                                && UninkedScrollItem.isInkingStage(intermediate)
                                && !ISpellContainer.isSpellContainer(intermediate)
                                && sameSpell(UninkedScrollItem.finishInking(intermediate), 1)
                                && levelTemplate.save(new CompoundTag()).equals(before),
                        "template_level_" + templateLevel + "_prints_only_spell_identity_then_inks_to_level_one");
            }
            require(blanks.getCount() == 2 && solution.getAmount() == 1000 && water.getAmount() == 1000
                    && template.save(new CompoundTag()).equals(templateBefore), "recipe_queries_are_read_only");
            results.put("registered_provider_blank_scroll_50mb_uninked_identity_and_level_reset", "PASS");

            require(filter.setFilter(template), "native_printer_filter_accepts_scroll");
            CompoundTag filterBefore = filter.getFilter().save(new CompoundTag());
            AtomicReference<TransportedItemStackHandlerBehaviour.TransportedResult> captured = new AtomicReference<>();
            TransportedItemStack transported = new TransportedItemStack(blanks.copy());
            TransportedItemStackHandlerBehaviour handler = new TransportedItemStackHandlerBehaviour(printer,
                    (distance, callback) -> captured.set(callback.apply(transported)));

            tank.getPrimaryHandler().setFluid(water.copy());
            require(printer.onItemEnters(transported, handler) == BeltProcessingBehaviour.ProcessingResult.PASS,
                    "real_printer_passes_wrong_fluid");
            printer.onItemHeld(transported, handler);
            require(captured.get() == null && tank.getPrimaryHandler().getFluidAmount() == 1000
                    && transported.stack.getCount() == 2, "real_printer_wrong_fluid_no_loss");
            tank.getPrimaryHandler().setFluid(new FluidStack(ArcaneContent.SOURCE.get(), 49));
            printer.onItemHeld(transported, handler);
            require(printer.processingTicks == -1 && captured.get() == null
                    && tank.getPrimaryHandler().getFluidAmount() == 49
                    && transported.stack.getCount() == 2, "real_printer_insufficient_fluid_waits_without_loss");
            results.put("real_printer_wrong_and_insufficient_fluid_no_loss", "PASS");

            tank.getPrimaryHandler().setFluid(solution.copy());
            for (ItemStack rejected : new ItemStack[]{new ItemStack(Items.PAPER), scroll(1), scroll(10)}) {
                TransportedItemStack invalidTransport = new TransportedItemStack(rejected.copy());
                require(printer.onItemEnters(invalidTransport, handler) == BeltProcessingBehaviour.ProcessingResult.PASS
                                && tank.getPrimaryHandler().getFluidAmount() == 1000
                                && invalidTransport.stack.getCount() == rejected.getCount(),
                        "real_printer_rejects_paper_and_populated_scroll_without_loss_" + rejected.getItem());
            }
            require(printer.onItemEnters(transported, handler) == BeltProcessingBehaviour.ProcessingResult.HOLD,
                    "real_printer_holds_blank_scroll");
            printer.onItemHeld(transported, handler);
            require(printer.processingTicks == PrinterBlockEntity.PROCESSING_TIME, "real_printer_started_50_tick_cycle");
            for (int tick = 0; tick < 44; tick++) printer.tick();
            printer.onItemHeld(transported, handler);
            require(captured.get() == null && tank.getPrimaryHandler().getFluidAmount() == 1000,
                    "real_printer_waits_for_completion");
            printer.tick();
            printer.onItemHeld(transported, handler);
            var processed = captured.get();
            require(processed != null && processed.getOutputs().size() == 1
                    && uninkedFirebolt(processed.getOutputs().get(0).stack)
                    && UninkedScrollItem.isInkingStage(processed.getOutputs().get(0).stack),
                    "real_printer_produces_native_half_complete_uninked_scroll");
            require(processed.hasHeldOutput() && UninkedScrollItem.isBlankNativeScroll(processed.getHeldOutput().stack)
                    && processed.getHeldOutput().stack.getCount() == 1, "real_printer_consumes_only_one_blank_scroll");
            require(tank.getPrimaryHandler().getFluidAmount() == 950, "real_printer_pays_exactly_50mb");
            require(filter.getFilter().save(new CompoundTag()).equals(filterBefore)
                    && template.save(new CompoundTag()).equals(templateBefore), "real_printer_keeps_template");
            results.put("real_printer_native_cycle_blank_scroll_50mb_uninked_output_and_template", "PASS");
        } finally {
            previous.restore(level, pos);
        }
    }

    private static void runMenus(ServerLevel level, BlockPos pos, ServerPlayer actor, Map<String, Object> results) {
        ArcaneAnvilMenu anvil = new ArcaneAnvilMenu(71, new Inventory(actor), ContainerLevelAccess.create(level, pos));
        anvil.getSlot(0).set(scroll(1));
        anvil.getSlot(1).set(inkForLevel(2));
        anvil.createResult();
        require(sameSpell(anvil.getSlot(2).getItem(), 2), "native_anvil_1_to_2_still_works");
        results.put("native_anvil_low_level_upgrade_preserved", "PASS");

        ItemStack printedInput = UninkedScrollItem.finishInking(UninkedScrollItem.create(SpellRegistry.FIREBOLT_SPELL.get()));
        anvil.getSlot(0).set(printedInput.copy());
        anvil.getSlot(1).set(inkForLevel(2));
        anvil.createResult();
        require(sameSpell(anvil.getSlot(2).getItem(), 2)
                        && UninkedScrollItem.isPrintedScroll(anvil.getSlot(2).getItem())
                        && UninkedScrollItem.isPrintedScroll(anvil.getSlot(0).getItem()),
                "native_anvil_upgrade_preserves_printed_provenance");
        results.put("native_anvil_upgrade_retains_printed_recycling_guard", "PASS");

        // Seeded stale output must disappear when the next input would require industry.
        anvil.getSlot(0).set(scroll(4));
        anvil.getSlot(1).set(inkForLevel(5));
        anvil.getSlot(2).set(scroll(2));
        anvil.createResult();
        require(anvil.getSlot(2).getItem().isEmpty() && sameSpell(anvil.getSlot(0).getItem(), 4)
                && anvil.getSlot(1).getItem().getCount() == 1, "native_anvil_4_to_5_rejected_and_stale_result_cleared");
        results.put("native_anvil_high_level_gate_and_no_input_loss", "PASS");

        int blockedBreakthroughs = 0;
        for (AbstractSpell spell : new AbstractSpell[]{SpellRegistry.COUNTERSPELL_SPELL.get(),
                SpellRegistry.PORTAL_SPELL.get(), SpellRegistry.TELEPORT_SPELL.get(), SpellRegistry.FIREBOLT_SPELL.get()}) {
            for (int addition = 0; addition <= 3; addition++) {
                ItemStack breakthroughInput = new ItemStack(ItemRegistry.SCROLL.get());
                ISpellContainer.createScrollContainer(spell, spell.getMaxLevel() + addition, breakthroughInput);
                anvil.getSlot(0).set(breakthroughInput);
                anvil.getSlot(1).set(new ItemStack(InkItem.getInkForRarity(SpellRarity.LEGENDARY)));
                anvil.getSlot(2).set(scroll(2));
                anvil.createResult();
                require(anvil.getSlot(2).getItem().isEmpty()
                                && ISpellContainer.get(anvil.getSlot(0).getItem()).getSpellAtIndex(0).getLevel()
                                == spell.getMaxLevel() + addition
                                && anvil.getSlot(1).getItem().getCount() == 1,
                        "native_anvil_breakthrough_gate_" + spell.getSpellId() + "_" + addition);
                blockedBreakthroughs++;
            }
        }
        results.put("native_anvil_breakthrough_gate_and_stale_output", "PASS: " + blockedBreakthroughs + " levels");

        AbstractSpell portal = SpellRegistry.PORTAL_SPELL.get();
        for (int current = 1; current < portal.getMaxLevel(); current++) {
            ItemStack input = new ItemStack(ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(portal, current, input);
            anvil.getSlot(0).set(input);
            anvil.getSlot(1).set(new ItemStack(InkItem.getInkForRarity(portal.getRarity(current + 1))));
            anvil.createResult();
            var result = NativeScrollRules.readValidScroll(anvil.getSlot(2).getItem());
            require(result.isPresent() && result.get().getSpell().equals(portal)
                    && result.get().getLevel() == current + 1, "native_low_max_spell_upgrade_preserved");
        }
        results.put("native_anvil_portal_low_levels_preserved", "PASS");

        anvil.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        anvil.getSlot(1).set(scroll(8));
        anvil.createResult();
        ItemStack sword = anvil.getSlot(2).getItem();
        ISpellContainer imbued = ISpellContainer.get(sword);
        require(sword.is(Items.DIAMOND_SWORD) && imbued != null
                && imbued.getSpellAtIndex(0).getLevel() == 8
                && imbued.getSpellAtIndex(0).getSpell().equals(SpellRegistry.FIREBOLT_SPELL.get()),
                "native_anvil_equipment_imbuing_preserved");
        results.put("native_anvil_equipment_function_preserved", "PASS");

        runInscription(level, pos.east(), actor, results);

        Snapshot previous = Snapshot.capture(level, pos);
        ScrollForgeTile tile = null;
        try {
            level.setBlock(pos, BlockRegistry.SCROLL_FORGE_BLOCK.get().defaultBlockState(), 3);
            require(level.getBlockEntity(pos) instanceof ScrollForgeTile, "registered_writing_table_tile");
            tile = (ScrollForgeTile) level.getBlockEntity(pos);
            ScrollForgeMenu menu = (ScrollForgeMenu) tile.createMenu(72, new Inventory(actor), actor);
            AbstractSpell spell = SpellRegistry.FIREBOLT_SPELL.get();
            ItemStack focus = ForgeRegistries.ITEMS.getValues().stream().map(ItemStack::new)
                    .filter(spell.getSchoolType()::isFocus).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Fire school focus tag is empty"));
            menu.getBlankScrollSlot().set(new ItemStack(Items.PAPER));
            menu.getFocusSlot().set(focus.copyWithCount(1));
            menu.getInkSlot().set(inkForLevel(1));
            menu.setRecipeSpell(spell);
            require(sameSpell(menu.getResultSlot().getItem(), 1), "native_writing_low_level_still_works");
            results.put("native_writing_low_level_preserved", "PASS");

            int blockedRarities = 0;
            for (SpellRarity rarity : SpellRarity.values()) {
                int targetLevel = spell.getMinLevelForRarity(rarity);
                if (targetLevel < 5 || targetLevel > spell.getMaxLevel()) continue;
                menu.getInkSlot().set(new ItemStack(InkItem.getInkForRarity(rarity)));
                menu.getResultSlot().set(scroll(2));
                menu.setRecipeSpell(spell);
                require(menu.getResultSlot().getItem().isEmpty(), "writing_target_" + targetLevel + "_rejected");
                require(menu.getBlankScrollSlot().getItem().getCount() == 1
                        && menu.getInkSlot().getItem().getCount() == 1
                        && menu.getFocusSlot().getItem().getCount() == 1, "writing_gate_keeps_inputs");
                blockedRarities++;
            }
            require(blockedRarities > 0, "writing_test_has_high_level_fixtures");
            results.put("native_writing_high_level_gate_and_stale_output", "PASS: " + blockedRarities + " ink rarities");
        } finally {
            // Native table removal drops its contents, so clear our fixture before restoring the old block.
            if (tile != null) for (int slot = 0; slot < tile.getItemHandler().getSlots(); slot++) {
                tile.getItemHandler().setStackInSlot(slot, ItemStack.EMPTY);
            }
            previous.restore(level, pos);
        }
    }

    private static void runInscription(ServerLevel level, BlockPos pos, ServerPlayer actor,
                                       Map<String, Object> results) {
        // The native menu owns a separate test Inventory. Replacing its book slot
        // does not alter a book the actor may have equipped in a Curios slot.
        InscriptionTableMenu menu = new InscriptionTableMenu(73, new Inventory(actor),
                ContainerLevelAccess.create(level, pos));
        ItemStack book = new ItemStack(ItemRegistry.IRON_SPELL_BOOK.get());
        ISpellContainer.set(book, ISpellContainer.create(3, false, false));
        menu.getSpellBookSlot().set(book);
        AbstractSpell spell = SpellRegistry.COUNTERSPELL_SPELL.get();
        require(spell.getRarity(1) != SpellRarity.COMMON,
                "inscription_recycle_fixture_has_non_common_native_level_one_ink");
        ItemStack printed = UninkedScrollItem.finishInking(UninkedScrollItem.create(spell));
        require(UninkedScrollItem.isPrintedScroll(printed), "inscription_fixture_is_printed");
        menu.getScrollSlot().set(printed.copy());
        menu.doInscription(0);
        require(menu.getScrollSlot().getItem().isEmpty()
                        && PrintedBookProvenance.spellAt(book, 0).filter(data -> data.getSpell().equals(spell)
                        && data.getLevel() == 1).isPresent()
                        && PrintedBookProvenance.isPrintedSpell(book, 0)
                        && !PrintedBookProvenance.isPrintedSpell(book, 1),
                "native_inscription_commits_printed_provenance_to_only_the_written_slot");

        CompoundTag bookBeforePreview = book.save(new CompoundTag());
        for (int repeat = 0; repeat < 3; repeat++) {
            menu.setSelectedSpell(0);
            ItemStack preview = menu.getResultSlot().getItem();
            require(UninkedScrollItem.isPrintedScroll(preview)
                            && NativeScrollRules.readValidScroll(preview).filter(data -> data.getSpell().equals(spell)
                            && data.getLevel() == 1).isPresent()
                            && PrintedBookProvenance.isPrintedSpell(book, 0)
                            && book.save(new CompoundTag()).equals(bookBeforePreview),
                    "native_inscription_preview_and_provenance_queries_do_not_mutate_book");
        }

        ItemStack blocked = scroll(1);
        menu.getScrollSlot().set(blocked.copy());
        CompoundTag blockedBefore = menu.getScrollSlot().getItem().save(new CompoundTag());
        menu.doInscription(0);
        require(menu.getScrollSlot().getItem().save(new CompoundTag()).equals(blockedBefore)
                        && book.save(new CompoundTag()).equals(bookBeforePreview)
                        && PrintedBookProvenance.isPrintedSpell(book, 0),
                "failed_inscription_into_occupied_slot_keeps_scroll_book_and_provenance");
        menu.getScrollSlot().set(ItemStack.EMPTY);
        menu.setSelectedSpell(0);
        ItemStack recovered = menu.getResultSlot().remove(1);
        menu.getResultSlot().onTake(actor, recovered);
        require(UninkedScrollItem.isPrintedScroll(recovered)
                        && PrintedBookProvenance.spellAt(book, 0).isEmpty()
                        && !PrintedBookProvenance.isPrintedSpell(book, 0)
                        && (!book.hasTag() || !book.getTag().contains(PrintedBookProvenance.BOOK_TAG)),
                "native_inscription_real_take_restores_printed_flag_and_clears_only_committed_record");

        Snapshot previous = Snapshot.capture(level, pos);
        try {
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("irons_spellbooks", "alchemist_cauldron"));
            require(block != null, "inscription_recycle_native_cauldron_registered");
            level.setBlock(pos, block.defaultBlockState(), 3);
            require(level.getBlockEntity(pos) instanceof AlchemistCauldronTile,
                    "inscription_recycle_native_cauldron_tile");
            AlchemistCauldronTile cauldron = (AlchemistCauldronTile) level.getBlockEntity(pos);
            cauldron.fluidInventory.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
            require(recovered.getCount() == 1 && cauldron.isValidInput(recovered),
                    "book_recovered_printed_scroll_preserves_native_recycling_eligibility");
            var printedInk = AlchemistCauldronTile.getInkFromScroll(recovered).fluid().get();
            cauldron.tryMeltInput(recovered);
            FluidStack printedRecycle = cauldron.fluidInventory.drain(250, IFluidHandler.FluidAction.SIMULATE);
            require(recovered.isEmpty() && printedRecycle.getAmount() == 250
                            && (printedRecycle.getFluid().isSame(Fluids.WATER)
                            || printedRecycle.getFluid().isSame(printedInk)),
                    "book_recovered_printed_scroll_uses_native_random_recycle_transaction");
            cauldron.fluidInventory.drain(4000, IFluidHandler.FluidAction.EXECUTE);
            cauldron.fluidInventory.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);

            menu.getScrollSlot().set(scroll(1));
            menu.doInscription(0);
            require(menu.getScrollSlot().getItem().isEmpty()
                            && !PrintedBookProvenance.isPrintedSpell(book, 0),
                    "wild_scroll_written_to_previously_printed_slot_is_not_marked");
            CompoundTag wildBookBeforePreview = book.save(new CompoundTag());
            menu.setSelectedSpell(1);
            require(menu.getResultSlot().getItem().isEmpty()
                            && book.save(new CompoundTag()).equals(wildBookBeforePreview),
                    "empty_inscription_slot_preview_does_not_change_book_or_keep_stale_output");
            menu.setSelectedSpell(0);
            require(sameSpell(menu.getResultSlot().getItem(), 1)
                            && !UninkedScrollItem.isPrintedScroll(menu.getResultSlot().getItem())
                            && book.save(new CompoundTag()).equals(wildBookBeforePreview),
                    "wild_inscription_preview_is_unmarked_and_read_only");
            ItemStack wildRecovered = menu.getResultSlot().remove(1);
            menu.getResultSlot().onTake(actor, wildRecovered);
            require(sameSpell(wildRecovered, 1) && !UninkedScrollItem.isPrintedScroll(wildRecovered)
                            && PrintedBookProvenance.spellAt(book, 0).isEmpty()
                            && cauldron.isValidInput(wildRecovered),
                    "wild_scroll_book_round_trip_preserves_native_recycling_eligibility");
            var expectedInk = AlchemistCauldronTile.getInkFromScroll(wildRecovered).fluid().get();
            cauldron.tryMeltInput(wildRecovered);
            FluidStack afterRecycle = cauldron.fluidInventory.drain(250, IFluidHandler.FluidAction.SIMULATE);
            require(wildRecovered.isEmpty() && afterRecycle.getAmount() == 250
                            && (afterRecycle.getFluid().isSame(Fluids.WATER)
                            || afterRecycle.getFluid().isSame(expectedInk)),
                    "wild_scroll_book_round_trip_still_uses_native_random_recycle_transaction");
        } finally {
            previous.restore(level, pos);
            menu.getSpellBookSlot().set(ItemStack.EMPTY);
            menu.getScrollSlot().set(ItemStack.EMPTY);
            menu.getResultSlot().set(ItemStack.EMPTY);
        }
        results.put("native_inscription_printed_provenance_preview_failure_and_real_take", "PASS");
        results.put("native_inscription_roundtrip_preserves_printed_and_wild_native_recycling", "PASS");
    }

    private static ItemStack scroll(int level) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(SpellRegistry.FIREBOLT_SPELL.get(), level, scroll);
        return scroll;
    }

    /** A real provider query using native printer/tank objects, without placing another world block. */
    static ItemStack printIntermediate(ServerLevel level, ItemStack template) {
        var state = CEIBlocks.PRINTER.getDefaultState();
        PrinterBlockEntity printer = (PrinterBlockEntity) CEIBlocks.PRINTER.get().newBlockEntity(BlockPos.ZERO, state);
        require(printer != null, "isolated_native_printer_fixture_exists");
        printer.setLevel(level);
        SmartFluidTankBehaviour tank = SmartFluidTankBehaviour.single(printer, 1000);
        PrintingBehaviour printing = PrintingBehaviour.create(level, tank, template).result().orElseThrow();
        ItemStack blank = new ItemStack(ItemRegistry.SCROLL.get());
        FluidStack blue = new FluidStack(ArcaneContent.SOURCE.get(), 50);
        CompoundTag sampleBefore = template.save(new CompoundTag());
        ItemStack result = printing.getResult(level, blank, blue);
        require(UninkedScrollItem.isInkingStage(result) && blank.getCount() == 1 && blue.getAmount() == 50
                        && template.save(new CompoundTag()).equals(sampleBefore),
                "isolated_provider_query_runs_native_sequence_without_mutating_inputs");
        return result;
    }

    private static ItemStack inkForLevel(int target) {
        return new ItemStack(InkItem.getInkForRarity(SpellRegistry.FIREBOLT_SPELL.get().getRarity(target)));
    }

    private static boolean sameSpell(ItemStack stack, int level) {
        return NativeScrollRules.readValidScroll(stack).map(data -> data.getLevel() == level
                && data.getSpell().equals(SpellRegistry.FIREBOLT_SPELL.get())).orElse(false);
    }

    private static boolean uninkedFirebolt(ItemStack stack) {
        return stack.is(ArcaneContent.UNINKED_SCROLL.get())
                && UninkedScrollItem.readSpell(stack).filter(SpellRegistry.FIREBOLT_SPELL.get()::equals).isPresent();
    }

    private static void require(boolean condition, String check) {
        if (!condition) throw new IllegalStateException("Factory compatibility test failed: " + check);
    }

    private record Snapshot(BlockState state, CompoundTag blockEntity) {
        static Snapshot capture(ServerLevel level, BlockPos pos) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                throw new IllegalArgumentException("Factory tests require a fixture position without an existing block entity: " + pos);
            }
            return new Snapshot(level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata());
        }

        void restore(ServerLevel level, BlockPos pos) {
            level.setBlock(pos, state, 3);
            BlockEntity restored = level.getBlockEntity(pos);
            if (blockEntity != null && restored != null) {
                restored.load(blockEntity);
                restored.setChanged();
            }
        }
    }
}
