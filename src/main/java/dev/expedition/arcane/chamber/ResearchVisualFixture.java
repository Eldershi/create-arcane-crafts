package dev.expedition.arcane.chamber;

import dev.expedition.arcane.ArcaneContent;
import dev.expedition.arcane.CrimsonContent;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Deliberately forced visual fixtures; never participates in the production research lottery. */
public final class ResearchVisualFixture {
    private ResearchVisualFixture() {}

    /** A blocked result slot pauses the real paid work clock at the shader-inspection phase. */
    public static void show(ServerLevel level, BlockPos pos, int logicalTick, boolean crimson, int rarity) {
        create(level, pos, logicalTick, crimson, rarity, false);
    }

    /** A real 161-tick processing/commit cycle with a controlled result for deterministic A/V QA. */
    public static void play(ServerLevel level, BlockPos pos, boolean crimson, int rarity) {
        create(level, pos, 0, crimson, rarity, true);
    }

    public static void play(ServerLevel level, BlockPos pos, int logicalTick, boolean crimson, int rarity) {
        create(level, pos, logicalTick, crimson, rarity, true);
    }

    private static void create(ServerLevel level, BlockPos pos, int logicalTick, boolean crimson,
                               int rarity, boolean playing) {
        if (!Boolean.getBoolean("arcane.prototype") || !level.getServer().isDedicatedServer()
                || !(level.getChunkSource().getGenerator() instanceof FlatLevelSource)) {
            throw new IllegalStateException("Research visual fixtures require the opt-in disposable flat test server");
        }
        if (logicalTick < 0 || logicalTick > 132 || rarity < 0 || rarity >= SpellRarity.values().length) {
            throw new IllegalArgumentException("logicalTick must be 0..132 and rarity must be 0..4");
        }
        ChamberTests.initializeSpellConfig(level);
        SpellRarity tier = SpellRarity.values()[rarity];
        AbstractSpell spell = RandomResearchRules.pool(SchoolRegistry.FIRE.get()).candidates(tier).stream()
                .findFirst().orElseGet(() -> RandomResearchRules.pools().stream()
                        .flatMap(pool -> pool.candidates(tier).stream()).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No enabled level-one native spell at rarity " + tier)));
        ItemStack output = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, 1, output);
        ItemStack primary = new ItemStack(ItemRegistry.SCROLL.get());
        ItemStack focus = new ItemStack(Items.BLAZE_ROD);
        long now = level.getGameTime();
        int progress = (int) Math.round(logicalTick * AscensionChamberBlockEntity.RESEARCH_PROCESSING_TIME / 132.0);

        // Only this disposable five-by-five scene is replaced. Detaching its old entities
        // prevents fixture resets from scattering previous forced output stacks.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos floor = pos.offset(x, -1, z);
            level.removeBlockEntity(floor);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        }
        for (int y = 0; y <= 3; y++) {
            level.removeBlockEntity(pos.above(y));
            level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, ArcaneContent.CHAMBER.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof AscensionChamberBlockEntity chamber)) {
            throw new IllegalStateException("Native ascension chamber was not created at fixture position");
        }
        if (!playing) chamber.inventory.setStackInSlot(AscensionChamberBlockEntity.RESULT_SLOT,
                new ItemStack(Items.COBBLESTONE, 64));
        chamber.sourceTank.fill(new FluidStack(crimson ? CrimsonContent.SOURCE.get() : ArcaneContent.SOURCE.get(), 500),
                IFluidHandler.FluidAction.EXECUTE);

        CompoundTag transaction = new CompoundTag();
        transaction.putString("Kind", RandomResearchRules.OPERATION);
        transaction.put("Primary", primary.save(new CompoundTag()));
        transaction.put("Modifier", focus.save(new CompoundTag()));
        transaction.put("Output", output.save(new CompoundTag()));
        transaction.putString("School", spell.getSchoolType().getId().toString());
        transaction.putString("Spell", spell.getSpellResource().toString());
        transaction.putInt("DrawnTier", rarity);
        transaction.putInt("ActualTier", rarity);
        transaction.putInt("PhysicalCostMb", RandomResearchRules.SOLUTION_COST_MB);
        transaction.putBoolean("GuideConsumed", true);
        transaction.putBoolean("Crimson", crimson);
        transaction.putBoolean("Headwear", false);
        transaction.putLong("VisualSeed", 0x5720a4f84L);
        CompoundTag saved = chamber.saveWithFullMetadata();
        saved.putString("ManualOperation", RandomResearchRules.OPERATION);
        saved.put("ManualTransaction", transaction);
        saved.putBoolean("ManualProcessing", true);
        saved.putBoolean("ManualResultReady", false);
        saved.putInt("ManualProgress", progress);
        saved.putLong("ManualStartedAt", now - progress);
        saved.putLong("ManualCompletedAt", -1);
        saved.putLong("ManualItemPlacedAt", now);
        saved.putLong("ManualMaterialPlacedAt", now);
        saved.putBoolean("ResearchVisualRunning", playing);
        saved.putLong("ResearchVisualSampleAt", now);
        chamber.load(saved);
        chamber.setChanged();
        level.sendBlockUpdated(pos, chamber.getBlockState(), chamber.getBlockState(), 3);
    }
}
