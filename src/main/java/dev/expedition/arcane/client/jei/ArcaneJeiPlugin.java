package dev.expedition.arcane.client.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import dev.expedition.arcane.item.UninkedScrollItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Discovered by JEI only on the client; none of the common registration code loads this class. */
@JeiPlugin
public final class ArcaneJeiPlugin implements IModPlugin {
    public static final RecipeType<SequencedAssemblyRecipe> ASCENSION = RecipeType.create(
            ArcaneIndustry.MOD_ID, "scroll_ascension", SequencedAssemblyRecipe.class);
    public static final RecipeType<ArcaneRecipeDisplays.Printing> IMPRINTING = RecipeType.create(
            ArcaneIndustry.MOD_ID, "scroll_imprinting", ArcaneRecipeDisplays.Printing.class);
    public static final RecipeType<ArcaneRecipeDisplays.Printing> INKING = RecipeType.create(
            ArcaneIndustry.MOD_ID, "scroll_inking", ArcaneRecipeDisplays.Printing.class);
    public static final RecipeType<SequencedAssemblyRecipe> NATIVE_ASSEMBLY = RecipeType.create(
            "create", "sequenced_assembly", SequencedAssemblyRecipe.class);
    public static final RecipeType<RandomResearchCategory.View> RESEARCH = RecipeType.create(
            ArcaneIndustry.MOD_ID, "random_research", RandomResearchCategory.View.class);
    public static final RecipeType<ArcaneRecyclingCategory.View> RECYCLING = RecipeType.create(
            ArcaneIndustry.MOD_ID, "arcane_recycling", ArcaneRecyclingCategory.View.class);
    public static final RecipeType<plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindingRecipe> NATIVE_GRINDING =
            plus.dragons.createenchantmentindustry.integration.jei.category.grinding.GrindingCategory.TYPE;
    static final ResourceLocation TEMPLATE_RECIPE_ID = ArcaneRecipeDisplays.id("ascension_template");
    static final ResourceLocation MANA_UPGRADE_RECIPE_ID = ArcaneRecipeDisplays.id("mana_supply_upgrade");
    private static IJeiRuntime currentRuntime;
    private ArcaneRecipeDisplays.Snapshot snapshot;

    @Override public ResourceLocation getPluginUid() { return ArcaneRecipeDisplays.id("jei_plugin"); }

