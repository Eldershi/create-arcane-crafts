package dev.expedition.arcane.testing;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import dev.expedition.arcane.compat.ScrollInkingRecipe;
import io.netty.buffer.Unpooled;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real recipe serializers and the login packet, with initialized server registries and tags. */
public final class RecipeNetworkTests {
    private RecipeNetworkTests() {}

    public static Map<String, Object> run(ServerLevel level) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Recipe<?>> recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        int nested = 0;
        List<String> owned = new ArrayList<>();
        for (Recipe<?> source : recipes) {
            try {
                Recipe<?> decoded = roundTrip(source);
                nested += compare(source, decoded);
                if (source.getId().getNamespace().equals("arcane_industry")) owned.add(source.getId().toString());
            } catch (RuntimeException error) {
                throw new IllegalStateException("Recipe network roundtrip failed for " + source.getId()
                        + " using " + ForgeRegistries.RECIPE_SERIALIZERS.getKey(source.getSerializer()), error);
            }
        }
        require(!recipes.isEmpty(), "real_recipe_manager_is_populated");
        FriendlyByteBuf packetBytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundUpdateRecipesPacket(recipes).write(packetBytes);
            int size = packetBytes.readableBytes();
            ClientboundUpdateRecipesPacket decoded = new ClientboundUpdateRecipesPacket(packetBytes);
            require(packetBytes.readableBytes() == 0, "full_login_recipe_packet_consumed_exactly");
            require(decoded.getRecipes().size() == recipes.size(), "full_login_packet_keeps_every_recipe");
            report.put("loginPacketBytes", size);
        } finally { packetBytes.release(); }

        int nativeInks = 0;
        for (SpellRarity rarity : SpellRarity.values()) {
            roundTrip(fixture("direct_" + rarity.name().toLowerCase(java.util.Locale.ROOT),
                    FluidIngredient.fromFluid(InkItem.getInkForRarity(rarity).fluid().get(), 250)));
            nativeInks++;
        }
        FluidIngredient emptyTag = FluidIngredient.fromTag(FluidTags.create(ScrollInkingRecipe.PRINTING_INKS), 250);
        emptyTag.matchingFluidStacks = List.of();
        var emptyDecoded = (ScrollInkingRecipe) roundTrip(fixture("empty_tag", emptyTag));
        require(emptyDecoded.getRequiredFluid() instanceof FluidIngredient.FluidTagIngredient
                        && emptyDecoded.getRequiredFluid().getMatchingFluidStacks().isEmpty(),
                "empty_server_tag_expansion_is_preserved_without_json_serialization");
        FluidIngredient expandedTag = FluidIngredient.fromTag(FluidTags.create(ScrollInkingRecipe.PRINTING_INKS), 250);
        expandedTag.matchingFluidStacks = List.of(new FluidStack(Fluids.WATER, 250),
                new FluidStack(InkItem.getInkForRarity(SpellRarity.COMMON).fluid().get(), 250));
        var expandedDecoded = (ScrollInkingRecipe) roundTrip(fixture("datapack_tag_expansion", expandedTag));
        require(expandedDecoded.getRequiredFluid().getMatchingFluidStacks().size() == 2,
                "server_authoritative_datapack_tag_expansion_is_preserved");
        rejects(fixture("foreign_direct_fluid", FluidIngredient.fromFluid(Fluids.WATER, 250)), "foreign_direct_fluid_rejected");
        rejects(fixture("wrong_amount", FluidIngredient.fromFluid(
                InkItem.getInkForRarity(SpellRarity.COMMON).fluid().get(), 249)), "wrong_ink_amount_rejected");

        report.put("recipes", recipes.size());
        report.put("sequenceStepsCompared", nested);
        report.put("ownedRecipes", owned);
        report.put("directNativeInkFixtures", nativeInks);
        report.put("emptyTag", "PASS");
        report.put("expandedTag", "PASS");
        report.put("foreignDirectFluid", "REJECTED");
        report.put("wrongAmount", "REJECTED");
        report.put("status", "PASS");
        return report;
    }

    private static ScrollInkingRecipe fixture(String name, FluidIngredient ingredient) {
        return new ProcessingRecipeBuilder<>(ScrollInkingRecipe::new,
                new ResourceLocation("arcane_industry", "network_test/" + name))
                .require(Items.PAPER).require(ingredient).output(Items.PAPER).build();
    }

    private static void rejects(Recipe<?> source, String message) {
        try { roundTrip(source); }
        catch (com.google.gson.JsonSyntaxException expected) { return; }
        throw new IllegalStateException("Expected network validation rejection: " + message);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Recipe<?> roundTrip(Recipe<?> source) {
        RecipeSerializer serializer = source.getSerializer();
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(bytes, source);
            Recipe<?> decoded = serializer.fromNetwork(source.getId(), bytes);
            require(decoded != null, "decoded_recipe_exists");
            require(bytes.readableBytes() == 0, "serializer_consumes_exact_payload_" + source.getId());
            return decoded;
        } finally { bytes.release(); }
    }

    private static int compare(Recipe<?> source, Recipe<?> decoded) {
        require(source.getId().equals(decoded.getId()) && source.getSerializer() == decoded.getSerializer(),
                "recipe_identity_and_serializer_preserved_" + source.getId());
        if (source instanceof ProcessingRecipe<?> original && decoded instanceof ProcessingRecipe<?> copy) {
            require(original.getFluidIngredients().size() == copy.getFluidIngredients().size(), "fluid_count_" + source.getId());
            for (int i = 0; i < original.getFluidIngredients().size(); i++) {
                var a = original.getFluidIngredients().get(i);
                var b = copy.getFluidIngredients().get(i);
                require(a.getRequiredAmount() == b.getRequiredAmount(), "fluid_amount_" + source.getId());
                require(fluidIds(a).equals(fluidIds(b)), "expanded_fluid_choices_" + source.getId());
            }
        }
        int steps = 0;
        if (source instanceof SequencedAssemblyRecipe original && decoded instanceof SequencedAssemblyRecipe copy) {
            require(original.getSequence().size() == copy.getSequence().size(), "sequence_size_" + source.getId());
            for (int i = 0; i < original.getSequence().size(); i++) {
                compare(original.getSequence().get(i).getRecipe(), copy.getSequence().get(i).getRecipe());
                steps++;
            }
        }
        return steps;
    }

    private static List<String> fluidIds(FluidIngredient ingredient) {
        return ingredient.getMatchingFluidStacks().stream().map(stack ->
                ForgeRegistries.FLUIDS.getKey(stack.getFluid()) + "@" + stack.getAmount()).sorted().toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
