package dev.expedition.arcane.testing;

import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import com.simibubi.create.AllRecipeTypes;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.compat.NativeScrollRules;
import dev.expedition.arcane.compat.AssemblyPrintingRecipe;
import dev.expedition.arcane.compat.ScrollInkingRecipe;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

/** Real native recipe dispatch and fluid transactions, using temporary game objects. */
public final class FluidRecipeTests {
    private FluidRecipeTests() {}

    public static Map<String,Object> run(ServerLevel level, BlockPos pos) {
        var results = new LinkedHashMap<String,Object>();
        for (String path : List.of("mix_arcane_solution", "mix_blood_solution", "mix_common_ink",
                "mix_uncommon_ink", "mix_rare_ink", "mix_epic_ink", "mix_legendary_ink",
                "brew_arcane_solution", "brew_blood_solution", "ascension_template", "ascension_chamber", "mana_supply_upgrade",
                "mix_crimson_solution", "craft_crimson_solution_bottle", "sequenced_assembly/ember_cake",
                "craft_blank_scroll", "sequenced_assembly/printed_scroll")) {
            require(level.getRecipeManager().byKey(id(path)).isPresent(), "loaded recipe " + path);
        }
        results.put("custom_mixing_brewing_and_crafting_recipes_loaded", "PASS");
        craft(level, "craft_blank_scroll", new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get()),
                Items.AIR, 0, new ItemStack(Items.PAPER), new ItemStack(Items.STRING));
        results.put("paper_and_string_craft_one_empty_native_scroll_without_spell_or_remainders", "PASS");
        var goldSheet = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create", "golden_sheet"));
        var copperSheet = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create", "copper_sheet"));
        var blazeTemplate = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create_dragons_plus", "blaze_upgrade_smithing_template"));
        require(goldSheet != null && goldSheet != Items.AIR && copperSheet != null && copperSheet != Items.AIR
                        && blazeTemplate != null && blazeTemplate != Items.AIR,
                "native_plate_and_blaze_upgrade_template_items_registered");
        craft(level, "ascension_template", new ItemStack(ArcaneContent.TEMPLATE.get()), Items.AIR, 0,
                new ItemStack(goldSheet), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(goldSheet),
                new ItemStack(copperSheet), new ItemStack(blazeTemplate), new ItemStack(copperSheet),
                new ItemStack(goldSheet), new ItemStack(copperSheet), new ItemStack(goldSheet));
        results.put("ascension_template_native_nine_slot_gold_amethyst_copper_blaze_upgrade_recipe", "PASS");
        var andesiteAlloy = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create", "andesite_alloy"));
        var blankRune = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "blank_rune"));
        var upgradeBase = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sophisticatedbackpacks", "upgrade_base"));
        var arcaneEssence = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "arcane_essence"));
        require(andesiteAlloy != null && andesiteAlloy != Items.AIR && blankRune != null && blankRune != Items.AIR
                        && upgradeBase != null && upgradeBase != Items.AIR && arcaneEssence != null && arcaneEssence != Items.AIR,
                "native_mana_upgrade_ingredients_registered");
        craft(level, "mana_supply_upgrade", new ItemStack(ArcaneContent.MANA_UPGRADE.get()), Items.AIR, 0,
                new ItemStack(andesiteAlloy), new ItemStack(blankRune), new ItemStack(andesiteAlloy),
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(upgradeBase), new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(andesiteAlloy), new ItemStack(arcaneEssence), new ItemStack(andesiteAlloy));
        results.put("mana_upgrade_native_nine_slot_andesite_corners_amethyst_sides_recipe", "PASS");
        for (SpellRarity rarity : SpellRarity.values()) {
            InkItem ink = InkItem.getInkForRarity(rarity);
            roundTrip(level, new FluidStack(ink.fluid().get(), 250), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(ink));
        }
        results.put("all_five_native_ink_fluids_spout_bottle_and_drain_roundtrip", "PASS");
        runScrollInking(level, results);
        roundTrip(level, new FluidStack(ArcaneContent.SOURCE.get(), 250), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(ArcaneContent.BOTTLE.get()));
        roundTrip(level, new FluidStack(ArcaneContent.SOURCE.get(), 1000), new ItemStack(Items.BUCKET), new ItemStack(ArcaneContent.BUCKET.get()));
        results.put("solution_bottle_and_bucket_spout_drain_roundtrip", "PASS");
        roundTrip(level, new FluidStack(CrimsonContent.SOURCE.get(), 250), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(CrimsonContent.BOTTLE.get()));
        roundTrip(level, new FluidStack(CrimsonContent.SOURCE.get(), 1000), new ItemStack(Items.BUCKET), new ItemStack(CrimsonContent.BUCKET.get()));
        results.put("crimson_bottle_and_bucket_spout_drain_roundtrip", "PASS");
        for (boolean crimson : new boolean[]{false, true}) {
            var bottle = crimson ? CrimsonContent.BOTTLE.get() : ArcaneContent.BOTTLE.get();
            var bucket = crimson ? CrimsonContent.BUCKET.get() : ArcaneContent.BUCKET.get();
            String prefix = crimson ? "crimson" : "arcane";
            craft(level, "craft_" + prefix + "_solution_bucket", new ItemStack(bucket), Items.GLASS_BOTTLE, 4,
                    new ItemStack(bottle), new ItemStack(bottle), new ItemStack(bottle), new ItemStack(bottle), new ItemStack(Items.BUCKET));
            craft(level, "craft_" + prefix + "_solution_bottles", new ItemStack(bottle, 4), Items.BUCKET, 1,
                    new ItemStack(bucket), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.GLASS_BOTTLE));
        }
        craft(level, "craft_crimson_solution_bottle", new ItemStack(CrimsonContent.BOTTLE.get()), Items.AIR, 0,
                new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "blood_vial"))),
                new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "arcane_essence"))));
        results.put("four_bottles_equal_bucket_crafting_and_empty_container_conservation", "PASS");
        runEmberCakeAssembly(level, results);
        var bottomless = TagKey.create(Registries.FLUID, new ResourceLocation("create", "bottomless/allow"));
        for (var fluid : List.of(ArcaneContent.SOURCE.get(), ArcaneContent.FLOWING.get(), CrimsonContent.SOURCE.get(), CrimsonContent.FLOWING.get())) {
            require(!fluid.canConvertToSource(fluid.defaultFluidState(), level, pos), "no adjacent source duplication");
            require(fluid.defaultFluidState().is(bottomless), "hose pulley bottomless allow tag");
        }
        results.put("no_source_conversion_and_create_bottomless_allow_tag", "PASS");

        var previous = level.getBlockState(pos);
        var previousEntity = level.getBlockEntity(pos);
        var previousTag = previousEntity == null ? null : previousEntity.saveWithFullMetadata();
        try {
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("irons_spellbooks", "alchemist_cauldron"));
            require(block != null, "native cauldron registered");
            level.setBlock(pos, block.defaultBlockState(), 3);
            var cauldron = (AlchemistCauldronTile)level.getBlockEntity(pos);
            var essence = ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "arcane_essence"));
            for (boolean blood : new boolean[]{false, true}) {
                cauldron.fluidInventory.drain(4000, IFluidHandler.FluidAction.EXECUTE);
                var inputFluid = blood ? ForgeRegistries.FLUIDS.getValue(new ResourceLocation("irons_spellbooks", "blood")) : Fluids.WATER;
                cauldron.fluidInventory.fill(new FluidStack(inputFluid, 250), IFluidHandler.FluidAction.EXECUTE);
                var reagent = new ItemStack(essence);
                cauldron.tryMeltInput(reagent);
                int expected = 250;
                require(reagent.isEmpty(), "native brewing consumed one essence");
                var fromPipe = cauldron.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).orElseThrow(() -> new IllegalStateException("cauldron fluid capability"));
                var drained = fromPipe.drain(expected, IFluidHandler.FluidAction.EXECUTE);
                require(drained.getFluid() == (blood ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get()) && drained.getAmount() == expected && cauldron.getFluidAmount() == 0,
                        "native cauldron brew yield and pipe extraction");
            }
            results.put("native_water_and_blood_brewing_with_pipe_extraction", "PASS");
            cauldron.fluidInventory.fill(new FluidStack(ArcaneContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
            var bottled = cauldron.tryExecuteRecipeInteractions(level, new ItemStack(Items.GLASS_BOTTLE));
            require(bottled.is(ArcaneContent.BOTTLE.get()) && cauldron.getFluidAmount() == 750, "cauldron bottle extraction");
            var glass = cauldron.tryExecuteRecipeInteractions(level, bottled);
            require(glass.is(Items.GLASS_BOTTLE) && cauldron.getFluidAmount() == 1000, "cauldron bottle filling");
            var bucket = cauldron.tryExecuteRecipeInteractions(level, new ItemStack(Items.BUCKET));
            require(bucket.is(ArcaneContent.BUCKET.get()) && cauldron.getFluidAmount() == 0, "cauldron bucket extraction");
            var emptyBucket = cauldron.tryExecuteRecipeInteractions(level, bucket);
            require(emptyBucket.is(Items.BUCKET) && cauldron.getFluidAmount() == 1000, "cauldron bucket filling");
            results.put("native_cauldron_bottle_bucket_roundtrip", "PASS");
            cauldron.fluidInventory.drain(4000, IFluidHandler.FluidAction.EXECUTE);
            cauldron.fluidInventory.fill(new FluidStack(CrimsonContent.SOURCE.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
            var redBottle = cauldron.tryExecuteRecipeInteractions(level, new ItemStack(Items.GLASS_BOTTLE));
            require(redBottle.is(CrimsonContent.BOTTLE.get()) && cauldron.getFluidAmount() == 750, "crimson cauldron bottle extraction");
            require(cauldron.tryExecuteRecipeInteractions(level, redBottle).is(Items.GLASS_BOTTLE) && cauldron.getFluidAmount() == 1000, "crimson cauldron bottle filling");
            var redBucket = cauldron.tryExecuteRecipeInteractions(level, new ItemStack(Items.BUCKET));
            require(redBucket.is(CrimsonContent.BUCKET.get()) && cauldron.getFluidAmount() == 0, "crimson cauldron bucket extraction");
            require(cauldron.tryExecuteRecipeInteractions(level, redBucket).is(Items.BUCKET) && cauldron.getFluidAmount() == 1000, "crimson cauldron bucket filling");
            results.put("native_crimson_cauldron_bottle_bucket_roundtrip", "PASS");
            runPrintedNativeRecycling(level, cauldron, results);
        } finally {
            level.setBlock(pos, previous, 3);
            if (previousTag != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(previousTag);
        }
        results.put("status", "PASS");
        return results;
    }

    private static void roundTrip(ServerLevel level, FluidStack fluid, ItemStack empty, ItemStack expected) {
        var available = fluid.copy();
        var input = empty.copy();
        int cost = FillingBySpout.getRequiredAmountForItem(level, input, available);
        require(cost == fluid.getAmount(), "spout fluid amount for " + expected);
        var result = FillingBySpout.fillItem(level, cost, input, available);
        require(ItemStack.isSameItemSameTags(result, expected) && input.isEmpty() && available.isEmpty(), "spout result and material balance");
        var emptied = GenericItemEmptying.emptyItem(level, result, false);
        require(emptied.getFirst().isFluidEqual(fluid) && emptied.getFirst().getAmount() == fluid.getAmount()
            && emptied.getSecond().is(empty.getItem()) && result.isEmpty(), "drain roundtrip balance");
    }

    private static void runEmberCakeAssembly(ServerLevel level, Map<String, Object> results) {
        var loaded = level.getRecipeManager().byKey(id("sequenced_assembly/ember_cake")).orElseThrow();
        require(loaded instanceof SequencedAssemblyRecipe, "cake uses the actual Create sequenced assembly recipe type");
        SequencedAssemblyRecipe assembly = (SequencedAssemblyRecipe) loaded;
        require(assembly.getLoops() == 1 && assembly.getSequence().size() == 4
                        && assembly.getIngredient().test(new ItemStack(Items.EGG))
                        && assembly.getTransitionalItem().is(CrimsonContent.EMBER_CAKE_BASE.get())
                        && CrimsonContent.EMBER_CAKE_BASE.get() instanceof SequencedAssemblyItem,
                "cake one-loop four-step assembly uses egg and native progress-bar intermediate");
        var expectedTypes = List.of(AllRecipeTypes.DEPLOYING.getType(), AllRecipeTypes.DEPLOYING.getType(),
                AllRecipeTypes.PRESSING.getType(), AllRecipeTypes.FILLING.getType());
        for (int index = 0; index < 4; index++) {
            var step = assembly.getSequence().get(index).getRecipe();
            require(step.getType() == expectedTypes.get(index)
                            && step instanceof IAssemblyRecipe && ((IAssemblyRecipe) step).supportsAssembly(),
                    "cake step " + index + " is a native assembly-compatible operation");
        }
        require(level.getRecipeManager().byKey(id("press_ember_cake_base")).isEmpty()
                        && level.getRecipeManager().byKey(id("fill_ember_cake")).isEmpty(),
                "old independent cake recipes cannot bypass the assembly sequence");

        ItemStack stage = new ItemStack(Items.EGG);
        ItemStack sugar = new ItemStack(Items.SUGAR, 2);
        ItemStack cinder = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.CINDER_ESSENCE.get(), 2);
        for (int index = 0; index < 2; index++) {
            var inventory = new ItemStackHandler(2);
            inventory.setStackInSlot(0, stage);
            inventory.setStackInSlot(1, index == 0 ? cinder.copy() : sugar.copy());
            var wrapper = new RecipeWrapper(inventory);
            CompoundTag stageBefore = stage.save(new CompoundTag());
            CompoundTag wrongBefore = inventory.getStackInSlot(1).save(new CompoundTag());
            require(SequencedAssemblyRecipe.getRecipe(level, wrapper, AllRecipeTypes.DEPLOYING.getType(),
                            DeployerApplicationRecipe.class).isEmpty()
                            && stage.save(new CompoundTag()).equals(stageBefore)
                            && inventory.getStackInSlot(1).save(new CompoundTag()).equals(wrongBefore),
                    "cake deployer rejects wrong ingredient without changing stage or reagent " + index);
            ItemStack held = index == 0 ? sugar : cinder;
            inventory.setStackInSlot(1, held);
            DeployerApplicationRecipe deploy = SequencedAssemblyRecipe.getRecipe(level, wrapper,
                    AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).orElseThrow();
            require(!deploy.shouldKeepHeldItem() && stage.save(new CompoundTag()).equals(stageBefore)
                            && held.getCount() == 2,
                    "cake native deployer lookup is read-only and reagent is consumed rather than reusable " + index);
            var output = RecipeApplier.applyRecipeOn(level, stage, deploy, false);
            require(output != null && output.size() == 1 && output.get(0).getCount() == 1,
                    "cake native deployer recipe application produces one intermediate " + index);
            // The native recipe declares one non-reusable held ingredient. The
            // deployer's hand consumes it after RecipeApplier replaces the belt item.
            held.shrink(1);
            stage = output.get(0);
            assertCakeStage(stage, index + 1);
            require(!FillingBySpout.canItemBeFilled(level, stage),
                    "cake cannot skip unfinished deploying or pressing to the spout " + index);
        }
        require(sugar.getCount() == 1 && cinder.getCount() == 1,
                "cake sequence requires exactly one sugar and one cinder essence");

        var pressInventory = new ItemStackHandler(1);
        pressInventory.setStackInSlot(0, stage);
        var pressWrapper = new RecipeWrapper(pressInventory);
        PressingRecipe pressing = SequencedAssemblyRecipe.getRecipe(level, pressWrapper,
                AllRecipeTypes.PRESSING.getType(), PressingRecipe.class).orElseThrow();
        var pressed = RecipeApplier.applyRecipeOn(level, stage, pressing, false);
        require(pressed != null && pressed.size() == 1, "cake native press produces one stage");
        stage = pressed.get(0);
        assertCakeStage(stage, 3);

        ItemStack plainBase = new ItemStack(CrimsonContent.EMBER_CAKE_BASE.get());
        var red = new FluidStack(CrimsonContent.SOURCE.get(), 1000);
        require(!FillingBySpout.canItemBeFilled(level, plainBase)
                        && FillingBySpout.getRequiredAmountForItem(level, plainBase, red) < 0,
                "bare creative cake base cannot skip the recipe's assembly progress");
        require(FillingBySpout.canItemBeFilled(level, stage)
                        && FillingBySpout.getRequiredAmountForItem(level, stage, red) == 1000,
                "pressed cake stage reaches the actual sequenced spout operation");
        var shortRed = new FluidStack(CrimsonContent.SOURCE.get(), 999);
        CompoundTag beforeShortQuote = stage.save(new CompoundTag());
        int quoted = FillingBySpout.getRequiredAmountForItem(level, stage, shortRed);
        require(quoted == 1000 && quoted > shortRed.getAmount() && shortRed.getAmount() == 999
                        && stage.save(new CompoundTag()).equals(beforeShortQuote),
                "native spout quote waits for a full bucket without consuming its short supply");
        for (FluidStack invalid : List.of(new FluidStack(ArcaneContent.SOURCE.get(), 1000),
                new FluidStack(Fluids.WATER, 1000))) {
            ItemStack unchanged = stage.copy();
            CompoundTag stageBefore = unchanged.save(new CompoundTag());
            CompoundTag fluidBefore = invalid.writeToNBT(new CompoundTag());
            // Native SpoutBlockEntity checks this quote before invoking fillItem;
            // GenericItemFilling's commit method trusts that caller-side gate.
            int invalidQuote = FillingBySpout.getRequiredAmountForItem(level, unchanged, invalid);
            require(invalidQuote < 0 && unchanged.save(new CompoundTag()).equals(stageBefore)
                            && invalid.writeToNBT(new CompoundTag()).equals(fluidBefore),
                    "cake wrong fluid is rejected by the native spout quote without consuming materials");
        }
        ItemStack cake = FillingBySpout.fillItem(level, 1000, stage, red);
        require(cake.is(CrimsonContent.EMBER_CAKE.get()) && cake.getCount() == 1
                        && !cake.isEdible() && stage.isEmpty() && red.isEmpty()
                        && (!cake.hasTag() || !cake.getTag().contains("SequencedAssembly")),
                "cake final native spout consumes one stage and 1000mB red and ends the sequence");
        results.put("ember_cake_native_four_step_assembly_progress_and_save_roundtrip", "PASS");
        results.put("ember_cake_egg_sugar_cinder_one_each_and_spout_1000mB_no_skip", "PASS");
    }

    private static void assertCakeStage(ItemStack stage, int step) {
        require(stage.is(CrimsonContent.EMBER_CAKE_BASE.get()) && stage.getCount() == 1 && stage.hasTag(),
                "cake stage is a single real transitional item " + step);
        CompoundTag progress = stage.getTag().getCompound("SequencedAssembly");
        require(progress.getString("id").equals(id("sequenced_assembly/ember_cake").toString())
                        && progress.getInt("Step") == step
                        && Math.abs(progress.getFloat("Progress") - step / 4F) < 0.0001F
                        && Math.abs(((SequencedAssemblyItem) stage.getItem()).getProgress(stage) - step / 4F) < 0.0001F,
                "cake native NBT progress matches completed steps " + step);
        ItemStack loaded = ItemStack.of(stage.save(new CompoundTag()));
        require(ItemStack.isSameItemSameTags(stage, loaded), "cake native assembly progress survives NBT save/load " + step);
    }

    private static void runScrollInking(ServerLevel level, Map<String, Object> results) {
        var fireboltRarity = SpellRegistry.FIREBOLT_SPELL.get().getRarity(1);
        var requiredInk = InkItem.getInkForRarity(fireboltRarity).fluid().get();
        var loaded = level.getRecipeManager().byKey(UninkedScrollItem.ASSEMBLY_ID).orElseThrow();
        require(loaded instanceof SequencedAssemblyRecipe, "scroll printing uses native sequenced assembly type");
        SequencedAssemblyRecipe assembly = (SequencedAssemblyRecipe) loaded;
        require(assembly.getLoops() == 1 && assembly.getSequence().size() == 2
                        && assembly.getSequence().get(0).getRecipe() instanceof AssemblyPrintingRecipe
                        && assembly.getSequence().get(1).getRecipe() instanceof ScrollInkingRecipe
                        && level.getRecipeManager().byKey(id("ink_printed_scroll")).isEmpty(),
                "scroll sequence is real printer then real spout with no independent filling shortcut");
        for (int templateLevel : new int[]{1, 10}) {
            ItemStack template = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(SpellRegistry.FIREBOLT_SPELL.get(), templateLevel, template);
            CompoundTag templateBefore = template.save(new CompoundTag());
            ItemStack intermediate = FactoryCompatTests.printIntermediate(level, template);
            require(intermediate.is(ArcaneContent.UNINKED_SCROLL.get())
                            && UninkedScrollItem.readSpell(intermediate).filter(SpellRegistry.FIREBOLT_SPELL.get()::equals).isPresent()
                            && !ISpellContainer.isSpellContainer(intermediate)
                            && UninkedScrollItem.isInkingStage(intermediate)
                            && ((SequencedAssemblyItem) intermediate.getItem()).getProgress(intermediate) == 0.5F,
                    "printed intermediate has actual native half-complete progress and spell identity without inherited level " + templateLevel);
            intermediate = ItemStack.of(intermediate.save(new CompoundTag()));
            require(UninkedScrollItem.isInkingStage(intermediate)
                            && UninkedScrollItem.readRequiredInk(intermediate).filter(requiredInk::isSame).isPresent(),
                    "scroll assembly progress payload and actual level-one ink survive NBT save/load");
            var inventory = new ItemStackHandler(1);
            inventory.setStackInSlot(0, intermediate.copy());
            var wrapper = new RecipeWrapper(inventory);
            require(SequencedAssemblyRecipe.getRecipe(level, wrapper, AllRecipeTypes.FILLING.getType(),
                            FillingRecipe.class).filter(recipe -> recipe instanceof ScrollInkingRecipe).isPresent(),
                    "native sequenced filling dispatch selects owned dynamic scroll recipe");
            var ink = new FluidStack(requiredInk, 300);
            intermediate.setCount(2);
            require(FillingBySpout.canItemBeFilled(level, intermediate)
                            && FillingBySpout.getRequiredAmountForItem(level, intermediate, ink) == 250,
                    "real spout resolves 250 native level-one-rarity ink for intermediate from level " + templateLevel);
            var finished = FillingBySpout.fillItem(level, 250, intermediate, ink);
            require(NativeScrollRules.readValidScroll(finished).filter(data -> data.getLevel() == 1
                            && data.getSpell().equals(SpellRegistry.FIREBOLT_SPELL.get())).isPresent()
                            && finished.getCount() == 1 && UninkedScrollItem.isPrintedScroll(finished)
                            && intermediate.getCount() == 1 && ink.getAmount() == 50
                            && !finished.getTag().contains(UninkedScrollItem.ASSEMBLY_TAG),
                    "real spout finishes native sequence at level one and consumes one intermediate plus 250 ink");
            require(template.save(new CompoundTag()).equals(templateBefore), "inking never consumes or edits printer template");
        }

        ItemStack sample = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(SpellRegistry.FIREBOLT_SPELL.get(), 10, sample);
        ItemStack valid = FactoryCompatTests.printIntermediate(level, sample);
        for (var wrong : List.of(new FluidStack(requiredInk, 249),
                new FluidStack(InkItem.getInkForRarity(fireboltRarity == SpellRarity.COMMON
                        ? SpellRarity.RARE : SpellRarity.COMMON).fluid().get(), 250),
                new FluidStack(ArcaneContent.SOURCE.get(), 250), new FluidStack(Fluids.WATER, 250))) {
            ItemStack input = valid.copy();
            CompoundTag before = input.save(new CompoundTag());
            CompoundTag fluidBefore = wrong.writeToNBT(new CompoundTag());
            int quote = FillingBySpout.getRequiredAmountForItem(level, input, wrong);
            require(quote < 0, "wrong or short level-one-rarity ink cannot begin a valid spout cycle");
            require(FillingBySpout.fillItem(level, 250, input, wrong).isEmpty()
                            && input.save(new CompoundTag()).equals(before)
                            && wrong.writeToNBT(new CompoundTag()).equals(fluidBefore),
                    "wrong or insufficient ink rejected atomically");
        }
        for (int invalidRequired : new int[]{0, 249, 251}) {
            ItemStack input = valid.copy();
            CompoundTag before = input.save(new CompoundTag());
            var ink = new FluidStack(requiredInk, 1000);
            require(FillingBySpout.fillItem(level, invalidRequired, input, ink).isEmpty()
                            && input.save(new CompoundTag()).equals(before) && ink.getAmount() == 1000,
                    "dynamic spout requires exact native 250mB transaction parameter");
        }
        ItemStack missingPayload = new ItemStack(ArcaneContent.UNINKED_SCROLL.get());
        ItemStack unknownPayload = valid.copy();
        if (unknownPayload.hasTag()) {
            for (String key : unknownPayload.getTag().getAllKeys()) {
                if (unknownPayload.getTag().contains(key, 8)) unknownPayload.getTag().putString(key, "arcane_industry:unregistered_spell_for_test");
            }
        }
        for (ItemStack invalid : new ItemStack[]{missingPayload, unknownPayload}) {
            CompoundTag before = invalid.save(new CompoundTag());
            var ink = new FluidStack(requiredInk, 250);
            require(UninkedScrollItem.readSpell(invalid).isEmpty()
                            && !FillingBySpout.canItemBeFilled(level, invalid)
                            && FillingBySpout.getRequiredAmountForItem(level, invalid, ink) < 0,
                    "missing or unknown intermediate payload has no filling recipe");
            require(FillingBySpout.fillItem(level, 250, invalid, ink).isEmpty()
                            && invalid.save(new CompoundTag()).equals(before) && ink.getAmount() == 250,
                    "invalid intermediate payload rejected without losing item or fluid");
        }
        for (String corruption : List.of("missing_progress", "wrong_recipe", "wrong_step", "wrong_progress")) {
            ItemStack invalid = valid.copy();
            CompoundTag tag = invalid.getOrCreateTag();
            if (corruption.equals("missing_progress")) tag.remove(UninkedScrollItem.ASSEMBLY_TAG);
            if (corruption.equals("wrong_recipe")) tag.getCompound(UninkedScrollItem.ASSEMBLY_TAG)
                    .putString("id", id("sequenced_assembly/ember_cake").toString());
            if (corruption.equals("wrong_step")) tag.getCompound(UninkedScrollItem.ASSEMBLY_TAG).putInt("Step", 0);
            if (corruption.equals("wrong_progress")) tag.getCompound(UninkedScrollItem.ASSEMBLY_TAG).putFloat("Progress", 0.25F);
            CompoundTag before = invalid.save(new CompoundTag());
            var ink = new FluidStack(requiredInk, 250);
            require(UninkedScrollItem.readSpell(invalid).isPresent() && !UninkedScrollItem.isInkingStage(invalid)
                            && !FillingBySpout.canItemBeFilled(level, invalid)
                            && FillingBySpout.fillItem(level, 250, invalid, ink).isEmpty()
                            && invalid.save(new CompoundTag()).equals(before) && ink.getAmount() == 250,
                    "scroll cannot bypass its real assembly phase using " + corruption);
        }
        results.put("uninked_scroll_native_spout_actual_level_one_ink_and_250mb_balance", "PASS");
        results.put("uninked_scroll_wrong_short_ink_bad_parameters_and_payload_no_loss", "PASS");
    }

    private static void runPrintedNativeRecycling(ServerLevel level, AlchemistCauldronTile cauldron,
                                                   Map<String, Object> results) {
        boolean testedHighRarityAtLevelOne = false;
        for (AbstractSpell spell : new AbstractSpell[]{SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.COUNTERSPELL_SPELL.get()}) {
            ItemStack sample = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(spell, spell.getMaxLevel(), sample);
            ItemStack uninked = FactoryCompatTests.printIntermediate(level, sample);
            require(!uninked.isEmpty() && !cauldron.isValidInput(uninked), "uninked scroll is not a cauldron reagent");
            cauldron.fluidInventory.drain(4000, IFluidHandler.FluidAction.EXECUTE);
            cauldron.fluidInventory.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
            CompoundTag uninkedBefore = uninked.save(new CompoundTag());
            cauldron.tryMeltInput(uninked);
            require(uninked.save(new CompoundTag()).equals(uninkedBefore) && cauldron.getFluidAmount() == 250,
                    "uninked paper cannot recycle into any ink");

            var printingInk = InkItem.getInkForRarity(spell.getRarity(1)).fluid().get();
            var paidInk = new FluidStack(printingInk, 250);
            ItemStack finished = FillingBySpout.fillItem(level, 250, uninked, paidInk);
            require(UninkedScrollItem.isPrintedScroll(finished) && uninked.isEmpty() && paidInk.isEmpty()
                            && NativeScrollRules.readValidScroll(finished).filter(data -> data.getLevel() == 1
                            && data.getSpell().equals(spell)).isPresent(),
                    "printed scroll pays actual native level-one ink and remains level one " + spell.getSpellId());
            ItemStack upgraded = ScrollUpgradeRules.prepare(finished, true, true).orElseThrow().output();
            require(UninkedScrollItem.isPrintedScroll(upgraded), "chamber upgrade preserves printed provenance");
            testedHighRarityAtLevelOne |= spell.getRarity(1) != SpellRarity.COMMON;
            ItemStack wild = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(spell, 1, wild);
            require(!UninkedScrollItem.isPrintedScroll(wild), "wild scroll fixture has no printing marker");
            for (ItemStack recyclable : new ItemStack[]{finished, upgraded, wild}) {
                ItemStack input = recyclable.copy();
                require(cauldron.isValidInput(input), "printed upgraded and wild scrolls all reach native recycling");
                var expectedInk = AlchemistCauldronTile.getInkFromScroll(input).fluid().get();
                cauldron.fluidInventory.drain(4000, IFluidHandler.FluidAction.EXECUTE);
                cauldron.fluidInventory.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
                cauldron.tryMeltInput(input);
                require(input.isEmpty(), "native recycling consumes exactly one printed upgraded or wild scroll");
                var remaining = cauldron.fluidInventory.drain(250, IFluidHandler.FluidAction.SIMULATE);
                require(remaining.getAmount() == 250
                                && (remaining.getFluid().isSame(Fluids.WATER) || remaining.getFluid().isSame(expectedInk)),
                        "native configured recycle roll leaves water or returns actual current-scroll-rarity ink");
            }
        }
        require(testedHighRarityAtLevelOne, "native recycle regression covers a non-common level-one spell");
        results.put("printed_and_upgraded_scrolls_use_original_native_random_recycling", "PASS");
        results.put("unprinted_wild_scroll_original_random_recycling_preserved", "PASS");
    }
    private static void craft(ServerLevel level, String recipeId, ItemStack expected, net.minecraft.world.item.Item remainder, int remainderCount, ItemStack... inputs) {
        var menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < inputs.length; i++) grid.setItem(i, inputs[i].copy());
        var recipe = (CraftingRecipe)level.getRecipeManager().byKey(id(recipeId)).orElseThrow();
        require(recipe.matches(grid, level), "crafting ingredients " + recipeId);
        var output = recipe.assemble(grid, level.registryAccess());
        require(ItemStack.isSameItemSameTags(output, expected) && output.getCount() == expected.getCount(), "crafting output " + recipeId);
        if (recipeId.equals("craft_blank_scroll")) {
            require(UninkedScrollItem.isBlankNativeScroll(output) && !ISpellContainer.isSpellContainer(output)
                            && !UninkedScrollItem.isPrintedScroll(output),
                    "crafted printer stock is native blank scroll with no spell or printed provenance");
        }
        int count = 0;
        for (ItemStack returned : recipe.getRemainingItems(grid)) {
            if (returned.isEmpty()) continue;
            require(returned.is(remainder), "unexpected crafting remainder " + recipeId);
            count += returned.getCount();
        }
        require(count == remainderCount, "crafting empty container conservation " + recipeId);
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("arcane_industry", path); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
