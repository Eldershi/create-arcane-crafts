package dev.expedition.arcane.client.jei;

import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.compat.jei.category.animations.AnimatedMixer;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One basin operation; the legacy sequence is only the NBT-specific display carrier. */
final class AscensionAssemblyCategory implements IRecipeCategory<SequencedAssemblyRecipe> {
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final AnimatedMixer mixer = new AnimatedMixer();

    AscensionAssemblyCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(210, 204);
        icon = gui.createDrawableItemStack(new ItemStack(ArcaneContent.CHAMBER.get()));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<SequencedAssemblyRecipe> getRecipeType() { return ArcaneJeiPlugin.ASCENSION; }
    @Override public Component getTitle() {
        return Component.translatable("jei.arcane_industry.category.scroll_ascension");
    }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(SequencedAssemblyRecipe recipe) { return recipe.getId(); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, SequencedAssemblyRecipe recipe, IFocusGroup focus) {
        NativeAssemblyDisplays.ascensionView(recipe).ifPresent(display -> {
            var upgrade = display.upgrade();
            JeiDrawing.item(builder, RecipeIngredientRole.INPUT, 8, 23, upgrade.input());
            ItemStack prepared = dev.expedition.arcane.item.UnfinishedAscensionScrollItem.create(upgrade.input());
            if (!prepared.isEmpty()) {
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(prepared.copy());
                builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(prepared.copy());
            }
            JeiDrawing.fluid(builder, 32, 23, upgrade.inkFluid(), ScrollUpgradeRules.INK_AMOUNT * 4L)
                    .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("与卷轴一同加入工作盆。")));
            JeiDrawing.item(builder, RecipeIngredientRole.OUTPUT, 181, 23, upgrade.output())
                    .setOutputSlotBackground();
            JeiDrawing.item(builder, RecipeIngredientRole.CATALYST, 8, 141,
                            new ItemStack(ArcaneContent.CHAMBER.get()))
                    .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("下方安装升华室；模板仅用于锻造升华室。")));
            JeiDrawing.fluid(builder, 56, 141,
                            display.crimson() ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(),
                            upgrade.solutionCostQuarterMb())
                    .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("供给下方升华室，成功时扣除。")));
            if (upgrade.requiresCake()) {
                JeiDrawing.item(builder, RecipeIngredientRole.INPUT, 104, 141,
                                new ItemStack(CrimsonContent.EMBER_CAKE.get()))
                        .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("超越升级成功时消耗一个。")));
                JeiDrawing.item(builder, RecipeIngredientRole.CATALYST, 152, 141, new ItemStack(Items.LIGHTNING_ROD))
                        .addTooltipCallback((slot, tooltip) -> {
                            tooltip.add(Component.literal("超越升级的环境条件，不消耗。"));
                            tooltip.add(Component.literal(AscensionChamberBlockEntity.LIGHTNING_ROD_RANGE
                                    + " 格内放置露天避雷针，避雷针须在所在列最高处。"));
                            tooltip.add(Component.literal("完成超越升级时引雷一次；无避雷针会击死烈焰人，本次材料保留。"));
                        });
            }
        });
    }

    @Override public void draw(SequencedAssemblyRecipe recipe, IRecipeSlotsView slots,
                               GuiGraphics graphics, double mouseX, double mouseY) {
        NativeAssemblyDisplays.ascensionView(recipe).ifPresent(display -> {
            var upgrade = display.upgrade();
            JeiDrawing.text(graphics, "动力升华 · 目标 " + upgrade.targetLevel() + " 级", 7, 3);
            JeiDrawing.text(graphics, "卷轴 + 墨水", 7, 45);
            JeiDrawing.text(graphics, ScrollUpgradeRules.INK_AMOUNT + " mB", 32, 56);
            arrow.draw(graphics, 145, 22);
            drawChamber(graphics, 112, 43);
            mixer.draw(graphics, 112, 43);
            JeiDrawing.text(graphics, "动力搅拌器", 6, 68);
            JeiDrawing.text(graphics, "工作盆", 6, 88);
            JeiDrawing.text(graphics, "下方升华室", 6, 108);
            JeiDrawing.text(graphics, "升华室", 5, 164);
            JeiDrawing.text(graphics, ArcaneRecipeDisplays.millibuckets(upgrade.solutionCostQuarterMb()) + " mB", 53, 164);
            if (upgrade.requiresCake()) {
                JeiDrawing.text(graphics, "余烬蛋糕", 101, 164);
                JeiDrawing.text(graphics, "露天避雷针", 149, 164);
            }
            JeiDrawing.wrap(graphics, "手动：无需工作盆，放入卷轴与同阶墨水瓶，加工 3.5 秒。", 7, 182, 196);
        });
    }

    private static void drawChamber(GuiGraphics graphics, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 180);
        pose.mulPose(Axis.XP.rotationDegrees(-15.5f));
        pose.mulPose(Axis.YP.rotationDegrees(22.5f));
        AnimatedKinetics.defaultBlockElement(ArcaneContent.CHAMBER.get().defaultBlockState())
                .atLocal(0, 2.65, 0).scale(23).render(graphics);
        pose.popPose();
    }
}
