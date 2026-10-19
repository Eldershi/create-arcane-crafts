package dev.expedition.arcane.mixin;

import dev.expedition.arcane.compat.PrintedBookProvenance;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu$5", remap = false)
public abstract class PrintedInscriptionResultSlotMixin {
    @Shadow(remap = false) @Final private InscriptionTableMenu this$0;
    @Unique private ItemStack arcaneIndustry$bookBefore = ItemStack.EMPTY;
    @Unique private int arcaneIndustry$indexBefore;
    @Unique private SpellData arcaneIndustry$spellBefore;

    // Slot.onTake is a vanilla override and is SRG-named in the pinned production JAR.
    @Inject(method = {"onTake", "m_142406_"}, at = @At("HEAD"), remap = false)
    private void arcaneIndustry$markTakenScroll(Player player, ItemStack taken, CallbackInfo callback) {
        arcaneIndustry$bookBefore = this$0.getSpellBookSlot().getItem();
        arcaneIndustry$indexBefore = ((PrintedBookProvenance.MenuAccess) this$0).arcaneIndustry$selectedSpellIndex();
        arcaneIndustry$spellBefore = PrintedBookProvenance.spellAt(arcaneIndustry$bookBefore, arcaneIndustry$indexBefore).orElse(null);
        PrintedBookProvenance.markExtraction(arcaneIndustry$bookBefore, arcaneIndustry$indexBefore, taken);
    }

    @Inject(method = {"onTake", "m_142406_"}, at = @At("TAIL"), remap = false)
    private void arcaneIndustry$clearTakenRecord(Player player, ItemStack taken, CallbackInfo callback) {
        ItemStack book = arcaneIndustry$bookBefore;
        int index = arcaneIndustry$indexBefore;
        SpellData before = arcaneIndustry$spellBefore;
        arcaneIndustry$bookBefore = ItemStack.EMPTY;
        arcaneIndustry$spellBefore = null;
        if (before == null || this$0.getSpellBookSlot().getItem() != book
                || PrintedBookProvenance.spellAt(book, index).isPresent()) return;
        PrintedBookProvenance.clearRecord(book, index);
        this$0.getSpellBookSlot().setChanged();
    }
}
