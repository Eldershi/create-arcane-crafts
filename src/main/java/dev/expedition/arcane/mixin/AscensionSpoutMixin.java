package dev.expedition.arcane.mixin;

import com.simibubi.create.content.fluids.spout.FillingBySpout;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Upgrade ink is committed with the chamber operation, so a spout cannot pre-ink its input. */
@Mixin(value = FillingBySpout.class, remap = false)
public abstract class AscensionSpoutMixin {
    @Unique
    private static boolean arcaneIndustry$ownsAscensionInput(ItemStack input) {
        return input.is(ItemRegistry.SCROLL.get()) && UnfinishedAscensionScrollItem.isRawSource(input);
    }

    @Inject(method = "canItemBeFilled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$canPrepareAscension(Level level, ItemStack input,
                                                          CallbackInfoReturnable<Boolean> callback) {
        if (input.getItem() instanceof UnfinishedAscensionScrollItem) {
            callback.setReturnValue(false);
        } else if (arcaneIndustry$ownsAscensionInput(input)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getRequiredAmountForItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$targetInkAmount(Level level, ItemStack input, FluidStack available,
                                                      CallbackInfoReturnable<Integer> callback) {
        if (input.getItem() instanceof UnfinishedAscensionScrollItem) {
            callback.setReturnValue(-1);
        } else if (arcaneIndustry$ownsAscensionInput(input)) {
            callback.setReturnValue(-1);
        }
    }

    @Inject(method = "fillItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcaneIndustry$prepareAscension(Level level, int requiredAmount, ItemStack input,
                                                       FluidStack available, CallbackInfoReturnable<ItemStack> callback) {
        if (input.getItem() instanceof UnfinishedAscensionScrollItem) {
            callback.setReturnValue(ItemStack.EMPTY);
        } else if (arcaneIndustry$ownsAscensionInput(input)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
