package dev.expedition.arcane.client;

import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.network.ArcanePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A small world-targeted selector: range opens the book, only a real ray hit opens the HUD. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChamberInteraction {
    private ChamberInteraction() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChamberManualClient.tick();
            target(false); // Track a newly aimed chamber before its first renderer pass.
        }
    }

    private static AscensionChamberBlockEntity target(boolean requireSelection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null || mc.screen != null
                || !mc.isWindowActive() || mc.options.hideGui || mc.player.isSpectator()
                || !(mc.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        double reach = mc.player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get());
        if (mc.player.getEyePosition().distanceToSqr(hit.getLocation()) > reach * reach + .001) return null;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof AscensionChamberBlockEntity chamber)
                || chamber.isBasinMode() || !chamber.isBlazeAlive()) return null;
        var state = ChamberManualClient.state(chamber);
        return !requireSelection || state.selectable(chamber, mc.level.getGameTime() + mc.getFrameTime()) ? chamber : null;
    }

    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        AscensionChamberBlockEntity chamber = target(true);
        if (chamber == null || event.getScrollDelta() == 0) return;
        var state = ChamberManualClient.state(chamber);
        var slots = ChamberManualClient.slots(chamber);
        if (slots.isEmpty()) return;
        int index = slots.indexOf(state.selectedSlot);
        int next = slots.get(Math.floorMod(index + (event.getScrollDelta() > 0 ? -1 : 1), slots.size()));
        if (next != state.selectedSlot) {
            state.selectedSlot = next;
            ChamberManualSounds.play("select", chamber.getBlockPos(), .12F, 1);
        }
        event.setCanceled(true); // Never scroll the hotbar while the selector owns this wheel gesture.
    }

    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        AscensionChamberBlockEntity chamber = target(true);
        if (chamber == null) return;
        Minecraft mc = Minecraft.getInstance();
        var held = mc.player.getMainHandItem();
        if (held.is(net.minecraft.world.item.Items.BUCKET) || held.is(net.minecraft.world.item.Items.GLASS_BOTTLE)
                || AscensionChamberBlockEntity.isSolutionContainer(held)) return;
        // Sneaking with an empty hand remains an explicit retrieval action.
        if (mc.player.isShiftKeyDown() && mc.player.getMainHandItem().isEmpty()) return;
        var state = ChamberManualClient.state(chamber);
        if (state.selectedSlot < 0) return;
        ArcanePackets.selectSpell(chamber.getBlockPos(), state.selectedSlot, chamber.getManualSelectionRevision());
        state.confirm(chamber, mc.level.getGameTime() + mc.getFrameTime());
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        AscensionChamberBlockEntity chamber = target(true);
        if (chamber == null) return;
        Minecraft mc = Minecraft.getInstance();
        var state = ChamberManualClient.state(chamber);
        var slots = ChamberManualClient.slots(chamber);
        int selected = slots.indexOf(state.selectedSlot);
        if (selected < 0) return;
        // Nine icons at most, centred around the selected native slot. No screen or mouse capture.
        int count = Math.min(9, slots.size());
        int first = Mth.clamp(selected - count / 2, 0, slots.size() - count);
        GuiGraphics gui = event.getGuiGraphics();
        int width = count * 22 + 8;
        int x = Math.min(gui.guiWidth() - width - 6, gui.guiWidth() / 2 + 20);
        x = Math.max(6, x);
        int y = Math.min(gui.guiHeight() - 34, gui.guiHeight() / 2 + 18);
        // Create's dark inset/pixel bevel and warm brass selection corners.
        gui.fill(x - 1, y + 1, x + width + 1, y + 27, 0xE0252525);
        gui.fill(x + 1, y - 1, x + width - 1, y + 29, 0xE0252525);
        gui.fill(x, y, x + width, y + 1, 0xFF777568);
        gui.fill(x, y + 1, x + 1, y + 27, 0xFF777568);
        gui.fill(x + 1, y + 27, x + width, y + 28, 0xFF121313);
        gui.fill(x + width - 1, y + 1, x + width, y + 27, 0xFF121313);
        for (int i = 0; i < count; i++) {
            int slot = slots.get(first + i), ix = x + 5 + i * 22, iy = y + 5;
            var spell = chamber.getManualBookSpells().get(slot);
            gui.fill(ix - 2, iy - 2, ix + 18, iy + 18, 0xFF191B1C);
            gui.blit(spell.getSpell().getSpellIconResource(), ix, iy, 0, 0, 16, 16, 16, 16);
            if (slot == state.selectedSlot) corners(gui, ix - 3, iy - 3, 22, 22, 0xFFDCBA69);
        }
    }

    private static void corners(GuiGraphics gui, int x, int y, int w, int h, int color) {
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            int sx = a == 0 ? x : x + w - 4, sy = b == 0 ? y : y + h - 1;
            gui.fill(sx, sy, sx + 4, sy + 1, color);
            sx = a == 0 ? x : x + w - 1; sy = b == 0 ? y : y + h - 4;
            gui.fill(sx, sy, sx + 1, sy + 4, color);
        }
    }
}
