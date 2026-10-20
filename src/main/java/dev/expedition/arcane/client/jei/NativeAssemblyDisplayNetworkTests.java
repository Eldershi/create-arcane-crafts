package dev.expedition.arcane.client.jei;

import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prototype JEI checks deliberately start from native packet-decoded, tag-expanded recipes. */
final class NativeAssemblyDisplayNetworkTests {
    private NativeAssemblyDisplayNetworkTests() {}

    static JsonObject run(RecipeManager source, ArcaneRecipeDisplays.Snapshot expected) {
        Map<ResourceLocation, byte[]> before = new LinkedHashMap<>();
        List<Recipe<?>> received = new ArrayList<>();
        for (ResourceLocation id : List.of(NativeAssemblyDisplays.PRINTING_ID, NativeAssemblyDisplays.ASCENSION_ID)) {
            var recipe = source.byKey(id).orElseThrow(() -> new IllegalStateException("Missing real assembly " + id));
            require(recipe instanceof SequencedAssemblyRecipe, "real_native_assembly_" + id);
            var assembly = (SequencedAssemblyRecipe) recipe;
            before.put(id, bytes(assembly));
            received.add(NativeAssemblyDisplays.networkCopy(assembly, id));
        }
        RecipeManager client = new RecipeManager();
        client.replaceRecipes(received);
        Map<ResourceLocation, byte[]> receivedBefore = new LinkedHashMap<>();
        for (Recipe<?> recipe : received) receivedBefore.put(recipe.getId(), bytes((SequencedAssemblyRecipe) recipe));

        var printing = expected.printing().stream().filter(display -> !display.samples().isEmpty()
                && dev.expedition.arcane.item.UninkedScrollItem.readSpell(display.uninked()).isPresent()).toList();
        // Before the fix, either call crashes while writing a packet-decoded FluidTagIngredient to JSON.
        var printingViews = NativeAssemblyDisplays.printing(client, printing);
        var ascension = expected.ascension().stream().filter(display -> !dev.expedition.arcane.item
                .UnfinishedAscensionScrollItem.create(display.upgrade().input()).isEmpty()).toList();
        var ascensionViews = NativeAssemblyDisplays.ascension(client, ascension);
        require(!printingViews.isEmpty() && printingViews.size() == printing.size(), "all_printing_views_survive_native_packet_roundtrip");
        require(!ascensionViews.isEmpty() && ascensionViews.size() == ascension.size(), "all_ascension_views_survive_native_packet_roundtrip");

        IdentityHashMap<Object, Boolean> owned = new IdentityHashMap<>();
        int printingChecks = 0;
        for (int i = 0; i < printingViews.size(); i++) {
            var view = printingViews.get(i);
            var display = printing.get(i);
            require(view.getSequence().size() == 2, "printing_keeps_real_two_step_structure");
            var samples = view.getSequence().get(0).getRecipe().getIngredients().get(1);
            for (ItemStack sample : display.samples()) require(samples.test(sample), "typed_printing_accepts_each_real_sample");
            ItemStack wrongNbt = display.samples().get(0).copy();
            wrongNbt.getOrCreateTag().putBoolean("arcane_network_clone_test", true);
            require(!samples.test(wrongNbt), "typed_printing_sample_alternatives_keep_strict_nbt");
            exactInk(view, 1, display.inkFluid(), dev.expedition.arcane.item.UninkedScrollItem.INK_AMOUNT);
            require(ItemStack.matches(view.getTransitionalItem(), display.uninked())
                    && ItemStack.matches(view.resultPool.get(0).getStack(), display.finished()), "printing_typed_item_nbt_preserved");
            ownSteps(view, owned);
            printingChecks++;
        }
        int ascensionChecks = 0;
        for (int i = 0; i < ascensionViews.size(); i++) {
            var view = ascensionViews.get(i);
            var display = ascension.get(i);
            exactInk(view, 0, display.upgrade().inkFluid(), dev.expedition.arcane.chamber.ScrollUpgradeRules.INK_AMOUNT);
            require(ItemStack.matches(view.resultPool.get(0).getStack(), display.upgrade().output()),
                    "ascension_typed_result_nbt_preserved");
            ownSteps(view, owned);
            ascensionChecks++;
        }
        for (var entry : before.entrySet()) require(Arrays.equals(entry.getValue(),
                bytes((SequencedAssemblyRecipe) source.byKey(entry.getKey()).orElseThrow())),
                "original_manager_assembly_bytes_unchanged_" + entry.getKey());
        for (var entry : receivedBefore.entrySet()) require(Arrays.equals(entry.getValue(),
                bytes((SequencedAssemblyRecipe) client.byKey(entry.getKey()).orElseThrow())),
                "packet_decoded_manager_assembly_bytes_unchanged_" + entry.getKey());
        for (var view : List.of(printingViews.get(0), ascensionViews.get(0))) {
            var again = NativeAssemblyDisplays.networkCopy(view, view.getId());
            require(ItemStack.matches(again.resultPool.get(0).getStack(), view.resultPool.get(0).getStack()),
                    "typed_view_can_roundtrip_native_packet_again");
        }
        JsonObject report = new JsonObject();
        report.addProperty("status", "PASS");
        report.addProperty("input", "native packet decoded assemblies, then a second native clone per JEI view");
        report.addProperty("printing_views_checked", printingChecks);
        report.addProperty("ascension_views_checked", ascensionChecks);
        report.addProperty("native_and_received_manager_bytes_unchanged", true);
        report.addProperty("all_steps_and_mutable_ingredient_lists_independent", true);
        report.addProperty("strict_sample_nbt_and_exact_native_ink_preserved", true);
        report.addProperty("typed_views_native_packet_roundtrip", true);
        return report;
    }

    private static void exactInk(SequencedAssemblyRecipe view, int index,
                                 net.minecraft.world.level.material.Fluid fluid, int amount) {
        var ingredients = view.getSequence().get(index).getRecipe().getFluidIngredients();
        require(ingredients.size() == 1 && ingredients.get(0).getRequiredAmount() == amount,
                "typed_view_has_one_exact_ink_amount");
        var stacks = ingredients.get(0).getMatchingFluidStacks();
        require(stacks.size() == 1 && stacks.get(0).getFluid().isSame(fluid) && stacks.get(0).getAmount() == amount,
                "typed_view_has_only_its_actual_native_ink");
    }

    private static void ownSteps(SequencedAssemblyRecipe view, IdentityHashMap<Object, Boolean> owned) {
        require(owned.put(view.getSequence(), true) == null, "view_owns_sequence_list");
        for (var entry : view.getSequence()) {
            var step = entry.getRecipe();
            require(owned.put(step, true) == null && owned.put(step.getIngredients(), true) == null
                            && owned.put(step.getFluidIngredients(), true) == null,
                    "view_owns_each_mutable_step_and_ingredient_list");
        }
    }

    private static byte[] bytes(SequencedAssemblyRecipe recipe) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ((SequencedAssemblyRecipeSerializer) recipe.getSerializer()).toNetwork(buffer, recipe);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native assembly network clone: " + message);
    }
}
