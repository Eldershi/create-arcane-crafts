package dev.expedition.arcane.network;

import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.chamber.ChamberDisplayHit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class ArcanePackets {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ArcaneIndustry.id("chamber"), () -> VERSION, VERSION::equals, VERSION::equals);
    private ArcanePackets() {}

    public static void register() {
        CHANNEL.registerMessage(0, SelectSpell.class, SelectSpell::encode, SelectSpell::decode,
                SelectSpell::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, TakeDisplayedItem.class, TakeDisplayedItem::encode, TakeDisplayedItem::decode,
                TakeDisplayedItem::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void selectSpell(BlockPos pos, int slot, int revision) {
        CHANNEL.sendToServer(new SelectSpell(pos.immutable(), slot, revision));
    }

    public static void takeDisplayedItem(BlockPos pos, int role, int revision) {
        CHANNEL.sendToServer(new TakeDisplayedItem(pos.immutable(), role, revision));
    }

    /** Floating books extend beyond the block outline; either visible surface is a valid aim. */
    public static boolean aimedForSelection(ServerPlayer player, AscensionChamberBlockEntity chamber) {
        ChamberDisplayHit.Target display = ChamberDisplayHit.find(player, 1);
        if (display != null) return display.chamber() == chamber && display.role() == 0;
        double reach = ChamberDisplayHit.reach(player);
        HitResult target = player.pick(reach, 1, false);
        return target instanceof BlockHitResult block && target.getType() == HitResult.Type.BLOCK
                && block.getBlockPos().equals(chamber.getBlockPos())
                && player.getEyePosition().distanceToSqr(block.getLocation()) <= reach * reach + .001;
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
                        || slot < 0 || slot >= 256 || revision < 0 || !player.level().hasChunkAt(pos)
                        || !player.level().mayInteract(player, pos)) return;
                Vec3 center = Vec3.atCenterOf(pos);
                if (Math.abs(player.getX() - center.x) > 2.5
                        || Math.abs(player.getZ() - center.z) > 2.5) return;
                if (player.level().getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber
                        && aimedForSelection(player, chamber)) {
                    chamber.selectBookSpell(player, slot, revision);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private record TakeDisplayedItem(BlockPos pos, int role, int revision) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos); buffer.writeVarInt(role); buffer.writeVarInt(revision);
        }
        static TakeDisplayedItem decode(FriendlyByteBuf buffer) {
            return new TakeDisplayedItem(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty()
                        || role < 0 || role > 1 || revision < 0 || !player.level().hasChunkAt(pos)
                        || !player.level().mayInteract(player, pos)) return;
                if (!(player.level().getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber)
                        || chamber.getManualDisplayRevision() != revision || chamber.isManualProcessing()) return;
                ChamberDisplayHit.Target hit = ChamberDisplayHit.find(player, 1);
                if (hit == null || hit.chamber() != chamber || hit.role() != role) return;
                chamber.takeDisplayedManualItem(player, role);
            });
            context.setPacketHandled(true);
        }
    }
}
