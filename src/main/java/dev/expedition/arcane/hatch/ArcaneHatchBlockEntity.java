package dev.expedition.arcane.hatch;

import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.ArcaneHatchContent;
import dev.expedition.arcane.CrimsonContent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * No inventory, tank, fluid NBT or conversion is owned here. Every capability operation
 * resolves the loaded attachment again, so a rotated/replaced target cannot leave a stale tank.
 */
public final class ArcaneHatchBlockEntity extends BlockEntity {
    public static final int BOTTLE_AMOUNT = 250;
    public static final int BUCKET_AMOUNT = 1000;
    private boolean busy;
    private final IFluidHandler proxy = new Proxy();
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> proxy);

    public ArcaneHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ArcaneHatchContent.BLOCK_ENTITY.get(), pos, state);
    }

    public static boolean isBucket(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(ArcaneContent.BUCKET.get()) || stack.is(CrimsonContent.BUCKET.get());
    }

    public static boolean isContainer(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || isBucket(stack)
                || stack.is(ArcaneContent.BOTTLE.get()) || stack.is(CrimsonContent.BOTTLE.get());
    }

    public static boolean isSolution(FluidStack stack) {
        return !stack.isEmpty() && (ArcaneContent.isArcaneFluid(stack) || CrimsonContent.isCrimsonFluid(stack));
    }

    /**
     * A single synchronous server transaction. Simulation never changes the player/container;
     * EXECUTE is issued once only after a full portion is proven. The same live handler is used
     * for both calls; the guard rejects reentrant uses and capability requests during commit.
     * As with Forge FluidUtil, the target must obey the IFluidHandler simulate/execute contract.
     */
    public boolean transferContainer(Player player, InteractionHand hand) {
        if (!canMutate() || player.level() != level || isRemoved() || busy) return false;
        ItemStack held = player.getItemInHand(hand);
        if (!isContainer(held)) return false;
        busy = true;
        try {
            Target target = resolveTarget();
            if (target == null) return false;
            boolean empty = held.is(Items.BUCKET) || held.is(Items.GLASS_BOTTLE);
            int amount = isBucket(held) ? BUCKET_AMOUNT : BOTTLE_AMOUNT;
            FluidStack contents;
            if (empty) {
                contents = findDrainable(target.handler(), amount);
                if (contents.isEmpty()) return false;
            } else {
                boolean crimson = held.is(CrimsonContent.BOTTLE.get()) || held.is(CrimsonContent.BUCKET.get());
                contents = new FluidStack(crimson ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), amount);
                if (target.handler().fill(contents.copy(), IFluidHandler.FluidAction.SIMULATE) != amount) return false;
            }
            if (!target.isLive() || player.getItemInHand(hand) != held || held.isEmpty()) return false;
            Item result = empty ? filledContainer(contents, amount == BUCKET_AMOUNT)
                    : amount == BUCKET_AMOUNT ? Items.BUCKET : Items.GLASS_BOTTLE;
            if (empty) {
                FluidStack drained = target.handler().drain(contents.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (!samePortion(contents, drained)) {
                    // Contract-violating targets get no filled item. Restore an unexpected partial
                    // drain where the target permits it; never credit a full native container.
                    if (!drained.isEmpty()) target.handler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    target.changed();
                    return false;
                }
            } else {
                int filled = target.handler().fill(contents.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (filled != amount) {
                    // A normal Forge handler cannot change between the two calls on the game
                    // thread. Compensate an unexpected partial acceptance instead of consuming
                    // a whole container or performing a second fill.
                    if (filled > 0) {
                        FluidStack rollback = contents.copy();
                        rollback.setAmount(filled);
                        target.handler().drain(rollback, IFluidHandler.FluidAction.EXECUTE);
                    }
                    target.changed();
                    return false;
                }
            }
            // Filling creative empties follows vanilla pickup semantics (keep the empty and
            // give at most one matching filled item). Creative pouring keeps the full item.
            if (empty || !player.getAbilities().instabuild) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(result)));
            }
            target.changed();
            return true;
        } finally {
            busy = false;
        }
    }

    private static Item filledContainer(FluidStack stack, boolean bucket) {
        boolean crimson = CrimsonContent.isCrimsonFluid(stack);
        return bucket ? crimson ? CrimsonContent.BUCKET.get() : ArcaneContent.BUCKET.get()
                : crimson ? CrimsonContent.BOTTLE.get() : ArcaneContent.BOTTLE.get();
    }

    private static boolean samePortion(FluidStack requested, FluidStack actual) {
        return requested.isFluidEqual(actual) && requested.getAmount() == actual.getAmount();
    }

    private static FluidStack findDrainable(IFluidHandler handler, int amount) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack request = handler.getFluidInTank(tank).copy();
            // Native full bottles/buckets cannot carry fluid NBT; do not silently erase it.
            if (!isSolution(request) || request.hasTag()) continue;
            request.setAmount(amount);
            if (samePortion(request, handler.drain(request.copy(), IFluidHandler.FluidAction.SIMULATE))) return request;
        }
        return FluidStack.EMPTY;
    }

    @Nullable private Target resolveTarget() {
        if (level == null || isRemoved()) return null;
        Set<BlockPos> visited = new HashSet<>();
        ArcaneHatchBlockEntity hatch = this;
        // Following hatch chains explicitly prevents recursive capability lookup and cycles.
        for (int depth = 0; depth < 64; depth++) {
            BlockState state = hatch.getBlockState();
            if (!(state.getBlock() instanceof ArcaneHatchBlock) || !visited.add(hatch.getBlockPos())) return null;
            Direction facing = state.getValue(ArcaneHatchBlock.FACING);
            BlockPos targetPos = hatch.getBlockPos().relative(facing);
            if (!level.isLoaded(targetPos)) return null;
            BlockEntity entity = level.getBlockEntity(targetPos);
            if (entity == null || entity.isRemoved()) return null;
            Direction port = facing.getOpposite();
            if (entity instanceof ArcaneHatchBlockEntity next) {
                if (!(next.getBlockState().getBlock() instanceof ArcaneHatchBlock)) return null;
                if (port == next.getBlockState().getValue(ArcaneHatchBlock.FACING)) return null;
                hatch = next;
                continue;
            }
            IFluidHandler handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, port).orElse(null);
            return handler == null ? null : new Target(entity, handler, port);
        }
        return null;
    }

    private record Target(BlockEntity entity, IFluidHandler handler, Direction port) {
        boolean isLive() {
            return !entity.isRemoved() && entity.getLevel() != null
                    && entity.getLevel().getBlockEntity(entity.getBlockPos()) == entity
                    && entity.getCapability(ForgeCapabilities.FLUID_HANDLER, port).orElse(null) == handler;
        }
        void changed() {
            entity.setChanged();
            if (entity.getLevel() instanceof ServerLevel server) server.getChunkSource().blockChanged(entity.getBlockPos());
        }
    }

    private <T> T withTarget(T absent, Function<Target, T> action) {
        if (busy || isRemoved()) return absent;
        busy = true;
        try {
            Target target = resolveTarget();
            return target == null ? absent : action.apply(target);
        } finally {
            busy = false;
        }
    }

    private boolean canMutate() {
        return level != null && !level.isClientSide && level.getServer() != null && level.getServer().isSameThread();
    }

    private final class Proxy implements IFluidHandler {
        @Override public int getTanks() { return withTarget(0, target -> target.handler().getTanks()); }
        @Override public FluidStack getFluidInTank(int tank) {
            return withTarget(FluidStack.EMPTY, target -> {
                if (tank < 0 || tank >= target.handler().getTanks()) return FluidStack.EMPTY;
                FluidStack stack = target.handler().getFluidInTank(tank);
                return isSolution(stack) ? stack.copy() : FluidStack.EMPTY;
            });
        }
        @Override public int getTankCapacity(int tank) {
            return withTarget(0, target -> tank >= 0 && tank < target.handler().getTanks()
                    ? target.handler().getTankCapacity(tank) : 0);
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return isSolution(stack) && withTarget(false, target -> tank >= 0 && tank < target.handler().getTanks()
                    && target.handler().isFluidValid(tank, stack.copy()));
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (!isSolution(stack) || !canMutate()) return 0;
            return withTarget(0, target -> {
                int filled = target.handler().fill(stack.copy(), action);
                if (action.execute() && filled > 0) target.changed();
                return filled;
            });
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            if (!isSolution(stack) || !canMutate()) return FluidStack.EMPTY;
            return withTarget(FluidStack.EMPTY, target -> {
                FluidStack drained = target.handler().drain(stack.copy(), action);
                if (action.execute() && !drained.isEmpty()) target.changed();
                return drained;
            });
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || !canMutate()) return FluidStack.EMPTY;
            return withTarget(FluidStack.EMPTY, target -> {
                for (int tank = 0; tank < target.handler().getTanks(); tank++) {
                    FluidStack request = target.handler().getFluidInTank(tank).copy();
                    if (!isSolution(request)) continue;
                    request.setAmount(amount);
                    FluidStack drained = target.handler().drain(request, action);
                    if (drained.isEmpty()) continue;
                    if (action.execute()) target.changed();
                    return drained;
                }
                return FluidStack.EMPTY;
            });
        }
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved() && capability == ForgeCapabilities.FLUID_HANDLER
                && getBlockState().getBlock() instanceof ArcaneHatchBlock
                && side != getBlockState().getValue(ArcaneHatchBlock.FACING)) {
            return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> proxy);
    }
}
