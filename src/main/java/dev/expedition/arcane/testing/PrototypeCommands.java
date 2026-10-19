package dev.expedition.arcane.testing;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import dev.expedition.arcane.*;
import dev.expedition.arcane.chamber.*;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid=ArcaneIndustry.MOD_ID)
public final class PrototypeCommands {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arcane_factory_test").requires(s -> s.hasPermission(2)).executes(ctx -> {
            var result = new LinkedHashMap<String,Object>();
            try {
                var fixture = inspectFixture(ctx.getSource().getLevel());
                result.put("fixture", fixture);
                if (!"PASS".equals(fixture.get("status"))) throw new IllegalStateException("Prototype fixture validation failed: " + fixture);
                result.put("chamber", ChamberTests.run(ctx.getSource().getLevel(), new BlockPos(24, 5, 0)));
                result.put("compat", FactoryCompatTests.run(ctx.getSource().getLevel(), new BlockPos(28, 5, 0)));
                result.put("fluids", FluidRecipeTests.run(ctx.getSource().getLevel(), new BlockPos(32, 5, 0)));
                result.put("status", "PASS");
                ctx.getSource().sendSuccess(() -> Component.literal("Arcane factory tests: PASS"), true);
            } catch (Throwable error) {
                result.put("status", "FAIL"); result.put("error", error.toString());
                ArcaneIndustry.LOGGER.error("Arcane factory test failed", error);
                ctx.getSource().sendFailure(Component.literal("Arcane factory tests: " + error));
            }
            try { Files.writeString(FMLPaths.GAMEDIR.get().resolve("arcane-factory-test-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
            catch (Exception error) { throw new RuntimeException(error); }
            return "PASS".equals(result.get("status")) ? 1 : 0;
        }));
        event.getDispatcher().register(Commands.literal("arcane_workshop").requires(s -> s.hasPermission(2)).executes(ctx -> {
            setup(ctx.getSource().getLevel()); return 1;
        }));
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("arcane.prototype")) return;
        // Iron normally initializes effective spell limits at player sync. Test-stock
        // validation runs earlier, including on an existing world with no new setup.
        ChamberTests.initializeSpellConfig(event.getServer().overworld());
        Path marker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-fixture-v1.json");
        if (!Files.exists(marker)) {
            setup(event.getServer().overworld());
            try { Files.writeString(marker, "{\"superflat\":true,\"generateStructures\":false,\"version\":1}\n"); }
            catch (Exception error) { throw new RuntimeException(error); }
        }
        Path visualMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-visuals-v2.json");
        if (!Files.exists(visualMarker)) {
            prepareVisualDisplay(event.getServer().overworld());
            try { Files.writeString(visualMarker, "{\"visuals\":2,\"idleDisplay\":true,\"fuelRefreshed\":true}\n"); }
            catch (Exception error) { throw new RuntimeException(error); }
        }
        Path fuelMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-fuels-v3.json");
        if (!Files.exists(fuelMarker)) {
            Map<String, Object> migration = supplementFuelSupplies(event.getServer().overworld());
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(fuelMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                // Leave the marker absent so freeing chest slots permits a retry on the next launch.
                ArcaneIndustry.LOGGER.warn("Prototype v3 supplies need free slots in the original test chests: {}", migration);
            }
        }
        Path guideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-guide-v4.json");
        if (!Files.exists(guideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(guide()), 4);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(guideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v4 guide needs a free slot in the original test chests: {}", migration);
            }
        }
        Path sourceGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-guide-v5.json");
        if (!Files.exists(sourceGuideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(guide()), 5);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(sourceGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v5 guide needs a free slot in the original test chests: {}", migration);
            }
        }
        Path conversionGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-guide-v6.json");
        if (!Files.exists(conversionGuideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(guide()), 6);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(conversionGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v6 guide needs a free slot in the original test chests: {}", migration);
            }
        }
        Path printingGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-printing-v7.json");
        if (!Files.exists(printingGuideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(
                    new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get(), 16),
                    dev.expedition.arcane.item.UninkedScrollItem.create(SpellRegistry.FIREBOLT_SPELL.get()), guide()), 7);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(printingGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v7 printing supplies need free slots in the original test chests: {}", migration);
            }
        }
        Path assemblyMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-assembly-v8.json");
        if (!Files.exists(assemblyMarker)) {
            int updated = migrateAssemblySamples(event.getServer().overworld());
            Map<String, Object> migration = new LinkedHashMap<>(supplementSupplies(
                    event.getServer().overworld(), List.of(guide()), 8));
            migration.put("legacy_intermediates_given_equivalent_assembly_progress", updated);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(assemblyMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
        Path ascensionMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-ascension-v9.json");
        if (!Files.exists(ascensionMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(
                    dev.expedition.arcane.item.UnfinishedAscensionScrollItem.create(scroll(4)), guide()), 9);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(ascensionMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v9 ascension supplies need free slots in the original test chests: {}", migration);
            }
        }
        Path cosmeticsMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-cosmetics-v10.json");
        if (!Files.exists(cosmeticsMarker)) {
            ServerLevel level = event.getServer().overworld();
            Map<String, Object> migration = new LinkedHashMap<>(supplementSupplies(level, List.of(
                    stack("create:brass_ingot", 16), stack("irons_spellbooks:mithril_ingot", 16),
                    stack("minecraft:copper_ingot", 16), stack("create:goggles", 4),
                    stack("irons_spellbooks:tarnished_helmet", 4), stack("create:blaze_burner", 4),
                    stack("minecraft:lightning_rod", 8), guide()), 10));
            BlockPos rod = addDemonstrationRod(level);
            migration.put("demonstration_lightning_rod", rod == null ? "existing blocks preserved; place supplied rod outdoors" : rod.toShortString());
            if (level.getBlockEntity(new BlockPos(0, 1, 6)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity oldSign
                    && oldSign.getFrontText().getMessage(0, false).getString().equals("超越升华室")) {
                sign(level, new BlockPos(0, 1, 6), "突破升华室", "猩红原液四倍省液", "超越原生上限+3", "超越须露天避雷针");
            }
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(cosmeticsMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
        Path emberMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-embers-v11.json");
        if (!Files.exists(emberMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(
                    stack("create:golden_sheet", 16), stack("create:copper_sheet", 16),
                    stack("minecraft:amethyst_shard", 16),
                    stack("create_dragons_plus:blaze_upgrade_smithing_template", 4),
                    new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.IRON_SHOVEL), guide()), 11);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(emberMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
        Path inkMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-ink-headwear-v12.json");
        if (!Files.exists(inkMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(
                    stack("create:andesite_alloy", 16), stack("minecraft:amethyst_shard", 16),
                    stack("irons_spellbooks:blank_rune", 8), stack("sophisticatedbackpacks:upgrade_base", 4), guide()), 12);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(inkMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
        Path extractionGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-extraction-guide-v13.json");
        if (!Files.exists(extractionGuideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(guide()), 13);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(extractionGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
        Path manualGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-manual-guide-v14.json");
        if (!Files.exists(manualGuideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(),
                    List.of(guide(), stack("create:mechanical_mixer", 1)), 14);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(manualGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v14 guide and mixer need free slots in the test chests: {}", migration);
            }
        }
        Path replacementGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-solution-guide-v15.json");
        if (!Files.exists(replacementGuideMarker)) {
            Map<String, Object> migration = supplementSupplies(event.getServer().overworld(), List.of(guide()), 15);
            if (Boolean.TRUE.equals(migration.get("complete"))) {
                try { Files.writeString(replacementGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(migration) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            } else {
                ArcaneIndustry.LOGGER.warn("Prototype v15 solution guide needs a free slot in the test chests: {}", migration);
            }
        }
        Path industryGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-industry-guide-v16.json");
        if (!Files.exists(industryGuideMarker)) {
            Map<String, Object> supplied = supplementSupplies(event.getServer().overworld(), List.of(guide(),
                    stack("arcane_industry:arcane_hatch", 4), stack("arcane_industry:arcane_grindstone", 2),
                    stack("arcane_industry:arcane_grindstone_drain", 2), stack("arcane_industry:arcane_essence_block", 4),
                    stack("arcane_industry:crimson_essence", 16), stack("arcane_industry:crimson_essence_block", 4),
                    stack("minecraft:amethyst_shard", 16), stack("minecraft:fire_charge", 16)), 16);
            if (Boolean.TRUE.equals(supplied.get("complete"))) {
                try { Files.writeString(industryGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(supplied) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
        Path recipeGuideMarker = event.getServer().getWorldPath(LevelResource.ROOT).resolve("arcane-prototype-recipe-guide-v17.json");
        if (!Files.exists(recipeGuideMarker)) {
            Map<String, Object> supplied = supplementSupplies(event.getServer().overworld(), List.of(guide()), 17);
            if (Boolean.TRUE.equals(supplied.get("complete"))) {
                try { Files.writeString(recipeGuideMarker, new GsonBuilder().setPrettyPrinting().create().toJson(supplied) + "\n"); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        }
    }

    private static BlockPos addDemonstrationRod(ServerLevel level) {
        for (int x : new int[]{0, 1, -1}) {
            BlockPos pos = new BlockPos(x, 1, 8);
            if (level.getBlockState(pos).is(Blocks.LIGHTNING_ROD) && level.canSeeSky(pos.above())) return pos;
            if (!level.isEmptyBlock(pos) || !level.canSeeSky(pos)) continue;
            level.setBlock(pos, Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP), 3);
            return pos;
        }
        return null;
    }

    /** Existing test-stock intermediates already paid the old recipe's equivalent materials. */
    private static int migrateAssemblySamples(ServerLevel level) {
        int updated = 0;
        for (int x : new int[]{2, 3}) {
            if (!(level.getBlockEntity(new BlockPos(x, 1, 0)) instanceof ChestBlockEntity chest)) continue;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack old = chest.getItem(slot);
                if (old.isEmpty() || (old.hasTag() && old.getTag().contains("SequencedAssembly"))) continue;
                ItemStack replacement;
                if (dev.expedition.arcane.item.UninkedScrollItem.readSpell(old).isPresent()) {
                    replacement = dev.expedition.arcane.item.UninkedScrollItem.withInkingProgress(old);
                } else if (old.is(CrimsonContent.EMBER_CAKE_BASE.get())) {
                    replacement = old.copy();
                    CompoundTag progress = new CompoundTag();
                    progress.putString("id", "arcane_industry:sequenced_assembly/ember_cake");
                    progress.putInt("Step", 3);
                    progress.putFloat("Progress", 0.75F);
                    replacement.getOrCreateTag().put("SequencedAssembly", progress);
                } else continue;
                chest.setItem(slot, replacement);
                chest.setChanged();
                updated++;
            }
        }
        return updated;
    }

    /** Adds the idle comparison without rebuilding or clearing an existing test scene. */
    private static void prepareVisualDisplay(ServerLevel level) {
        BlockPos idle = new BlockPos(-8, 1, 4);
        if (level.isEmptyBlock(idle)) {
            level.setBlock(idle, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
            sign(level, new BlockPos(-8, 1, 6), "待机升华室", "未放入燃料", "观察薄眉和低眼位", "右侧为普通与突破");
        }
        for (int x : new int[]{-4, 0, 4}) {
            if (!(level.getBlockEntity(new BlockPos(x, 1, 4)) instanceof AscensionChamberBlockEntity chamber)) continue;
            boolean transcendent = x == 0;
            chamber.sourceTank.fill(new FluidStack(transcendent ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 4000),
                    IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** Supplements only empty slots; never rebuilds the world or alters a player's machine/inventory. */
    private static Map<String, Object> supplementFuelSupplies(ServerLevel level) {
        List<ItemStack> desired = addonSupplies();
        desired.add(stack("irons_spellbooks:blood_vial", 16));
        desired.add(scroll(10));
        desired.add(guide());
        return supplementSupplies(level, desired, 3);
    }

    private static Map<String, Object> supplementSupplies(ServerLevel level, List<ItemStack> desired, int version) {
        List<ChestBlockEntity> chests = new ArrayList<>();
        for (int x : new int[]{2, 3}) {
            if (level.getBlockEntity(new BlockPos(x, 1, 0)) instanceof ChestBlockEntity chest) chests.add(chest);
        }
        boolean originalChestsPresent = chests.size() == 2;
        ChestBlockEntity existingOverflow = updateSupplyChest(level, false);
        if (existingOverflow != null) chests.add(existingOverflow);
        List<String> added = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (ItemStack supply : desired) {
            if (supply.isEmpty() || alreadySupplied(chests, supply)) continue;
            boolean inserted = false;
            for (ChestBlockEntity chest : chests) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (!chest.getItem(slot).isEmpty()) continue;
                    chest.setItem(slot, supply.copy());
                    chest.setChanged();
                    inserted = true;
                    break;
                }
                if (inserted) break;
            }
            if (!inserted && originalChestsPresent) {
                ChestBlockEntity overflow = updateSupplyChest(level, true);
                if (overflow != null) {
                    if (!chests.contains(overflow)) chests.add(overflow);
                    for (int slot = 0; slot < overflow.getContainerSize(); slot++) {
                        if (!overflow.getItem(slot).isEmpty()) continue;
                        overflow.setItem(slot, supply.copy());
                        overflow.setChanged();
                        inserted = true;
                        break;
                    }
                }
            }
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(supply.getItem()));
            (inserted ? added : pending).add(id);
        }
        return Map.of("version", version, "complete", originalChestsPresent && pending.isEmpty(),
                "added_to_empty_slots", added, "pending", pending,
                "preserved", "all existing blocks, chest stacks, scrolls and legacy chamber slot 6");
    }

    /** Extra stock grows into a new empty position, never by replacing an existing stack or block. */
    private static ChestBlockEntity updateSupplyChest(ServerLevel level, boolean create) {
        BlockPos pos = new BlockPos(5, 1, 0);
        String name = "机械动力：奥术工艺 · 更新补给";
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            return chest.getCustomName() != null && name.equals(chest.getCustomName().getString()) ? chest : null;
        }
        if (!create || !level.isEmptyBlock(pos)) return null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof ChestBlock) return null;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return null;
        chest.setCustomName(Component.literal(name));
        chest.setChanged();
        return chest;
    }

    private static boolean alreadySupplied(List<ChestBlockEntity> chests, ItemStack desired) {
        for (ChestBlockEntity chest : chests) for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack existing = chest.getItem(slot);
            if (existing.isEmpty() || !existing.is(desired.getItem())) continue;
            if (desired.is(Items.WRITTEN_BOOK)) {
                if (existing.hasTag() && desired.hasTag()
                        && existing.getTag().getInt("arcane_industry_guide_version")
                        >= desired.getTag().getInt("arcane_industry_guide_version")) return true;
            } else if (ScrollUpgradeRules.isScroll(desired)) {
                if (scrollLevel(existing) == scrollLevel(desired)
                        && ISpellContainer.get(existing).getActiveSpells().get(0).getSpell().equals(SpellRegistry.FIREBOLT_SPELL.get())) return true;
            } else return true;
        }
        return false;
    }

    private static List<ItemStack> addonSupplies() {
        List<ItemStack> supplies = new ArrayList<>();
        ForgeRegistries.ITEMS.getEntries().stream().filter(e -> e.getKey().location().getNamespace().equals(ArcaneIndustry.MOD_ID))
                .sorted(Comparator.comparing(e -> e.getKey().location().toString())).forEach(e -> {
                    String path = e.getKey().location().getPath();
                    int count = path.endsWith("_bottle") || path.equals("ember_cake") ? 16 : path.equals("ascension_chamber") ? 4 : 1;
                    supplies.add(path.equals("unfinished_ascension_scroll")
                            ? dev.expedition.arcane.item.UnfinishedAscensionScrollItem.create(scroll(4))
                            : new ItemStack(e.getValue(), count));
                });
        return supplies;
    }

    public static void setup(ServerLevel level) {
        ArcaneIndustry.LOGGER.info("Prototype fixture spell configuration: {}", ChamberTests.initializeSpellConfig(level));
        for (int x=-12;x<=12;x++) for(int z=-10;z<=10;z++) {
            level.setBlock(new BlockPos(x,0,z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for(int y=1;y<=5;y++) level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(),3);
        }
        level.setDefaultSpawnPos(new BlockPos(0,1,0), 180);
        level.setDayTime(6000);
        level.setWeatherParameters(0,0,false,false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,level.getServer());
        for(int x=2;x<=3;x++) level.setBlock(new BlockPos(x,1,0),Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,x==2?ChestType.LEFT:ChestType.RIGHT),3);
        var left=(ChestBlockEntity)level.getBlockEntity(new BlockPos(2,1,0));
        var right=(ChestBlockEntity)level.getBlockEntity(new BlockPos(3,1,0));
        left.setCustomName(Component.literal("机械动力：奥术工艺 · 全物品与测试材料"));
        List<ItemStack> stacks=addonSupplies();
        for(String id:List.of("irons_spellbooks:arcane_essence","irons_spellbooks:cinder_essence","irons_spellbooks:blood_vial","irons_spellbooks:common_ink","irons_spellbooks:uncommon_ink","irons_spellbooks:rare_ink","irons_spellbooks:epic_ink","irons_spellbooks:legendary_ink","minecraft:paper","minecraft:glass_bottle","minecraft:ink_sac","minecraft:copper_ingot","minecraft:iron_ingot","minecraft:gold_ingot","minecraft:amethyst_shard"))stacks.add(stack(id,32));
        for(String id:List.of("sophisticatedbackpacks:diamond_backpack","sophisticatedbackpacks:tank_upgrade","create:basin","create:item_drain","create:fluid_pipe","create:mechanical_pump","create:spout","create:depot","create_enchantment_industry:printer","create:creative_fluid_tank","irons_spellbooks:alchemist_cauldron","irons_spellbooks:arcane_anvil","irons_spellbooks:scroll_forge","create:creative_motor","create:shaft"))stacks.add(stack(id,1));
        for(int l:List.of(1,4,7,9,10))stacks.add(scroll(l));
        stacks.add(guide());
        for(int i=0;i<stacks.size()&&i<54;i++)(i<27?left:right).setItem(i%27,stacks.get(i));
        left.setChanged();right.setChanged();
        if (stacks.size() > 54) supplementSupplies(level, stacks.subList(54, stacks.size()), 16);
        demo(level,new BlockPos(-4,1,4),4,false,false);
        demo(level,new BlockPos(0,1,4),7,true,false);
        demo(level,new BlockPos(4,1,4),4,false,true);
        level.setBlock(new BlockPos(-4,1,-3),AllBlocks.ITEM_DRAIN.getDefaultState(),3);
        sign(level,new BlockPos(-4,1,6),"普通升华室","2至7级逐级升级","蓝液按目标等级计费","不工作不耗液");
        sign(level,new BlockPos(0,1,6),"突破升华室","猩红原液四倍省液","超越原生上限+3","每级蛋糕+传奇墨水");
        sign(level,new BlockPos(4,1,6),"工作盆模式","盆中墨水和卷轴","须装动力搅拌器","成品完成时扣料");
        sign(level,new BlockPos(-4,1,-5),"原液补给站","原液瓶/桶右键倒入","站上分液池补法力","满蓝停止耗液");
        // Rebuilding intentionally replaces this disposable scene; remove only its resulting loose items.
        level.getEntitiesOfClass(ItemEntity.class, new AABB(-13, -1, -11, 13, 8, 11))
                .forEach(ItemEntity::discard);
    }

    /** Checks the loaded world's generator/options and the actual supplies and demonstration machines. */
    public static Map<String, Object> inspectFixture(ServerLevel level) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spell_config", ChamberTests.initializeSpellConfig(level));
        boolean flat = level.getChunkSource().getGenerator() instanceof FlatLevelSource;
        boolean structuresDisabled = !level.getServer().getWorldData().worldGenOptions().generateStructures();
        result.put("generator_class", level.getChunkSource().getGenerator().getClass().getName());
        result.put("actual_superflat", flat);
        result.put("actual_structures_disabled", structuresDisabled);

        Set<String> expected = new TreeSet<>();
        ForgeRegistries.ITEMS.getEntries().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(ArcaneIndustry.MOD_ID))
                .forEach(entry -> expected.add(entry.getKey().location().toString()));
        Set<String> found = new TreeSet<>();
        boolean bothChests = true;
        for (int x : new int[]{2, 3}) {
            if (!(level.getBlockEntity(new BlockPos(x, 1, 0)) instanceof ChestBlockEntity chest)) {
                bothChests = false;
                continue;
            }
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack item = chest.getItem(slot);
                if (!item.isEmpty()) found.add(String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem())));
            }
        }
        ChestBlockEntity overflow = updateSupplyChest(level, false);
        if (overflow != null) for (int slot = 0; slot < overflow.getContainerSize(); slot++) {
            ItemStack item = overflow.getItem(slot);
            if (!item.isEmpty()) found.add(String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem())));
        }
        result.put("update_supply_chest_present", overflow != null);
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(found);
        result.put("test_chests_present", bothChests);
        result.put("registered_addon_items", expected);
        result.put("missing_addon_items", missing);
        result.put("all_addon_items_in_chests", bothChests && !expected.isEmpty() && missing.isEmpty());

        Map<String, Object> ordinary = inspectDemoInk(level, new BlockPos(-4, 1, 4), 4, false);
        Map<String, Object> transcendent = inspectDemoInk(level, new BlockPos(0, 1, 4), 7, false);
        Map<String, Object> basin = inspectDemoInk(level, new BlockPos(4, 1, 4), 4, true);
        result.put("ordinary_demo", ordinary);
        result.put("transcendent_demo", transcendent);
        result.put("basin_demo", basin);
        boolean demonstrationsValid = "PASS".equals(ordinary.get("status"))
                && "PASS".equals(transcendent.get("status")) && "PASS".equals(basin.get("status"));
        result.put("status", flat && structuresDisabled && bothChests && !expected.isEmpty()
                && missing.isEmpty() && demonstrationsValid ? "PASS" : "FAIL");
        return result;
    }

    private static Map<String, Object> inspectDemoInk(ServerLevel level, BlockPos pos, int originalLevel, boolean basinMode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("position", pos.toShortString());
        var rarity = SpellRegistry.FIREBOLT_SPELL.get().getRarity(originalLevel + 1);
        result.put("expected_target_rarity", rarity.name());
        if (!(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber)) {
            result.put("status", "FAIL");
            result.put("reason", "missing_chamber");
            return result;
        }
        if (basinMode) {
            if (!(level.getBlockEntity(pos.above()) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin)) {
                result.put("status", "FAIL");
                result.put("reason", "missing_native_basin");
                return result;
            }
            var fluidHandler = basin.inputTank.getCapability().orElse(null);
            int matchingInk = 0;
            if (fluidHandler != null) {
                for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                    FluidStack fluid = fluidHandler.getFluidInTank(tank);
                    if (!fluid.isEmpty() && fluid.getFluid().isSame(InkItem.getInkForRarity(rarity).fluid().get())) {
                        matchingInk += fluid.getAmount();
                    }
                }
            }
            int input = -1;
            int output = -1;
            for (int slot = 0; slot < basin.getInputInventory().getSlots(); slot++) {
                input = Math.max(input, scrollLevel(basin.getInputInventory().getStackInSlot(slot)));
            }
            for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) {
                output = Math.max(output, scrollLevel(basin.getOutputInventory().getStackInSlot(slot)));
            }
            result.put("input_level", input);
            result.put("output_level", output);
            result.put("matching_ink_mB", matchingInk);
            result.put("status", matchingInk >= 250 && (input == originalLevel || output == originalLevel + 1) ? "PASS" : "FAIL");
        } else {
            ItemStack input = chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.SCROLL_SLOT);
            ItemStack output = chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT);
            ItemStack ink = chamber.inventory.getStackInSlot(AscensionChamberBlockEntity.INK_SLOT);
            boolean inkMatches = !ink.isEmpty() && ink.getItem() instanceof InkItem item && item.getRarity() == rarity;
            boolean inputReady = scrollLevel(input) == originalLevel && inkMatches;
            boolean alreadyCompleted = input.isEmpty() && scrollLevel(output) == originalLevel + 1
                    && (ink.isEmpty() || inkMatches);
            result.put("input_level", scrollLevel(input));
            result.put("output_level", scrollLevel(output));
            result.put("ink_item", ink.isEmpty() ? "consumed" : String.valueOf(ForgeRegistries.ITEMS.getKey(ink.getItem())));
            result.put("ink_matches_target_or_completed", inputReady || alreadyCompleted);
            result.put("status", inputReady || alreadyCompleted ? "PASS" : "FAIL");
        }
        return result;
    }

    private static int scrollLevel(ItemStack stack) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) return -1;
        ISpellContainer spells = ISpellContainer.get(stack);
        return spells == null || spells.getActiveSpellCount() != 1 ? -1 : spells.getActiveSpells().get(0).getLevel();
    }

    private static void demo(ServerLevel level,BlockPos pos,int inputLevel,boolean superMode,boolean basin) {
        level.setBlock(pos,ArcaneContent.CHAMBER.get().defaultBlockState(),3);
        var chamber=(AscensionChamberBlockEntity)level.getBlockEntity(pos);
        chamber.inventory.setStackInSlot(0,new ItemStack(ArcaneContent.TEMPLATE.get()));
        chamber.sourceTank.fill(new FluidStack(superMode ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(),4000),
                IFluidHandler.FluidAction.EXECUTE);
        var ink=InkItem.getInkForRarity(SpellRegistry.FIREBOLT_SPELL.get().getRarity(inputLevel+1));
        if(basin) {
            level.setBlock(pos.above(),AllBlocks.BASIN.getDefaultState(),3);
            var be=(com.simibubi.create.content.processing.basin.BasinBlockEntity)level.getBlockEntity(pos.above());
            be.getInputInventory().insertItem(0,scroll(inputLevel),false);
            be.inputTank.getPrimaryHandler().fill(new FluidStack(ink.fluid().get(),750),IFluidHandler.FluidAction.EXECUTE);
            be.notifyChangeOfContents();
        } else {
            chamber.inventory.setStackInSlot(1,scroll(inputLevel));
            chamber.inventory.setStackInSlot(2,new ItemStack(ink));
        }
    }
    private static void sign(ServerLevel level,BlockPos pos,String...lines) {
        level.setBlock(pos,Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,8),3);
        if(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            var text=sign.getFrontText();for(int i=0;i<Math.min(4,lines.length);i++)text=text.setMessage(i,Component.literal(lines[i]));
            sign.setText(text,true);sign.setChanged();
        }
    }
    private static ItemStack stack(String id,int count) {
        Item item=ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id));
        return item==null?ItemStack.EMPTY:new ItemStack(item,count);
    }
    private static ItemStack scroll(int level) {
        ItemStack result=stack("irons_spellbooks:scroll",1);ISpellContainer.createScrollContainer(SpellRegistry.FIREBOLT_SPELL.get(),level,result);return result;
    }
    private static ItemStack guide() {
        ItemStack book=new ItemStack(Items.WRITTEN_BOOK);CompoundTag tag=book.getOrCreateTag();
        tag.putString("title","机械动力：奥术工艺测试指南");tag.putString("author","机械远征");
        tag.putInt("arcane_industry_guide_version",17);
        ListTag pages=new ListTag();
        for(String page:List.of(
                "机械动力：奥术工艺 · 第17版\n\n源质与块新增工作台、动力合成器、工作盆压制、粉碎轮和两种石磨兼容；原液兼容铁魔法炼药锅。\n\n本版只完成离线构建与安装，游戏内验证待安排。自然回魔为5%。",
                "随机研习\n\n安装模板，放1张原生空白卷轴和学派引导物，加入500mB任一种原液。引导物和模板保留。产出原生1级卷轴，手动70刻。\n\n邪术、禁用、不可制作、需学习的法术排除；普通档为空不开工。",
                "研习概率\n\n普通/卓越/稀有/史诗/传说：\n蓝70/20/7/2.5/0.5%\n红65/22/8/4/1%\n\n先抽品级，再在同学派该档均匀选术；缺档只向最近较低档回落。",
                "研习头饰\n\n室内安装护目镜或黯淡皇冠，同效不叠加、不损耗：普通-5、卓越+3、稀有+2个百分点。\n\n蓝65/23/9/2.5/0.5%\n红60/25/10/4/1%\n红液研习仍耗500mB。",
                "锁定研习\n\n开工时1张空白卷轴和500mB原液一起转为保存的待取结果。离开、重载、堵料或换头饰不会重抽。\n\n完成后空手优先取卷轴。拆机返还已经付费的结果。",
                "奥术舱口\n\n附着储液设备侧面，代理该面流体能力。空瓶250mB、空桶1000mB；装满的蓝红原液瓶桶也可倒入。\n\n必须完整一份；不足或拒收不扣物品。自身不储液。",
                "奥术砂轮\n\n原生动力砂轮加1秘银锭合成。搭配奥术砂轮分液池，保留原生配方、经验与附魔处理，另加去魔回收。\n\n粗暴移除可回收法术与已投入法球，不退法球。",
                "源质回收\n\n按被移除法术当前实际品级：普通1、卓越2、稀有4、史诗8、传说16源质。普通法球8、魔力法球24。\n\n固有法术、空槽、扩槽、亲和、名称和耐久不产源质。",
                "湿磨\n\n无液干磨产实体奥术源质。分液池通水或血时，每1源质价值消耗250mB对应液体，改产250mB奥术或猩红原液，不再掉源质。\n\n模式开工锁定；缺液或输出堵塞暂停，大额分批保存。",
                "源质与压缩\n\n9奥术源质合成1奥术源质块，拆回9；9猩红源质合成1猩红源质块，拆回9。\n\n原生奥术源质复用铁魔法物品，两种块都不是四倍液体兑换。",
                "猩红源质新配方\n\n动力压制：1奥术源质+250mB血→1猩红源质。\n动力搅拌：1猩红源质+250mB水→250mB猩红原液。\n\n既有原液配方保留；红源质是等价源质单位。",
                "源质块 · 双向加工\n\n工作台、动力合成器或工作盆压制：9源质→1对应块。\n\n工作台、动力合成器、粉碎轮、Create石磨或森罗物语石磨：1块→9对应源质，无随机额外产物。",
                "原液结晶\n\n工作盆压制：250mB奥术/猩红原液→1对应源质。\n\n工作台或动力合成器：1原液瓶→1对应源质，返空玻璃瓶。\n\n不覆盖原生蓝源质，也不新增颗粒物品。",
                "炼药锅配制\n\n铁魔法炼药锅和动力搅拌器均支持：1对应源质+250mB水→250mB对应原液。\n\n原来的奥术源质+血→猩红原液路线保留。每块9源质，总共对应2250mB原液，逐份配制。",
                "手工猩红源质\n\n原来的1奥术源质+1猩红精华仍制成1猩红原液瓶；再把原液瓶结晶为1猩红源质，返空瓶。\n\n机械端可直接工作盆压制：1奥术源质+250mB血→1猩红源质。",
                "死亡后的空升华室\n\n烈焰人被雷劈死后，留下有余火的空升华室；余火只作装饰，不能加工。\n\n空手左键或铲子右键扑灭，成为无火空笼；打火石右键可重新点燃。工具依照原生消耗耐久。",
                "空笼与重填\n\n有火、无火空笼都保留皮肤，保存或拆放不会复活烈焰人。头饰保留于室内，拆机仍单独返还。\n\n用装有烈焰人的燃烧室重填会清余火，再按储液恢复普通或突破工作状态。",
                "升华模板新配方\n\n九宫格从上到下：\n金板｜紫水晶碎片｜金板\n铜板｜升级锻造模板｜铜板\n金板｜铜板｜金板\n\n中心为与烈焰人附魔室升级相同的烈焰人升级锻造模板。产出1个升华模板，加工时可重复使用。",
                "皮肤切换\n\n手持黄铜锭、秘银锭右键升华室，切换笼架与底座皮肤；铜锭切回铜版。三个版本功能、容量、配方和工作进度相同。\n\n生存每次换肤消耗1锭，同皮肤不消耗。",
                "彩蛋头饰\n\n持工程师护目镜或铁魔法黯淡皇冠右键佩戴，每次只能戴一个。空手右键先取成品、空瓶、卷轴与墨水；潜行先取模板等材料。对应物品取完后，再摘头饰，每次点击只做一项。\n\n生存、创造均搬移原件并保留完整数据。死亡保留头饰，拆机返还。",
                "奥术补给升级新配方\n\n九宫格从上到下：\n安山合金｜空白符石｜安山合金\n紫水晶碎片｜空白升级｜紫水晶碎片\n安山合金｜奥术原质｜安山合金\n\n配合背包储罐升级使用。配方可在JEI中查看。",
                "名称调整\n\n猩红原液对应突破状态；超过法术原生上限的每次升一级称为超越升级。\n\n普通上限内升级不引雷。使用猩红原液的低级升级也不引雷。",
                "超越与引雷\n\n每次超越升级在提交前引雷一次，沿用附魔工业规则：128格内需有处于所在位置最高表面的露天避雷针。\n\n没有有效避雷针，烈焰人死亡、本次失败，卷轴、墨水、原液和蛋糕均保留。",
                "恢复空笼\n\n烈焰人死亡后，活体与工作符文消失，留下可扑灭的余火；笼架皮肤和头饰保留。\n\n持装有烈焰人的机械动力燃烧室右键重新填充，生存消耗该燃烧室并返还空燃烧室。重新安装避雷针后再尝试超越。",
                "原液与容量\n\n1份奥术源质=1瓶原液=250mB。4瓶=1桶=1000mB。\n\n250mB蓝液档一桶4次；对应猩红62.5mB档一桶16次。实际按目标等级扣液，待机不消耗。",
                "升华室\n\n右键装模板、卷轴、对应瓶墨。原液瓶、桶或管道均可补液；奥术源质可直接喂食。\n\n同罐存蓝液为普通，存猩红原液为突破。灰烬源质改用于蛋糕，不再作为燃料。",
                "单罐覆盖换液\n\n升华室仅有一个4000mB储罐。真正接受不同种原液时，旧液和旧猩红小数余额全部损失，只保留新输入的液体。\n\n红蓝不再进行四倍实体兑换；猩红升华省液仍保留。",
                "换液与取液\n\n红液800mB+蓝瓶250mB，得到蓝液250mB，原红液全部损失。\n\n想保留旧液，先用空桶接1000mB，或空玻璃瓶接250mB；不足整份不扣液、不扣容器。",
                "源质喂食与溢出\n\n非满蓝罐喂1份源质，消耗1份并补最多250mB，溢出舍弃。\n\n蓝液3900mB+1份源质，得到蓝液4000mB，舍弃150mB。\n\n非满红罐喂源质，舍弃旧红液及余额，改存250mB蓝液。",
                "源质满罐限制\n\n物理存量等于4000mB时，无论红蓝都不能投源质，物品不消耗。自动入口也拒绝新源质。\n\n已存队列保留等待；非满红罐可以由源质触发换蓝液。",
                "同种瓶桶转移\n\n同种瓶和桶须放得下整份250mB或1000mB。空间不足，液体和手持容器均不变化。\n\n源质保留满罐限制。空桶、空瓶取液只认实体存量，不用小数余额凑整。",
                "异种瓶桶与管道\n\n不同液体的瓶桶换入时，舍弃旧液，只存新容器的250mB或1000mB；生存返空容器。\n\n异种管道每次最多接受4000mB，未接受的请求留在上游。模拟或空请求不丢旧液。",
                "加工与耗液\n\n手动加工70刻（3.5秒），末尾留1秒无声后播放成功音效。盆路线由动力搅拌器执行。\n\n升级缺料或输出堵塞时不耗液，仅在成功完成时扣费；随机研习采用开工锁定的已付费结果。",
                "猩红四倍效率\n\n升华室升级使用猩红原液，耗液为对应基础档的四分之一；墨水、手动加工时长和蛋糕用量不变。\n\n小数余量保留，连续两次37.5mB合计75mB。此效率不改变制蛋糕、补魔或打印配方。",
                "猩红小数余额\n\n余额仅用于同种猩红原液的后续升华扣费，不能变成可抽出的蓝液。\n\n换入蓝液时连同旧红液一起舍弃；蓝液与猩红原液不会通过换液增加实体体积。",
                "低阶升级\n\n已有1级卷轴也能在升华室逐级升级。目标2/3/4级各耗奥术原液100/150/200mB。\n\n原生低阶升级方式仍可用。1级起始卷轴可原生制作、打印或随机研习；打印耗50mB，研习耗500mB。",
                "低阶使用猩红\n\n目标2/3/4级耗猩红原液25/37.5/50mB。\n\n目标5/6/7级耗猩红原液62.5/125/187.5mB。\n\n原生上限内的升级不耗蛋糕；墨水用量仍为250mB。",
                "原生等级耗液\n\n目标5/6/7级：奥术原液250/500/750mB。\n\n目标8/9/10级：猩红原液62.5/125/187.5mB。\n\n每次另需250mB对应品级墨水。只在配方成功完成时扣料。",
                "工作盆\n\n工作盆紧贴升华室上方，盆内放卷轴与250mB对应墨水；盆上方需安装并驱动动力搅拌器。\n\n没有运转的搅拌器不会升级。原液、模板与超越蛋糕供给下方升华室。",
                "等级超越\n\n原生满级后最多再升3级，每次需突破状态、1个余烬蛋糕、250mB传奇墨水及相应猩红原液。\n\n原生上限10的法术：11/12/13级耗红液250/312.5/375mB。",
                "低上限法术超越\n\n原生上限不超过7的法术，超越第1/2/3级各耗猩红原液62.5/125/187.5mB。\n\n上限8或9时延续红液等级阶梯。每级仍需1个蛋糕和250mB传奇墨水。",
                "猩红原液与蛋糕\n\n奥术原液混合血，或猩红精华瓶加奥术源质，制成猩红原液。\n\n蛋糕原生序列：鸡蛋→机械手加糖→机械手加灰烬源质→辊压→注入1000mB猩红原液。各材料1份，只做1轮。",
                "卷轴印制 · 两工序\n\n纸和线各1个合成空白卷轴。有效原生卷轴设置样板，送入空白卷轴和50mB奥术原液，得到未注墨卷轴。\n\n样板只决定法术种类，不消耗，也不复制其等级。纸和已有法术的卷轴不能当空白材料。",
                "卷轴注墨\n\n未注墨卷轴需注入250mB该法术1级对应品级的墨水，得到1级卷轴。\n\n样板只决定法术种类；墨水按成品1级品级收费。印制成品可按原生概率在炼金锅回收墨水。",
                "卷轴升级 · 一次加工\n\n手动直接放卷轴和对应瓶墨；自动将卷轴和250mB墨水放入工作盆，由动力搅拌器完成。\n\n不再预注墨制作待升华卷轴。旧版已付墨的中间物仍可处理。",
                "法术书原位升级\n\n放书后默认闭合。进入5×5范围会开书；准星指向且可触及时只显示法术图标。\n\n滚轮选择，右键确认合书，再放对应墨水。只升级选定法术，取书一并返还空瓶。",
                "手动工艺\n\n还可进行固有法术升级、法术注入、法球强化、法术书扩槽、亲和戒绑定和忏悔石净化。\n\n主物品与对应材料各一份；模板保留，成功扣原液。净化返还法球，空手同次取回。",
                "JEI配方查询\n\n鼠标指向卷轴，默认R查配方、U查用途。奥术升华页展示工作盆、动力搅拌器与下方升华室的一次加工。\n\n印制与蛋糕保留原生序列组装；超越页保留避雷针条件。",
                "法力补给\n\n向分液池倒入奥术原液，站上面补蓝。\n\n背包装储罐升级和奥术补给升级，储罐内装奥术原液即可自动补蓝。猩红原液仅供工业使用。\n\n测试：/arcane_factory_test"))pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        tag.put("pages",pages);return book;
    }
}
