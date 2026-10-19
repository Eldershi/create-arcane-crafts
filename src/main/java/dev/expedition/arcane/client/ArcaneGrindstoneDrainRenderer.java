package dev.expedition.arcane.client;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import dev.expedition.arcane.grinding.ArcaneGrindingContent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneDrainBlockEntity;
import plus.dragons.createenchantmentindustry.common.kinetics.grindstone.GrindstoneDrainRenderer;

/** Uses the arcane wheel model while retaining CEI's item, fluid and kinetic rendering. */
public final class ArcaneGrindstoneDrainRenderer extends GrindstoneDrainRenderer {
    public ArcaneGrindstoneDrainRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected BlockState getRenderedBlockState(GrindstoneDrainBlockEntity blockEntity) {
        return ArcaneGrindingContent.GRINDSTONE.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, getRotationAxisOf(blockEntity));
    }
}