    private ArcaneRecipeDisplays.Snapshot snapshot() {
        if (snapshot == null) snapshot = ArcaneRecipeDisplays.create();
        return snapshot;
    }

    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Iron already registers its native scroll interpreter using spell ID + level.
        // Do not overwrite it (or treat irrelevant names/lock flags as a new spell).
        registration.registerSubtypeInterpreter(ArcaneContent.UNINKED_SCROLL.get(),
                (IIngredientSubtypeInterpreter<ItemStack>) (stack, context) ->
                        UninkedScrollItem.readSpell(stack).map(spell -> "uninked:" + spell.getSpellId()).orElse(""));
        registration.registerSubtypeInterpreter(ArcaneContent.UNFINISHED_ASCENSION_SCROLL.get(),
                (IIngredientSubtypeInterpreter<ItemStack>) (stack, context) -> UnfinishedAscensionScrollItem.sourceOf(stack)
                        .map(source -> "ascension:" + ArcaneRecipeDisplays.scrollIdentity(source)).orElse(""));
    }

    @Override public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(snapshot().uninkedVariants());
        registration.addExtraItemStacks(snapshot().breakthroughVariants());
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AscensionAssemblyCategory(gui),
                new RandomResearchCategory(gui),
                new ArcaneRecyclingCategory(gui),
                new ScrollPrintingCategory(gui, ScrollPrintingCategory.Stage.IMPRINTING),
                new ScrollPrintingCategory(gui, ScrollPrintingCategory.Stage.INKING));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var displays = snapshot();
        registration.addRecipes(IMPRINTING, displays.printing());
        registration.addRecipes(INKING, displays.printing());
        registration.addRecipes(RESEARCH, RandomResearchCategory.views());
        registration.addRecipes(RECYCLING, ArcaneRecyclingCategory.views());
        var level = Minecraft.getInstance().level;
        var printingAssemblies = level == null ? List.<SequencedAssemblyRecipe>of()
                : NativeAssemblyDisplays.printing(level.getRecipeManager(), displays.printing());
        var ascensionAssemblies = level == null ? List.<SequencedAssemblyRecipe>of()
                : NativeAssemblyDisplays.ascension(level.getRecipeManager(), displays.ascension());
        registration.addRecipes(NATIVE_ASSEMBLY, printingAssemblies);
        registration.addRecipes(ASCENSION, ascensionAssemblies);
        registration.addItemStackInfo(new ItemStack(ArcaneContent.CHAMBER.get()),
                Component.literal("动力加工：卷轴与 250 mB 对应墨水一起加入工作盆，上方动力搅拌器搅拌，下方放置升华室。"),
                Component.literal("也可直接向升华室放入物品与材料，手动加工 70 tick（3.5 秒），无需工作盆；升华模板保留。"),
                Component.literal("手动支持卷轴及书内选定法术升级、固有法术升级、法术注入、法球强化、法术书扩槽、亲和戒指绑定与忏悔石净化。"),
                Component.literal("法术书在 5×5 范围内翻开，指向升华室后滚轮选择法术，右键确认合书，再放入墨水瓶；空手右键优先取物。"),
                Component.literal("成功出成品时扣原液与所需的余烬蛋糕；墨水瓶的空瓶随取物返还。"),
                Component.literal("猩红原液使其进入突破状态；超过法术原生上限的超越升级还需余烬蛋糕与露天避雷针。"),
                Component.literal("烈焰人被雷击后留下带余火的空笼；余火仅用于装饰，不能加工。"),
                Component.literal("空手左键或用铲子右键可扑灭余火；打火石可重新点燃空笼。"),
                Component.literal("用装有烈焰人的燃烧室右键可重新装填，返还空的燃烧室。"));
        registration.addItemStackInfo(new ItemStack(CrimsonContent.EMBER_CAKE.get()),
                Component.literal("原生序列组装：鸡蛋依次装配糖与灰烬源质，冲压后注入猩红原液。"),
                Component.literal("超越原生上限时，每次升一级消耗一个蛋糕，最多超越三级。"));
        registration.addItemStackInfo(new ItemStack(dev.expedition.arcane.ArcaneHatchContent.ITEM.get()),
                Component.literal("附着流体设备侧面，代理对应面的流体能力；自身不储液。"),
                Component.literal("空/满玻璃瓶每份250 mB、桶1000 mB，双向搬移奥术或猩红原液；不足整份或拒收不消耗。"),
                Component.literal("管道可接舱口正面及侧面，仍遵守相连设备该面的输入/输出限制。空手交互不取机器物品。"));
        registration.addItemStackInfo(List.of(new ItemStack(dev.expedition.arcane.EssenceContent.ARCANE_BLOCK_ITEM.get()),
                        new ItemStack(dev.expedition.arcane.EssenceContent.CRIMSON_BLOCK_ITEM.get())),
                Component.literal("九份对应源质压缩为一块，拆回九份。奥术源质使用铁魔法原生物品。"),
                Component.literal("工作台、工作盆压制和动力合成器支持压块；工作台、粉碎轮、Create石磨、森罗物语石磨及动力合成器支持拆块，始终返九份。"),
                Component.literal("压缩不改变源质单位，不进行四倍液体兑换。"));
        registration.addItemStackInfo(new ItemStack(dev.expedition.arcane.EssenceContent.CRIMSON_ESSENCE.get()),
                Component.literal("动力压制：1奥术源质＋250 mB血液→1猩红源质。"),
                Component.literal("动力搅拌／铁魔法炼药锅：1猩红源质＋250 mB水→250 mB猩红原液；既有原液配方保留。"),
                Component.literal("250 mB对应原液可在工作盆压制结晶为1源质；工作台和动力合成器也可用1原液瓶结晶，返还空玻璃瓶。"));
        registration.addItemStackInfo(new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.ARCANE_ESSENCE.get()),
                Component.literal("1奥术源质＋250 mB水可在动力搅拌器或铁魔法炼药锅配制250 mB奥术原液。"),
                Component.literal("250 mB奥术原液可工作盆压制结晶为1源质；1原液瓶可在工作台或动力合成器结晶，返空瓶。九份源质可压块储运。"));
        registration.addItemStackInfo(List.of(new ItemStack(dev.expedition.arcane.grinding.ArcaneGrindingContent.GRINDSTONE_ITEM.get()),
                        new ItemStack(dev.expedition.arcane.grinding.ArcaneGrindingContent.DRAIN_ITEM.get())),
                Component.literal("完整继承CEI动力砂轮的手持抛光、修复、附魔经验、磨制和序列配方行为。奥术回收使用配套奥术分液池。"),
                Component.literal("法术按当前品级回收1/2/4/8/16源质，普通法球8、魔力法球24。粗暴清空，不退法球；固有法术、空槽、扩槽、亲和、名称与耐久不计价值。"),
                Component.literal("分液池朝向轴的一对面接应力；顺时针侧输入水/血，逆时针侧输出原液。原生经验/磨制流体保留独立罐并可由两流体面存取。"),
                Component.literal("每源质价值＋250 mB水/血→250 mB蓝/红原液；湿磨不额外掉源质。模式开工锁定，缺液或堵塞暂停，大额分批存档。"),
                Component.literal("空手依次取回库存，不清空其它成品；自动化可提取成品和源质。拆机将未完成的液体账本封存在分液池物品。"));
        ArcaneIndustry.LOGGER.info("Arcane JEI: {} ascensions, {} native spell assembly views",
                ascensionAssemblies.size(), printingAssemblies.size());
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ArcaneContent.CHAMBER.get(), ASCENSION);
        registration.addRecipeCatalyst(ArcaneContent.CHAMBER.get(), RESEARCH);
        registration.addRecipeCatalyst(dev.expedition.arcane.grinding.ArcaneGrindingContent.GRINDSTONE_ITEM.get(), RECYCLING, NATIVE_GRINDING);
        registration.addRecipeCatalyst(dev.expedition.arcane.grinding.ArcaneGrindingContent.DRAIN_ITEM.get(), RECYCLING, NATIVE_GRINDING);
        registration.addRecipeCatalyst(AllBlocks.MECHANICAL_MIXER.get(), ASCENSION);
        registration.addRecipeCatalyst(AllBlocks.BASIN.get(), ASCENSION);
        ItemStack printer = JeiDrawing.registeredItem("create_enchantment_industry:printer");
        if (!printer.isEmpty()) registration.addRecipeCatalyst(printer, IMPRINTING, NATIVE_ASSEMBLY);
        registration.addRecipeCatalyst(AllBlocks.SPOUT.get(), INKING);
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        currentRuntime = runtime;
        // This server recipe computes its result from the incoming NBT. Create's generic
        // category cannot show that and otherwise presents an incorrect empty-scroll result.
        // Hide only its JEI entry; our per-spell inking entries retain the real level-one output.
        int hidden = runtime.getRecipeManager().getRecipeType(
                new ResourceLocation("create", "spout_filling"), FillingRecipe.class).map(type -> {
            List<FillingRecipe> placeholders = runtime.getRecipeManager().createRecipeLookup(type).includeHidden().get()
                    .filter(recipe -> recipe.getId().equals(ArcaneRecipeDisplays.id("ink_printed_scroll"))).toList();
            runtime.getRecipeManager().hideRecipes(type, placeholders);
            return placeholders.size();
        }).orElse(0);
        ArcaneIndustry.LOGGER.info("Arcane JEI: hid {} dynamic generic inking placeholder(s)", hidden);
        var genericAssemblies = runtime.getRecipeManager().createRecipeLookup(NATIVE_ASSEMBLY).includeHidden().get()
                .filter(recipe -> recipe.getId().equals(NativeAssemblyDisplays.PRINTING_ID)
                        || recipe.getId().equals(NativeAssemblyDisplays.ASCENSION_ID)).toList();
        runtime.getRecipeManager().hideRecipes(NATIVE_ASSEMBLY, genericAssemblies);
        if (Boolean.getBoolean("arcane.prototype")) {
            JeiPrototypeVerification.write(runtime, snapshot(), hidden, genericAssemblies.size());
        }
    }

    /** A narrow client-thread entry point used only by the prototype review request hook. */
    public static boolean showCategory(String name) {
        IJeiRuntime runtime = currentRuntime;
        if (runtime == null || Minecraft.getInstance().level == null) return false;
        RecipeType<?> type = switch (name) {
            case "ascension", "ascension_crimson", "ascension_breakthrough", "ascension_transcendence" -> ASCENSION;
            case "imprinting" -> IMPRINTING;
            case "inking" -> INKING;
            case "research", "random_research" -> RESEARCH;
            case "recycling", "arcane_recycling" -> RECYCLING;
            case "template_recipe", "mana_upgrade_recipe" -> RecipeTypes.CRAFTING;
            case "printing_chain", "cake_chain", "native_assembly" -> NATIVE_ASSEMBLY;
            default -> null;
        };
        if (type == null) return false;
        if (name.equals("template_recipe") || name.equals("mana_upgrade_recipe")) {
            ResourceLocation recipeId = name.equals("template_recipe") ? TEMPLATE_RECIPE_ID : MANA_UPGRADE_RECIPE_ID;
            var recipes = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                    .filter(recipe -> recipe.getId().equals(recipeId)).toList();
            if (recipes.isEmpty()) return false;
            runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING), recipes, List.of());
        } else if (name.equals("printing_chain") || name.equals("cake_chain")) {
            var recipes = runtime.getRecipeManager().createRecipeLookup(NATIVE_ASSEMBLY).get()
                    .filter(recipe -> name.equals("printing_chain") ? NativeAssemblyDisplays.isPrintingView(recipe)
                            : recipe.getId().equals(NativeAssemblyDisplays.CAKE_ID)).toList();
            if (recipes.isEmpty()) return false;
            runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(NATIVE_ASSEMBLY), recipes, List.of());
        } else if (name.startsWith("ascension")) {
            var recipes = runtime.getRecipeManager().createRecipeLookup(ASCENSION).get()
                    .filter(recipe -> NativeAssemblyDisplays.ascensionView(recipe).map(display -> switch (name) {
                        case "ascension_breakthrough", "ascension_transcendence" -> display.upgrade().requiresCake();
                        case "ascension_crimson" -> display.crimson();
                        default -> true;
                    }).orElse(false)).toList();
            if (recipes.isEmpty()) return false;
            runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(ASCENSION), recipes, List.of());
        } else {
            if (runtime.getRecipeManager().createRecipeLookup(type).get().findAny().isEmpty()) return false;
            runtime.getRecipesGui().showTypes(List.of(type));
        }
        return true;
    }

    @Override public void onRuntimeUnavailable() {
        currentRuntime = null;
        snapshot = null;
    }
}
