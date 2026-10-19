package dev.expedition.arcane.client.jei;

import com.simibubi.create.AllBlocks;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.compat.NativeScrollRules;
import dev.expedition.arcane.item.UninkedScrollItem;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class ScrollPrintingCategory implements IRecipeCategory<ArcaneRecipeDisplays.Printing> {
    enum Stage { IMPRINTING, INKING }
    private final Stage stage;
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;

    ScrollPrintingCategory(IGuiHelper gui, Stage stage) {
        this.stage = stage;
        background = gui.createBlankDrawable(210, 118);
        var iconStack = stage == Stage.INKING ? AllBlocks.SPOUT.asStack()
                : JeiDrawing.registeredItem("create_enchantment_industry:printer");
        icon = gui.createDrawableItemStack(iconStack);
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<ArcaneRecipeDisplays.Printing> getRecipeType() {
        return switch (stage) {
            case IMPRINTING -> ArcaneJeiPlugin.IMPRINTING;
            case INKING -> ArcaneJeiPlugin.INKING;
        };
    }
    @Override public Component getTitle() {
        return Component.literal(switch (stage) {
            case IMPRINTING -> "卷轴法术压印";
            case INKING -> "卷轴注墨定稿";
        });
    }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(ArcaneRecipeDisplays.Printing recipe) {
        return ArcaneRecipeDisplays.id(stage.name().toLowerCase(java.util.Locale.ROOT) + "/" + recipe.id().getPath());
    }

    private void sample(IRecipeLayoutBuilder builder, ArcaneRecipeDisplays.Printing recipe, int x, int y) {
        builder.addSlot(RecipeIngredientRole.CATALYST, x, y).setStandardSlotBackground().addItemStacks(recipe.samples())
                .addTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.literal("法术样板保留，不消耗。"));
                    tooltip.add(Component.literal("只识别法术，成品固定为 1 级。"));
                });
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, ArcaneRecipeDisplays.Printing recipe, IFocusGroup focus) {
        if (stage == Stage.IMPRINTING) {
            sample(builder, recipe, 8, 7);
            JeiDrawing.item(builder, RecipeIngredientRole.INPUT, 8, 45, recipe.blank());
            JeiDrawing.fluid(builder, 52, 45, ArcaneContent.SOURCE.get(), NativeScrollRules.PRINTING_FLUID_COST * 4L);
            JeiDrawing.item(builder, RecipeIngredientRole.OUTPUT, 170, 45, recipe.uninked()).setOutputSlotBackground();
        } else {
            JeiDrawing.item(builder, RecipeIngredientRole.INPUT, 8, 35, recipe.uninked());
            JeiDrawing.fluid(builder, 68, 35, recipe.inkFluid(), UninkedScrollItem.INK_AMOUNT * 4L);
            JeiDrawing.item(builder, RecipeIngredientRole.OUTPUT, 170, 35, recipe.finished()).setOutputSlotBackground();
        }
    }

    @Override public void draw(ArcaneRecipeDisplays.Printing recipe, IRecipeSlotsView slots,
                               GuiGraphics graphics, double mouseX, double mouseY) {
        if (stage == Stage.IMPRINTING) {
            JeiDrawing.text(graphics, "法术样板（保留）", 34, 10);
            arrow.draw(graphics, 112, 44);
            JeiDrawing.text(graphics, "空白卷轴", 3, 70);
            JeiDrawing.text(graphics, "原液 " + NativeScrollRules.PRINTING_FLUID_COST + " mB", 48, 70);
            JeiDrawing.text(graphics, "未注墨", 163, 70);
            JeiDrawing.wrap(graphics, "法术压印机：样板不消耗，只复制法术种类。", 7, 94, 196);
        } else {
            JeiDrawing.text(graphics, "注墨定稿 → 固定 1 级", 7, 7);
            arrow.draw(graphics, 112, 34);
            JeiDrawing.text(graphics, "未注墨卷轴", 3, 61);
            JeiDrawing.text(graphics, recipe.inkFluid().getFluidType().getDescription().getString(), 65, 61);
            JeiDrawing.text(graphics, "1 级卷轴", 163, 61);
            JeiDrawing.wrap(graphics, "注液器：消耗 " + UninkedScrollItem.INK_AMOUNT + " mB " + recipe.inkFluid().getFluidType().getDescription().getString() + "，产出原生 1 级卷轴。", 7, 86, 196);
        }
    }
}
