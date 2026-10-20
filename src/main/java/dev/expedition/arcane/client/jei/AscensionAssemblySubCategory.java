package dev.expedition.arcane.client.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The chamber step only; Create owns the complete assembly page, numbering and input/output path. */
public final class AscensionAssemblySubCategory extends SequencedAssemblySubCategory {
    public AscensionAssemblySubCategory() { super(92); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, SequencedRecipe<?> step, IFocusGroup focuses, int x) {
        builder.addSlot(RecipeIngredientRole.CATALYST, x + 7, 15)
                .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                .addItemStack(new ItemStack(ArcaneContent.CHAMBER.get()))
                .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("在工作盆下方安装升华室。")));
        NativeAssemblyDisplays.ascensionStep(step.getRecipe()).ifPresent(display -> {
            var upgrade = display.upgrade();
            // This real first-step output is also accepted directly by the chamber. Index it
            // in both directions so R/U on an in-progress scroll finds its native full sequence.
            ItemStack prepared = UnfinishedAscensionScrollItem.create(upgrade.input());
            if (!prepared.isEmpty()) {
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(prepared.copy());
                builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(prepared.copy());
            }
            JeiDrawing.fluid(builder, x + 36, 15,
                    display.crimson() ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(),
                    upgrade.solutionCostQuarterMb())
                    .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal(
                            display.crimson() ? "使升华室进入突破状态。" : "用于普通升华状态。")));
            if (upgrade.requiresCake()) {
                builder.addSlot(RecipeIngredientRole.INPUT, x + 65, 15)
                        .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                        .addItemStack(new ItemStack(CrimsonContent.EMBER_CAKE.get()))
                        .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("超越升级成功时消耗一个。")));
                builder.addSlot(RecipeIngredientRole.CATALYST, x + 65, 48)
                        .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                        .addItemStack(new ItemStack(Items.LIGHTNING_ROD))
                        .addTooltipCallback((slot, tooltip) -> {
                            tooltip.add(Component.literal("超越升级的环境条件，不消耗。"));
                            tooltip.add(Component.literal(AscensionChamberBlockEntity.LIGHTNING_ROD_RANGE
                                    + " 格内放置露天避雷针，避雷针须在所在列最高处。"));
                            tooltip.add(Component.literal("完成超越升级时引雷一次；无避雷针会击死烈焰人，本次材料保留。"));
                        });
            }
        });
    }

    @Override public void draw(SequencedRecipe<?> step, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        NativeAssemblyDisplays.ascensionStep(step.getRecipe()).ifPresent(display ->
                centered(graphics, ArcaneRecipeDisplays.millibuckets(display.upgrade().solutionCostQuarterMb()) + " mB", 35));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(getWidth() / 2f - 14, 45, 0);
        pose.scale(1.75f, 1.75f, 1.75f);
        graphics.renderItem(new ItemStack(ArcaneContent.CHAMBER.get()), 0, 0);
        pose.popPose();
        centered(graphics, AscensionChamberBlockEntity.PROCESSING_TIME + " tick", 76);
    }

    private void centered(GuiGraphics graphics, String message, int y) {
        JeiDrawing.text(graphics, message, (getWidth() - Minecraft.getInstance().font.width(message)) / 2, y);
    }
}
