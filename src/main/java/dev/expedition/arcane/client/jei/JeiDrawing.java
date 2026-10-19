package dev.expedition.arcane.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

final class JeiDrawing {
    static final int TEXT = 0x404040;
    static final int MUTED = 0x606060;
    private JeiDrawing() {}

    static IRecipeSlotBuilder item(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, ItemStack stack) {
        return builder.addSlot(role, x, y).setStandardSlotBackground().addItemStack(stack.copy());
    }

    static IRecipeSlotBuilder fluid(IRecipeLayoutBuilder builder, int x, int y, Fluid fluid, long quarterUnits) {
        long rounded = Math.max(1, (quarterUnits + 3) / 4);
        return builder.addSlot(RecipeIngredientRole.INPUT, x, y).setStandardSlotBackground()
                .addFluidStack(fluid, rounded).setFluidRenderer(rounded, false, 16, 16)
                .addTooltipCallback((slot, tooltip) -> {
                    tooltip.clear();
                    tooltip.add(fluid.getFluidType().getDescription());
                    tooltip.add(Component.literal("精确耗量：" + ArcaneRecipeDisplays.millibuckets(quarterUnits) + " mB"));
                    if (quarterUnits % 4 != 0) tooltip.add(Component.literal("小数余量保留，连续加工不会多扣原液。"));
                });
    }

    static void text(GuiGraphics graphics, String message, int x, int y) {
        graphics.drawString(Minecraft.getInstance().font, Component.literal(message), x, y, TEXT, false);
    }

    static void wrap(GuiGraphics graphics, String message, int x, int y, int width) {
        graphics.drawWordWrap(Minecraft.getInstance().font, Component.literal(message), x, y, width, MUTED);
    }

    static ItemStack registeredItem(String id) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
