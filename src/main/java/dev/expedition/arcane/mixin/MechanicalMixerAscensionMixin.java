package dev.expedition.arcane.mixin;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import dev.expedition.arcane.compat.ChamberMixingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MechanicalMixerBlockEntity.class, remap = false)
public abstract class MechanicalMixerAscensionMixin {
    @Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true, remap = false)
    private void arcaneIndustry$mixScroll(CallbackInfoReturnable<List<Recipe<?>>> callback) {
        ChamberMixingRecipe.find((MechanicalMixerBlockEntity) (Object) this).ifPresent(recipe -> {
            List<Recipe<?>> matches = new ArrayList<>(callback.getReturnValue());
            matches.add(0, recipe);
            callback.setReturnValue(matches);
        });
    }
}
