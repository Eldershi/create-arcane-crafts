package dev.expedition.arcane.client.jei;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.compat.jei.category.SequencedAssemblyCategory;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.compat.PrinterCompat;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import dev.expedition.arcane.item.UninkedScrollItem;
import dev.expedition.arcane.compat.NativeScrollRules;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Prototype-only evidence from JEI's registered runtime, rather than a parallel recipe simulation. */
final class JeiPrototypeVerification {
    private JeiPrototypeVerification() {}

    static Path runDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().resolveSibling("run");
    }

    static void write(IJeiRuntime runtime, ArcaneRecipeDisplays.Snapshot expected,
                      int hiddenPlaceholders, int hiddenGenericAssemblies) {
        if (!Boolean.getBoolean("arcane.prototype")) return;
        try {
            IRecipeManager manager = runtime.getRecipeManager();
            List<ArcaneRecipeDisplays.Printing> printing = manager.createRecipeLookup(ArcaneJeiPlugin.IMPRINTING).get().toList();
            var assemblies = manager.createRecipeLookup(ArcaneJeiPlugin.NATIVE_ASSEMBLY).get().toList();
            var ascensionAssemblies = manager.createRecipeLookup(ArcaneJeiPlugin.ASCENSION).get().toList();
            List<ArcaneRecipeDisplays.Ascension> ascension = ascensionAssemblies.stream()
                    .map(recipe -> NativeAssemblyDisplays.ascensionView(recipe).orElseThrow()).toList();
            JsonObject report = new JsonObject();
            report.addProperty("time", Instant.now().toString());
            report.addProperty("prototype_only", true);
            report.addProperty("source", "JEI IRecipeManager registered runtime");
            report.addProperty("visual_review_required", true);
            JsonObject counts = new JsonObject();
            counts.addProperty("ascension", ascension.size());
            counts.addProperty("native_ascension_assembly_views", ascension.size());
            count(manager, ArcaneJeiPlugin.IMPRINTING, counts, "imprinting");
            count(manager, ArcaneJeiPlugin.INKING, counts, "inking");
            counts.addProperty("native_printing_assembly_views", assemblies.stream().filter(NativeAssemblyDisplays::isPrintingView).count());
            counts.addProperty("native_cake_assembly", assemblies.stream().filter(recipe -> recipe.getId().equals(NativeAssemblyDisplays.CAKE_ID)).count());
            report.addProperty("complete_flow_category", "arcane_industry:scroll_ascension");
            report.addProperty("printing_cake_flow_category", "create:sequenced_assembly");
            report.addProperty("ascension_views_in_create_category", assemblies.stream()
                    .filter(recipe -> NativeAssemblyDisplays.ascensionView(recipe).isPresent()).count());
            report.add("registered_counts", counts);
            Set<String> expectedIds = expected.ascension().stream().map(recipe -> NativeAssemblyDisplays.ascensionViewId(recipe).toString()).collect(Collectors.toSet());
            Set<String> actualIds = ascensionAssemblies.stream().map(recipe -> recipe.getId().toString()).collect(Collectors.toSet());
            TreeSet<String> missing = new TreeSet<>(expectedIds);
            missing.removeAll(actualIds);
            TreeSet<String> extra = new TreeSet<>(actualIds);
            extra.removeAll(expectedIds);
            report.add("missing_ascension_ids", strings(missing));
            report.add("unexpected_ascension_ids", strings(extra));
            report.addProperty("all_rule_generated_upgrades_registered", missing.isEmpty() && extra.isEmpty());

            var coverage = new LinkedHashMap<String, JsonObject>();
            for (var recipe : ascension) {
                var slot = ISpellContainer.get(recipe.upgrade().input()).getActiveSpells().get(0);
                String spellId = slot.getSpell().getSpellId();
                JsonObject spell = coverage.computeIfAbsent(spellId, ignored -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("spell", spellId);
                    item.addProperty("native_min", slot.getSpell().getMinLevel());
                    item.addProperty("native_max", slot.getSpell().getMaxLevel());
                    item.addProperty("breakthrough_max", slot.getSpell().getMaxLevel() + 3);
                    item.add("steps", new JsonArray());
                    return item;
                });
                JsonObject step = new JsonObject();
                step.addProperty("input_level", slot.getLevel());
                step.addProperty("target_level", recipe.upgrade().targetLevel());
                step.addProperty("solution", recipe.crimson() ? "crimson" : "arcane");
                step.addProperty("exact_solution_mb", ArcaneRecipeDisplays.millibuckets(recipe.upgrade().solutionCostQuarterMb()));
                step.addProperty("cake_count", recipe.upgrade().requiresCake() ? 1 : 0);
                spell.getAsJsonArray("steps").add(step);
            }
            JsonArray spellCoverage = new JsonArray();
            coverage.values().forEach(spellCoverage::add);
            report.add("spell_upgrade_coverage", spellCoverage);
            long invalidSamples = printing.stream().flatMap(recipe -> recipe.samples().stream())
                    .filter(stack -> PrinterCompat.readPrintableTemplate(stack).isEmpty()).count();
            report.addProperty("invalid_printing_sample_count", invalidSamples);
            report.addProperty("printing_spell_count_matches", printing.size() == expected.printing().size());
            report.addProperty("native_printing_assembly_count_matches", assemblies.stream()
                    .filter(NativeAssemblyDisplays::isPrintingView).count() == expected.printing().size());
            report.addProperty("generic_dynamic_assemblies_hidden", hiddenGenericAssemblies);
            report.addProperty("generic_printing_assemblies_hidden", manager.createRecipeLookup(ArcaneJeiPlugin.NATIVE_ASSEMBLY)
                    .includeHidden().get().filter(recipe -> recipe.getId().equals(NativeAssemblyDisplays.PRINTING_ID)).count());
            report.addProperty("generic_printing_assembly_visible", assemblies.stream()
                    .filter(recipe -> recipe.getId().equals(NativeAssemblyDisplays.PRINTING_ID)).count());
            report.addProperty("generic_ascension_assembly_visible", assemblies.stream()
                    .filter(recipe -> recipe.getId().equals(NativeAssemblyDisplays.ASCENSION_ID)).count());
            var nativeCategory = manager.getRecipeCategory(ArcaneJeiPlugin.NATIVE_ASSEMBLY);
            report.addProperty("native_assembly_category_class", nativeCategory.getClass().getName());
            var ascensionCategory = manager.getRecipeCategory(ArcaneJeiPlugin.ASCENSION);
            report.addProperty("ascension_category_class", ascensionCategory.getClass().getName());
            report.addProperty("ascension_category_superclass", ascensionCategory.getClass().getSuperclass().getName());
            report.addProperty("ascension_category_uses_native_layout", ascensionCategory instanceof SequencedAssemblyCategory);
            report.addProperty("ascension_category_title", ascensionCategory.getTitle().getString());
            report.addProperty("ascension_layout_method_owner", ascensionCategory.getClass().getMethod("setRecipe",
                    mezz.jei.api.gui.builder.IRecipeLayoutBuilder.class,
                    com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.class,
                    mezz.jei.api.recipe.IFocusGroup.class).getDeclaringClass().getName());
            var allDisplays = new ArrayList<>(assemblies);
            allDisplays.addAll(ascensionAssemblies);
            JsonArray nativeRecipes = new JsonArray();
            boolean printingInkExact = true, printingSourceCostExact = true, printingTwoSteps = true;
            JsonObject printingRarityCounts = new JsonObject();
            JsonArray printingInkLookups = new JsonArray();
            Set<String> checkedPrintingRarities = new TreeSet<>();
            for (var recipe : allDisplays) {
                if (!NativeAssemblyDisplays.isPrintingView(recipe) && !recipe.getId().equals(NativeAssemblyDisplays.CAKE_ID)
                        && NativeAssemblyDisplays.ascensionView(recipe).isEmpty()) continue;
                JsonObject nativeRecipe = new JsonObject();
                nativeRecipe.addProperty("id", recipe.getId().toString());
                nativeRecipe.addProperty("loops", recipe.getLoops());
                nativeRecipe.addProperty("transitional_item", recipe.getTransitionalItem().save(new net.minecraft.nbt.CompoundTag()).toString());
                var result = recipe.resultPool.get(0).getStack();
                nativeRecipe.addProperty("result_item", result.save(new net.minecraft.nbt.CompoundTag()).toString());
                if (NativeAssemblyDisplays.isPrintingView(recipe)) {
                    nativeRecipe.addProperty("result_is_native_scroll", result.is(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get()));
                    nativeRecipe.addProperty("result_level", ISpellContainer.get(result).getActiveSpells().get(0).getLevel());
                    var spell = UninkedScrollItem.readSpell(recipe.getTransitionalItem()).orElseThrow();
                    var rarity = spell.getRarity(1);
                    var expectedInk = InkItem.getInkForRarity(rarity).fluid().get();
                    boolean twoSteps = recipe.getSequence().size() == 2;
                    var inkIngredients = twoSteps ? recipe.getSequence().get(1).getRecipe().getFluidIngredients() : List.<com.simibubi.create.foundation.fluid.FluidIngredient>of();
                    var inks = inkIngredients.stream().flatMap(ingredient -> ingredient.getMatchingFluidStacks().stream()).toList();
                    boolean exactInk = inkIngredients.size() == 1 && inks.size() == 1
                            && inkIngredients.get(0).getRequiredAmount() == UninkedScrollItem.INK_AMOUNT
                            && inks.get(0).getFluid().isSame(expectedInk) && inks.get(0).getAmount() == UninkedScrollItem.INK_AMOUNT;
                    var sourceIngredients = recipe.getSequence().get(0).getRecipe().getFluidIngredients();
                    var sources = sourceIngredients.stream().flatMap(ingredient -> ingredient.getMatchingFluidStacks().stream()).toList();
                    boolean exactSource = sourceIngredients.size() == 1 && sources.size() == 1
                            && sources.get(0).getFluid().isSame(ArcaneContent.SOURCE.get())
                            && sources.get(0).getAmount() == NativeScrollRules.PRINTING_FLUID_COST;
                    printingInkExact &= exactInk;
                    printingSourceCostExact &= exactSource;
                    printingTwoSteps &= twoSteps;
                    String rarityId = rarity.getSerializedName();
                    printingRarityCounts.addProperty(rarityId, printingRarityCounts.has(rarityId)
                            ? printingRarityCounts.get(rarityId).getAsInt() + 1 : 1);
                    nativeRecipe.addProperty("printing_level_one_rarity", rarityId);
                    nativeRecipe.addProperty("printing_expected_ink", ForgeRegistries.FLUIDS.getKey(expectedInk).toString());
                    nativeRecipe.addProperty("printing_ink_alternative_count", inks.size());
                    nativeRecipe.addProperty("printing_ink_is_single_exact_required_fluid", exactInk);
                    nativeRecipe.addProperty("printing_source_cost_remains_50_mb", exactSource);
                    nativeRecipe.addProperty("printing_native_two_step_flow", twoSteps);
                    if (checkedPrintingRarities.add(rarityId)) {
                        JsonObject lookup = new JsonObject();
                        lookup.addProperty("id", recipe.getId().toString());
                        lookup.addProperty("level_one_rarity", rarityId);
                        lookup.addProperty("required_ink_U", lookupPrintingInk(runtime,
                                new FluidStack(expectedInk, UninkedScrollItem.INK_AMOUNT), recipe.getId()));
                        boolean excludesOtherTiers = true;
                        for (var other : io.redspace.ironsspellbooks.api.spells.SpellRarity.values()) {
                            if (other == rarity) continue;
                            excludesOtherTiers &= !lookupPrintingInk(runtime,
                                    new FluidStack(InkItem.getInkForRarity(other).fluid().get(), UninkedScrollItem.INK_AMOUNT), recipe.getId());
                        }
                        lookup.addProperty("wrong_ink_tiers_excluded_from_U", excludesOtherTiers);
                        printingInkLookups.add(lookup);
                    }
                }
                NativeAssemblyDisplays.ascensionView(recipe).ifPresent(display -> {
                    var upgrade = display.upgrade();
                    nativeRecipe.addProperty("source_scroll", upgrade.input().save(new net.minecraft.nbt.CompoundTag()).toString());
                    nativeRecipe.addProperty("result_level", ISpellContainer.get(result).getActiveSpells().get(0).getLevel());
                    nativeRecipe.addProperty("target_level", upgrade.targetLevel());
                    nativeRecipe.addProperty("typed_result_matches_rule", net.minecraft.world.item.ItemStack.isSameItemSameTags(result, upgrade.output()));
                    nativeRecipe.addProperty("crimson", display.crimson());
                    nativeRecipe.addProperty("exact_solution_mb", ArcaneRecipeDisplays.millibuckets(upgrade.solutionCostQuarterMb()));
                    nativeRecipe.addProperty("cake_count", upgrade.requiresCake() ? 1 : 0);
                    nativeRecipe.addProperty("lightning_rod_environment_required", upgrade.requiresCake());
                    nativeRecipe.addProperty("inking_matches_rule", recipe.getSequence().get(0).getRecipe().getFluidIngredients().stream()
                            .flatMap(ingredient -> ingredient.getMatchingFluidStacks().stream())
                            .anyMatch(fluid -> fluid.getFluid() == upgrade.inkFluid()
                                    && fluid.getAmount() == dev.expedition.arcane.chamber.ScrollUpgradeRules.INK_AMOUNT));
                });
                JsonArray steps = new JsonArray();
                for (var step : recipe.getSequence()) {
                    JsonObject nativeStep = new JsonObject();
                    nativeStep.addProperty("class", step.getRecipe().getClass().getName());
                    nativeStep.addProperty("description", step.getAsAssemblyRecipe().getDescriptionForAssembly().getString());
                    nativeStep.addProperty("jei_subcategory", step.getAsAssemblyRecipe().getJEISubCategory().get().get().getClass().getName());
                    steps.add(nativeStep);
                }
                nativeRecipe.add("steps", steps);
                nativeRecipes.add(nativeRecipe);
            }
            report.add("native_assembly_recipes", nativeRecipes);
            boolean hasPrintingViews = assemblies.stream().anyMatch(NativeAssemblyDisplays::isPrintingView);
            report.addProperty("all_printing_views_use_single_level_one_rarity_ink", hasPrintingViews && printingInkExact);
            report.addProperty("all_printing_views_keep_50_mb_blue_source", hasPrintingViews && printingSourceCostExact);
            report.addProperty("all_printing_views_keep_native_two_step_flow", hasPrintingViews && printingTwoSteps);
            report.add("printing_views_by_level_one_rarity", printingRarityCounts);
            report.add("native_printing_ink_lookup_examples", printingInkLookups);
            var lookupExamples = new LinkedHashMap<ResourceLocation, com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe>();
            ascensionAssemblies.stream().filter(recipe -> !NativeAssemblyDisplays.ascensionView(recipe).orElseThrow().crimson())
                    .findFirst().ifPresent(recipe -> lookupExamples.put(recipe.getId(), recipe));
            ascensionAssemblies.stream().filter(recipe -> NativeAssemblyDisplays.ascensionView(recipe).orElseThrow().crimson())
                    .findFirst().ifPresent(recipe -> lookupExamples.put(recipe.getId(), recipe));
            ascensionAssemblies.stream().filter(recipe -> NativeAssemblyDisplays.ascensionView(recipe).orElseThrow().upgrade().requiresCake())
                    .findFirst().ifPresent(recipe -> lookupExamples.put(recipe.getId(), recipe));
            ascensionAssemblies.stream().filter(recipe -> NativeAssemblyDisplays.ascensionView(recipe).orElseThrow().upgrade().solutionCostQuarterMb() % 4 != 0)
                    .findFirst().ifPresent(recipe -> lookupExamples.put(recipe.getId(), recipe));
            JsonArray lookups = new JsonArray();
            for (var recipe : lookupExamples.values()) {
                var display = NativeAssemblyDisplays.ascensionView(recipe).orElseThrow();
                var upgrade = display.upgrade();
                JsonObject lookup = new JsonObject();
                lookup.addProperty("id", recipe.getId().toString());
                lookup.addProperty("source_U", lookupContains(runtime, RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, upgrade.input(), recipe.getId()));
                lookup.addProperty("result_R", lookupContains(runtime, RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, upgrade.output(), recipe.getId()));
                var prepared = UnfinishedAscensionScrollItem.create(upgrade.input());
                lookup.addProperty("prepared_U", lookupContains(runtime, RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, prepared, recipe.getId()));
                lookup.addProperty("prepared_R", lookupContains(runtime, RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, prepared, recipe.getId()));
                lookup.addProperty("template_catalyst", lookupContains(runtime, RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK,
                        new ItemStack(ArcaneContent.TEMPLATE.get()), recipe.getId()));
                lookup.addProperty("source_fluid_U", lookupContains(runtime, RecipeIngredientRole.INPUT, ForgeTypes.FLUID_STACK,
                        new FluidStack(display.crimson() ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), upgrade.solutionCost()), recipe.getId()));
                lookup.addProperty("ink_U", lookupContains(runtime, RecipeIngredientRole.INPUT, ForgeTypes.FLUID_STACK,
                        new FluidStack(upgrade.inkFluid(), dev.expedition.arcane.chamber.ScrollUpgradeRules.INK_AMOUNT), recipe.getId()));
                if (upgrade.requiresCake()) {
                    lookup.addProperty("cake_U", lookupContains(runtime, RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK,
                            new ItemStack(CrimsonContent.EMBER_CAKE.get()), recipe.getId()));
                    lookup.addProperty("lightning_rod_catalyst", lookupContains(runtime, RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK,
                            new ItemStack(Items.LIGHTNING_ROD), recipe.getId()));
                }
                lookups.add(lookup);
            }
            report.add("native_ascension_lookup_examples", lookups);
            var rodFocus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.CATALYST,
                    VanillaTypes.ITEM_STACK, new ItemStack(Items.LIGHTNING_ROD));
            Set<String> rodIds = manager.createRecipeLookup(ArcaneJeiPlugin.ASCENSION).limitFocus(List.of(rodFocus)).get()
                    .map(recipe -> recipe.getId().toString()).collect(Collectors.toSet());
            Set<String> transcendentIds = ascensionAssemblies.stream()
                    .filter(recipe -> NativeAssemblyDisplays.ascensionView(recipe).orElseThrow().upgrade().requiresCake())
                    .map(recipe -> recipe.getId().toString()).collect(Collectors.toSet());
            report.addProperty("lightning_rod_catalyst_recipe_count", rodIds.size());
            report.addProperty("lightning_rod_only_required_for_transcendence", rodIds.equals(transcendentIds));
            report.addProperty("generic_inking_placeholders_hidden", hiddenPlaceholders);
            long visibleGeneric = manager.getRecipeType(new ResourceLocation("create", "spout_filling"), FillingRecipe.class)
                    .map(type -> manager.createRecipeLookup(type).get()
                            .filter(recipe -> recipe.getId().equals(ArcaneRecipeDisplays.id("ink_printed_scroll"))).count())
                    .orElse(0L);
            report.addProperty("generic_inking_placeholder_visible", visibleGeneric);
            report.addProperty("generic_inking_placeholder_hidden_successfully", visibleGeneric == 0);
            verifyCraftingRecipe(runtime, report, "template_vanilla_crafting", ArcaneJeiPlugin.TEMPLATE_RECIPE_ID,
                    new ItemStack(ArcaneContent.TEMPLATE.get()), Map.of("cdp_template_U",
                            JeiDrawing.registeredItem("create_dragons_plus:blaze_upgrade_smithing_template")));
            verifyCraftingRecipe(runtime, report, "mana_upgrade_vanilla_crafting", ArcaneJeiPlugin.MANA_UPGRADE_RECIPE_ID,
                    new ItemStack(ArcaneContent.MANA_UPGRADE.get()), Map.of(
                            "andesite_alloy_U", JeiDrawing.registeredItem("create:andesite_alloy"),
                            "amethyst_shard_U", new ItemStack(Items.AMETHYST_SHARD),
                            "blank_rune_U", JeiDrawing.registeredItem("irons_spellbooks:blank_rune"),
                            "upgrade_base_U", JeiDrawing.registeredItem("sophisticatedbackpacks:upgrade_base"),
                            "arcane_essence_U", JeiDrawing.registeredItem("irons_spellbooks:arcane_essence")));
            writeJson("jei-verification.json", report);
        } catch (Exception failure) {
            ArcaneIndustry.LOGGER.error("Could not write prototype JEI verification", failure);
        }
    }

    private static <T> void count(IRecipeManager manager, RecipeType<T> type, JsonObject counts, String name) {
        counts.addProperty(name, manager.createRecipeLookup(type).get().count());
    }

    /** Read JEI's existing vanilla recipe entry and focus indexes; do not register a second copy. */
    private static void verifyCraftingRecipe(IJeiRuntime runtime, JsonObject report, String reportKey,
                                            ResourceLocation recipeId, ItemStack result,
                                            Map<String, ItemStack> indexedInputs) {
        var manager = runtime.getRecipeManager();
        var recipes = manager.createRecipeLookup(RecipeTypes.CRAFTING).get()
                .filter(recipe -> recipe.getId().equals(recipeId)).toList();
        JsonObject template = new JsonObject();
        template.addProperty("recipe_id", recipeId.toString());
        template.addProperty("category", RecipeTypes.CRAFTING.getUid().toString());
        template.addProperty("category_class", manager.getRecipeCategory(RecipeTypes.CRAFTING).getClass().getName());
        template.addProperty("registered_count", recipes.size());
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var output = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK,
                result);
        template.addProperty("output_R", manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(output)).get()
                .anyMatch(recipe -> recipe.getId().equals(recipeId)));
        indexedInputs.forEach((name, stack) -> {
            var input = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
            template.addProperty(name, manager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(input)).get()
                    .anyMatch(recipe -> recipe.getId().equals(recipeId)));
        });
        if (recipes.size() == 1) {
            var recipe = recipes.get(0);
            template.addProperty("recipe_class", recipe.getClass().getName());
            if (recipe instanceof ShapedRecipe shaped) {
                template.addProperty("shaped_width", shaped.getWidth());
                template.addProperty("shaped_height", shaped.getHeight());
            }
            JsonArray slots = new JsonArray();
            for (int index = 0; index < recipe.getIngredients().size(); index++) {
                JsonObject slot = new JsonObject();
                slot.addProperty("slot", index);
                JsonArray alternatives = new JsonArray();
                for (ItemStack stack : recipe.getIngredients().get(index).getItems())
                    alternatives.add(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
                slot.add("item_ids", alternatives);
                slots.add(slot);
            }
            template.add("ingredient_slots", slots);
            var actualResult = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
            template.addProperty("output_item", ForgeRegistries.ITEMS.getKey(actualResult.getItem()).toString());
            template.addProperty("output_count", actualResult.getCount());
        }
        report.add(reportKey, template);
    }

    private static <T> boolean lookupContains(IJeiRuntime runtime, RecipeIngredientRole role, IIngredientType<T> type,
                                               T ingredient, ResourceLocation recipeId) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, type, ingredient);
        return runtime.getRecipeManager().createRecipeLookup(ArcaneJeiPlugin.ASCENSION).limitFocus(List.of(focus)).get()
                .anyMatch(recipe -> recipe.getId().equals(recipeId));
    }

    private static boolean lookupPrintingInk(IJeiRuntime runtime, FluidStack ink, ResourceLocation recipeId) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT, ForgeTypes.FLUID_STACK, ink);
        return runtime.getRecipeManager().createRecipeLookup(ArcaneJeiPlugin.NATIVE_ASSEMBLY)
                .limitFocus(List.of(focus)).get().anyMatch(recipe -> recipe.getId().equals(recipeId));
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    static void writeJson(String filename, JsonObject value) throws java.io.IOException {
        Path run = runDirectory();
        Files.createDirectories(run);
        Files.writeString(run.resolve(filename), new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n",
                StandardCharsets.UTF_8);
    }
}
