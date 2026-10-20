package dev.expedition.arcane.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.chamber.ChamberDisplayHit;
import dev.expedition.arcane.network.ArcanePackets;
import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A world-targeted native spell bar. Empty-hand use always retrieves the aimed item. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChamberInteraction {
    private static long rawConfirmedAt = Long.MIN_VALUE;
    private ChamberInteraction() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChamberManualClient.tick();
            target(false);
        }
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.gameMode != null && mc.screen == null
                && mc.isWindowActive() && !mc.player.isSpectator();
    }

    private static AscensionChamberBlockEntity target(boolean requireSelection) {
        if (!active()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (requireSelection && mc.options.hideGui) return null;
        ChamberDisplayHit.Target display = ChamberDisplayHit.find(mc.player, mc.getFrameTime());
        AscensionChamberBlockEntity chamber;
        if (display != null) {
            if (requireSelection && display.role() != 0) return null;
            chamber = display.chamber();
        } else {
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || mc.player.getEyePosition().distanceToSqr(hit.getLocation()) > Math.pow(ChamberDisplayHit.reach(mc.player), 2) + .001
                    || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof AscensionChamberBlockEntity found)) return null;
            chamber = found;
        }
        if (chamber.isBasinMode() || !chamber.isBlazeAlive()) return null;
        var state = ChamberManualClient.state(chamber);
        return !requireSelection || state.selectable(chamber, mc.level.getGameTime() + mc.getFrameTime()) ? chamber : null;
    }

    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        AscensionChamberBlockEntity chamber = target(true);
        if (chamber == null || event.getScrollDelta() == 0) return;
        var state = ChamberManualClient.state(chamber);
        var slots = ChamberManualClient.slots(chamber);
        // An empty book still owns its visible selector, so scrolling cannot silently switch the hotbar.
        if (!slots.isEmpty()) {
            int index = Math.max(0, slots.indexOf(state.selectedSlot));
            int next = slots.get(Math.floorMod(index + (event.getScrollDelta() > 0 ? -1 : 1), slots.size()));
            if (next != state.selectedSlot) {
                state.selectedSlot = next;
                ChamberManualSounds.play("select", chamber.getBlockPos(), .12F, 1);
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            // Forge emits the off-hand event even when the main-hand event was cancelled.
            if (event.isUseItem() && mc.player.getMainHandItem().isEmpty()
                    && ChamberDisplayHit.find(mc.player, mc.getFrameTime()) != null) {
                event.setSwingHand(false); event.setCanceled(true);
            }
            return;
        }
        if (event.isUseItem() && mc.player.getMainHandItem().isEmpty()) {
            ChamberDisplayHit.Target hit = ChamberDisplayHit.find(mc.player, mc.getFrameTime());
            if (hit == null) return;
            if (hit.chamber().isManualProcessing()) {
                mc.player.displayClientMessage(Component.translatable("gui.arcane_industry.chamber.processing"), true);
            } else {
                ArcanePackets.takeDisplayedItem(hit.chamber().getBlockPos(), hit.role(), hit.chamber().getManualDisplayRevision());
            }
            // Cancel vanilla use, including its off-hand fallback; one gesture owns one transfer.
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        // Middle click has no item-use or insertion side effect. Right click is reserved for
        // placing materials, containers, decorations and individually retrieving the displays.
        if (!event.isPickBlock()) return;
        long sinceRaw = mc.level.getGameTime() - rawConfirmedAt;
        if (sinceRaw >= 0 && sinceRaw <= 1 || confirmSelection()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !active()) return;
        var binding = Minecraft.getInstance().options.keyPickItem;
        // Vanilla pickBlock checks hitResult != MISS before Forge emits its
        // InteractionKeyMappingTriggered event. Floating book geometry needs
        // this earlier native input hook; the global hitResult stays untouched.
        if (binding.matchesMouse(event.getButton()) && binding.isConflictContextAndModifierActive()
                && confirmSelection()) {
            rawConfirmedAt = Minecraft.getInstance().level.getGameTime();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void keyboard(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !active()) return;
        var binding = Minecraft.getInstance().options.keyPickItem;
        if (binding.matches(event.getKey(), event.getScanCode()) && binding.isConflictContextAndModifierActive()
                && confirmSelection()) rawConfirmedAt = Minecraft.getInstance().level.getGameTime();
    }

    private static boolean confirmSelection() {
        AscensionChamberBlockEntity chamber = target(true);
        if (chamber == null) return false;
        Minecraft mc = Minecraft.getInstance();
        var state = ChamberManualClient.state(chamber);
        if (state.selectedSlot >= 0 && ChamberManualClient.slots(chamber).contains(state.selectedSlot)) {
            ArcanePackets.selectSpell(chamber.getBlockPos(), state.selectedSlot, chamber.getManualSelectionRevision());
            state.confirm(chamber, mc.level.getGameTime() + mc.getFrameTime());
        } else {
            mc.player.displayClientMessage(Component.translatable("gui.arcane_industry.chamber.empty_book"), true);
        }
        return true;
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        AscensionChamberBlockEntity chamber = target(true);
        if (chamber == null) return;
        Minecraft mc = Minecraft.getInstance();
        var state = ChamberManualClient.state(chamber);
        var slots = ChamberManualClient.slots(chamber);
        int titleSlot = slots.isEmpty() ? -1 : slots.get(Math.max(0, slots.indexOf(state.selectedSlot)));
        Layout layout = layout(event.getGuiGraphics(), slots, state.selectedSlot);
        GuiGraphics gui = event.getGuiGraphics();
        // Exact upstream SpellBarOverlay atlas, frame dimensions, UVs and icon rendering API.
        // The dependency/resource pack owns these assets; no native bitmap is copied into this mod.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        for (int i = 0; i < layout.count; i++) {
            int x = layout.x + i * 22;
            gui.blit(SpellBarOverlay.TEXTURE, x, layout.y, 66, 84, 22, 22);
            if (!slots.isEmpty()) {
                int slot = slots.get(layout.first + i);
                var spell = chamber.getManualBookSpells().get(slot);
                gui.blit(spell.getSpell().getSpellIconResource(), x + 3, layout.y + 3, 0, 0, 16, 16, 16, 16);
                gui.blit(SpellBarOverlay.TEXTURE, x, layout.y, slot == state.selectedSlot ? 0 : 22, 84, 22, 22);
            } else gui.blit(SpellBarOverlay.TEXTURE, x, layout.y, 22, 84, 22, 22);
        }
        Component title = slots.isEmpty() ? Component.translatable("gui.arcane_industry.chamber.empty_book")
                : chamber.getManualBookSpells().get(titleSlot).getSpell().getDisplayName(null)
                    .copy().append(" " + chamber.getManualBookSpells().get(titleSlot).getLevel());
        gui.drawString(mc.font, title, layout.x, layout.y - 12, slots.isEmpty() ? 0xE8D8BD : 0xFFFFFF, true);
        gui.drawString(mc.font, Component.translatable(slots.isEmpty()
                        ? "gui.arcane_industry.chamber.empty_book_hint" : "gui.arcane_industry.chamber.select_hint"),
                layout.x, layout.y + 27, 0xE2E2E2, true);
        RenderSystem.disableBlend();
    }

    private static Layout layout(GuiGraphics gui, List<Integer> slots, int selectedSlot) {
        int selected = Math.max(0, slots.indexOf(selectedSlot));
        int count = Math.max(1, Math.min(9, slots.size()));
        int first = Mth.clamp(selected - count / 2, 0, Math.max(0, slots.size() - count));
        int x = Math.max(6, Math.min(gui.guiWidth() - Math.max(count * 22, 190) - 6, gui.guiWidth() / 2 + 20));
        int y = Math.min(gui.guiHeight() - 58, gui.guiHeight() / 2 + 18);
        return new Layout(x, y, count, first);
    }

    private record Layout(int x, int y, int count, int first) {}

    /** Read-only presenter inspection for accessibility and isolated native-client acceptance. */
    public static Map<String, Object> presentationSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!active()) { result.put("active", false); return result; }
        Minecraft mc = Minecraft.getInstance();
        result.put("active", true);
        result.put("nativeHitType", mc.hitResult == null ? "null" : mc.hitResult.getType().name());
        ChamberDisplayHit.Target hit = ChamberDisplayHit.find(mc.player, mc.getFrameTime());
        result.put("aimedRole", hit == null ? -1 : hit.role());
        if (hit != null) result.put("aimedBlock", hit.chamber().getBlockPos().toShortString());
        AscensionChamberBlockEntity chamber = target(true);
        result.put("selectorVisible", chamber != null);
        if (chamber != null) {
            var state = ChamberManualClient.state(chamber);
            var slots = ChamberManualClient.slots(chamber);
            var window = mc.getWindow();
            int count = Math.max(1, Math.min(9, slots.size()));
            int first = Mth.clamp(Math.max(0, slots.indexOf(state.selectedSlot)) - count / 2, 0, Math.max(0, slots.size() - count));
            int x = Math.max(6, Math.min(window.getGuiScaledWidth() - Math.max(count * 22, 190) - 6, window.getGuiScaledWidth() / 2 + 20));
            int y = Math.min(window.getGuiScaledHeight() - 58, window.getGuiScaledHeight() / 2 + 18);
            result.put("selectedNativeSlot", state.selectedSlot);
            result.put("spellCount", slots.size());
            result.put("hintKey", slots.isEmpty() ? "gui.arcane_industry.chamber.empty_book_hint" : "gui.arcane_industry.chamber.select_hint");
            result.put("nativeTexture", SpellBarOverlay.TEXTURE.toString());
            List<Map<String, Integer>> frames = new ArrayList<>();
            for (int i = 0; i < count; i++) frames.add(Map.of("nativeSlot", slots.isEmpty() ? -1 : slots.get(first + i),
                    "x", x + i * 22, "y", y, "width", 22, "height", 22));
            result.put("frames", frames);
        }
        return result;
    }
}
