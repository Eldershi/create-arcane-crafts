package dev.expedition.arcane.mixin;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.expedition.arcane.compat.ChamberMixingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BasinRecipe.class, remap = false)
public abstract class AscensionBasinRecipeMixin {
    @Inject(method = "match", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$match(BasinBlockEntity basin, Recipe<?> recipe,
                                            CallbackInfoReturnable<Boolean> callback) {
        if (recipe instanceof ChamberMixingRecipe mixing) callback.setReturnValue(mixing.matchesChamber(basin));
    }

    @Inject(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$commit(BasinBlockEntity basin, Recipe<?> recipe,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (recipe instanceof ChamberMixingRecipe mixing) callback.setReturnValue(mixing.commit(basin));
    }
}
