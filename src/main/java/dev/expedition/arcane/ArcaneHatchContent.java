package dev.expedition.arcane;

import dev.expedition.arcane.hatch.ArcaneHatchBlock;
import dev.expedition.arcane.hatch.ArcaneHatchBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Separate registration keeps the hatch's stateless fluid proxy independent of the machines. */
public final class ArcaneHatchContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArcaneIndustry.MOD_ID);

    public static final RegistryObject<ArcaneHatchBlock> BLOCK = BLOCKS.register("arcane_hatch", ArcaneHatchBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("arcane_hatch", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ArcaneHatchBlockEntity>> BLOCK_ENTITY = ENTITIES.register("arcane_hatch",
            () -> BlockEntityType.Builder.of(ArcaneHatchBlockEntity::new, BLOCK.get()).build(null));

    private ArcaneHatchContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
    }
}
