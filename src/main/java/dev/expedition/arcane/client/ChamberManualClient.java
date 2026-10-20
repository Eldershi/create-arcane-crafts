package dev.expedition.arcane.client;

import dev.expedition.arcane.chamber.AscensionChamberBlock;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Local presentation only: the server owns the item, selected native slot and processing clock. */
final class ChamberManualClient {
    static final double WORK_SECONDS = 3.5;
    private static final Map<AscensionChamberBlockEntity, State> STATES = new WeakHashMap<>();
    private static Level world;
    private static final float[] FLIPS = new float[65];
    static {
        float flip = 0, velocity = 0;
        for (int i = 1; i < FLIPS.length; i++) {
            velocity += (Mth.clamp((3 - flip) * .4F, -.2F, .2F) - velocity) * .9F;
            flip += velocity;
            FLIPS[i] = flip;
        }
    }
    private ChamberManualClient() {}

    static boolean isBook(ItemStack stack) {
        // The native item contract includes future and addon spell books as well.
        return !stack.isEmpty() && stack.getItem() instanceof ISpellbook;
    }

    static State state(AscensionChamberBlockEntity chamber) {
        checkWorld();
        State state = STATES.computeIfAbsent(chamber, unused -> new State());
        state.tick(chamber);
        // Block replacement/NBT packets can reuse a client entity in the same tick
        // with an unchanged revision. Presentation must never keep a vacant index.
        var currentSlots = slots(chamber);
        if (!currentSlots.contains(state.selectedSlot)) state.selectedSlot = currentSlots.isEmpty() ? -1 : currentSlots.get(0);
        return state;
    }

