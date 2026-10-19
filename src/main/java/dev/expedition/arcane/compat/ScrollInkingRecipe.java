package dev.expedition.arcane.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.network.FriendlyByteBuf;
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

/** A native Create filling recipe whose final spell comes from the input scroll's validated payload. */
public final class ScrollInkingRecipe extends FillingRecipe {
    public static final ResourceLocation PRINTING_INKS = new ResourceLocation("arcane_industry", "printing_inks");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS, ArcaneIndustry.MOD_ID);
    public static final RegistryObject<ProcessingRecipeSerializer<ScrollInkingRecipe>> SERIALIZER = SERIALIZERS.register(
            "scroll_inking", () -> new ProcessingRecipeSerializer<>(ScrollInkingRecipe::new));
    public static final RegistryObject<ProcessingRecipeSerializer<AssemblyPrintingRecipe>> PRINTING_SERIALIZER = SERIALIZERS.register(
            "assembly_scroll_printing", () -> new ProcessingRecipeSerializer<>(AssemblyPrintingRecipe::new));

    public ScrollInkingRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        return super.matches(inventory, level) && UninkedScrollItem.isInkingStage(inventory.getItem(0));
    }

    @Override
    public ItemStack assemble(RecipeWrapper inventory, net.minecraft.core.RegistryAccess access) {
        return UninkedScrollItem.isInkingStage(inventory.getItem(0))
                ? UninkedScrollItem.finishInking(inventory.getItem(0)) : ItemStack.EMPTY;
    }

    @Override
    public void readAdditional(JsonObject json) {
        super.readAdditional(json);
        validatePrintingInk();
    }

    @Override
    public void readAdditional(FriendlyByteBuf buffer) {
        super.readAdditional(buffer);
        validatePrintingInk();
    }

    private void validatePrintingInk() {
        if (fluidIngredients.size() != 1 || getRequiredFluid().getRequiredAmount() != UninkedScrollItem.INK_AMOUNT) {
            throw new JsonSyntaxException("Scroll inking requires exactly 250 mB of native ink");
        }
        // Tags may not be bound while recipes load. Validate the declared tag, not its current expansion.
        JsonObject ingredient = getRequiredFluid().serialize();
        if (ingredient.has("fluidTag") && PRINTING_INKS.toString().equals(ingredient.get("fluidTag").getAsString())) return;
        // JEI creates per-spell display copies with the one actual native ink instead of the generic tag.
        if (ingredient.has("fluid")) {
            ResourceLocation id = ResourceLocation.tryParse(ingredient.get("fluid").getAsString());
            for (SpellRarity rarity : SpellRarity.values()) {
                if (id != null && id.equals(ForgeRegistries.FLUIDS.getKey(InkItem.getInkForRarity(rarity).fluid().get()))) return;
            }
        }
        throw new JsonSyntaxException("Scroll inking requires the printing_inks tag or one native ink fluid");
    }

    private static Optional<FillingRecipe> findOwnedRecipe(Level level, ItemStack input) {
        if (level == null || !UninkedScrollItem.isInkingStage(input)) return Optional.empty();
        var expected = UninkedScrollItem.readRequiredInk(input);
        if (expected.isEmpty()) return Optional.empty();
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(handler);
        RecipeType<FillingRecipe> type = AllRecipeTypes.FILLING.getType();
        return SequencedAssemblyRecipe.getRecipe(level, wrapper, type, FillingRecipe.class,
                candidate -> candidate instanceof ScrollInkingRecipe
                        && candidate.getId().toString().equals(UninkedScrollItem.ASSEMBLY_ID + "_step_1")
                        && candidate.matches(wrapper, level)
                        && candidate.getRequiredFluid().getRequiredAmount() == UninkedScrollItem.INK_AMOUNT
                        && candidate.getRequiredFluid().test(new FluidStack(expected.get(), UninkedScrollItem.INK_AMOUNT)));
    }

    public static boolean canFillOwnedItem(Level level, ItemStack input) {
        return findOwnedRecipe(level, input).isPresent();
    }

    /** Wrong ink and short tanks cannot begin a spout cycle; quoting never consumes anything. */
    public static int requiredInkAmount(Level level, ItemStack input, FluidStack available) {
        var expected = UninkedScrollItem.readRequiredInk(input);
        return expected.isPresent() && !available.isEmpty() && available.getAmount() >= UninkedScrollItem.INK_AMOUNT
                && expected.get().isSame(available.getFluid()) && canFillOwnedItem(level, input)
                ? UninkedScrollItem.INK_AMOUNT : -1;
    }

    /** Called only for our item by the narrow spout hook; every failure leaves item and fluid untouched. */
    public static ItemStack fillOwnedItem(Level level, int requiredAmount, ItemStack input, FluidStack available) {
        if (requiredAmount != UninkedScrollItem.INK_AMOUNT
                || requiredInkAmount(level, input, available) != requiredAmount) return ItemStack.EMPTY;
        var recipe = findOwnedRecipe(level, input);
        if (recipe.isEmpty()) return ItemStack.EMPTY;
        // Native advance resolves completion; the owned assembly hook supplies the selected spell.
        var results = recipe.get().rollResults();
        if (results.size() != 1) return ItemStack.EMPTY;
        ItemStack output = results.get(0);
        var expected = UninkedScrollItem.readSpell(input);
        ISpellContainer container = ISpellContainer.isSpellContainer(output) ? ISpellContainer.get(output) : null;
        if (!UninkedScrollItem.isPrintedScroll(output) || output.getCount() != 1 || container == null
                || container.getActiveSpellCount() != 1 || expected.isEmpty()
                || !container.getSpellAtIndex(0).getSpell().equals(expected.get())
                || container.getSpellAtIndex(0).getLevel() != 1) return ItemStack.EMPTY;
        available.shrink(requiredAmount);
        input.shrink(1);
        return output;
    }
}
