package dev.expedition.arcane.client;

import com.google.gson.GsonBuilder;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.mixin.client.NativeInkFluidTypeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Replaces the visuals of the five original ink types; registry identities stay native. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeInkVisuals {
    private static final List<String> QUALITIES = List.of("common", "uncommon", "rare", "epic", "legendary");
    private static final Map<String, InstalledInk> INSTALLED = new LinkedHashMap<>();

    private NativeInkVisuals() {}

    public static void install() {
        if (!INSTALLED.isEmpty()) return;
        for (String quality : QUALITIES) {
            ResourceLocation nativeId = new ResourceLocation("irons_spellbooks", quality + "_ink");
            Fluid fluid = Objects.requireNonNull(ForgeRegistries.FLUIDS.getValue(nativeId), nativeId.toString());
            FluidType type = Objects.requireNonNull(ForgeRegistries.FLUID_TYPES.get().getValue(nativeId), nativeId.toString());
            if (fluid.getFluidType() != type) {
                throw new IllegalStateException("Iron ink fluid/type identity mismatch: " + nativeId);
            }
            IClientFluidTypeExtensions previous = IClientFluidTypeExtensions.of(type);
            InkExtension extension = new InkExtension(ArcaneIndustry.id("block/ink/" + quality + "_still"),
                    ArcaneIndustry.id("block/ink/" + quality + "_flow"));
            ((NativeInkFluidTypeAccessor) (Object) type).arcane$setClientExtension(extension);
            if (IClientFluidTypeExtensions.of(type) != extension) {
                throw new IllegalStateException("Could not install native ink extension: " + nativeId);
            }
            INSTALLED.put(quality, new InstalledInk(nativeId, fluid, type, extension,
                    String.valueOf(previous.getStillTexture()), previous.getTintColor()));
        }
        ArcaneIndustry.LOGGER.info("Installed animated black-body visuals for the five native Iron ink fluid types");
    }

    /** Read after the block atlas is stitched; also usable by prototype JEI/runtime verification. */
    public static Map<String, Object> snapshot() {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        return snapshot(atlas);
    }

    private static Map<String, Object> snapshot(TextureAtlas atlas) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", Instant.now().toString());
        result.put("source", "Actual IClientFluidTypeExtensions.of(native FluidType) and stitched block atlas");
        result.put("atlas", atlas.location().toString());
        result.put("installed_count", INSTALLED.size());
        List<Map<String, Object>> entries = new ArrayList<>();
        boolean allValid = INSTALLED.size() == QUALITIES.size();
        for (var entry : INSTALLED.entrySet()) {
            InstalledInk installed = entry.getValue();
            IClientFluidTypeExtensions actual = IClientFluidTypeExtensions.of(installed.type);
            ResourceLocation still = actual.getStillTexture(new FluidStack(installed.fluid, 250));
            ResourceLocation flow = actual.getFlowingTexture(new FluidStack(installed.fluid, 250));
            int tint = actual.getTintColor(new FluidStack(installed.fluid, 250));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("quality", entry.getKey());
            item.put("fluid", ForgeRegistries.FLUIDS.getKey(installed.fluid).toString());
            item.put("fluid_type", ForgeRegistries.FLUID_TYPES.get().getKey(installed.type).toString());
            item.put("fluid_and_type_identity_unchanged", installed.fluid.getFluidType() == installed.type);
            item.put("extension_is_replacement", actual == installed.extension);
            item.put("previous_still", installed.previousStill);
            item.put("previous_tint_argb", String.format("%08X", installed.previousTint));
            item.put("actual_tint_argb", String.format("%08X", tint));
            item.put("actual_still", String.valueOf(still));
            item.put("actual_flow", String.valueOf(flow));
            Map<String, Object> stillSprite = sprite(atlas, still);
            Map<String, Object> flowSprite = sprite(atlas, flow);
            item.put("still_sprite", stillSprite);
            item.put("flow_sprite", flowSprite);
            boolean valid = actual == installed.extension && tint == 0xFFFFFFFF
                    && installed.extension.still.equals(still) && installed.extension.flow.equals(flow)
                    && Boolean.FALSE.equals(stillSprite.get("missing")) && Boolean.FALSE.equals(flowSprite.get("missing"))
                    && ((Number) stillSprite.get("unique_animation_frames")).longValue() > 1
                    && ((Number) flowSprite.get("unique_animation_frames")).longValue() > 1;
            item.put("valid", valid);
            allValid &= valid;
            entries.add(item);
        }
        result.put("inks", entries);
        result.put("all_valid", allValid);
        return result;
    }

    private static Map<String, Object> sprite(TextureAtlas atlas, ResourceLocation id) {
        TextureAtlasSprite sprite = atlas.getSprite(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", String.valueOf(id));
        result.put("stitched_name", sprite.contents().name().toString());
        result.put("missing", !sprite.contents().name().equals(id));
        result.put("width", sprite.contents().width());
        result.put("height", sprite.contents().height());
        result.put("unique_animation_frames", sprite.contents().getUniqueFrames().count());
        int dark = 0, count = 0;
        for (int y = 0; y < sprite.contents().height(); y++) {
            for (int x = 0; x < sprite.contents().width(); x++) {
                int pixel = sprite.getPixelRGBA(0, x, y);
                if ((pixel >>> 24) == 0) continue;
                count++;
                int maximum = Math.max(pixel & 255, Math.max(pixel >>> 8 & 255, pixel >>> 16 & 255));
                if (maximum <= 40) dark++;
            }
        }
        result.put("first_frame_dark_pixel_fraction", count == 0 ? 0 : (double) dark / count);
        return result;
    }

    @SubscribeEvent
    public static void atlasStitched(TextureStitchEvent.Post event) {
        if (!Boolean.getBoolean("arcane.prototype") || !event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) return;
        try {
            Path run = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().resolveSibling("run");
            Files.createDirectories(run);
            Files.writeString(run.resolve("ink-fluid-render-verification.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(snapshot(event.getAtlas())) + "\n", StandardCharsets.UTF_8);
        } catch (Exception failure) {
            ArcaneIndustry.LOGGER.error("Could not verify native ink visual bindings", failure);
        }
    }

    private record InstalledInk(ResourceLocation id, Fluid fluid, FluidType type, InkExtension extension,
                                String previousStill, int previousTint) {}

    private record InkExtension(ResourceLocation still, ResourceLocation flow) implements IClientFluidTypeExtensions {
        @Override public ResourceLocation getStillTexture() { return still; }
        @Override public ResourceLocation getFlowingTexture() { return flow; }
        // Ink colours live in the sprites. Reusing Iron's old tint would multiply
        // the coloured highlights down to black and distort the five palettes.
        @Override public int getTintColor() { return 0xFFFFFFFF; }
    }
}
