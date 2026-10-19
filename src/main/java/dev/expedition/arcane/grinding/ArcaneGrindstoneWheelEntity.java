package dev.expedition.arcane.grinding;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import plus.dragons.createenchantmentindustry.common.registry.CEIBlocks;

public final class ArcaneGrindstoneWheelEntity extends KineticBlockEntity {
    public ArcaneGrindstoneWheelEntity(BlockPos pos, BlockState state) { super(ArcaneGrindingContent.WHEEL_ENTITY.get(), pos, state); }
    @Override protected Block getStressConfigKey() { return CEIBlocks.MECHANICAL_GRINDSTONE.get(); }
}
