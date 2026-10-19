package dev.expedition.arcane.mixin;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.expedition.arcane.item.UninkedScrollItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the sample-selected spell through the final native Create assembly result roll. */
@Mixin(value = SequencedAssemblyRecipe.class, remap = false)
public abstract class PrintedScrollAssemblyMixin {
    @Inject(method = "advance", at = @At("RETURN"), cancellable = true, remap = false)
    private void arcaneIndustry$finishPrintedScroll(ItemStack input, CallbackInfoReturnable<ItemStack> callback) {
        SequencedAssemblyRecipe recipe = (SequencedAssemblyRecipe) (Object) this;
        if (UninkedScrollItem.ASSEMBLY_ID.equals(recipe.getId()) && recipe.getLoops() == 1
                && recipe.getSequence().size() == 2 && UninkedScrollItem.isInkingStage(input)) {
            callback.setReturnValue(UninkedScrollItem.finishInking(input));
        }
    }
}
