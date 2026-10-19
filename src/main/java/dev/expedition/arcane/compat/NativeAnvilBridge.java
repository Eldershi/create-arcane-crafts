package dev.expedition.arcane.compat;

import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu;
import io.redspace.ironsspellbooks.item.UpgradeOrbTypeData;
import io.redspace.ironsspellbooks.item.armor.UpgradeOrbType;
import io.redspace.ironsspellbooks.registries.UpgradeOrbTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Asks the installed dependency to calculate an anvil result. These menus have
 * private input/output storage and a fresh inventory, are never opened, and do
 * not call onTake: the chamber alone owns consumption and delivery.
 */
public final class NativeAnvilBridge {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("e127c335-3dbf-48e6-8b32-ef118698d03a"), "[ArcaneCrafts]");
    // Weak values avoid retaining a client Level through its preview player.
    private static final Map<Level, WeakReference<Player>> PREVIEW_PLAYERS = new WeakHashMap<>();

    private NativeAnvilBridge() {}

    /** Implemented by the narrow mixin into the dependency's existing menu. */
    public interface DropsAccess {
        List<ItemStack> arcaneCrafts$copyAdditionalDrops();
    }

    public record Result(ItemStack output, List<ItemStack> byproducts) {
        public Result {
            output = output.copyWithCount(1);
            byproducts = byproducts.stream().map(ItemStack::copy).toList();
        }
    }

    public static Optional<Result> prepare(Level level, ItemStack primary, ItemStack material) {
        if (level == null || primary.isEmpty() || material.isEmpty()) return Optional.empty();
        // Native createResult expects this dynamic registry to exist for orb
        // upgrades. A partially connected client may not have received it yet.
        if (UpgradeOrbTypeData.has(material)
                && level.registryAccess().registry(UpgradeOrbTypeRegistry.UPGRADE_ORB_REGISTRY_KEY).isEmpty()) {
            return Optional.empty();
        }
        SnapshotMenu menu = new SnapshotMenu(new Inventory(contextPlayer(level)));
        return menu.prepare(primary, material);
    }

    /**
     * The pinned menu mutates its orb containerItem stack while building refunds.
     * Only snapshot menus intercept that getter, before the dependency sees it;
     * the dependency's shared registry template is never passed to setCount.
     */
    public static Optional<ItemStack> refundTemplate(ArcaneAnvilMenu menu, UpgradeOrbType type) {
        Optional<ItemStack> template = type.containerItem();
        return menu instanceof SnapshotMenu ? template.map(ItemStack::copy) : template;
    }

    private static Player contextPlayer(Level level) {
        if (level instanceof ServerLevel server) return FakePlayerFactory.get(server, PROFILE);
        synchronized (PREVIEW_PLAYERS) {
            WeakReference<Player> reference = PREVIEW_PLAYERS.get(level);
            Player player = reference == null ? null : reference.get();
            if (player == null) {
                player = new PreviewPlayer(level);
                PREVIEW_PLAYERS.put(level, new WeakReference<>(player));
            }
            return player;
        }
    }

    /** Allows client recipe previews without linking server-only player constructors. */
    private static final class PreviewPlayer extends Player {
        private PreviewPlayer(Level level) { super(level, BlockPos.ZERO, 0, PROFILE); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }

    private static final class SnapshotMenu extends ArcaneAnvilMenu {
        private SnapshotMenu(Inventory inventory) { super(0, inventory, ContainerLevelAccess.NULL); }

        // Input assignment triggers slotsChanged in vanilla. Wait until both
        // snapshots are installed before calculating the one complete recipe.
        @Override public void slotsChanged(Container changed) {}

        private Optional<Result> prepare(ItemStack primary, ItemStack material) {
            inputSlots.setItem(0, primary.copyWithCount(1));
            inputSlots.setItem(1, material.copyWithCount(1));
            super.createResult();
            ItemStack output = resultSlots.getItem(0);
            if (output.isEmpty()) return Optional.empty();
            List<ItemStack> refunds = new ArrayList<>();
            for (ItemStack stack : ((DropsAccess) (Object) this).arcaneCrafts$copyAdditionalDrops()) {
                if (stack.isEmpty()) continue;
                // Native refunds can exceed an item's legal stack size. Split
                // copies for the chamber's persisted byproduct transaction.
                int remaining = stack.getCount();
                while (remaining > 0) {
                    int count = Math.min(remaining, stack.getMaxStackSize());
                    refunds.add(stack.copyWithCount(count));
                    remaining -= count;
                }
            }
            return Optional.of(new Result(output, refunds));
        }
    }
}
