package dev.expedition.arcane.client.jei;

import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.chamber.RandomResearchRules;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.item.crafting.Ingredient;

/** Four honest probability views per live, researchable school, using the machine's own pool. */
final class RandomResearchCategory implements IRecipeCategory<RandomResearchCategory.View> {
    record View(RandomResearchRules.Pool pool, boolean crimson, boolean headwear) {}
    private final IDrawable background, icon, arrow;
    RandomResearchCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(238, 188);
        icon = gui.createDrawableItemStack(new ItemStack(ArcaneContent.CHAMBER.get()));
        arrow = gui.getRecipeArrow();
    }
    static List<View> views() {
        List<View> views = new ArrayList<>();
        for (var pool : RandomResearchRules.pools()) if (pool.canResearch())
            for (boolean crimson : new boolean[]{false, true}) for (boolean headwear : new boolean[]{false, true})
                views.add(new View(pool, crimson, headwear));
        return List.copyOf(views);
    }
    @Override public RecipeType<View> getRecipeType() { return ArcaneJeiPlugin.RESEARCH; }
    @Override public Component getTitle() { return Component.translatable("jei.arcane_industry.category.random_research"); }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(View view) {
        return ArcaneRecipeDisplays.id("research/" + view.pool().school().getId().getNamespace() + "/"
                + view.pool().school().getId().getPath() + "/" + (view.crimson() ? "red" : "blue")
                + (view.headwear() ? "_headwear" : "_base"));
    }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, View view, IFocusGroup focus) {
        JeiDrawing.item(builder, RecipeIngredientRole.INPUT, 6, 24, new ItemStack(ItemRegistry.SCROLL.get()));
        builder.addSlot(RecipeIngredientRole.INPUT, 35, 24).setStandardSlotBackground()
                .addIngredients(Ingredient.of(view.pool().school().getFocus()))
                .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("每次研习消耗一个学派引导物。")));
        JeiDrawing.fluid(builder, 65, 24, view.crimson() ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 2000);
        if (view.headwear()) builder.addSlot(RecipeIngredientRole.CATALYST, 135, 24).setStandardSlotBackground()
                .addItemStacks(List.of(JeiDrawing.registeredItem("create:goggles"),
                        JeiDrawing.registeredItem("irons_spellbooks:tarnished_helmet")))
                .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("室内安装任一种；同效互斥，不损耗。")));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 207, 24).setOutputSlotBackground()
                .addItemStacks(view.pool().tiers().stream().flatMap(List::stream)
                        .map(spell -> ArcaneRecipeDisplays.scroll(spell, 1)).toList())
                .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("先抽品级，再在对应档均匀抽取；成品固定原生1级。")));
    }
    @Override public void draw(View view, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        JeiDrawing.text(graphics, view.pool().school().getDisplayName().getString()
                + " · " + (view.crimson() ? "猩红" : "奥术") + (view.headwear() ? " · 头饰" : " · 基础"), 5, 5);
        arrow.draw(graphics, 174, 24);
        int[] weights = RandomResearchRules.weights(view.crimson(), view.headwear());
        for (int tier = 0; tier < 5; tier++) {
            int target = tier;
            while (target > 0 && view.pool().tiers().get(target).isEmpty()) target--;
            String fallback = target == tier ? "" : " → " + SpellRarity.values()[target].getDisplayName().getString();
            JeiDrawing.text(graphics, SpellRarity.values()[tier].getDisplayName().getString() + " "
                    + String.format(Locale.ROOT, "%s%%", weights[tier] / 100.0) + " · "
                    + view.pool().tiers().get(tier).size() + "种" + fallback, 7, 53 + tier * 12);
        }
        JeiDrawing.wrap(graphics, "消耗1张空白卷轴、1个引导物与500 mB原液。161刻。开始锁定结果，缺档只向下回落。", 5, 118, 230);
        JeiDrawing.wrap(graphics, "普通档为空则不开工。法术池实时遵守启用、允许制作、无需研习；邪术排除。", 5, 146, 230);
    }
}
