package dev.expedition.arcane.compat;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.Optional;

/** A per-basin native mixing candidate. Only the chamber transaction may commit its dynamic NBT. */
public final class ChamberMixingRecipe extends MixingRecipe {
    private BlockPos chamberPos;
    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;

    private ChamberMixingRecipe(ProcessingRecipeBuilder.ProcessingRecipeParams params) { super(params); }

    public static Optional<ChamberMixingRecipe> find(MechanicalMixerBlockEntity mixer) {
        Level level = mixer.getLevel();
        if (level == null || level.isClientSide || mixer.getSpeed() == 0 || !mixer.isSpeedRequirementFulfilled()
                || !(level.getBlockEntity(mixer.getBlockPos().below(2)) instanceof BasinBlockEntity basin)
                || !(level.getBlockEntity(basin.getBlockPos().below()) instanceof AscensionChamberBlockEntity chamber)
                || !chamber.canProcessBasinMixing(basin)) return Optional.empty();
        ItemStack input = chamber.getBasinMixingInput(basin), output = chamber.getBasinMixingOutput(basin);
        if (input.isEmpty() || output.isEmpty()) return Optional.empty();
        FluidStack ink = chamber.getBasinMixingInk(basin);
        var builder = new ProcessingRecipeBuilder<>(ChamberMixingRecipe::new, ArcaneIndustry.id("chamber_mixing"))
                .require(Ingredient.of(input)).output(output).duration(70);
        if (!ink.isEmpty()) builder.require(ink.getFluid(), ink.getAmount());
        ChamberMixingRecipe recipe = builder.build();
        recipe.chamberPos = chamber.getBlockPos().immutable();
        recipe.input = input.copyWithCount(1); recipe.output = output.copy();
        return recipe.matchesChamber(basin) ? Optional.of(recipe) : Optional.empty();
    }

    public boolean matchesChamber(BasinBlockEntity basin) {
        Level level = basin.getLevel();
        if (level == null || level.isClientSide || chamberPos == null
                || !basin.getBlockPos().below().equals(chamberPos)
                || !(level.getBlockEntity(chamberPos) instanceof AscensionChamberBlockEntity chamber)
                || !(level.getBlockEntity(basin.getBlockPos().above(2)) instanceof MechanicalMixerBlockEntity mixer)
                || mixer.getSpeed() == 0 || !mixer.isSpeedRequirementFulfilled()
                || !chamber.canProcessBasinMixing(basin) || basin.getFilter() == null
                || !basin.getFilter().test(output)) return false;
        return ItemStack.isSameItemSameTags(input, chamber.getBasinMixingInput(basin))
                && ItemStack.isSameItemSameTags(output, chamber.getBasinMixingOutput(basin));
    }

    public boolean commit(BasinBlockEntity basin) {
        if (!matchesChamber(basin)) return false;
        Level level = basin.getLevel();
        MechanicalMixerBlockEntity mixer = (MechanicalMixerBlockEntity) level.getBlockEntity(basin.getBlockPos().above(2));
        // Native mixer calls apply only when its lowered, powered processing phase finishes.
        if (!mixer.running || mixer.runningTicks < 20 || mixer.runningTicks > 21) return false;
        return ((AscensionChamberBlockEntity) level.getBlockEntity(chamberPos)).processBasinMixingCycle(basin);
    }
}
