package dev.expedition.arcane.grinding;

import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import plus.dragons.createenchantmentindustry.common.registry.CEIBlocks;

/** Separate registrations: the original CEI blocks and block entity types are never replaced. */
public final class ArcaneGrindingContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArcaneIndustry.MOD_ID);
    public static final RegistryObject<ArcaneGrindstoneBlock> GRINDSTONE = BLOCKS.register("arcane_grindstone",
            () -> new ArcaneGrindstoneBlock(BlockBehaviour.Properties.copy(CEIBlocks.MECHANICAL_GRINDSTONE.get())));
    public static final RegistryObject<ArcaneGrindstoneDrainBlock> DRAIN = BLOCKS.register("arcane_grindstone_drain",
            () -> new ArcaneGrindstoneDrainBlock(GRINDSTONE.get(), BlockBehaviour.Properties.copy(CEIBlocks.GRINDSTONE_DRAIN.get())));
    public static final RegistryObject<Item> GRINDSTONE_ITEM = ITEMS.register("arcane_grindstone",
            () -> new ArcaneGrindstoneItem(GRINDSTONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DRAIN_ITEM = ITEMS.register("arcane_grindstone_drain",
            () -> new BlockItem(DRAIN.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<ArcaneGrindstoneWheelEntity>> WHEEL_ENTITY = ENTITIES.register("arcane_grindstone",
            () -> BlockEntityType.Builder.of(ArcaneGrindstoneWheelEntity::new, GRINDSTONE.get()).build(null));
    public static final RegistryObject<BlockEntityType<ArcaneGrindstoneDrainEntity>> DRAIN_ENTITY = ENTITIES.register("arcane_grindstone_drain",
            () -> BlockEntityType.Builder.of(ArcaneGrindstoneDrainEntity::new, DRAIN.get()).build(null));
    private ArcaneGrindingContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); }
}
