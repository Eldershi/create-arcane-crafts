package dev.expedition.arcane.mixin;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.compat.ScrollAscensionRecipes;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves a full source scroll across native progress and gates the final native result. */
@Mixin(value = SequencedAssemblyRecipe.class, remap = false)
public abstract class ScrollAscensionAssemblyMixin {
    @Inject(method = "advance", at = @At("RETURN"), cancellable = true, remap = false)
    private void arcaneIndustry$ascendScroll(ItemStack input, CallbackInfoReturnable<ItemStack> callback) {
        SequencedAssemblyRecipe recipe = (SequencedAssemblyRecipe) (Object) this;
        if (!UnfinishedAscensionScrollItem.ASSEMBLY_ID.equals(recipe.getId())) return;
        if (recipe.getLoops() != 1 || recipe.getSequence().size() != 2) {
            callback.setReturnValue(ItemStack.EMPTY);
        } else if (UnfinishedAscensionScrollItem.isRawSource(input)) {
            callback.setReturnValue(UnfinishedAscensionScrollItem.wrapNativeStage(callback.getReturnValue(), input));
        } else if (UnfinishedAscensionScrollItem.isPreparedStage(input)) {
            callback.setReturnValue(ScrollAscensionRecipes.authorizedResult(input));
        } else {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
