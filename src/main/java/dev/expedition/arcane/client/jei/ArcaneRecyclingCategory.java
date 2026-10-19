package dev.expedition.arcane.client.jei;

import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.grinding.ArcaneGrindingContent;
import dev.expedition.arcane.grinding.ArcaneSalvage;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.item.UpgradeOrbItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/** Scroll and orb indexing uses the actual copy-only salvage rule; equipment is described in info. */
final class ArcaneRecyclingCategory implements IRecipeCategory<ArcaneRecyclingCategory.View> {
    record View(ResourceLocation id, ItemStack input, ItemStack empty, long units, int mode) {}
    private final IDrawable background, icon, arrow;
    ArcaneRecyclingCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(232, 136);
        icon = gui.createDrawableItemStack(new ItemStack(ArcaneGrindingContent.GRINDSTONE_ITEM.get()));
        arrow = gui.getRecipeArrow();
    }
    static List<View> views() {
        List<View> views = new ArrayList<>();
        SpellRegistry.REGISTRY.get().getValues().stream().filter(spell -> !spell.equals(SpellRegistry.none()))
                .sorted(Comparator.comparing(spell -> spell.getSpellResource().toString())).forEach(spell -> {
                    if (spell.getMaxLevel() < 1 || spell.getMaxLevel() > Integer.MAX_VALUE - ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS) return;
                    int ceiling = spell.getMaxLevel() + ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS;
                    for (int level = Math.max(1, spell.getMinLevel()); level <= ceiling; level++) {
                        add(views, "spell/" + spell.getSpellResource().getNamespace() + "/"
                                + spell.getSpellResource().getPath() + "/" + level, ArcaneRecipeDisplays.scroll(spell, level));
                    }
                });
        ForgeRegistries.ITEMS.getEntries().stream().filter(entry -> entry.getValue() instanceof UpgradeOrbItem
                        || entry.getValue() == ItemRegistry.UPGRADE_ORB.get())
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString())).forEach(entry ->
                        add(views, "orb/" + entry.getKey().location().getNamespace() + "/"
                                + entry.getKey().location().getPath(), entry.getValue().getDefaultInstance()));
        return List.copyOf(views);
    }
    private static void add(List<View> views, String path, ItemStack input) {
        var stripped = ArcaneSalvage.strip(input);
        if (!stripped.changed() || stripped.units() <= 0) return;
        for (int mode = 0; mode < 3; mode++) views.add(new View(ArcaneRecipeDisplays.id("recycling/" + path + "/" + mode),
                input.copy(), stripped.stack().copy(), stripped.units(), mode));
    }
    @Override public RecipeType<View> getRecipeType() { return ArcaneJeiPlugin.RECYCLING; }
    @Override public Component getTitle() { return Component.translatable("jei.arcane_industry.category.arcane_recycling"); }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(View view) { return view.id(); }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, View view, IFocusGroup focus) {
        JeiDrawing.item(builder, RecipeIngredientRole.INPUT, 9, 26, view.input());
        if (!view.empty().isEmpty()) JeiDrawing.item(builder, RecipeIngredientRole.OUTPUT, 158, 26, view.empty())
                .addTooltipCallback((slot, tooltip) -> tooltip.add(Component.literal("明确空法术容器，保留容量；不会恢复固有法术。")));
        if (view.mode() == 0) {
            JeiDrawing.item(builder, RecipeIngredientRole.OUTPUT, 204, 26,
                    new ItemStack(ItemRegistry.ARCANE_ESSENCE.get(), (int) view.units()));
        } else {
            JeiDrawing.fluid(builder, 57, 26, view.mode() == 2
                    ? ForgeRegistries.FLUIDS.getValue(new ResourceLocation("irons_spellbooks", "blood")) : Fluids.WATER,
                    view.units() * 1000);
            long amount = view.units() * 250;
            var fluid = view.mode() == 2 ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get();
            builder.addSlot(RecipeIngredientRole.OUTPUT, 204, 26).setOutputSlotBackground()
                    .addFluidStack(fluid, amount).setFluidRenderer(amount, false, 16, 16);
        }
    }
    @Override public void draw(View view, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        JeiDrawing.text(graphics, "去魔 · " + (view.mode() == 0 ? "干磨" : view.mode() == 1 ? "通水" : "通血")
                + " · " + view.units() + " 源质单位", 6, 5);
        arrow.draw(graphics, 105, 26);
        JeiDrawing.text(graphics, view.empty().isEmpty() ? "法球不返还" : "清空法术容器", 142, 53);
        JeiDrawing.wrap(graphics, "普通/卓越/稀有/史诗/传说：1/2/4/8/16；法球8，魔力法球24。按当前实际品级。", 5, 72, 226);
        JeiDrawing.wrap(graphics, view.mode() == 0 ? "奥术砂轮＋奥术分液池，需应力。保留原生配方、附魔和经验处理。"
                : "每单位250 mB输入→250 mB原液；不另掉源质。模式锁定，大额分批，缺液或堵塞暂停。", 5, 105, 226);
    }
}
