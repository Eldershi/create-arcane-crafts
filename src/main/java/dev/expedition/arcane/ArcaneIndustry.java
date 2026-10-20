package dev.expedition.arcane;

import com.mojang.logging.LogUtils;
import dev.expedition.arcane.compat.PrinterCompat;
import dev.expedition.arcane.compat.ScrollInkingRecipe;
import dev.expedition.arcane.compat.ScrollAscensionRecipes;
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
        registerDevelopmentHooks();
    }

    /** Optional prototype classes are deliberately absent from the production JAR. */
    private static void registerDevelopmentHooks() {
        if (!Boolean.getBoolean("arcane.prototype")) return;
        try {
            Class.forName("dev.expedition.arcane.testing.PrototypeTests")
                    .getMethod("register").invoke(null);
        } catch (ClassNotFoundException absent) {
            LOGGER.info("Prototype checks are not included in this production build");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not register prototype checks", failure);
        }
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(MOD_ID, path); }
}
