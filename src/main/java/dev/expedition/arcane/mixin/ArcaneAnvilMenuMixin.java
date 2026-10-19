package dev.expedition.arcane.mixin;

import dev.expedition.arcane.compat.NativeScrollRules;
import dev.expedition.arcane.compat.NativeAnvilBridge;
import io.redspace.ironsspellbooks.item.armor.UpgradeOrbType;
import io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(value = ArcaneAnvilMenu.class, remap = false)
public abstract class ArcaneAnvilMenuMixin extends ItemCombinerMenu implements NativeAnvilBridge.DropsAccess {
    @Shadow(remap = false) @Final
    private List<ItemStack> additionalDrops;

    protected ArcaneAnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory,
                                   ContainerLevelAccess access) {
        super(menuType, containerId, inventory, access);
    }

    @Unique
    @Override public List<ItemStack> arcaneCrafts$copyAdditionalDrops() {
        return additionalDrops.stream().map(ItemStack::copy).toList();
    }

    @Redirect(method = "lambda$createResult$3", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/item/armor/UpgradeOrbType;containerItem()Ljava/util/Optional;"),
            remap = false)
    private Optional<ItemStack> arcaneCrafts$isolateRefundTemplate(UpgradeOrbType type) {
        return NativeAnvilBridge.refundTemplate((ArcaneAnvilMenu) (Object) this, type);
    }

    // Match the named development method or its pinned production SRG name.
    @Inject(method = {"createResult()V", "m_6640_()V"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void arcaneIndustry$requireIndustrialScrollUpgrade(CallbackInfo callback) {
        if (NativeScrollRules.blocksNativeAnvilUpgrade(inputSlots.getItem(0), inputSlots.getItem(1))) {
            additionalDrops.clear();
            resultSlots.setItem(0, ItemStack.EMPTY);
            callback.cancel();
        }
    }
}
