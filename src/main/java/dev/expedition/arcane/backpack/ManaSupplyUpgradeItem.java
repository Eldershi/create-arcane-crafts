package dev.expedition.arcane.backpack;

import dev.expedition.arcane.mana.ManaSupplyService;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

public final class ManaSupplyUpgradeItem extends UpgradeItemBase<ManaSupplyUpgradeItem.Wrapper> {
    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);
    private static final IUpgradeCountLimitConfig LIMITS = new IUpgradeCountLimitConfig() {
        @Override
        public int getMaxUpgradesPerStorage(String storageType, ResourceLocation upgradeRegistryName) {
            return 1;
        }

        @Override
        public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup upgradeGroup) {
            return Integer.MAX_VALUE;
        }
    };

    public ManaSupplyUpgradeItem() {
        super(LIMITS);
    }

    @Override
    public UpgradeType<Wrapper> getType() {
        return TYPE;
    }

    @Override
    public List<IUpgradeItem.UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }

    public static final class Wrapper extends UpgradeWrapperBase<Wrapper, ManaSupplyUpgradeItem>
        implements ITickableUpgrade {
        private Wrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
            super(storageWrapper, upgrade, upgradeSaveHandler);
        }

        @Override
        public boolean hideSettingsTab() {
            return true;
        }

        @Override
        public void tick(Entity entity, Level level, BlockPos pos) {
            if (level.isClientSide || !isEnabled() || !(entity instanceof ServerPlayer player)
                || !storageWrapper.isUpgradeRunnable(upgrade)) {
                return;
            }
            storageWrapper.getFluidHandler().ifPresent(handler ->
                ManaSupplyService.supplyFromFluid(player, handler, 25, 20,
                    ManaSupplyService.Channel.BACKPACK));
        }
    }
}
