package dev.expedition.arcane.compat;

import dev.expedition.arcane.item.UninkedScrollItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Exact per-slot provenance for spells transferred through Iron's inscription table. */
public final class PrintedBookProvenance {
    public static final String BOOK_TAG = "arcane_industry:printed_book_slots";

    private PrintedBookProvenance() {}

    public interface MenuAccess {
        int arcaneIndustry$selectedSpellIndex();
    }

    public static Optional<SpellData> spellAt(ItemStack stack, int index) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) return Optional.empty();
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || index < 0 || index >= container.getMaxSpellCount()) return Optional.empty();
        SpellData data = container.getSpellAtIndex(index);
        return data == null || data.getSpell().equals(SpellRegistry.none()) ? Optional.empty() : Optional.of(data);
    }

    public static boolean isPrintedSpell(ItemStack book, int index) {
        Optional<SpellData> current = spellAt(book, index);
        if (current.isEmpty() || !book.hasTag() || !book.getTag().contains(BOOK_TAG, Tag.TAG_COMPOUND)) return false;
        CompoundTag record = book.getTag().getCompound(BOOK_TAG).getCompound(Integer.toString(index));
        return record.getString("spell").equals(current.get().getSpell().getSpellId())
                && record.getInt("level") == current.get().getLevel();
    }

    /** Called after a successful insertion; a normal scroll also clears any stale record for its slot. */
    public static void recordInsertion(ItemStack book, int index, ItemStack consumedScroll) {
        Optional<SpellData> source = spellAt(consumedScroll, 0);
        Optional<SpellData> target = spellAt(book, index);
        if (source.isEmpty() || target.isEmpty() || !sameSpell(source.get(), target.get())) return;
        if (!UninkedScrollItem.isPrintedScroll(consumedScroll)) {
            clearRecord(book, index);
            return;
        }
        CompoundTag records = book.getOrCreateTag().getCompound(BOOK_TAG);
        CompoundTag record = new CompoundTag();
        record.putString("spell", source.get().getSpell().getSpellId());
        record.putInt("level", source.get().getLevel());
        records.put(Integer.toString(index), record);
        book.getOrCreateTag().put(BOOK_TAG, records);
    }

    /** Reads the book only; preview and actual extraction use the same exact identity check. */
    public static void markExtraction(ItemStack book, int index, ItemStack output) {
        Optional<SpellData> current = spellAt(book, index);
        Optional<SpellData> result = spellAt(output, 0);
        if (isPrintedSpell(book, index) && current.isPresent() && result.isPresent()
                && sameSpell(current.get(), result.get())) {
            output.getOrCreateTag().putBoolean(UninkedScrollItem.PRINTED_TAG, true);
        }
    }

    /** Called only after a committed extraction or replacement, never from a preview query. */
    public static void clearRecord(ItemStack book, int index) {
        if (!book.hasTag() || !book.getTag().contains(BOOK_TAG, Tag.TAG_COMPOUND)) return;
        CompoundTag records = book.getTag().getCompound(BOOK_TAG);
        records.remove(Integer.toString(index));
        if (records.isEmpty()) book.getTag().remove(BOOK_TAG);
        else book.getTag().put(BOOK_TAG, records);
    }

    public static boolean sameSpell(SpellData first, SpellData second) {
        return first.getSpell().equals(second.getSpell()) && first.getLevel() == second.getLevel();
    }
}
