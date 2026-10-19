package dev.expedition.arcane.chamber;

import net.minecraft.resources.ResourceLocation;

/** Native item identities shared by reversible chamber cosmetics and their tests. */
public final class ChamberDecorations {
    // Iron's TARNISHED_CROWN field is registered as tarnished_helmet, despite its display name.
    public static final ResourceLocation NECROMANCER_CROWN_ID = new ResourceLocation("irons_spellbooks", "tarnished_helmet");
    private ChamberDecorations() {}
}
