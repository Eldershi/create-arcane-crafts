package dev.expedition.arcane.client;

import io.redspace.ironsspellbooks.particle.CleanseParticle;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.CrimsonContent;
import dev.expedition.arcane.client.assets.RuntimeAssetPack;
import net.minecraftforge.event.AddPackFindersEvent;
import dev.expedition.arcane.grinding.ArcaneGrindingContent;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArcaneClientEvents {
    private ArcaneClientEvents() {}

    @SubscribeEvent public static void registerRuntimeAssets(AddPackFindersEvent event) {
        RuntimeAssetPack.register(event);
    }

    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ArcaneContent.CHAMBER_ENTITY.get(), AscensionChamberRenderer::new);
        event.registerBlockEntityRenderer(ArcaneGrindingContent.WHEEL_ENTITY.get(), KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ArcaneGrindingContent.DRAIN_ENTITY.get(), ArcaneGrindstoneDrainRenderer::new);
    }

    @SubscribeEvent public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AscensionChamberRenderer.BROWS_LAYER, AscensionChamberRenderer::createBrowsLayer);
    }

    @SubscribeEvent public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ArcaneContent.CLEANSE_GOLD.get(), sprites -> {
            AscensionCompletionParticle.sprites(false, sprites);
            return new CleanseParticle.Provider(sprites);
        });
        event.registerSpriteSet(ArcaneContent.CLEANSE_BLUE.get(), sprites -> {
            AscensionCompletionParticle.sprites(true, sprites);
            return new CleanseParticle.Provider(sprites);
        });
    }

    @SubscribeEvent public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NativeInkVisuals.install();
            ItemBlockRenderTypes.setRenderLayer(ArcaneContent.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ArcaneContent.FLOWING.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(CrimsonContent.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(CrimsonContent.FLOWING.get(), RenderType.translucent());
        });
    }
}
