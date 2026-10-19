package dev.expedition.arcane.client.jei;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import dev.expedition.arcane.item.UninkedScrollItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** NBT-specific views of the actual server assembly, rendered by Create's own JEI category. */
final class NativeAssemblyDisplays {
    static final ResourceLocation PRINTING_ID = ArcaneRecipeDisplays.id("sequenced_assembly/printed_scroll");
    static final ResourceLocation CAKE_ID = ArcaneRecipeDisplays.id("sequenced_assembly/ember_cake");
    static final ResourceLocation ASCENSION_ID = ArcaneRecipeDisplays.id("sequenced_assembly/scroll_ascension");
    private static final String VIEW_PREFIX = "jei/sequenced_assembly/printed_scroll/";
    private static final String ASCENSION_VIEW_PREFIX = "jei/sequenced_assembly/scroll_ascension/";
    private static final String PRINTING_STEP = "arcane_industry:assembly_scroll_printing";
    private static final String INKING_STEP = "arcane_industry:scroll_inking";
    private static final Map<ProcessingRecipe<?>, ArcaneRecipeDisplays.Ascension> ASCENSION_STEPS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NativeAssemblyDisplays() {}

    static List<SequencedAssemblyRecipe> printing(RecipeManager manager, List<ArcaneRecipeDisplays.Printing> spells) {
        var actual = manager.byKey(PRINTING_ID).orElse(null);
        if (!(actual instanceof SequencedAssemblyRecipe assembly)
                || !(assembly.getSerializer() instanceof SequencedAssemblyRecipeSerializer serializer)) return List.of();
        JsonObject source = new JsonObject();
        serializer.write(source, assembly);
        List<SequencedAssemblyRecipe> views = new ArrayList<>();
        for (var spell : spells) {
            var target = UninkedScrollItem.readSpell(spell.uninked());
            if (target.isEmpty() || spell.samples().isEmpty()) continue;
            JsonObject json = source.deepCopy();
            boolean foundPrinter = false, foundInk = false;
            for (var entry : json.getAsJsonArray("sequence")) {
                JsonObject step = entry.getAsJsonObject();
                String type = step.get("type").getAsString();
                if (type.equals(PRINTING_STEP)) {
                    JsonArray sampleAlternatives = new JsonArray();
                    spell.samples().forEach(sample -> sampleAlternatives.add(StrictNBTIngredient.of(sample.copy()).toJson()));
                    step.getAsJsonArray("ingredients").set(1, sampleAlternatives);
                    foundPrinter = true;
                } else if (type.equals(INKING_STEP)) {
                    // The server assembly accepts the ink tag; this typed view shows only its real required tier.
                    JsonArray ingredients = step.getAsJsonArray("ingredients");
                    for (int index = 0; index < ingredients.size(); index++) {
                        if (!FluidIngredient.isFluidIngredient(ingredients.get(index))) continue;
                        ingredients.set(index, FluidIngredient.fromFluid(spell.inkFluid(), UninkedScrollItem.INK_AMOUNT).serialize());
                        foundInk = true;
                    }
                }
            }
            if (!foundPrinter || !foundInk) continue;
            ResourceLocation spellId = target.get().getSpellResource();
            var structure = serializer.fromJson(ArcaneRecipeDisplays.id(VIEW_PREFIX + spellId.getNamespace() + "/" + spellId.getPath()), json);
            views.add(new SpellAssemblyView(structure, spell.uninked(), spell.finished()));
        }
        return List.copyOf(views);
    }

    static boolean isPrintingView(SequencedAssemblyRecipe recipe) {
        return recipe.getId().getNamespace().equals(PRINTING_ID.getNamespace())
                && recipe.getId().getPath().startsWith(VIEW_PREFIX);
    }

