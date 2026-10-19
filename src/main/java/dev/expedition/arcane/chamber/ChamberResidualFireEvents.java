package dev.expedition.arcane.chamber;

import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Consume the first empty-hand strike as extinguishing, before creative or survival block breaking. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChamberResidualFireEvents {
    private ChamberResidualFireEvents() {}

    @SubscribeEvent
    public static void leftClickEmptyFire(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !event.getEntity().getMainHandItem().isEmpty() || event.getEntity().isSpectator()
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof AscensionChamberBlockEntity chamber)
                || !chamber.hasResidualFire()) return;
        if (event.getEntity() instanceof ServerPlayer player
                && (!player.canReach(event.getPos(), 1.5) || !event.getLevel().mayInteract(player, event.getPos()))) return;
        // Forge's client prediction still sends START_DESTROY_BLOCK when this event is cancelled.
        // Cancelling on both sides prevents creative instant break; the server owns the flame state.
        event.setCanceled(true);
        if (!event.getLevel().isClientSide) chamber.extinguishResidualFire();
    }
}
