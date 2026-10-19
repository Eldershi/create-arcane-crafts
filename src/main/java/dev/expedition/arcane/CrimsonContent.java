package dev.expedition.arcane;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Crimson industrial fuel. It shares neither drinking nor mana effects with arcane solution. */
public final class CrimsonContent {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneIndustry.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneIndustry.MOD_ID);

    public static final RegistryObject<FluidType> TYPE = TYPES.register("crimson_solution", () -> new FluidType(FluidType.Properties.create().density(1000).viscosity(1000).canConvertToSource(false)) {
        @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return new ResourceLocation(ArcaneIndustry.MOD_ID, "block/crimson_solution_still"); }
                @Override public ResourceLocation getFlowingTexture() { return new ResourceLocation(ArcaneIndustry.MOD_ID, "block/crimson_solution_flow"); }
                @Override public int getTintColor() { return 0xFFFFFFFF; }
            });
        }
    });
    public static final RegistryObject<FlowingFluid> SOURCE = FLUIDS.register("crimson_solution", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING = FLUIDS.register("flowing_crimson_solution", () -> new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<LiquidBlock> BLOCK = BLOCKS.register("crimson_solution", () -> new LiquidBlock(SOURCE, BlockBehaviour.Properties.copy(Blocks.WATER)));
    public static final RegistryObject<Item> BOTTLE = ITEMS.register("crimson_solution_bottle", () -> new Item(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));
    public static final RegistryObject<Item> BUCKET = ITEMS.register("crimson_solution_bucket", () -> new BucketItem(SOURCE, new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
    public static final RegistryObject<Item> EMBER_CAKE_BASE = ITEMS.register("ember_cake_base", () -> new SequencedAssemblyItem(new Item.Properties()));
    public static final RegistryObject<Item> EMBER_CAKE = ITEMS.register("ember_cake", () -> new Item(new Item.Properties()));

    private CrimsonContent() {}

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(TYPE, SOURCE, FLOWING).bucket(BUCKET).block(BLOCK).tickRate(5).slopeFindDistance(4).levelDecreasePerBlock(1);
    }

    public static boolean isCrimsonFluid(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(SOURCE.get());
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