    static List<SequencedAssemblyRecipe> ascension(RecipeManager manager, List<ArcaneRecipeDisplays.Ascension> upgrades) {
        var actual = manager.byKey(ASCENSION_ID).orElse(null);
        if (!(actual instanceof SequencedAssemblyRecipe assembly)
                || !(assembly.getSerializer() instanceof SequencedAssemblyRecipeSerializer serializer)) return List.of();
        JsonObject source = new JsonObject();
        serializer.write(source, assembly);
        List<SequencedAssemblyRecipe> views = new ArrayList<>();
        for (var display : upgrades) {
            var upgrade = display.upgrade();
            ItemStack transition = UnfinishedAscensionScrollItem.create(upgrade.input());
            if (transition.isEmpty()) continue;
            JsonObject json = source.deepCopy();
            JsonObject inking = json.getAsJsonArray("sequence").get(0).getAsJsonObject();
            JsonArray ingredients = inking.getAsJsonArray("ingredients");
            boolean replacedInk = false;
            for (int index = 0; index < ingredients.size(); index++) {
                if (!FluidIngredient.isFluidIngredient(ingredients.get(index))) continue;
                ingredients.set(index, FluidIngredient.fromFluid(upgrade.inkFluid(), ScrollUpgradeRules.INK_AMOUNT).serialize());
                replacedInk = true;
            }
            if (!replacedInk) continue;
            ResourceLocation viewId = ascensionViewId(display);
            var structure = serializer.fromJson(viewId, json);
            var view = new AscensionAssemblyView(structure, display, transition);
            view.getSequence().forEach(step -> ASCENSION_STEPS.put(step.getRecipe(), display));
            views.add(view);
        }
        return List.copyOf(views);
    }

    static ResourceLocation ascensionViewId(ArcaneRecipeDisplays.Ascension display) {
        return ArcaneRecipeDisplays.id(ASCENSION_VIEW_PREFIX + display.id().getPath().substring("ascension/".length()));
    }

    static Optional<ArcaneRecipeDisplays.Ascension> ascensionStep(ProcessingRecipe<?> step) {
        return Optional.ofNullable(ASCENSION_STEPS.get(step));
    }

    static Optional<ArcaneRecipeDisplays.Ascension> ascensionView(SequencedAssemblyRecipe recipe) {
        return recipe instanceof AscensionAssemblyView view ? Optional.of(view.display) : Optional.empty();
    }

    /** Create's output JSON adapter does not preserve all NBT number types. Copy stacks directly. */
    private static class SpellAssemblyView extends SequencedAssemblyRecipe {
        SpellAssemblyView(SequencedAssemblyRecipe structure, ItemStack transition, ItemStack finished) {
            this(structure, structure.getIngredient(), transition, finished);
        }

        SpellAssemblyView(SequencedAssemblyRecipe structure, Ingredient initial, ItemStack transition, ItemStack finished) {
            super(structure.getId(), (SequencedAssemblyRecipeSerializer) structure.getSerializer());
            ingredient = initial;
            loops = structure.getLoops();
            sequence = new ArrayList<>(structure.getSequence());
            transitionalItem = new ProcessingOutput(transition.copy(), 1);
            resultPool.clear();
            resultPool.add(new ProcessingOutput(finished.copy(), 1));
            // Match Create's first-step initial/transition alternatives with the real typed payload.
            Ingredient transit = Ingredient.of(transition.copy());
            for (int index = 0; index < sequence.size(); index++) {
                sequence.get(index).getRecipe().getIngredients().set(0,
                        index == 0 ? Ingredient.merge(List.of(transit, ingredient)) : transit);
            }
        }
    }

    private static final class AscensionAssemblyView extends SpellAssemblyView {
        private final ArcaneRecipeDisplays.Ascension display;

        AscensionAssemblyView(SequencedAssemblyRecipe structure, ArcaneRecipeDisplays.Ascension display, ItemStack transition) {
            super(structure, StrictNBTIngredient.of(display.upgrade().input().copy()), transition, display.upgrade().output());
            this.display = display;
        }
    }
}
