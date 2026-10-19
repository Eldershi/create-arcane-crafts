package dev.expedition.arcane.mixin.client;

import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-only access to the extension slot Forge initializes in FluidType's constructor. */
@Mixin(value = FluidType.class, remap = false)
public interface NativeInkFluidTypeAccessor {
    @Accessor(value = "renderProperties", remap = false)
    void arcane$setClientExtension(Object extension);
}