    static void tick() {
        checkWorld();
        var iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var chamber = entry.getKey();
            if (chamber.isRemoved() || chamber.getLevel() != world) {
                entry.getValue().stop(); iterator.remove();
            } else entry.getValue().tick(chamber);
        }
    }

    private static void checkWorld() {
        Level current = Minecraft.getInstance().level;
        if (world != current) {
            STATES.values().forEach(State::stop);
            STATES.clear(); world = current;
        }
    }

    static boolean inRange(AscensionChamberBlockEntity chamber) {
        var player = Minecraft.getInstance().player;
        return player != null && Math.abs(player.getX() - chamber.getBlockPos().getX() - .5) <= 2.5
                && Math.abs(player.getZ() - chamber.getBlockPos().getZ() - .5) <= 2.5;
    }

    static List<Integer> slots(AscensionChamberBlockEntity chamber) {
        List<SpellData> spells = chamber.getManualBookSpells();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < spells.size(); i++) {
            SpellData spell = spells.get(i);
            if (spell != null && spell != SpellData.EMPTY && spell.getLevel() > 0) result.add(i);
        }
        return result;
    }

    static float ease(double value) {
        float t = Mth.clamp((float) value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static final class State {
        private enum Motion { CLOSED, OPENING, OPEN, CLOSING, LOCKED }
        private Motion motion = Motion.CLOSED;
        private long tick = Long.MIN_VALUE, placed = Long.MIN_VALUE, started = -1, completed = -1, materialPlaced = -1;
        private double transitionAt, pageStartedAt, pendingAt;
        private float fromOpen, closingPage1 = .1F, closingPage2 = .9F;
        private boolean observed, pending, closeSoundPlayed;
        boolean hadPartner;
        int selectedSlot = -1, selectionRevision = -1;
        private int emitted;
        private boolean completionBlue;
        private long researchSeed = Long.MIN_VALUE;
        private ChamberManualSounds.Research researchSound;
        private net.minecraft.client.resources.sounds.SimpleSoundInstance openingSound;

        void tick(AscensionChamberBlockEntity chamber) {
            if (chamber.getLevel() == null) return;
            long now = chamber.getLevel().getGameTime();
            if (tick == now) return;
            tick = now;
            long nextPlaced = chamber.getManualItemPlacedAt();
            long nextStarted = chamber.getManualStartedAt();
            long nextCompleted = chamber.getManualCompletedAt();
            ItemStack primary = chamber.getManualPrimary();
            if (placed != nextPlaced) {
                if (observed && nextPlaced >= 0) ChamberManualSounds.play("material", chamber.getBlockPos(), .168F, 1);
                placed = nextPlaced; motion = Motion.CLOSED; pending = false; fromOpen = 0;
                emitted = 0; hadPartner = false;
            }
            if (selectionRevision != chamber.getManualSelectionRevision()) {
                selectionRevision = chamber.getManualSelectionRevision();
                selectedSlot = chamber.getSelectedBookSpellSlot();
                var slots = slots(chamber);
                if (!slots.contains(selectedSlot)) selectedSlot = slots.isEmpty() ? -1 : slots.get(0);
                if (!chamber.isManualBookSelectionLocked()) pending = false;
            }
            if (pending && now - pendingAt > 40 && !chamber.isManualBookSelectionLocked()) pending = false;
            long nextMaterial = chamber.getManualMaterialPlacedAt();
            if (nextMaterial != materialPlaced) {
                if (observed && nextMaterial >= 0) ChamberManualSounds.play("material", chamber.getBlockPos(), .168F, 1);
                materialPlaced = nextMaterial;
            }
            if (chamber.isManualProcessing()) hadPartner |= !chamber.getManualSecondary().isEmpty();
            boolean research = chamber.hasResearchPresentation();
            if (chamber.isManualProcessing()) {
                completionBlue = chamber.getBlockState().getValue(AscensionChamberBlock.STATE) == 2;
            }
            if (research) {
                if (researchSound == null || researchSeed != chamber.getResearchVisualSeed()) {
                    if (researchSound != null) researchSound.stop();
                    researchSeed = chamber.getResearchVisualSeed();
                    researchSound = ChamberManualSounds.research(chamber.getBlockPos(), chamber.isResearchCrimson());
                }
                researchSound.update(ScrollRevealMotion.logicalTick(chamber.getResearchVisualTicks(now)),
                        chamber.getResearchRarity(), !chamber.isManualProcessing() || chamber.isResearchVisualRunning());
            } else if (researchSound != null) {
                researchSound.stop(); researchSound = null;
            }
            started = nextStarted;
            if (nextCompleted != completed) {
                if (!research && observed && nextCompleted >= 0 && now - nextCompleted <= 2) {
                    ChamberManualSounds.complete(chamber.getManualOperation(), chamber.getBlockPos());
                    emitted = 0;
                } else if (nextCompleted >= 0) emitted = 48;
                completed = nextCompleted;
            }
            if (!research && completed >= 0 && !primary.isEmpty() && chamber.getLevel() instanceof ClientLevel client) {
                int target = (int) Math.min(48, Math.floor((now - completed + 1) / 28.0 * 48));
                if (target > emitted) { AscensionCompletionParticle.emit(client, chamber.getBlockPos(), target - emitted, completionBlue); emitted = target; }
            }
            boolean forcedClosed = pending || chamber.isManualBookSelectionLocked() || chamber.isManualProcessing()
                    || completed >= 0 && !chamber.isBookRearmed();
            boolean wantOpen = !chamber.isBasinMode() && !primary.isEmpty() && isBook(primary) && !forcedClosed && inRange(chamber) && chamber.isBlazeAlive();
            if (wantOpen && (motion == Motion.CLOSED || motion == Motion.LOCKED || motion == Motion.CLOSING)) {
                fromOpen = sample(now).open;
                transitionAt = pageStartedAt = now;
                motion = Motion.OPENING;
                openingSound = ChamberManualSounds.play("book_open", chamber.getBlockPos(), .104F, 1);
            } else if (!wantOpen && (motion == Motion.OPEN || motion == Motion.OPENING)) beginClose(chamber, now);
            if (motion == Motion.OPENING && now - transitionAt >= 10) motion = Motion.OPEN;
            if (motion == Motion.CLOSING) {
                if (!closeSoundPlayed && now - transitionAt >= 8.8) {
                    closeSoundPlayed = true;
                    ChamberManualSounds.play("book_close", chamber.getBlockPos(), .2F, 1);
                }
                if (now - transitionAt >= 10.4) motion = forcedClosed ? Motion.LOCKED : Motion.CLOSED;
            }
            if (primary.isEmpty() || !chamber.isBlazeAlive()) { motion = Motion.CLOSED; pending = false; stop(); }
            observed = true;
        }

        private void beginClose(AscensionChamberBlockEntity chamber, double now) {
            if (openingSound != null) {
                Minecraft.getInstance().getSoundManager().stop(openingSound);
                openingSound = null;
            }
            Pose current = sample(now);
            fromOpen = current.open; closingPage1 = current.page1; closingPage2 = current.page2;
            transitionAt = now; closeSoundPlayed = false; motion = Motion.CLOSING;
        }

        void confirm(AscensionChamberBlockEntity chamber, double now) {
            pending = true; pendingAt = now;
            beginClose(chamber, now);
        }

        boolean selectable(AscensionChamberBlockEntity chamber, double now) {
            return !pending && !chamber.isManualBookSelectionLocked() && !chamber.isManualProcessing()
                    && (chamber.getManualCompletedAt() < 0 || chamber.isBookRearmed()) && inRange(chamber)
                    && (motion == Motion.OPEN || motion == Motion.OPENING) && now - transitionAt >= 7
                    && isBook(chamber.getManualPrimary()) && chamber.isBlazeAlive();
        }

        Pose sample(double now) {
            float open = 0, p1 = closingPage1, p2 = closingPage2, impact = 0;
            if (motion == Motion.OPENING || motion == Motion.OPEN) {
                open = fromOpen + (1 - fromOpen) * Mth.clamp((float) ((now - transitionAt) / 10), 0, 1);
                float position = Mth.clamp((float) (now - pageStartedAt), 0, FLIPS.length - 1);
                int index = (int) position;
                float flip = Mth.lerp(position - index, FLIPS[index], FLIPS[Math.min(index + 1, FLIPS.length - 1)]);
                p1 = page(flip + .25F); p2 = page(flip + .75F);
            } else if (motion == Motion.CLOSING) {
                double age = (now - transitionAt) / 20;
                open = fromOpen * (1 - (float) Math.pow(Mth.clamp(age / .44, 0, 1), 4));
                if (fromOpen > .2) impact = Mth.sin((float) (Mth.clamp((age - .44) / .08, 0, 1) * Math.PI));
            }
            return new Pose(open, p1, p2, impact);
        }
        private static float page(float value) { return Mth.clamp((value - Mth.floor(value)) * 1.6F - .3F, 0, 1); }
        void stop() {
            if (researchSound != null) { researchSound.stop(); researchSound = null; }
            if (openingSound != null) { Minecraft.getInstance().getSoundManager().stop(openingSound); openingSound = null; }
        }
    }
    record Pose(float open, float page1, float page2, float impact) {}
}
