package dev.expedition.arcane.mana;

import dev.expedition.arcane.ArcaneContent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Supplies Iron's native mana; no second mana pool is stored here. */
public final class ManaSupplyService {
    public static final float MANA_PER_MB = 0.2F;
    public static final int DRINK_DURATION = 32;
    private static final float EPSILON = 0.00001F;

    // Server-player identity also separates reconnects and replacement players after respawn.
    private static final Map<ServerPlayer, EnumMap<Channel, Long>> NEXT_SUPPLY_TICK = new WeakHashMap<>();

    private ManaSupplyService() {}

    public enum Channel {
        STATION,
        BACKPACK
    }

    public static float missingMana(ServerPlayer player) {
        float current = MagicData.getPlayerMagicData(player).getMana();
        float maximum = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        if (!Float.isFinite(current) || !Float.isFinite(maximum)) {
            return 0;
        }
        return Math.max(0, maximum - current);
    }

    /** Returns the actual increase after Iron's ChangeManaEvent and MAX_MANA cap. */
    public static float restore(ServerPlayer player, float amount) {
        if (!player.isAlive() || !Float.isFinite(amount) || amount <= 0) {
            return 0;
        }
        MagicData magic = MagicData.getPlayerMagicData(player);
        float before = magic.getMana();
        float increase = Math.min(amount, missingMana(player));
        if (increase <= EPSILON) {
            return 0;
        }
        magic.addMana(increase);
        float after = magic.getMana();
        if (Float.compare(before, after) != 0) {
            PacketDistributor.sendToPlayer(player, new SyncManaPacket(magic));
        }
        return Math.max(0, after - before);
    }

    /**
     * Simulates, drains and restores on the server thread. Each channel is throttled per player,
     * so carrying multiple backpacks or touching adjacent stations cannot multiply its rate.
     */
    public static float supplyFromFluid(ServerPlayer player, IFluidHandler handler, int maximumMb,
                                       int intervalTicks, Channel channel) {
        if (!player.isAlive() || player.isSpectator() || maximumMb <= 0 || intervalTicks <= 0) {
            return 0;
        }
        long now = player.serverLevel().getGameTime();
        EnumMap<Channel, Long> cooldowns = NEXT_SUPPLY_TICK.get(player);
        if (cooldowns != null && now < cooldowns.getOrDefault(channel, Long.MIN_VALUE)) {
            return 0;
        }
        float missing = missingMana(player);
        if (missing <= EPSILON) {
            return 0;
        }
        // One mB is the smallest fluid unit; a final partial mana unit is capped by Iron itself.
        int requestedMb = Math.min(maximumMb, Math.max(1, (int) Math.ceil(missing / MANA_PER_MB)));
        FluidStack request = new FluidStack(ArcaneContent.SOURCE.get(), requestedMb);
        FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty() || simulated.getFluid() != ArcaneContent.SOURCE.get()) {
            return 0;
        }
        int availableMb = Math.min(requestedMb, simulated.getAmount());
        if (availableMb <= 0) {
            return 0;
        }
        FluidStack drained = handler.drain(new FluidStack(ArcaneContent.SOURCE.get(), availableMb),
            IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getFluid() != ArcaneContent.SOURCE.get()) {
            return 0;
        }
        // Only fluid actually removed is converted. A second native backpack tick sees this cooldown.
        NEXT_SUPPLY_TICK.computeIfAbsent(player, ignored -> new EnumMap<>(Channel.class))
            .put(channel, now + intervalTicks);
        return restore(player, Math.min(availableMb, drained.getAmount()) * MANA_PER_MB);
    }

    static InteractionResultHolder<ItemStack> beginDrink(Level level, net.minecraft.world.entity.player.Player player,
                                                        InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator()
            || (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && missingMana(serverPlayer) <= EPSILON)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    static ItemStack finishDrink(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity,
                                 int doseMb, boolean bottle) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return stack;
        }
        float restored = restore(player, doseMb * MANA_PER_MB);
        if (restored <= EPSILON || player.getAbilities().instabuild) {
            return stack;
        }
        return ItemUtils.createFilledResult(stack, player,
            new ItemStack(bottle ? Items.GLASS_BOTTLE : Items.BUCKET));
    }
}
