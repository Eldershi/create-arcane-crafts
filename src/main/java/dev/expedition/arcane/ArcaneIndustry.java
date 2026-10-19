package dev.expedition.arcane;

import com.mojang.logging.LogUtils;
import dev.expedition.arcane.compat.PrinterCompat;
import dev.expedition.arcane.compat.ScrollInkingRecipe;
import dev.expedition.arcane.compat.ScrollAscensionRecipes;
import dev.expedition.arcane.testing.PrototypeTests;
import dev.expedition.arcane.grinding.ArcaneGrindingContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ArcaneIndustry.MOD_ID)
public final class ArcaneIndustry {
    public static final String MOD_ID = "arcane_industry";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcaneIndustry() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ArcaneContent.register(bus);
        EssenceContent.register(bus);
        ArcaneHatchContent.register(bus);
        ArcaneGrindingContent.register(bus);
        ArcaneSounds.register(bus);
        dev.expedition.arcane.network.ArcanePackets.register();
        ScrollInkingRecipe.register(bus);
        ScrollAscensionRecipes.register(bus);
        PrinterCompat.register();
        PrototypeTests.register();
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(MOD_ID, path); }
}
