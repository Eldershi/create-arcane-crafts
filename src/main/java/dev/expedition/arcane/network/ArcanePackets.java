package dev.expedition.arcane.network;

import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class ArcanePackets {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ArcaneIndustry.id("chamber"), () -> VERSION, VERSION::equals, VERSION::equals);
    private ArcanePackets() {}

    public static void register() {
        CHANNEL.registerMessage(0, SelectSpell.class, SelectSpell::encode, SelectSpell::decode,
                SelectSpell::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void selectSpell(BlockPos pos, int slot, int revision) {
        CHANNEL.sendToServer(new SelectSpell(pos.immutable(), slot, revision));
    }

    private record SelectSpell(BlockPos pos, int slot, int revision) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos); buffer.writeVarInt(slot); buffer.writeVarInt(revision);
        }
        static SelectSpell decode(FriendlyByteBuf buffer) {
            return new SelectSpell(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !player.isAlive() || player.isSpectator()
                        || slot < 0 || slot >= 256 || revision < 0 || !player.level().hasChunkAt(pos)) return;
                Vec3 center = Vec3.atCenterOf(pos);
                if (Math.abs(player.getX() - center.x) > 2.5
                        || Math.abs(player.getZ() - center.z) > 2.5) return;
                double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
                HitResult target = player.pick(reach, 0, false);
                if (!(target instanceof BlockHitResult block) || target.getType() != HitResult.Type.BLOCK
                        || !block.getBlockPos().equals(pos)
                        || player.getEyePosition().distanceToSqr(block.getLocation()) > reach * reach + 0.01) return;
                if (player.level().getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber) {
                    chamber.selectBookSpell(player, slot, revision);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
