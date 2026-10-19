package dev.expedition.arcane.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.client.jei.PrinterAssemblySubCategory;
import dev.expedition.arcane.item.UninkedScrollItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrintingInput;
import plus.dragons.createenchantmentindustry.common.fluids.printer.PrintingRecipe;

import java.util.function.Supplier;

/** CEI's native assembly printing step, restricted to an empty scroll and a valid spell sample. */
public final class AssemblyPrintingRecipe extends PrintingRecipe {
    public AssemblyPrintingRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ScrollInkingRecipe.PRINTING_SERIALIZER.get();
    }

    @Override
    public boolean matches(PrintingInput input, Level level) {
        return UninkedScrollItem.isBlankNativeScroll(input.base())
                && PrinterCompat.readPrintableTemplate(input.template()).isPresent() && super.matches(input, level);
    }

    @Override
    public Component getDescriptionForAssembly() {
        return Component.translatable("recipe.arcane_industry.assembly.scroll_printing");
    }

    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> PrinterAssemblySubCategory::new;
    }

    @Override
    public void readAdditional(JsonObject json) {
        super.readAdditional(json);
        validatePrintingFluid();
    }

    @Override
    public void readAdditional(FriendlyByteBuf buffer) {
        super.readAdditional(buffer);
        validatePrintingFluid();
    }

    private void validatePrintingFluid() {
        if (ingredients.size() != 2 || fluidIngredients.size() != 1
                || fluidIngredients.get(0).getRequiredAmount() != PrinterCompat.PRINTING_FLUID_AMOUNT
                || fluidIngredients.get(0).getMatchingFluidStacks().size() != 1
                || !fluidIngredients.get(0).getMatchingFluidStacks().get(0).getFluid().isSame(ArcaneContent.SOURCE.get())) {
            throw new JsonSyntaxException("Scroll assembly printing requires a sample and exactly 50 mB of arcane solution");
        }
    }
}
