package dev.expedition.arcane.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;

/** Native assembly spouting step. The owned spout hook selects the target-level ink dynamically. */
public final class AscensionInkingRecipe extends FillingRecipe {
    public AscensionInkingRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ScrollAscensionRecipes.INKING_SERIALIZER.get();
    }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        return super.matches(inventory, level) && UnfinishedAscensionScrollItem.isRawSource(inventory.getItem(0));
    }

    @Override
    public Component getDescriptionForAssembly() {
        return Component.translatable("recipe.arcane_industry.assembly.ascension_inking");
    }

    @Override
    public void readAdditional(JsonObject json) {
        super.readAdditional(json);
        validateAmount();
    }

    @Override
    public void readAdditional(FriendlyByteBuf buffer) {
        super.readAdditional(buffer);
        validateAmount();
    }

    private void validateAmount() {
        if (ingredients.size() != 1 || fluidIngredients.size() != 1
                || getRequiredFluid().getRequiredAmount() != ScrollUpgradeRules.INK_AMOUNT) {
            throw new JsonSyntaxException("Ascension assembly inking requires one scroll and exactly 250 mB of target ink");
        }
    }
}
