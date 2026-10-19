package dev.expedition.arcane.client;

import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rebuilds the generated book meshes whenever dependency resources or resource packs reload. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BookMeshReloadListener {
    private BookMeshReloadListener() {}

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) AnimatedSpellBookMeshes::reload);
    }
}
