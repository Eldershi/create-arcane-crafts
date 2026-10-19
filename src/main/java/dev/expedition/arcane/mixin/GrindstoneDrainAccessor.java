package dev.expedition.arcane.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneDrainBlockEntity;

/** Access is used solely by the new subclass's safe destruction path. */
@Mixin(value = GrindstoneDrainBlockEntity.class, remap = false)
public interface GrindstoneDrainAccessor {
    @Accessor("processedItem") void arcane$setProcessedItem(ItemStack stack);
}
