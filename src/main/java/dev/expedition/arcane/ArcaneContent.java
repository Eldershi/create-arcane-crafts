package dev.expedition.arcane;

import dev.expedition.arcane.backpack.ManaSupplyUpgradeItem;
import dev.expedition.arcane.chamber.AscensionChamberBlock;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import dev.expedition.arcane.mana.ArcaneBottleItem;
import dev.expedition.arcane.mana.ArcaneBucketItem;
import dev.expedition.arcane.item.UninkedScrollItem;
import dev.expedition.arcane.item.UnfinishedAscensionScrollItem;
import dev.expedition.arcane.grinding.ArcaneGrindingContent;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.*;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.*;
import net.minecraftforge.registries.*;

public final class ArcaneContent {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, ArcaneIndustry.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, ArcaneIndustry.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneIndustry.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneIndustry.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArcaneIndustry.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcaneIndustry.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ArcaneIndustry.MOD_ID);

    public static final RegistryObject<SimpleParticleType> CLEANSE_GOLD = PARTICLES.register("ascension_cleanse_gold", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CLEANSE_BLUE = PARTICLES.register("ascension_cleanse_blue", () -> new SimpleParticleType(false));

    public static final RegistryObject<FluidType> SOLUTION_TYPE = FLUID_TYPES.register("arcane_solution", () -> new FluidType(FluidType.Properties.create().density(1000).viscosity(1000).canConvertToSource(false)) {
        @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return new ResourceLocation(ArcaneIndustry.MOD_ID, "block/arcane_solution_still"); }
                @Override public ResourceLocation getFlowingTexture() { return new ResourceLocation(ArcaneIndustry.MOD_ID, "block/arcane_solution_flow"); }
                @Override public int getTintColor() { return 0xFFFFFFFF; }
            });
        }
    });
    public static final RegistryObject<FlowingFluid> SOURCE = FLUIDS.register("arcane_solution", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING = FLUIDS.register("flowing_arcane_solution", () -> new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<LiquidBlock> SOLUTION_BLOCK = BLOCKS.register("arcane_solution", () -> new LiquidBlock(SOURCE, BlockBehaviour.Properties.copy(Blocks.WATER)));
    public static final RegistryObject<Item> BUCKET = ITEMS.register("arcane_solution_bucket", () -> new ArcaneBucketItem(SOURCE, new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
    public static final RegistryObject<Item> BOTTLE = ITEMS.register("arcane_solution_bottle", () -> new ArcaneBottleItem(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));
    public static final RegistryObject<Item> TEMPLATE = ITEMS.register("ascension_template", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> UNINKED_SCROLL = ITEMS.register("uninked_scroll", () -> new UninkedScrollItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> UNFINISHED_ASCENSION_SCROLL = ITEMS.register("unfinished_ascension_scroll", () -> new UnfinishedAscensionScrollItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MANA_UPGRADE = ITEMS.register("mana_supply_upgrade", ManaSupplyUpgradeItem::new);
    public static final RegistryObject<AscensionChamberBlock> CHAMBER = BLOCKS.register("ascension_chamber", AscensionChamberBlock::new);
    public static final RegistryObject<Item> CHAMBER_ITEM = ITEMS.register("ascension_chamber", () -> new BlockItem(CHAMBER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<AscensionChamberBlockEntity>> CHAMBER_ENTITY = BLOCK_ENTITIES.register("ascension_chamber", () -> BlockEntityType.Builder.of(AscensionChamberBlockEntity::new, CHAMBER.get()).build(null));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("prototype", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcane_industry"))
            .icon(() -> new ItemStack(CHAMBER_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(CHAMBER_ITEM.get()); output.accept(TEMPLATE.get()); output.accept(BOTTLE.get()); output.accept(BUCKET.get()); output.accept(MANA_UPGRADE.get());
                output.accept(CrimsonContent.BOTTLE.get()); output.accept(CrimsonContent.BUCKET.get());
                output.accept(CrimsonContent.EMBER_CAKE_BASE.get()); output.accept(CrimsonContent.EMBER_CAKE.get());
                output.accept(ArcaneHatchContent.ITEM.get());
                output.accept(ArcaneGrindingContent.GRINDSTONE_ITEM.get()); output.accept(ArcaneGrindingContent.DRAIN_ITEM.get());
                output.accept(EssenceContent.ARCANE_BLOCK_ITEM.get()); output.accept(EssenceContent.CRIMSON_ESSENCE.get());
                output.accept(EssenceContent.CRIMSON_BLOCK_ITEM.get());
                output.accept(UNINKED_SCROLL.get());
                // Keep the legacy registry item for old saves, outside the current one-step workflow.
            }).build());

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(SOLUTION_TYPE, SOURCE, FLOWING).bucket(BUCKET).block(SOLUTION_BLOCK).tickRate(5).slopeFindDistance(4).levelDecreasePerBlock(1);
    }
    public static boolean isArcaneFluid(FluidStack fluid) { return fluid.getFluid().isSame(SOURCE.get()); }
    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus); PARTICLES.register(bus);
        CrimsonContent.register(bus);
    }
}
