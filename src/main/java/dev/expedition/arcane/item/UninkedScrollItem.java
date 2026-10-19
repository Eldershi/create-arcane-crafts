package dev.expedition.arcane.item;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import dev.expedition.arcane.ArcaneContent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.item.InkItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

/** Printer intermediate, deliberately not an IScroll and not a native spell container. */
public final class UninkedScrollItem extends SequencedAssemblyItem {
    public static final int INK_AMOUNT = 250;
    public static final String SPELL_ID_TAG = "arcane_industry:printed_spell";
    public static final String PRINTED_TAG = "arcane_industry_printed";
    public static final ResourceLocation ASSEMBLY_ID = new ResourceLocation("arcane_industry", "sequenced_assembly/printed_scroll");
    public static final String ASSEMBLY_TAG = "SequencedAssembly";

    public UninkedScrollItem(Properties properties) {
        super(properties);
    }

    public static boolean isPrintableSpell(AbstractSpell spell) {
        return spell != null && !spell.equals(SpellRegistry.none()) && spell.isEnabled()
                && spell.getMinLevel() <= 1 && spell.getMaxLevel() >= 1 && spell.allowCrafting();
    }

    /** Only the exact native empty scroll is printer stock; paper and populated scrolls are rejected. */
    public static boolean isBlankNativeScroll(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemRegistry.SCROLL.get())) return false;
        if (stack.hasTag() && stack.getTag().contains(ASSEMBLY_TAG)) return false;
        if (!ISpellContainer.isSpellContainer(stack)) return true;
        ISpellContainer container = ISpellContainer.get(stack);
        return container != null && container.isEmpty() && container.getActiveSpellCount() == 0;
    }

    public static ItemStack create(AbstractSpell spell) {
        if (!isPrintableSpell(spell)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(ArcaneContent.UNINKED_SCROLL.get());
        result.getOrCreateTag().putString(SPELL_ID_TAG, spell.getSpellResource().toString());
        return withInkingProgress(result);
    }

    /** Creates a display/test-stock copy; production printing obtains progress from native advance. */
    public static ItemStack withInkingProgress(ItemStack stack) {
        if (readSpell(stack).isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        CompoundTag progress = new CompoundTag();
        progress.putString("id", ASSEMBLY_ID.toString());
        progress.putInt("Step", 1);
        progress.putFloat("Progress", 0.5F);
        result.getOrCreateTag().put(ASSEMBLY_TAG, progress);
        return result;
    }

    public static Optional<AbstractSpell> readSpell(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof UninkedScrollItem) || !stack.hasTag()
                || !stack.getTag().contains(SPELL_ID_TAG, Tag.TAG_STRING)) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(stack.getTag().getString(SPELL_ID_TAG));
        if (id == null) return Optional.empty();
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return isPrintableSpell(spell) ? Optional.of(spell) : Optional.empty();
    }

    /** Printing always creates level one, so ink follows that level's native rarity. */
    public static Optional<Fluid> readRequiredInk(ItemStack stack) {
        return readSpell(stack).map(spell -> InkItem.getInkForRarity(spell.getRarity(1)).fluid().get());
    }

    /** Only Create's completed printing step may enter the inking step. Bare JEI previews cannot. */
    public static boolean isInkingStage(ItemStack stack) {
        if (readSpell(stack).isEmpty() || !stack.getTag().contains(ASSEMBLY_TAG, Tag.TAG_COMPOUND)) return false;
        var progress = stack.getTag().getCompound(ASSEMBLY_TAG);
        return ASSEMBLY_ID.toString().equals(progress.getString("id"))
                && progress.getInt("Step") == 1 && progress.getFloat("Progress") == 0.5F;
    }

    /** Read-only preview; the filling transaction consumes its inputs separately after validation. */
    public static ItemStack finishInking(ItemStack stack) {
        return readSpell(stack).map(spell -> {
            ItemStack result = new ItemStack(ItemRegistry.SCROLL.get());
            ISpellContainer.createScrollContainer(spell, 1, result);
            result.getOrCreateTag().putBoolean(PRINTED_TAG, true);
            return result;
        }).orElse(ItemStack.EMPTY);
    }

    public static boolean isPrintedScroll(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemRegistry.SCROLL.get()) && stack.hasTag()
                && stack.getTag().getBoolean(PRINTED_TAG);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, level, tooltip, flags);
        readSpell(stack).ifPresent(spell -> tooltip.add(Component.translatable(
                "item.arcane_industry.uninked_scroll.target", spell.getDisplayName(null)).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("item.arcane_industry.uninked_scroll.finish").withStyle(ChatFormatting.BLUE));
    }
}
