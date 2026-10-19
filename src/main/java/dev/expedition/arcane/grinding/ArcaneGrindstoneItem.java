// SPDX-License-Identifier: LGPL-3.0-or-later
/*
 * Placement compatibility adapted from Create Enchantment Industry 2.5.4b
 * MechanicalGrindStoneItem (DragonsPlusMinecraft and contributors).
 * This file is distributed under LGPL-3.0-or-later. Local changes target arcane blocks.
 * See THIRD_PARTY_NOTICES.md and LICENSES/Create-Enchantment-Industry-2.5.4b-LICENSE.txt.
 */
package dev.expedition.arcane.grinding;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Native placement shape: right-click an empty Create drain to install the lower wheel. */
public final class ArcaneGrindstoneItem extends BlockItem {
    public ArcaneGrindstoneItem(Block block, Item.Properties properties) { super(block, properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        boolean drain = context.getLevel().getBlockState(pos).is(AllBlocks.ITEM_DRAIN.get());
        if (drain) {
            if (!(context.getLevel().getBlockEntity(pos) instanceof ItemDrainBlockEntity entity) || !entity.getHeldItemStack().isEmpty()) return InteractionResult.FAIL;
            IFluidHandler tank = entity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (tank != null) for (int i = 0; i < tank.getTanks(); i++) if (!tank.getFluidInTank(i).isEmpty()) return InteractionResult.FAIL;
        }
        return place(new Placement(context, drain));
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        if (context instanceof Placement placement && placement.drain)
            return ArcaneGrindingContent.DRAIN.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
        return super.getPlacementState(context);
    }
    private static final class Placement extends BlockPlaceContext {
        final boolean drain;
        Placement(UseOnContext context, boolean drain) { super(context); this.drain = drain; replaceClicked |= drain; }
    }
}
