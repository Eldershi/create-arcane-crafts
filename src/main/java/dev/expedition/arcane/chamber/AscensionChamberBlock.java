package dev.expedition.arcane.chamber;

import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;
import javax.annotation.Nullable;
import java.util.List;

public class AscensionChamberBlock extends BaseEntityBlock {
    /** 0 idle, 1 ordinary, 2 breakthrough. Processing arrangement is detected separately. */
    public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 2);
    public static final EnumProperty<Skin> SKIN = EnumProperty.create("skin", Skin.class);
    public static final BooleanProperty ALIVE = BooleanProperty.create("alive");
    /** Decorative residual flame in an empty chamber; never a working heat state. */
    public static final BooleanProperty BURNING = BooleanProperty.create("burning");

    public enum Skin implements StringRepresentable {
        COPPER("copper"), BRASS("brass"), MITHRIL("mithril");
        private final String key;
        Skin(String key) { this.key = key; }
        @Override public String getSerializedName() { return key; }
        public static Skin byKey(String key) {
            for (Skin skin : values()) if (skin.key.equals(key)) return skin;
            return COPPER;
        }
    }

    public AscensionChamberBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.5F).sound(SoundType.METAL)
                .noOcclusion().lightLevel(state -> !state.getValue(ALIVE) ? state.getValue(BURNING) ? 12 : 0
                        : state.getValue(STATE) == 0 ? 8 : 15));
        registerDefaultState(stateDefinition.any().setValue(STATE, 0).setValue(SKIN, Skin.COPPER)
                .setValue(ALIVE, true).setValue(BURNING, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE, SKIN, ALIVE, BURNING);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AscensionChamberBlockEntity(pos, state);
    }

    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ArcaneContent.CHAMBER_ENTITY.get(), level.isClientSide
                ? AscensionChamberBlockEntity::clientTicker : AscensionChamberBlockEntity::serverTicker);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (chamber.interactResidualFire(held, player, hand)) return InteractionResult.CONSUME;
        if (chamber.interactCosmetic(held, player) || chamber.restoreBlaze(held, player)) {
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (chamber.isManualProcessing()) {
                player.displayClientMessage(Component.translatable("gui.arcane_industry.chamber.processing"), true);
                return InteractionResult.CONSUME;
            }
            if (!chamber.isBasinMode() && (!chamber.getManualPrimary().isEmpty() || !chamber.getManualSecondary().isEmpty())) {
                // The client normally sends the floating-model packet because its geometry extends
                // above this block. Keep the native block path identical and never fall through to
                // sequential extraction of a different slot or to the off-hand's implicit fallback.
                if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
                    ChamberDisplayHit.Target aimed = ChamberDisplayHit.find(player, 1);
                    if (aimed != null && aimed.chamber() == chamber) chamber.takeDisplayedManualItem(player, aimed.role());
                    else player.displayClientMessage(Component.translatable("gui.arcane_industry.chamber.aim_to_take"), true);
                }
                return InteractionResult.CONSUME;
            }
            ItemStack extracted = chamber.extractForPlayer(player.isShiftKeyDown());
            if (!extracted.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, extracted);
                return InteractionResult.CONSUME;
            }
            chamber.removeDecorationForPlayer(player);
            return InteractionResult.CONSUME;
        }
        if (held.is(Items.BUCKET) || held.is(Items.GLASS_BOTTLE)) {
            chamber.drainToContainer(held, player);
            return InteractionResult.CONSUME;
        }
        if (AscensionChamberBlockEntity.isSolutionContainer(held)) {
            chamber.fillFromContainer(held, player);
            return InteractionResult.CONSUME;
        }
        if (held.is(CrimsonContent.EMBER_CAKE.get()) && !chamber.isTranscendent()) {
            return InteractionResult.CONSUME;
        }
        boolean essence = AscensionChamberBlockEntity.isArcaneEssence(held);
        if (chamber.insertFromPlayer(held, player.getAbilities().instabuild)) {
            return InteractionResult.CONSUME;
        }
        if (essence) {
            return InteractionResult.CONSUME;
        }
        if (chamber.acceptsInputType(held)) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AscensionChamberBlockEntity chamber) {
            // A dead chamber cannot acquire another blaze simply by being broken and replaced.
            // Inventory and decorations drop separately; never duplicate them in this tag.
            for (ItemStack drop : drops) if (drop.is(ArcaneContent.CHAMBER_ITEM.get())) {
                drop.getOrCreateTag().put("BlockEntityTag", chamber.savePortableAppearance());
            }
        }
        return drops;
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber) {
            for (int slot = 0; slot < chamber.inventory.getSlots(); slot++) {
                ItemStack stack = chamber.inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) popResource(level, pos, stack.copy());
            }
            for (ItemStack returned : chamber.getAdditionalReturnDrops()) popResource(level, pos, returned);
            for (ItemStack decoration : chamber.getDecorationDrops()) popResource(level, pos, decoration);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
