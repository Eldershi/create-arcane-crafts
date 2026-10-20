package dev.expedition.arcane.chamber;

import com.mojang.authlib.GameProfile;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.client.ChamberItemMotion;
import dev.expedition.arcane.client.ScrollRevealMotion;
import dev.expedition.arcane.network.ArcanePackets;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Native-world rays, not mocked block hit results, including the area above the block outline. */
public final class ChamberDisplayHitTests {
    private ChamberDisplayHitTests() {}

    public static Map<String, Object> run(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Saved> restore = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) for (int z = -1; z <= 4; z++) {
            BlockPos at = pos.offset(x, y, z);
            var entity = level.getBlockEntity(at);
            restore.put(at, new Saved(level.getBlockState(at), entity == null ? null : entity.saveWithFullMetadata()));
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        }
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("07ce8a25-8fc3-497c-a3a8-c0e3561f27d8"), "ChamberRayTest"));
        Vec3 oldPosition = player.position();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        float oldHeadYaw = player.yHeadRot, oldHeadYawO = player.yHeadRotO;
        double oldXo = player.xo, oldYo = player.yo, oldZo = player.zo;
        float oldPitchO = player.xRotO;
        ListTag oldInventory = player.getInventory().save(new ListTag());
        Map<String, Object> checks = new LinkedHashMap<>();
        try {
            level.setBlock(pos, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
            var chamber = (AscensionChamberBlockEntity) level.getBlockEntity(pos);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, new ItemStack(ItemRegistry.SCROLL.get()));
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, new ItemStack(Items.BLAZE_ROD));
            player.getInventory().clearContent();
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 3);
            aim(player, ChamberDisplayHit.center(chamber, player, 0, level.getGameTime() + 1));
            require(player.pick(ChamberDisplayHit.reach(player), 1, false).getType() == HitResult.Type.MISS,
                    "primary_ray_is_above_the_vanilla_block_box");
            requireRole(chamber, player, 0, "primary_above_block_is_independently_pickable");
            int revision = chamber.getManualDisplayRevision();
            require(chamber.takeDisplayedManualItem(player, 0)
                            && chamber.getManualPrimary().isEmpty() && !chamber.getManualSecondary().isEmpty()
                            && chamber.getManualDisplayRevision() != revision,
                    "primary_transfer_preserves_secondary_and_advances_revision");
            aim(player, ChamberDisplayHit.center(chamber, player, 1, level.getGameTime() + 1));
            requireRole(chamber, player, 1, "secondary_remains_pickable_when_primary_is_empty");
            require(chamber.takeDisplayedManualItem(player, 1) && chamber.getManualSecondary().isEmpty()
                    && ChamberDisplayHit.find(player, 1) == null, "secondary_transfer_has_no_fallback_or_ghost_target");
            checks.put("native_above_block_and_independent_transfers", "PASS");

            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, new ItemStack(ItemRegistry.SCROLL.get()));
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, new ItemStack(Items.BLAZE_ROD));
            for (int role = 0; role < 2; role++) {
                aim(player, ChamberDisplayHit.center(chamber, player, role, level.getGameTime() + 1));
                requireRole(chamber, player, role, "two_items_choose_aimed_role_" + role);
            }
            checks.put("two_display_roles_are_distinct", "PASS");

            BlockPos wall = pos.offset(0, 1, 1);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            aim(player, ChamberDisplayHit.center(chamber, player, 0, level.getGameTime() + 1));
            require(ChamberDisplayHit.find(player, 1) == null, "opaque_wall_blocks_display_interaction");
            level.setBlock(wall, Blocks.GLASS.defaultBlockState(), 3);
            require(ChamberDisplayHit.find(player, 1) == null, "transparent_solid_wall_still_blocks_interaction");
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 30);
            aim(player, ChamberDisplayHit.center(chamber, player, 0, level.getGameTime() + 1));
            require(ChamberDisplayHit.find(player, 1) == null && ChamberDisplayHit.hit(chamber, player, 1) == null,
                    "display_beyond_attribute_reach_is_rejected");
            checks.put("occlusion_and_reach_authority", "PASS");

            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 3);
            ItemStack book = new ItemStack(ItemRegistry.IRON_SPELL_BOOK.get());
            ISpellContainer.getOrCreate(book).addSpell(SpellRegistry.FIREBOLT_SPELL.get(), 1, true, book);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, book);
            aim(player, ChamberDisplayHit.center(chamber, player, 0, level.getGameTime() + 1));
            requireRole(chamber, player, 0, "articulated_book_is_pickable_above_the_block");
            require(ArcanePackets.aimedForSelection(player, chamber), "server_accepts_book_model_as_selector_aim");
            aim(player, ChamberDisplayHit.center(chamber, player, 1, level.getGameTime() + 1));
            require(!ArcanePackets.aimedForSelection(player, chamber), "secondary_item_does_not_confirm_book_selection");
            checks.put("native_book_selector_targeting", "PASS");

            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT, ItemStack.EMPTY);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.INK_SLOT, ItemStack.EMPTY);
            chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT, new ItemStack(ItemRegistry.SCROLL.get()));
            CompoundTag completed = chamber.saveWithFullMetadata();
            completed.putBoolean("ManualResultReady", true);
            completed.putBoolean("ManualProcessing", false);
            completed.putString("ManualOperation", ManualUpgradeRecipes.SCROLL_UPGRADE);
            completed.putLong("ManualCompletedAt", level.getGameTime());
            int endpoints = 0;
            for (double age : new double[]{0, .25, .6, 1.19}) {
                completed.putLong("ManualCompletedAt", level.getGameTime() - Math.round(age * 20));
                chamber.load(completed.copy());
                double actualAge = (level.getGameTime() - chamber.getManualCompletedAt()) / 20.0;
                var motion = ChamberItemMotion.pose(actualAge, level.getGameTime() / 20.0, true, true);
                var rotation = ChamberItemMotion.itemQuaternion(motion, 0, ScrollRevealMotion.Quaternion.IDENTITY);
                Vec3 center = ChamberDisplayHit.center(chamber, player, 0, level.getGameTime());
                // Analytic corners of the actual .66 native sprite after the production quaternion.
                // A square's far corner must remain pickable even when it leaves the final thin plane.
                for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
                    Vec3 endpoint = center.add(rotate(rotation, new Vec3(x * .329, y * .329, 0)));
                    aim(player, endpoint);
                    var hit = ChamberDisplayHit.hit(chamber, player, 0);
                    require(hit != null && hit.role() == 0, "completed_inertial_corner_" + actualAge + "_" + x + "_" + y);
                    endpoints++;
                }
            }
            completed.putLong("ManualCompletedAt", level.getGameTime() - 40);
            chamber.load(completed.copy());
            Vec3 idleCenter = ChamberDisplayHit.center(chamber, player, 0, level.getGameTime());
            aim(player, idleCenter.add(.48, 0, 0));
            require(ChamberDisplayHit.hit(chamber, player, 0) == null, "settled_item_restores_its_thin_non_overlapping_pick_box");
            checks.put("completion_inertial_endpoints", "PASS: " + endpoints + " transformed sprite corners; idle thin plane restored");
            checks.put("status", "PASS");
            return checks;
        } finally {
            player.getInventory().clearContent(); player.getInventory().load(oldInventory);
            player.setPos(oldPosition.x, oldPosition.y, oldPosition.z); player.setYRot(oldYaw); player.setXRot(oldPitch);
            player.setYHeadRot(oldHeadYaw); player.yHeadRotO = oldHeadYawO;
            player.xo = oldXo; player.yo = oldYo; player.zo = oldZo; player.xRotO = oldPitchO;
            for (var entry : restore.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue().state, 3);
                if (entry.getValue().entity != null && level.getBlockEntity(entry.getKey()) != null)
                    level.getBlockEntity(entry.getKey()).load(entry.getValue().entity.copy());
            }
        }
    }

    private static void requireRole(AscensionChamberBlockEntity chamber, ServerPlayer player, int role, String name) {
        var hit = ChamberDisplayHit.find(player, 1);
        require(hit != null && hit.chamber() == chamber && hit.role() == role,
                name + "; got=" + (hit == null ? "null" : hit.role()) + "; eye=" + player.getEyePosition(1)
                        + "; view=" + player.getViewVector(1) + "; center=" + ChamberDisplayHit.center(chamber, player, role,
                        player.level().getGameTime() + 1) + "; reach=" + ChamberDisplayHit.reach(player)
                        + "; direct=" + ChamberDisplayHit.hit(chamber, player, 1));
    }

    private static void aim(ServerPlayer player, Vec3 target) {
        Vec3 direction = target.subtract(player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        player.setYRot(yaw);
        // LivingEntity.getViewYRot reads the head, normally synchronised by the
        // player's real movement tick. FakePlayer.tick is a no-op.
        player.setYHeadRot(yaw); player.yHeadRotO = yaw;
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
        // End-point tests sample partial tick zero. Synchronise the fake actor's
        // previous pose as well, as a real movement tick would do before rendering.
        player.xo = player.getX(); player.yo = player.getY(); player.zo = player.getZ();
        player.xRotO = player.getXRot();
    }
    private static Vec3 rotate(ScrollRevealMotion.Quaternion q, Vec3 v) {
        var result = q.multiply(new ScrollRevealMotion.Quaternion(v.x, v.y, v.z, 0)).multiply(q.inverse());
        return new Vec3(result.x(), result.y(), result.z());
    }
    private static void require(boolean success, String name) { if (!success) throw new IllegalStateException(name); }
    private record Saved(BlockState state, CompoundTag entity) {}
}
