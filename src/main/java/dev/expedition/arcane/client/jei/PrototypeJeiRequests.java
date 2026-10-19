package dev.expedition.arcane.client.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Whitelisted in-game JEI navigation for visual review; no commands, key injection or OS UI access. */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PrototypeJeiRequests {
    private static long nextPoll;
    private PrototypeJeiRequests() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arcane.prototype") || event.phase != TickEvent.Phase.END
                || !ModList.get().isLoaded("jei")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        long now = System.nanoTime();
        if (now < nextPoll) return;
        nextPoll = now + 250_000_000L;
        Path request = JeiPrototypeVerification.runDirectory().resolve("jei-preview-request.json");
        if (!Files.isRegularFile(request) || Files.isSymbolicLink(request)) return;
        JsonObject receipt = new JsonObject();
        receipt.addProperty("prototype_only", true);
        receipt.addProperty("time", Instant.now().toString());
        try {
            if (Files.size(request) > 8192) throw new IllegalArgumentException("JEI request exceeds 8192 bytes");
            JsonObject json = JsonParser.parseString(Files.readString(request, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = json.has("id") ? json.get("id").getAsString() : "jei-review";
            String category = json.get("category").getAsString();
            if (id.length() > 96 || !id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Expected short plain request id");
            receipt.addProperty("id", id);
            receipt.addProperty("category", category);
            Files.delete(request);
            boolean shown = ArcaneJeiPlugin.showCategory(category);
            receipt.addProperty("status", shown ? "opened" : "unavailable");
            receipt.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        } catch (Exception failure) {
            try { Files.deleteIfExists(request); } catch (Exception ignored) {}
            receipt.addProperty("status", "failed");
            receipt.addProperty("detail", failure.toString());
        }
        try {
            JeiPrototypeVerification.writeJson("jei-preview-result.json", receipt);
        } catch (Exception failure) {
            ArcaneIndustry.LOGGER.error("Could not write prototype JEI navigation receipt", failure);
        }
    }
}
