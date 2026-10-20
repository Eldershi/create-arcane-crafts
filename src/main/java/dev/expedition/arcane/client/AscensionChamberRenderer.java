/*
 * Animation portions adapted from Create 6.0.8 BlazeBurnerRenderer.renderShared
 * and BlazeBurnerBlockEntity.tickAnimation.
 * Copyright (c) The Create Team / The Creators of Create. MIT License.
 * Full original notice: LICENSES/Create-6.0.8-LICENSE.md.
 * Local changes: arcane states, book rendering and processing animation.
 */
package dev.expedition.arcane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlock;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.chamber.ManualUpgradeRecipes;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

/** The approved chamber art, with Create's native eight rods and vertical animation. */
public final class AscensionChamberRenderer implements BlockEntityRenderer<AscensionChamberBlockEntity> {
    public static final ModelLayerLocation BROWS_LAYER = new ModelLayerLocation(
            ArcaneIndustry.id("arcane_blaze_brows"), "main");
    private static final String[] STATES = {"idle", "active", "super"};
    private final ChamberVisuals visuals = new ChamberVisuals();
    private final ItemRenderer itemRenderer;
    private final AnimatedSpellBook books = new AnimatedSpellBook();
    private final ChamberScrollRevealRenderer scrolls = new ChamberScrollRevealRenderer();
    private final Map<AscensionChamberBlockEntity, BasinPose> basinPoses = new WeakHashMap<>();
    private final Map<AscensionChamberBlockEntity, ResearchRig> researchRigs = new WeakHashMap<>();
    private final Map<AscensionChamberBlockEntity, Quaternionf[]> completionTargets = new WeakHashMap<>();

    public AscensionChamberRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    public static LayerDefinition createBrowsLayer() {
        // Retain the registered API while the accepted zero-thickness idle brows
        // and selected active/super shelves are rendered from the exported mesh.
        return LayerDefinition.create(new MeshDefinition(), 64, 32);
    }

    @Override public void render(AscensionChamberBlockEntity chamber, float partialTicks, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        Level level = chamber.getLevel();
        if (level == null) return;
        // An empty cage can retain an extinguishable native campfire layer.
        // None of the living head, rods, headwear or working rune layers remain.
        if (!chamber.isBlazeAlive()) {
            if (chamber.hasResidualFire()) visuals.residualFire(pose, buffers, overlay);
            return;
        }
        int state = chamber.getBlockState().getValue(AscensionChamberBlock.STATE);
        double renderTime = level.getGameTime() + (double) partialTicks;
        float time = (float) renderTime;
        float phase = (chamber.getBlockPos().asLong() & 255L) * 0.1F;
        String appearance = STATES[state];
        float renderTick = time + (chamber.hashCode() % 13) * 16.0F;
        float q = renderTick / 16.0F;
        BasinPose movement = basinPoses.computeIfAbsent(chamber, unused -> new BasinPose());
        boolean manualWork = !chamber.isBasinMode() && chamber.isManualProcessing()
                && (!chamber.isResearchProcessing() || researchTick(chamber, renderTime) < ScrollRevealMotion.WORK_END);
        float animation = movement.value(chamber, partialTicks, state > 0 && (chamber.hasWorkBlockAbove() || manualWork)) * .175F;
        float offset = Mth.sin((float) (q % (Math.PI * 2))) / (state == 0 ? 16 : 64);
        pose.pushPose();
        rotateHead(pose, movement.angle(partialTicks));
        // Match Create's working-eye switch after the head has settled under a basin/tank.
        double headY = offset - animation * .75F;
        visuals.decorations(appearance, chamber.hasGoggles(), chamber.hasNecromancerCrown(),
                headY, pose, buffers, overlay);
        visuals.head(appearance, state > 0 && animation > .125F, headY, pose, buffers, overlay);
        pose.popPose();
        if (state > 0) {
            float offset1 = Mth.sin((float) ((q + Math.PI) % (Math.PI * 2))) / 64;
            float offset2 = Mth.sin((float) ((q + Math.PI / 2) % (Math.PI * 2))) / 64;
            visuals.rods(appearance, "small", offset1 + animation + .125F, pose, buffers, overlay);
            visuals.rods(appearance, "large", offset2 + animation - .1875F, pose, buffers, overlay);
            // Create draws its upward sheet only after the working pose has
            // settled beneath a basin or tank. The runes share that trigger.
            if (animation > .125F && chamber.hasWorkBlockAbove() && !manualWork) {
                visuals.flame(appearance, renderTime, pose, buffers, overlay);
                visuals.runes(appearance, renderTime, pose, buffers, overlay);
            }
        }

        if (!chamber.isBasinMode()) {
            renderManual(chamber, renderTime, pose, buffers, light, overlay);
        }
    }

