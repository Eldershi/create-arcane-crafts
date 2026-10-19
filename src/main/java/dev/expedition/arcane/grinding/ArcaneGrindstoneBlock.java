package dev.expedition.arcane.grinding;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.MechanicalGrindstoneBlock;

/** Exact native wheel interactions, repair, enchanting/XP, polishing and rotation. */
public final class ArcaneGrindstoneBlock extends MechanicalGrindstoneBlock {
    public ArcaneGrindstoneBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ArcaneGrindingContent.WHEEL_ENTITY.get();
    }
}
