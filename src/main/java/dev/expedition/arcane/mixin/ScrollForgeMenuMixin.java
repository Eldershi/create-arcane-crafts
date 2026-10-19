package dev.expedition.arcane.mixin;

import dev.expedition.arcane.compat.NativeScrollRules;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScrollForgeMenu.class, remap = false)
public abstract class ScrollForgeMenuMixin {
    @Shadow(remap = false) @Final
    private Slot inkSlot;
    @Shadow(remap = false) @Final
    private Slot resultSlot;
    @Shadow(remap = false)
    private AbstractSpell spellRecipeSelection;

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcaneIndustry$requireIndustrialHighLevelWriting(AbstractSpell spell, CallbackInfo callback) {
        if (NativeScrollRules.blocksNativeWriting(spell, inkSlot.getItem())) {
            spellRecipeSelection = SpellRegistry.none();
            resultSlot.set(ItemStack.EMPTY);
            callback.cancel();
        }
    }
}
