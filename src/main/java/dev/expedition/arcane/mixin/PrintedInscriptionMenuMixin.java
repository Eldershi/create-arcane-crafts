package dev.expedition.arcane.mixin;

import dev.expedition.arcane.compat.PrintedBookProvenance;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InscriptionTableMenu.class, remap = false)
public abstract class PrintedInscriptionMenuMixin implements PrintedBookProvenance.MenuAccess {
    @Shadow(remap = false) private int selectedSpellIndex;
    @Shadow(remap = false) public abstract Slot getSpellBookSlot();
    @Shadow(remap = false) public abstract Slot getScrollSlot();
    @Shadow(remap = false) public abstract Slot getResultSlot();

    @Unique private ItemStack arcaneIndustry$pendingBook = ItemStack.EMPTY;
    @Unique private ItemStack arcaneIndustry$pendingScroll = ItemStack.EMPTY;
    @Unique private int arcaneIndustry$pendingCount;

    @Override
    public int arcaneIndustry$selectedSpellIndex() { return selectedSpellIndex; }

    @Inject(method = "doInscription", at = @At("HEAD"), remap = false)
    private void arcaneIndustry$captureInsertion(int index, CallbackInfo callback) {
        arcaneIndustry$pendingBook = ItemStack.EMPTY;
        arcaneIndustry$pendingScroll = ItemStack.EMPTY;
        ItemStack book = getSpellBookSlot().getItem();
        ItemStack source = getScrollSlot().getItem();
        if (!(book.getItem() instanceof SpellBook) || !source.is(ItemRegistry.SCROLL.get())
                || PrintedBookProvenance.spellAt(source, 0).isEmpty()
                || PrintedBookProvenance.spellAt(book, index).isPresent()) return;
        arcaneIndustry$pendingBook = book;
        arcaneIndustry$pendingScroll = source.copyWithCount(1);
        arcaneIndustry$pendingCount = source.getCount();
    }

    @Inject(method = "doInscription", at = @At("TAIL"), remap = false)
    private void arcaneIndustry$commitInsertion(int index, CallbackInfo callback) {
        ItemStack book = arcaneIndustry$pendingBook;
        ItemStack source = arcaneIndustry$pendingScroll;
        int count = arcaneIndustry$pendingCount;
        arcaneIndustry$pendingBook = ItemStack.EMPTY;
        arcaneIndustry$pendingScroll = ItemStack.EMPTY;
        if (book.isEmpty() || source.isEmpty() || getSpellBookSlot().getItem() != book
                || getScrollSlot().getItem().getCount() != count - 1) return;
        var inserted = PrintedBookProvenance.spellAt(book, index);
        var original = PrintedBookProvenance.spellAt(source, 0);
        if (inserted.isEmpty() || original.isEmpty()
                || !PrintedBookProvenance.sameSpell(inserted.get(), original.get())) return;
        PrintedBookProvenance.recordInsertion(book, index, source);
        getSpellBookSlot().setChanged();
    }

    @Inject(method = "setupResultSlot", at = @At("TAIL"), remap = false)
    private void arcaneIndustry$markPreview(CallbackInfo callback) {
        PrintedBookProvenance.markExtraction(getSpellBookSlot().getItem(), selectedSpellIndex, getResultSlot().getItem());
    }
}
