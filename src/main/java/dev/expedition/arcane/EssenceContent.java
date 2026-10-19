package dev.expedition.arcane;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.materials.ExperienceBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import plus.dragons.createenchantmentindustry.common.registry.CEIBlocks;

/** Solid storage is nine equivalent essence units, independent of upgrade fuel efficiency. */
public final class EssenceContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneIndustry.MOD_ID);
    public static final RegistryObject<Block> ARCANE_BLOCK = BLOCKS.register("arcane_essence_block",
            () -> new ExperienceBlock(BlockBehaviour.Properties.copy(AllBlocks.EXPERIENCE_BLOCK.get()).lightLevel(state -> 15)));
    public static final RegistryObject<Item> ARCANE_BLOCK_ITEM = ITEMS.register("arcane_essence_block",
            () -> new BlockItem(ARCANE_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CRIMSON_ESSENCE = ITEMS.register("crimson_essence", () -> new Item(new Item.Properties()) {
        @Override public boolean isFoil(ItemStack stack) { return true; }
    });
    public static final RegistryObject<Block> CRIMSON_BLOCK = BLOCKS.register("crimson_essence_block",
            () -> new ExperienceBlock(BlockBehaviour.Properties.copy(CEIBlocks.SUPER_EXPERIENCE_BLOCK.get()).lightLevel(state -> 15)));
    public static final RegistryObject<Item> CRIMSON_BLOCK_ITEM = ITEMS.register("crimson_essence_block",
            () -> new BlockItem(CRIMSON_BLOCK.get(), new Item.Properties()));
    private EssenceContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
}
