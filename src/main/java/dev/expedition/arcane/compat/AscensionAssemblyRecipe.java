package dev.expedition.arcane.compat;

import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.client.jei.AscensionAssemblySubCategory;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** The chamber's actual native Create assembly step; its transaction authorizes each completion. */
public final class AscensionAssemblyRecipe extends ProcessingRecipe<RecipeWrapper> implements IAssemblyRecipe {
    public AscensionAssemblyRecipe(ProcessingRecipeParams params) {
        super(ScrollAscensionRecipes.TYPE_INFO, params);
    }

    @Override protected int getMaxInputCount() { return 1; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override protected boolean canSpecifyDuration() { return true; }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        return ingredients.size() == 1 && ingredients.get(0).test(inventory.getItem(0))
                && UnfinishedAscensionScrollItem.isPreparedStage(inventory.getItem(0));
    }

    @Override
    public Component getDescriptionForAssembly() {
        return Component.translatable("recipe.arcane_industry.assembly.scroll_ascension");
    }

    @Override
    public void addRequiredMachines(Set<ItemLike> required) {
        required.add(ArcaneContent.CHAMBER_ITEM.get());
    }

    @Override
    public void addAssemblyIngredients(List<Ingredient> list) {
        // Catalyst, fluid cost and conditional cake are shown by the native step subcategory.
    }

    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> AscensionAssemblySubCategory::new;
    }
}
