package dev.expedition.arcane.client.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import plus.dragons.createenchantmentindustry.integration.jei.category.printing.AnimatedPrinter;

/** Only the printer step within Create's native sequenced-assembly layout. */
public final class PrinterAssemblySubCategory extends SequencedAssemblySubCategory {
    private final AnimatedPrinter printer = new AnimatedPrinter();

    public PrinterAssemblySubCategory() { super(36); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, SequencedRecipe<?> step, IFocusGroup focuses, int x) {
        builder.addSlot(RecipeIngredientRole.CATALYST, x, 15)
                .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                .addIngredients(step.getRecipe().getIngredients().get(1))
                .addTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.literal("法术样板保留，不消耗。"));
                    tooltip.add(Component.literal("只识别法术，成品固定为 1 级。"));
                });
        CreateRecipeCategory.addFluidSlot(builder, x + 18, 15, step.getRecipe().getFluidIngredients().get(0));
    }

    @Override public void draw(SequencedRecipe<?> step, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        var fluids = step.getRecipe().getFluidIngredients().get(0).getMatchingFluidStacks();
        if (fluids.isEmpty()) return;
        var pose = graphics.pose();
        printer.offset = index;
        pose.pushPose();
        pose.translate(-7, 50, 0);
        pose.scale(.75f, .75f, .75f);
        printer.withFluid(fluids.get(0)).draw(graphics, getWidth() / 2, 0);
        pose.popPose();
    }
}
