package dev.expedition.arcane.testing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.backpack.ManaSupplyUpgradeItem;
import dev.expedition.arcane.mana.ManaStationEvents;
import dev.expedition.arcane.mana.ManaSupplyService;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.loading.FMLPaths;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageFluidHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;

/** Opt-in, operator-only integration checks against actual objects in a running game. */
public final class PrototypeTests {
    private static final PrototypeTests INSTANCE = new PrototypeTests();
    private static boolean registered;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private PrototypeTests() {}

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(INSTANCE);
        }
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        if (Boolean.getBoolean("arcane.prototype")) {
            event.getDispatcher().register(Commands.literal("arcane_recipe_network_test")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        var checks = RecipeNetworkTests.run(context.getSource().getLevel());
                        Path result = FMLPaths.GAMEDIR.get().resolve("recipe-network-results.json");
                        try { Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(checks)); }
                        catch (java.io.IOException error) { throw new IllegalStateException(error); }
                        context.getSource().sendSuccess(() -> Component.literal("Recipe network: " + checks), false);
                        return 1;
                    }));
            event.getDispatcher().register(Commands.literal("arcane_reveal_show")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("tick", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 132))
                            .then(Commands.argument("crimson", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                    .then(Commands.argument("rarity", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
                                            .executes(context -> {
                                                dev.expedition.arcane.chamber.ResearchVisualFixture.show(
                                                        context.getSource().getLevel(), new BlockPos(0, 65, 0),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "tick"),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "crimson"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rarity"));
                                                return 1;
                                            })))));
            event.getDispatcher().register(Commands.literal("arcane_reveal_play")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("crimson", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                            .then(Commands.argument("rarity", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
                                    .executes(context -> {
                                        dev.expedition.arcane.chamber.ResearchVisualFixture.play(
                                                context.getSource().getLevel(), new BlockPos(0, 65, 0),
                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "crimson"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rarity"));
                                        return 1;
                                    }))));
            event.getDispatcher().register(Commands.literal("arcane_research_test")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        var checks = dev.expedition.arcane.chamber.ResearchPresentationTests.run(
                                context.getSource().getLevel(), new BlockPos(56, 65, 56));
                        Path result = FMLPaths.GAMEDIR.get().resolve("research-presentation-results.json");
                        try { Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(checks)); }
                        catch (java.io.IOException error) { throw new IllegalStateException(error); }
                        context.getSource().sendSuccess(() -> Component.literal("Research presentation: " + checks), false);
                        return 1;
                    }));
            event.getDispatcher().register(Commands.literal("arcane_chamber_test")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        var checks = dev.expedition.arcane.chamber.ChamberTests.run(
                                context.getSource().getLevel(), new BlockPos(64, 65, 56));
                        Path result = FMLPaths.GAMEDIR.get().resolve("chamber-regression-results.json");
                        try { Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(checks)); }
                        catch (java.io.IOException error) { throw new IllegalStateException(error); }
                        context.getSource().sendSuccess(() -> Component.literal("Chamber regression: " + checks), false);
                        return 1;
                    }));
        }
        event.getDispatcher().register(Commands.literal("arcane_test")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (player instanceof FakePlayer || sessions.containsKey(player.getUUID()) || !player.isAlive() || player.isPassenger()
                    || player.isSleeping()) {
                    context.getSource().sendFailure(Component.literal("无法开始：已有测试、玩家未存活、正在骑乘或睡眠。"));
                    return 0;
                }
                Session session = new Session(player);
                sessions.put(player.getUUID(), session);
                // Existing native cooldowns expire without a test-only bypass of production state.
                player.setGameMode(GameType.SPECTATOR);
                context.getSource().sendSuccess(() -> Component.literal(
                    "奥术原型测试已启动，约 1 秒后执行；结束后恢复玩家状态并输出 arcane-test-results.json。"), false);
                return 1;
            }));
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Session session : List.copyOf(sessions.values())) {
            if (--session.waitTicks > 0) {
                continue;
            }
            sessions.remove(session.player.getUUID());
            session.run();
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = sessions.remove(player.getUUID());
            if (session != null) {
                session.abort("Player disconnected before integration checks ran");
            }
        }
    }

    @SubscribeEvent
    public void stopping(ServerStoppingEvent event) {
        for (Session session : List.copyOf(sessions.values())) {
            session.abort("Server stopped before integration checks ran");
        }
        sessions.clear();
    }

    private static final class Session {
        private final ServerPlayer player;
        private final ServerLevel originalLevel;
        private final ListTag originalInventory;
        private final int originalSelected;
        private final float originalMana;
        private final GameType originalMode;
        private final Vec3 originalPosition;
        private final Vec3 originalMotion;
        private final float originalYaw;
        private final float originalPitch;
        private final float originalFallDistance;
        private final boolean originalOnGround;
        private final boolean originalFlying;
        private final JsonArray results = new JsonArray();
        private final List<IBackpackWrapper> testBackpacks = new ArrayList<>();
        private BlockPos drainPosition;
        private int waitTicks = 21;

        private Session(ServerPlayer player) {
            this.player = player;
            originalLevel = player.serverLevel();
            originalInventory = player.getInventory().save(new ListTag());
            originalSelected = player.getInventory().selected;
            originalMana = mana();
            originalMode = player.gameMode.getGameModeForPlayer();
            originalPosition = player.position();
            originalMotion = player.getDeltaMovement();
            originalYaw = player.getYRot();
            originalPitch = player.getXRot();
            originalFallDistance = player.fallDistance;
            originalOnGround = player.onGround();
            originalFlying = player.getAbilities().flying;
        }

        private void run() {
            try {
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                serviceChecks();
                drinkChecks();
                stationChecks();
                backpackChecks();
            } catch (Throwable failure) {
                record("suite_setup", false, failure.toString());
            } finally {
                finish();
            }
        }

        private void abort(String reason) {
            record("suite_cancelled", false, reason);
            finish();
        }

        private void serviceChecks() {
            check("restore_missing_mana", () -> {
                setMana(0);
                float expected = Math.min(10, maximumMana());
                float restored = ManaSupplyService.restore(player, 10);
                requireClose(restored, expected, "returned increase");
                requireClose(mana(), expected, "native mana");
                return "Native MagicData increased by " + restored;
            });
            check("restore_caps_at_maximum", () -> {
                setMana(Math.max(0, maximumMana() - 2));
                float before = mana();
                float restored = ManaSupplyService.restore(player, 10000);
                requireClose(mana(), maximumMana(), "MAX_MANA cap");
                requireClose(restored, maximumMana() - before, "actual increment");
                return "Maximum=" + maximumMana() + ", actual increment=" + restored;
            });
            check("restore_full_mana_no_change", () -> {
                setMana(maximumMana());
                requireClose(ManaSupplyService.restore(player, 50), 0, "full mana return");
                requireClose(mana(), maximumMana(), "full mana remains capped");
                return "Full mana unchanged";
            });
        }

        private void drinkChecks() {
            check("bottle_restores_and_returns_glass", () -> {
                setMana(0);
                ItemStack stack = new ItemStack(ArcaneContent.BOTTLE.get());
                ItemStack result = stack.getItem().finishUsingItem(stack, originalLevel, player);
                require(result.is(Items.GLASS_BOTTLE) && result.getCount() == 1, "Expected one empty glass bottle");
                requireClose(mana(), Math.min(50, maximumMana()), "bottle mana");
                return "250 mB dose; returned glass bottle";
            });
            check("bucket_restores_and_returns_bucket", () -> {
                setMana(0);
                ItemStack stack = new ItemStack(ArcaneContent.BUCKET.get());
                ItemStack result = stack.getItem().finishUsingItem(stack, originalLevel, player);
                require(result.is(Items.BUCKET) && result.getCount() == 1, "Expected one empty bucket");
                requireClose(mana(), Math.min(200, maximumMana()), "bucket mana");
                return "1000 mB dose; returned empty bucket";
            });
            check("full_mana_does_not_consume_drinks", () -> {
                setMana(maximumMana());
                for (var item : List.of(ArcaneContent.BOTTLE.get(), ArcaneContent.BUCKET.get())) {
                    ItemStack stack = new ItemStack(item);
                    ItemStack result = item.finishUsingItem(stack, originalLevel, player);
                    require(result.is(item) && result.getCount() == 1, "Full mana consumed " + item);
                }
                return "Both containers kept when full";
            });
            check("creative_drinks_keep_containers", () -> {
                player.setGameMode(GameType.CREATIVE);
                try {
                    for (var item : List.of(ArcaneContent.BOTTLE.get(), ArcaneContent.BUCKET.get())) {
                        setMana(0);
                        ItemStack stack = new ItemStack(item);
                        ItemStack result = item.finishUsingItem(stack, originalLevel, player);
                        require(result.is(item) && result.getCount() == 1, "Creative mode consumed " + item);
                        require(mana() > 0, "Creative drink did not restore mana");
                    }
                } finally {
                    player.setGameMode(GameType.SURVIVAL);
                }
                return "Both creative drinks restored native mana without consuming containers";
            });
        }

        private void stationChecks() {
            check("native_item_drain_setup_and_manual_bucket_fill", () -> {
                drainPosition = findEmptyTestPosition();
                require(originalLevel.setBlock(drainPosition, AllBlocks.ITEM_DRAIN.getDefaultState(), 3),
                    "Could not place temporary ItemDrain");
                require(originalLevel.getBlockEntity(drainPosition) instanceof ItemDrainBlockEntity,
                    "Actual Create ItemDrainBlockEntity missing");
                ItemDrainBlockEntity drain = (ItemDrainBlockEntity) originalLevel.getBlockEntity(drainPosition);
                IFluidHandler tank = drainTank(drain);
                require(tank.getTankCapacity(0) == 1500, "Unexpected native drain capacity");
                require(tank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 1000),
                    IFluidHandler.FluidAction.SIMULATE) == 0, "Native pipe input should remain disabled");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArcaneContent.BUCKET.get()));
                AllBlocks.ITEM_DRAIN.get().use(drain.getBlockState(), originalLevel, drainPosition, player,
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(drainPosition), Direction.UP,
                        drainPosition, false));
                require(fluidAmount(tank, ArcaneContent.SOURCE.get()) == 1000, "Bucket did not fill native drain");
                require(player.getMainHandItem().is(Items.BUCKET), "Manual fill did not return empty bucket");
                return "Actual Create drain holds 1000 / 1500 mB after normal block use";
            });
            check("station_full_mana_does_not_drain", () -> {
                IFluidHandler tank = placedDrainTank();
                standOnDrain(13.0 / 16.0);
                setMana(maximumMana());
                int before = fluidAmount(tank, ArcaneContent.SOURCE.get());
                ManaStationEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                require(fluidAmount(tank, ArcaneContent.SOURCE.get()) == before, "Full-mana station consumed fluid");
                return "Full mana leaves native tank unchanged";
            });
            check("station_rejects_wrong_standing_height", () -> {
                IFluidHandler tank = placedDrainTank();
                standOnDrain(1.0);
                setMana(0);
                int before = fluidAmount(tank, ArcaneContent.SOURCE.get());
                ManaStationEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                require(fluidAmount(tank, ArcaneContent.SOURCE.get()) == before && mana() == 0,
                    "A player at a full-block height was treated as standing on the 13/16 drain");
                return "AABB height gate rejected 16/16 instead of 13/16";
            });
            check("station_at_13px_consumes_actual_fluid_and_restores", () -> {
                IFluidHandler tank = placedDrainTank();
                standOnDrain(13.0 / 16.0);
                setMana(0);
                int before = fluidAmount(tank, ArcaneContent.SOURCE.get());
                ManaStationEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                int consumed = before - fluidAmount(tank, ArcaneContent.SOURCE.get());
                require(consumed > 0 && consumed <= 50, "Expected up to 50 mB, got " + consumed);
                requireClose(mana(), consumed * ManaSupplyService.MANA_PER_MB, "station conversion");
                float manaAfter = mana();
                ManaStationEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                require(before - fluidAmount(tank, ArcaneContent.SOURCE.get()) == consumed,
                    "Station supplied twice in one server tick");
                requireClose(mana(), manaAfter, "station same-tick cooldown");
                return "Actual native tank consumed " + consumed + " mB; native mana=" + manaAfter;
            });
        }

        private void backpackChecks() {
            check("native_backpack_upgrade_and_tank_transactions", () -> {
                IBackpackWrapper backpack = makeBackpack(false);
                ManaSupplyUpgradeItem.Wrapper upgrade = manaUpgrade(backpack);
                require(backpack.getFluidHandler().isEmpty(), "A backpack without Tank Upgrade exposed a tank");
                setMana(0);
                upgrade.tick(player, originalLevel, player.blockPosition());
                requireClose(mana(), 0, "no Tank Upgrade");

                insertUpgrade(backpack.getUpgradeHandler(), 1, new ItemStack(ModItems.TANK_UPGRADE.get()));
                IStorageFluidHandler handler = backpack.getFluidHandler().orElseThrow(
                    () -> new AssertionError("Native Tank Upgrade did not create a storage fluid handler"));
                upgrade = manaUpgrade(backpack);
                upgrade.tick(player, originalLevel, player.blockPosition());
                requireClose(mana(), 0, "empty tank");

                require(handler.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE) == 250,
                    "Could not fill native tank with water");
                upgrade.tick(player, originalLevel, player.blockPosition());
                requireClose(mana(), 0, "wrong fluid");
                require(fluidAmount(handler, Fluids.WATER) == 250, "Water was consumed as arcane solution");
                handler.drain(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
                require(handler.fill(new FluidStack(ArcaneContent.SOURCE.get(), 500),
                    IFluidHandler.FluidAction.EXECUTE) == 500, "Could not fill native tank with source fluid");

                setMana(maximumMana());
                upgrade.tick(player, originalLevel, player.blockPosition());
                require(fluidAmount(handler, ArcaneContent.SOURCE.get()) == 500, "Full mana consumed tank fluid");
                setMana(0);
                upgrade.setEnabled(false);
                upgrade.tick(player, originalLevel, player.blockPosition());
                require(fluidAmount(handler, ArcaneContent.SOURCE.get()) == 500 && mana() == 0,
                    "Disabled native upgrade consumed fluid or restored mana");
                upgrade.setEnabled(true);
                upgrade = manaUpgrade(backpack);
                upgrade.tick(player, originalLevel, player.blockPosition());
                int consumed = 500 - fluidAmount(handler, ArcaneContent.SOURCE.get());
                require(consumed > 0 && consumed <= 25, "Expected up to 25 mB, got " + consumed);
                requireClose(mana(), consumed * ManaSupplyService.MANA_PER_MB, "backpack conversion");
                float afterFirst = mana();
                upgrade.tick(player, originalLevel, player.blockPosition());
                require(fluidAmount(handler, ArcaneContent.SOURCE.get()) == 500 - consumed,
                    "Same upgrade supplied twice in one tick");
                requireClose(mana(), afterFirst, "same-upgrade same-tick limit");

                IBackpackWrapper secondBackpack = makeBackpack(true);
                IStorageFluidHandler secondTank = secondBackpack.getFluidHandler().orElseThrow();
                require(secondTank.fill(new FluidStack(ArcaneContent.SOURCE.get(), 500),
                    IFluidHandler.FluidAction.EXECUTE) == 500, "Could not fill second backpack tank");
                manaUpgrade(secondBackpack).tick(player, originalLevel, player.blockPosition());
                require(fluidAmount(secondTank, ArcaneContent.SOURCE.get()) == 500,
                    "A second backpack bypassed the player's shared cooldown");
                requireClose(mana(), afterFirst, "multiple-backpack same-tick limit");
                return "Real BackpackWrapper + native Tank Upgrade: no tank/empty/water/full/disabled guards; "
                    + consumed + " mB -> " + afterFirst + " mana; repeated and second-backpack ticks blocked";
            });
        }

        private IBackpackWrapper makeBackpack(boolean withTank) {
            ItemStack stack = new ItemStack(ModItems.DIAMOND_BACKPACK.get());
            IBackpackWrapper backpack = stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                .resolve().orElseThrow(() -> new AssertionError("Real backpack capability missing"));
            require(backpack instanceof BackpackWrapper, "Capability is not a native BackpackWrapper");
            backpack.setContentsUuid(UUID.randomUUID());
            testBackpacks.add(backpack);
            ItemStack manaStack = new ItemStack(ArcaneContent.MANA_UPGRADE.get());
            require(manaStack.is(ModItems.BACKPACK_UPGRADE_TAG),
                "Mana Upgrade is missing the native sophisticatedbackpacks:upgrade item tag");
            insertUpgrade(backpack.getUpgradeHandler(), 0, manaStack);
            if (withTank) {
                insertUpgrade(backpack.getUpgradeHandler(), 1, new ItemStack(ModItems.TANK_UPGRADE.get()));
            }
            return backpack;
        }

        private static void insertUpgrade(UpgradeHandler upgrades, int slot, ItemStack stack) {
            require(upgrades.getSlots() > slot, "Native backpack has insufficient upgrade slots");
            require(upgrades.isItemValid(slot, stack),
                "Native backpack slot " + slot + " rejects " + stack + " before insertion (check its upgrade tag)");
            ItemStack remainder = upgrades.insertItem(slot, stack, false);
            require(remainder.isEmpty(), "Native upgrade insertion rejected " + stack + "; remaining=" + remainder
                + ", occupied=" + upgrades.getStackInSlot(slot) + ", slot limit=" + upgrades.getSlotLimit(slot));
        }

        private static ManaSupplyUpgradeItem.Wrapper manaUpgrade(IBackpackWrapper backpack) {
            var wrappers = backpack.getUpgradeHandler().getTypeWrappers(ManaSupplyUpgradeItem.TYPE);
            require(wrappers.size() == 1, "Native UpgradeHandler did not expose exactly one mana wrapper");
            return wrappers.get(0);
        }

        private BlockPos findEmptyTestPosition() {
            for (int dy = 3; dy < 8; dy++) {
                for (int dx = 3; dx < 8; dx++) {
                    BlockPos pos = originalPositionToBlock().offset(dx, dy, 0);
                    if (originalLevel.hasChunkAt(pos) && pos.getY() + 3 < originalLevel.getMaxBuildHeight()
                        && originalLevel.isEmptyBlock(pos) && originalLevel.isEmptyBlock(pos.above())
                        && originalLevel.isEmptyBlock(pos.above(2))) {
                        return pos;
                    }
                }
            }
            throw new AssertionError("No three-block air space near player for a temporary drain");
        }

        private BlockPos originalPositionToBlock() {
            return BlockPos.containing(originalPosition);
        }

        private IFluidHandler placedDrainTank() {
            require(drainPosition != null && originalLevel.getBlockEntity(drainPosition) instanceof ItemDrainBlockEntity,
                "Temporary native ItemDrain setup failed");
            return drainTank((ItemDrainBlockEntity) originalLevel.getBlockEntity(drainPosition));
        }

        private static IFluidHandler drainTank(ItemDrainBlockEntity drain) {
            return drain.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve()
                .orElseThrow(() -> new AssertionError("Native ItemDrain fluid capability missing"));
        }

        private void standOnDrain(double height) {
            require(drainPosition != null, "No temporary drain");
            player.teleportTo(originalLevel, drainPosition.getX() + 0.5,
                drainPosition.getY() + height, drainPosition.getZ() + 0.5, originalYaw, originalPitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
        }

        private float mana() {
            return MagicData.getPlayerMagicData(player).getMana();
        }

        private float maximumMana() {
            return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        }

        private void setMana(float mana) {
            MagicData.getPlayerMagicData(player).setMana(mana);
        }

        private void check(String name, TestBody body) {
            try {
                record(name, true, body.run());
            } catch (Throwable failure) {
                record(name, false, failure.toString());
                ArcaneIndustry.LOGGER.warn("Arcane integration check failed: {}", name, failure);
            }
        }

        private void record(String name, boolean passed, String detail) {
            JsonObject result = new JsonObject();
            result.addProperty("name", name);
            result.addProperty("passed", passed);
            result.addProperty("detail", detail);
            results.add(result);
        }

        private void finish() {
            check("restore_player_and_cleanup", () -> {
                try {
                    for (IBackpackWrapper backpack : testBackpacks) {
                        backpack.removeContentsUuid();
                    }
                    if (drainPosition != null && originalLevel.getBlockEntity(drainPosition) instanceof ItemDrainBlockEntity) {
                        originalLevel.setBlock(drainPosition, Blocks.AIR.defaultBlockState(), 3);
                    }
                } finally {
                    player.getInventory().clearContent();
                    player.getInventory().load(originalInventory.copy());
                    player.getInventory().selected = originalSelected;
                    player.setGameMode(originalMode);
                    player.teleportTo(originalLevel, originalPosition.x, originalPosition.y, originalPosition.z,
                        originalYaw, originalPitch);
                    player.setDeltaMovement(originalMotion);
                    player.setOnGround(originalOnGround);
                    player.fallDistance = originalFallDistance;
                    player.getAbilities().flying = originalFlying;
                    player.onUpdateAbilities();
                    setMana(originalMana);
                    PacketDistributor.sendToPlayer(player, new SyncManaPacket(MagicData.getPlayerMagicData(player)));
                    player.inventoryMenu.broadcastChanges();
                }
                return "Inventory, selected slot, mana, gamemode, position, motion and flight restored; test objects removed";
            });
            boolean allPassed = true;
            int passed = 0;
            for (var entry : results) {
                if (entry.getAsJsonObject().get("passed").getAsBoolean()) {
                    passed++;
                } else {
                    allPassed = false;
                }
            }
            JsonObject report = new JsonObject();
            report.addProperty("format_version", 1);
            report.addProperty("completed_at", Instant.now().toString());
            report.addProperty("player_uuid", player.getUUID().toString());
            report.addProperty("complete", true);
            report.addProperty("all_passed", allPassed);
            report.addProperty("passed", passed);
            report.addProperty("total", results.size());
            report.add("checks", results);
            JsonArray limits = new JsonArray();
            limits.add("Drink finishUsingItem is called on real stacks; the 32-tick client drinking animation is not exercised.");
            limits.add("Station geometry is checked by invoking the production PlayerTickEvent handler at actual player coordinates; an elapsed physical standing period is not exercised.");
            limits.add("Backpack wrappers are obtained from real native upgrade insertion and ticked directly; automatic inventory/Curios dispatch is not exercised.");
            limits.add("Native mana sync packets are sent; client HUD rendering and packet receipt are not asserted.");
            report.add("limitations", limits);
            Path output = FMLPaths.GAMEDIR.get().resolve("arcane-test-results.json");
            try {
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
                String summary = "奥术原型测试：" + passed + "/" + results.size() + " 通过。结果：" + output;
                player.sendSystemMessage(Component.literal(summary));
                ArcaneIndustry.LOGGER.info(summary);
            } catch (Exception failure) {
                ArcaneIndustry.LOGGER.error("Could not save arcane prototype integration results", failure);
                player.sendSystemMessage(Component.literal("测试结束，但 JSON 写入失败：" + failure));
            }
        }
    }

    private static int fluidAmount(IFluidHandler handler, Fluid fluid) {
        int total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (contents.getFluid() == fluid) {
                total += contents.getAmount();
            }
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(float actual, float expected, String description) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) < 0.001F,
            description + ": expected " + expected + ", got " + actual);
    }

    @FunctionalInterface
    private interface TestBody {
        String run() throws Exception;
    }
}
