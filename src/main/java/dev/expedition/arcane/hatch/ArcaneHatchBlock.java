package dev.expedition.arcane.hatch;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import dev.expedition.arcane.ArcaneHatchContent;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Horizontal Create-style access plate; FACING points into the attached fluid handler. */
public final class ArcaneHatchBlock extends HorizontalDirectionalBlock
        implements IBE<ArcaneHatchBlockEntity>, IWrenchable, ProperWaterloggedBlock {
    public ArcaneHatchBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.5F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace().getAxis().isVertical()) return null;
        return withWater(defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()), context);
    }

    @Override public FluidState getFluidState(BlockState state) { return fluidState(state); }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                             LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return state;
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        // In particular, an empty hand never extracts an item or removes a chamber decoration.
        ItemStack held = player.getItemInHand(hand);
        if (!ArcaneHatchBlockEntity.isContainer(held)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean bucket = ArcaneHatchBlockEntity.isBucket(held);
        boolean taking = held.is(Items.BUCKET) || held.is(Items.GLASS_BOTTLE);
        if (level.getBlockEntity(pos) instanceof ArcaneHatchBlockEntity hatch && hatch.transferContainer(player, hand)) {
            level.playSound(null, pos, bucket ? taking ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY
                            : taking ? SoundEvents.BOTTLE_FILL : SoundEvents.BOTTLE_EMPTY,
                    SoundSource.BLOCKS, .5F, 1F);
        }
        // Consume a recognized failed use as well: a rejected pour must not place a fluid block
        // or start drinking the blue bottle through the hatch's face.
        return InteractionResult.CONSUME;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.ITEM_HATCH.get(state.getValue(FACING).getOpposite());
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        IBE.onRemove(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }

    @Override public Class<ArcaneHatchBlockEntity> getBlockEntityClass() { return ArcaneHatchBlockEntity.class; }
    @Override public BlockEntityType<? extends ArcaneHatchBlockEntity> getBlockEntityType() { return ArcaneHatchContent.BLOCK_ENTITY.get(); }
    @Override public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
}
