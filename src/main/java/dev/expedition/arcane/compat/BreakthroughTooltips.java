package dev.expedition.arcane.compat;

import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.ScrollUpgradeRules;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Common Forge event with no Minecraft client class references, safe to load on a dedicated server. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID)
public final class BreakthroughTooltips {
    private BreakthroughTooltips() {}

    @SubscribeEvent
    public static void appendBreakthrough(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof IScroll) || !ISpellContainer.isSpellContainer(stack)) return;
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.getActiveSpellCount() != 1) return;
        SpellSlot slot = container.getActiveSpells().get(0);
        if (slot.getSpell().equals(SpellRegistry.none())) return;
        int nativeMax = slot.getSpell().getMaxLevel();
        long extra = (long) slot.getLevel() - nativeMax;
        if (extra <= 0) return;
        event.getToolTip().add(Component.literal("超越 +" + extra + "/" + ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS
                + " · 原生上限 " + nativeMax).withStyle(extra <= ScrollUpgradeRules.MAX_BREAKTHROUGH_LEVELS
                ? ChatFormatting.GOLD : ChatFormatting.RED));
        event.getToolTip().add(Component.literal("超越卷轴不可打印复制").withStyle(ChatFormatting.DARK_GRAY));
    }
}