    private static void rotateHead(PoseStack pose, float angle) {
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-.5, 0, -.5);
    }

    private static final class BasinPose {
        private long lastTick = Long.MIN_VALUE;
        private final LerpedFloat animation = LerpedFloat.linear();
        private final LerpedFloat angle = LerpedFloat.angular().startWithValue(0);
        float value(AscensionChamberBlockEntity chamber, float partialTicks, boolean above) {
            long tick = chamber.getLevel().getGameTime();
            if (lastTick == Long.MIN_VALUE || tick < lastTick) animation.startWithValue(above ? 1 : 0);
            if (lastTick != tick) {
                // Native Create headAnimation uses this exact Catnip chaser.
                animation.chase(above ? 1 : 0, .25, LerpedFloat.Chaser.exp(.25));
                float target = 0;
                var player = Minecraft.getInstance().player;
                if (!above && player != null && !player.isInvisible()) {
                    double dx = player.getX() - chamber.getBlockPos().getX() - .5;
                    double dz = player.getZ() - chamber.getBlockPos().getZ() - .5;
                    target = (float) (-Mth.atan2(dz, dx) * 180 / Math.PI - 90);
                }
                target = angle.getValue() + Mth.wrapDegrees(target - angle.getValue());
                angle.chase(target, above ? .125 : .25, above ? LerpedFloat.Chaser.EXP : LerpedFloat.Chaser.exp(5));
                int steps = lastTick == Long.MIN_VALUE || tick < lastTick ? 1 : (int) Math.min(100, tick - lastTick);
                for (int i = 0; i < steps; i++) { animation.tickChaser(); angle.tickChaser(); }
                lastTick = tick;
            }
            return animation.getValue(partialTicks);
        }
        float angle(float partialTicks) { return angle.getValue(partialTicks); }
    }

    private void renderManual(AscensionChamberBlockEntity chamber, double now, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        var presentation = ChamberManualClient.state(chamber);
        if (chamber.hasResearchPresentation()) {
            renderResearch(chamber, now, pose, buffers, light, overlay);
            return;
        }
        researchRigs.remove(chamber);
        ItemStack primary = chamber.getManualPrimary();
        if (primary.isEmpty()) return;
        ItemStack secondary = chamber.getManualSecondary();
        boolean partner = !secondary.isEmpty();
        boolean working = chamber.isManualProcessing();
        boolean done = chamber.getManualCompletedAt() >= 0;
        double elapsed = working ? Math.max(0, (now - chamber.getManualStartedAt()) / 20)
                : done ? ChamberManualClient.WORK_SECONDS + Math.max(0, (now - chamber.getManualCompletedAt()) / 20) : 0;
        double doneAge = done ? Math.max(0, (now - chamber.getManualCompletedAt()) / 20) : 0;
        boolean operation = !ManualUpgradeRecipes.BOOK_SPELL_UPGRADE.equals(chamber.getManualOperation());
        var motion = ChamberItemMotion.pose(done ? doneAge : elapsed, now / 20, done, operation);
        if (!done) completionTargets.remove(chamber);
        float yaw = 0;
        var player = Minecraft.getInstance().player;
        if (player != null) yaw = (float) Math.atan2(player.getX() - chamber.getBlockPos().getX() - .5,
                player.getZ() - chamber.getBlockPos().getZ() - .5);
        var bookPose = presentation.sample(now);
        boolean isBook = books.supports(primary);
        double y = working || done ? motion.vertical().y() : 1.12
                - .04 * ChamberManualClient.ease(bookPose.open()) - bookPose.impact() * .008
                + ChamberItemMotion.idle(now / 20).y();
        // Keep native two-item spacing until consumption; only a two-to-one result recentres.
        double horizontal = partner ? -.40 : done && presentation.hadPartner
                ? -.40 * (1 - ChamberManualClient.ease(doneAge / .85)) : 0;
        renderManualItem(primary, isBook, horizontal, y, yaw, motion, working || done, 0,
                bookPose, pose, buffers, light, overlay, chamber);
        if (partner) {
            double secondaryY = working ? ChamberItemMotion.processing(elapsed, operation, true).y()
                    : done ? ChamberItemMotion.completed(doneAge, now / 20, operation, true).y()
                    : 1.12 + ChamberItemMotion.idle(now / 20, ChamberItemMotion.SECONDARY_PHASE).y();
            renderManualItem(secondary, books.supports(secondary), .40, secondaryY, yaw, motion,
                    working || done, 1, bookPose, pose, buffers, light, overlay, chamber);
        }
        if (working) {
            double alpha = ChamberManualClient.ease(elapsed / .16)
                    * ChamberManualClient.ease((ChamberManualClient.WORK_SECONDS - elapsed) / .28);
            String state = chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == 2 ? "super" : "active";
            visuals.manualEffects(state, now, y, alpha, pose, buffers, overlay);
        }
    }

    private void renderManualItem(ItemStack stack, boolean book, double horizontal, double y, float yaw,
                                  ChamberItemMotion.Pose motion, boolean moving, int index, ChamberManualClient.Pose bookPose,
                                  PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                  AscensionChamberBlockEntity chamber) {
        pose.pushPose();
        pose.translate(.5 + Math.cos(yaw) * horizontal, y, .5 - Math.sin(yaw) * horizontal);
        if (moving && book && !motion.settled()) {
            float turn = (float) (motion.angular().y() + index * Math.PI);
            pose.mulPose(Axis.XP.rotation(turn));
            pose.mulPose(Axis.ZP.rotation(turn));
        }
        if (book) {
            pose.mulPose(Axis.YP.rotation(yaw + Mth.HALF_PI));
            pose.mulPose(Axis.ZP.rotationDegrees(100 + bookPose.impact() * .7F));
            books.render(stack, bookPose.open(), bookPose.page1(), bookPose.page2(), pose, buffers, light, overlay);
        } else {
            Quaternionf camera = new Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            if (moving) {
                var target = quaternion(camera);
                if (motion.completed()) {
                    Quaternionf[] targets = completionTargets.computeIfAbsent(chamber, unused -> new Quaternionf[2]);
                    if (targets[index] == null) target = ChamberItemMotion.initialCompletionTarget(index, target);
                    else target = target.hemisphere(quaternion(targets[index]));
                    targets[index] = joml(target);
                }
                pose.mulPose(joml(ChamberItemMotion.itemQuaternion(motion, index, target)));
            } else pose.mulPose(camera);
            float scale = itemScale(stack);
            pose.scale(scale, scale, scale);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                    chamber.getLevel(), chamber.getBlockPos().hashCode() + index);
        }
        pose.popPose();
    }

    private static double researchTick(AscensionChamberBlockEntity chamber, double now) {
        return ScrollRevealMotion.logicalTick(chamber.getResearchVisualTicks(now));
    }

    private void renderResearch(AscensionChamberBlockEntity chamber, double now, PoseStack pose,
                                MultiBufferSource buffers, int light, int overlay) {
        completionTargets.remove(chamber);
        ResearchRig rig = researchRigs.get(chamber);
        if (rig == null || rig.seed != chamber.getResearchVisualSeed()) {
            var player = Minecraft.getInstance().player;
            float yaw = player == null ? 0 : (float) Math.atan2(player.getX() - chamber.getBlockPos().getX() - .5,
                    player.getZ() - chamber.getBlockPos().getZ() - .5);
            rig = new ResearchRig(chamber.getResearchVisualSeed(), yaw);
            researchRigs.put(chamber, rig);
        }
        double tick = researchTick(chamber, now);
        Quaternionf rest = Axis.YP.rotation(-rig.yaw).mul(
                new Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation()))
                .mul(Axis.YP.rotation(Mth.PI));
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotation(rig.yaw));
        scrolls.render(chamber, tick, now / 20, pose, buffers, light, overlay,
                chamber.getResearchPrimary(), chamber.getResearchSecondary(), chamber.getResearchOutput(),
                chamber.isResearchCrimson(), chamber.getResearchRarity(), rig.seed, rest);
        pose.popPose();
        double workClock = tick / ScrollRevealMotion.WORK_END * ChamberManualClient.WORK_SECONDS;
        double alpha = ScrollRevealMotion.ease(workClock / .16)
                * ScrollRevealMotion.ease((ChamberManualClient.WORK_SECONDS - workClock) / .28);
        visuals.manualEffects(chamber.isResearchCrimson() ? "super" : "active", now, 1,
                alpha, pose, buffers, overlay);
    }

    private record ResearchRig(long seed, float yaw) {}
    private static ScrollRevealMotion.Quaternion quaternion(Quaternionf value) {
        return new ScrollRevealMotion.Quaternion(value.x(), value.y(), value.z(), value.w());
    }
    private static Quaternionf joml(ScrollRevealMotion.Quaternion value) {
        return new Quaternionf((float) value.x(), (float) value.y(), (float) value.z(), (float) value.w());
    }

    private static float itemScale(ItemStack stack) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();
        return path.contains("orb") || path.contains("shriving") || path.contains("slot") || path.contains("upgrade_token") ? .48F : .66F;
    }

}
