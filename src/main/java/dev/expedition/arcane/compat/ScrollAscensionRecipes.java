package dev.expedition.arcane.compat;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

/** Real Create sequencing, with final resource authorization supplied by the chamber transaction. */
public final class ScrollAscensionRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(
            ForgeRegistries.RECIPE_TYPES, ArcaneIndustry.MOD_ID);
    public static final RegistryObject<ProcessingRecipeSerializer<AscensionInkingRecipe>> INKING_SERIALIZER = SERIALIZERS.register(
            "scroll_ascension_inking", () -> new ProcessingRecipeSerializer<>(AscensionInkingRecipe::new));
    public static final RegistryObject<ProcessingRecipeSerializer<AscensionAssemblyRecipe>> ASSEMBLY_SERIALIZER = SERIALIZERS.register(
            "ascension_assembly", () -> new ProcessingRecipeSerializer<>(AscensionAssemblyRecipe::new));
    public static final RegistryObject<RecipeType<AscensionAssemblyRecipe>> ASSEMBLY_TYPE = TYPES.register(
            "ascension_assembly", () -> new RecipeType<>() {
                @Override public String toString() { return ArcaneIndustry.MOD_ID + ":ascension_assembly"; }
            });
    public static final IRecipeTypeInfo TYPE_INFO = new IRecipeTypeInfo() {
        @Override public ResourceLocation getId() { return ArcaneIndustry.id("ascension_assembly"); }
        @Override @SuppressWarnings("unchecked")
        public <T extends RecipeSerializer<?>> T getSerializer() { return (T) ASSEMBLY_SERIALIZER.get(); }
        @Override @SuppressWarnings("unchecked")
        public <T extends RecipeType<?>> T getType() { return (T) ASSEMBLY_TYPE.get(); }
    };
    private static final ThreadLocal<Completion> AUTHORIZED_COMPLETION = new ThreadLocal<>();

    private ScrollAscensionRecipes() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }

    public static Optional<ScrollUpgradeRules.Upgrade> prepareUpgrade(ItemStack input, boolean transcendent, boolean hasCake) {
        Optional<ItemStack> source = input.getItem() instanceof UnfinishedAscensionScrollItem
                ? UnfinishedAscensionScrollItem.sourceOf(input)
                : UnfinishedAscensionScrollItem.isRawSource(input) ? Optional.of(input.copyWithCount(1)) : Optional.empty();
        return source.flatMap(stack -> ScrollUpgradeRules.prepare(stack, transcendent, hasCake));
    }

    private static RecipeWrapper wrapper(ItemStack input) {
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, input.copyWithCount(1));
        return new RecipeWrapper(inventory);
    }

    private static Optional<FillingRecipe> inkingStep(Level level, ItemStack source) {
        if (!UnfinishedAscensionScrollItem.isRawSource(source)) return Optional.empty();
        RecipeWrapper input = wrapper(source);
        RecipeType<FillingRecipe> type = AllRecipeTypes.FILLING.getType();
        return SequencedAssemblyRecipe.getRecipe(level, input, type, FillingRecipe.class,
                recipe -> recipe instanceof AscensionInkingRecipe
                        && recipe.getId().toString().equals(UnfinishedAscensionScrollItem.ASSEMBLY_ID + "_step_0")
                        && recipe.matches(input, level));
    }

    public static boolean canPrepareStage(Level level, ItemStack source) {
        return inkingStep(level, source).isPresent();
    }

    /** A read-only recipe execution. Callers commit the corresponding ink/item transfer atomically. */
    public static ItemStack prepareStage(Level level, ItemStack source) {
        var recipe = inkingStep(level, source);
        if (recipe.isEmpty()) return ItemStack.EMPTY;
        var results = recipe.get().rollResults();
        if (results.size() != 1) return ItemStack.EMPTY;
        ItemStack result = results.get(0);
        var recovered = UnfinishedAscensionScrollItem.sourceOf(result);
        return recovered.isPresent() && ItemStack.isSameItemSameTags(recovered.get(), source)
                ? result.copyWithCount(1) : ItemStack.EMPTY;
    }

    /** Dynamic target ink; the generic sequence's common ink is only its client-neutral placeholder. */
    public static int requiredInkAmount(Level level, ItemStack source, FluidStack available) {
        var upgrade = ScrollUpgradeRules.prepare(source, true, true);
        return upgrade.isPresent() && !available.isEmpty()
                && available.getFluid().isSame(upgrade.get().inkFluid()) && canPrepareStage(level, source)
                ? ScrollUpgradeRules.INK_AMOUNT : 0;
    }

    public static ItemStack fillBySpout(Level level, int requiredAmount, ItemStack source, FluidStack available) {
        if (requiredAmount != ScrollUpgradeRules.INK_AMOUNT || available.getAmount() < requiredAmount
                || requiredInkAmount(level, source, available) != requiredAmount) return ItemStack.EMPTY;
        ItemStack prepared = prepareStage(level, source);
        if (prepared.isEmpty()) return ItemStack.EMPTY;
        source.shrink(1);
        available.shrink(requiredAmount);
        return prepared;
    }

    /** No resources are removed here. Only a chamber with a validated transaction should call this. */
    public static ItemStack finishStage(Level level, ItemStack prepared, boolean transcendent, boolean hasCake) {
        if (!UnfinishedAscensionScrollItem.isPreparedStage(prepared) || AUTHORIZED_COMPLETION.get() != null) {
            return ItemStack.EMPTY;
        }
        var upgrade = prepareUpgrade(prepared, transcendent, hasCake);
        if (upgrade.isEmpty()) return ItemStack.EMPTY;
        RecipeWrapper input = wrapper(prepared);
        var step = SequencedAssemblyRecipe.getRecipe(level, input, ASSEMBLY_TYPE.get(), AscensionAssemblyRecipe.class,
                recipe -> recipe.getId().toString().equals(UnfinishedAscensionScrollItem.ASSEMBLY_ID + "_step_1")
                        && recipe.matches(input, level));
        if (step.isEmpty()) return ItemStack.EMPTY;
        Completion completion = new Completion(input.getItem(0).copy(), upgrade.get().output().copy());
        AUTHORIZED_COMPLETION.set(completion);
        try {
            var results = step.get().rollResults();
            if (!completion.used || results.size() != 1
                    || !ItemStack.isSameItemSameTags(results.get(0), upgrade.get().output())
                    || results.get(0).getCount() != 1) return ItemStack.EMPTY;
            return results.get(0).copy();
        } finally {
            AUTHORIZED_COMPLETION.remove();
        }
    }

    /** Called only by the narrow native advance hook; outside an authorized commit it fails closed. */
    public static ItemStack authorizedResult(ItemStack prepared) {
        Completion completion = AUTHORIZED_COMPLETION.get();
        if (completion == null || completion.used || !ItemStack.isSameItemSameTags(completion.prepared, prepared)) {
            return ItemStack.EMPTY;
        }
        completion.used = true;
        return completion.output.copy();
    }

    private static final class Completion {
        final ItemStack prepared;
        final ItemStack output;
        boolean used;
        Completion(ItemStack prepared, ItemStack output) {
            this.prepared = prepared;
            this.output = output;
        }
    }
}
