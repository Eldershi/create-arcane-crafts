package dev.expedition.arcane.mixin;

import com.simibubi.create.content.fluids.spout.FillingBySpout;
import dev.expedition.arcane.compat.ScrollInkingRecipe;
import dev.expedition.arcane.item.UninkedScrollItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FillingBySpout.class, remap = false)
public abstract class ScrollInkingSpoutMixin {
    @Inject(method = "canItemBeFilled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$canInkPrintedSpell(Level level, ItemStack input,
                                                          CallbackInfoReturnable<Boolean> callback) {
        if (input.getItem() instanceof UninkedScrollItem) {
            callback.setReturnValue(ScrollInkingRecipe.canFillOwnedItem(level, input));
        }
    }

    @Inject(method = "getRequiredAmountForItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$printedInkAmount(Level level, ItemStack input, FluidStack fluid,
                                                       CallbackInfoReturnable<Integer> callback) {
        if (input.getItem() instanceof UninkedScrollItem) {
            callback.setReturnValue(ScrollInkingRecipe.requiredInkAmount(level, input, fluid));
        }
    }

    @Inject(method = "fillItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$inkPrintedSpell(Level level, int requiredAmount, ItemStack input,
                                                       FluidStack fluid, CallbackInfoReturnable<ItemStack> callback) {
        if (input.getItem() instanceof UninkedScrollItem) {
            callback.setReturnValue(ScrollInkingRecipe.fillOwnedItem(level, requiredAmount, input, fluid));
        }
    }
}
