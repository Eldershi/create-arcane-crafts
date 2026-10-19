package dev.expedition.arcane.item;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** A paid-for ink stage; the original scroll is embedded without losing its native data. */
public final class UnfinishedAscensionScrollItem extends SequencedAssemblyItem {
    public static final ResourceLocation ASSEMBLY_ID = ArcaneIndustry.id("sequenced_assembly/scroll_ascension");
    public static final String ASSEMBLY_TAG = "SequencedAssembly";
    public static final String SOURCE_TAG = "arcane_industry:ascension_source";

    public UnfinishedAscensionScrollItem(Properties properties) {
        super(properties);
    }

    public static boolean isRawSource(ItemStack stack) {
        return !stack.isEmpty() && (!stack.hasTag() || !stack.getTag().contains(ASSEMBLY_TAG))
                && ScrollUpgradeRules.prepare(stack, true, true).isPresent();
    }

    private static boolean hasNativeProgress(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof UnfinishedAscensionScrollItem) || !stack.hasTag()
                || !stack.getTag().contains(ASSEMBLY_TAG, Tag.TAG_COMPOUND)) return false;
        CompoundTag progress = stack.getTag().getCompound(ASSEMBLY_TAG);
        return ASSEMBLY_ID.toString().equals(progress.getString("id"))
                && progress.getInt("Step") == 1 && progress.getFloat("Progress") == 0.5F;
    }

    public static Optional<ItemStack> sourceOf(ItemStack stack) {
        if (!hasNativeProgress(stack) || !stack.getTag().contains(SOURCE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ItemStack source = ItemStack.of(stack.getTag().getCompound(SOURCE_TAG).copy());
        return source.getCount() == 1 && isRawSource(source) ? Optional.of(source) : Optional.empty();
    }

    public static boolean isPreparedStage(ItemStack stack) {
        return sourceOf(stack).isPresent();
    }

    /** Attaches the complete source only after Create has produced valid first-step progress. */
    public static ItemStack wrapNativeStage(ItemStack advanced, ItemStack source) {
        if (!hasNativeProgress(advanced) || !isRawSource(source)) return ItemStack.EMPTY;
        ItemStack result = advanced.copyWithCount(1);
        result.getOrCreateTag().put(SOURCE_TAG, source.copyWithCount(1).save(new CompoundTag()));
        return result;
    }

    /** Typed JEI/test stock; actual processing obtains this progress from native Create advance. */
    public static ItemStack create(ItemStack source) {
        if (!isRawSource(source)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(ArcaneContent.UNFINISHED_ASCENSION_SCROLL.get());
        CompoundTag progress = new CompoundTag();
        progress.putString("id", ASSEMBLY_ID.toString());
        progress.putInt("Step", 1);
        progress.putFloat("Progress", 0.5F);
        result.getOrCreateTag().put(ASSEMBLY_TAG, progress);
        return wrapNativeStage(result, source);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, level, tooltip, flags);
        sourceOf(stack).ifPresent(source -> {
            tooltip.add(Component.translatable("item.arcane_industry.unfinished_ascension_scroll.source",
                    source.getHoverName()).withStyle(ChatFormatting.GRAY));
            ScrollUpgradeRules.prepare(source, true, true).ifPresent(upgrade -> tooltip.add(Component.translatable(
                    "item.arcane_industry.unfinished_ascension_scroll.target", upgrade.targetLevel())
                    .withStyle(ChatFormatting.GRAY)));
        });
        tooltip.add(Component.translatable("item.arcane_industry.unfinished_ascension_scroll.finish")
                .withStyle(ChatFormatting.AQUA));
    }
}
