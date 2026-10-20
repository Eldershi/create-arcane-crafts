package dev.expedition.arcane.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Unlit, fog-aware local effects. These states never replace Minecraft's post chain. */
final class ChamberGlowRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> CORE = Util.memoize(texture -> create(
            "arcane_reveal_core", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096,
            false, true, CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));
    private static final Function<ResourceLocation, RenderType> HALO = Util.memoize(texture -> create(
            "arcane_reveal_halo", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096,
            false, true, CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    // Vanilla eyes() is ONE + ONE: fading alpha there would not fade its RGB.
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private ChamberGlowRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                   int size, boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sorted, setup, clear);
    }

    static RenderType core(ResourceLocation texture) { return CORE.apply(texture); }
    static RenderType halo(ResourceLocation texture) { return HALO.apply(texture); }
}
