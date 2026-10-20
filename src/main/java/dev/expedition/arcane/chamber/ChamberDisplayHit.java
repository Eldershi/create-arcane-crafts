package dev.expedition.arcane.chamber;

import dev.expedition.arcane.client.ChamberItemMotion;
import dev.expedition.arcane.client.ScrollRevealMotion;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared client/server picking for the two actual displayed items. The vanilla
 * block box ends at y=1, below these models, so block picking alone cannot own
 * this interaction. No client rendering classes or resource data are used here.
 */
public final class ChamberDisplayHit {
    private static final double EPSILON = .0001;
    private ChamberDisplayHit() {}

    public record Target(AscensionChamberBlockEntity chamber, int role, Vec3 location, double distanceSquared) {}

    /** Search only loaded blocks touched by the short eye ray and its display envelope. */
    @Nullable public static Target find(Player player, float partialTicks) {
        if (player == null || !player.isAlive() || player.isSpectator()) return null;
        double reach = reach(player);
        Vec3 start = player.getEyePosition(partialTicks), end = start.add(player.getViewVector(partialTicks).scale(reach));
        Level level = player.level();
        Set<BlockPos> visited = new HashSet<>();
        Target best = null;
        // Each model is within .85 horizontally and 2.5 vertically of its block.
        // Sampling at .4 blocks plus neighbouring cells cannot skip that volume.
        int steps = Math.max(1, (int) Math.ceil(reach / .4));
        for (int i = 0; i <= steps; i++) {
            BlockPos cell = BlockPos.containing(start.lerp(end, i / (double) steps));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -2; dy <= 0; dy++) {
                BlockPos pos = cell.offset(dx, dy, dz);
                if (!visited.add(pos) || !level.hasChunkAt(pos)
                        || !(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber)) continue;
                Target hit = hit(chamber, player, partialTicks);
                if (hit != null && (best == null || hit.distanceSquared < best.distanceSquared)) best = hit;
            }
        }
        return best;
    }

    /** Recomputed on the server; a packet's claimed role and position are never trusted. */
    @Nullable public static Target hit(AscensionChamberBlockEntity chamber, Player player, float partialTicks) {
        if (chamber.getLevel() != player.level() || chamber.isRemoved() || chamber.isBasinMode()
                || !chamber.isBlazeAlive() || !player.isAlive() || player.isSpectator()) return null;
        double reach = reach(player), now = player.level().getGameTime() + partialTicks;
        Vec3 eye = player.getEyePosition(partialTicks), view = player.getViewVector(partialTicks).normalize();
        Vec3 end = eye.add(view.scale(reach));
        Vec3 right = view.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < EPSILON) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(view).normalize();
        Target best = null;
        for (int role = 0; role < 2; role++) {
            ItemStack stack = role == 0 ? chamber.getManualPrimary() : chamber.getManualSecondary();
            if (stack.isEmpty()) continue;
            Vec3 center = center(chamber, player, role, now);
            boolean book = stack.getItem() instanceof ISpellbook;
            double half = book ? .40 : itemScale(stack) * .5 + .025;
            Vec3 localStart = local(eye.subtract(center), right, up, view);
            Vec3 localEnd = local(end.subtract(center), right, up, view);
            // A rotated square's corner reaches sqrt(2) times its half edge. During
            // the short inertial settle use that orientation-independent envelope:
            // the server has no client camera interpolation/hemisphere history.
            // Return to the thin camera plane once idle, keeping the two slots distinct.
            boolean turning = chamber.isManualProcessing() || chamber.getManualCompletedAt() >= 0
                    && (now - chamber.getManualCompletedAt()) / 20 < ChamberItemMotion.COMPLETION_SETTLE_SECONDS;
            double envelope = half * Math.sqrt(2);
            AABB box = book ? new AABB(-half, -.31, -.32, half, .31, .32)
                    : turning ? new AABB(-envelope, -envelope, -envelope, envelope, envelope, envelope)
                    : new AABB(-half, -half, -.075, half, half, .075);
            var clipped = box.clip(localStart, localEnd);
            if (clipped.isEmpty() && !box.contains(localStart)) continue;
            Vec3 point = clipped.orElse(localStart);
            double along = Math.max(0, point.subtract(localStart).length());
            Vec3 location = eye.add(view.scale(along));
            double distance = eye.distanceToSqr(location);
            if (distance > reach * reach + EPSILON || !unobstructed(player, eye, location)) continue;
            if (best == null || distance < best.distanceSquared) best = new Target(chamber, role, location, distance);
        }
        return best;
    }

    /** The same camera-facing slot axis and item-only vertical clock as the renderer. */
    public static Vec3 center(AscensionChamberBlockEntity chamber, Player viewer, int role, double now) {
        BlockPos pos = chamber.getBlockPos();
        boolean partner = !chamber.getManualSecondary().isEmpty();
        boolean done = chamber.getManualCompletedAt() >= 0;
        double doneAge = done ? Math.max(0, (now - chamber.getManualCompletedAt()) / 20) : 0;
        double horizontal = role == 1 ? ChamberItemMotion.SLOT_HORIZONTAL
                : partner ? -ChamberItemMotion.SLOT_HORIZONTAL : 0;
        double y;
        if (chamber.hasResearchPresentation() && !chamber.isManualProcessing()) {
            var sample = ScrollRevealMotion.sample(ScrollRevealMotion.DURATION_TICKS, now / 20);
            horizontal = 0; y = sample.mainY();
        } else {
            boolean operation = !ManualUpgradeRecipes.BOOK_SPELL_UPGRADE.equals(chamber.getManualOperation());
            ItemStack stack = role == 0 ? chamber.getManualPrimary() : chamber.getManualSecondary();
            boolean book = stack.getItem() instanceof ISpellbook;
            y = done ? ChamberItemMotion.completed(doneAge, now / 20, operation, role == 1, book).y()
                    : chamber.isManualProcessing() ? ChamberItemMotion.processing(
                    Math.max(0, (now - chamber.getManualStartedAt()) / 20), now / 20, operation, role == 1, book).y()
                    : ChamberItemMotion.idleHeight(now / 20, role == 1, book);
            if (role == 0 && !partner && done) {
                // A consumed partner recentres the primary over .85 seconds.
                // A result without a partner was centred throughout its operation.
                if (!chamber.getManualOperation().isEmpty()) {
                    horizontal = -ChamberItemMotion.SLOT_HORIZONTAL * (1 - ease(doneAge / .85));
                }
            }
        }
        double yaw = Math.atan2(viewer.getX() - pos.getX() - .5, viewer.getZ() - pos.getZ() - .5);
        return new Vec3(pos.getX() + .5 + Math.cos(yaw) * horizontal, pos.getY() + y,
                pos.getZ() + .5 - Math.sin(yaw) * horizontal);
    }

    /** Same scale policy as the native item renderer; no baked-model lookup on the server. */
    public static float itemScale(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();
        return path.contains("orb") || path.contains("shriving") || path.contains("slot") || path.contains("upgrade_token") ? .48F : .66F;
    }

    private static Vec3 local(Vec3 value, Vec3 right, Vec3 up, Vec3 normal) {
        return new Vec3(value.dot(right), value.dot(up), value.dot(normal));
    }

    public static boolean unobstructed(Player player, Vec3 eye, Vec3 location) {
        HitResult wall = player.level().clip(new ClipContext(eye, location, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return wall.getType() == HitResult.Type.MISS || eye.distanceToSqr(wall.getLocation()) + EPSILON >= eye.distanceToSqr(location);
    }

    public static double reach(Player player) {
        double value = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        return Double.isFinite(value) ? Mth.clamp(value, 0, 16) : 0;
    }

    private static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
