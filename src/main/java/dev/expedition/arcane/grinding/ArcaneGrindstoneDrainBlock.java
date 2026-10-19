package dev.expedition.arcane.grinding;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneDrainBlock;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneDrainBlockEntity;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.MechanicalGrindstoneBlock;

public final class ArcaneGrindstoneDrainBlock extends GrindstoneDrainBlock {
    public ArcaneGrindstoneDrainBlock(MechanicalGrindstoneBlock wheel, BlockBehaviour.Properties properties) { super(wheel, properties); }
    @Override public BlockEntityType<? extends GrindstoneDrainBlockEntity> getBlockEntityType() { return ArcaneGrindingContent.DRAIN_ENTITY.get(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis().isHorizontal() && face.getAxis() == getRotationAxis(state);
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) { return new ItemStack(ArcaneGrindingContent.DRAIN_ITEM.get()); }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ArcaneGrindstoneDrainEntity drain) {
            for (ItemStack drop : drops) if (drop.is(ArcaneGrindingContent.DRAIN_ITEM.get()))
                drop.getOrCreateTag().put("BlockEntityTag", drain.savePortableRecovery());
        }
        return drops;
    }
}
