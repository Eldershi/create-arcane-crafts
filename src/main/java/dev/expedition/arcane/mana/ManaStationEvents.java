package dev.expedition.arcane.mana;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ManaStationEvents {
    private static final double SURFACE_HEIGHT = 13.0 / 16.0;
    private static final double FOOT_TOLERANCE = 0.04;

    private ManaStationEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
            || !player.onGround() || player.isPassenger() || player.isSpectator()) {
            return;
        }
        AABB body = player.getBoundingBox();
        double feetY = body.minY;
        int blockY = (int) Math.floor(feetY - SURFACE_HEIGHT + FOOT_TOLERANCE);
        int minX = (int) Math.floor(body.minX + 0.0001);
        int maxX = (int) Math.floor(body.maxX - 0.0001);
        int minZ = (int) Math.floor(body.minZ + 0.0001);
        int maxZ = (int) Math.floor(body.maxZ - 0.0001);
        if (Math.abs(feetY - (blockY + SURFACE_HEIGHT)) > FOOT_TOLERANCE) {
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, blockY, z);
                if (!player.serverLevel().hasChunkAt(pos)
                    || !(player.serverLevel().getBlockEntity(pos) instanceof ItemDrainBlockEntity drain)) {
                    continue;
                }
                float restored = drain.getCapability(ForgeCapabilities.FLUID_HANDLER).map(handler ->
                    ManaSupplyService.supplyFromFluid(player, handler, 50, 10,
                        ManaSupplyService.Channel.STATION)).orElse(0F);
                if (restored > 0) {
                    return;
                }
            }
        }
    }
}
