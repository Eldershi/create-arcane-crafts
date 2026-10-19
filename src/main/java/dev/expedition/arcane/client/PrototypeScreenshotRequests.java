package dev.expedition.arcane.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.expedition.arcane.ArcaneIndustry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development-only framebuffer capture through Minecraft's own screenshot implementation.
 * Enabled only with -Darcane.prototype=true; it never captures desktop/OS windows or sends commands.
 *
 * Place an atomically written JSON file beside the prototype instance at run/screenshot-request.json:
 * {"id":"chamber-front","filename":"arcane-chamber-front.png","delay_frames":10,"hide_gui":true}
 * It is consumed once. Receipt: run/screenshot-result.json. PNG: instance/screenshots/<filename>.
 */
@Mod.EventBusSubscriber(modid = ArcaneIndustry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PrototypeScreenshotRequests {
    private static final long POLL_INTERVAL_NANOS = 250_000_000L;
    private static final int MAX_REQUEST_BYTES = 8192;
    private static long nextPoll;
    private static Pending pending;
    private static boolean writingScreenshot;

    private PrototypeScreenshotRequests() {}

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("arcane.prototype") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (pending != null) {
            if (pending.startedInWorld && (minecraft.level == null || minecraft.player == null)) {
                Pending request = pending;
                pending = null;
                minecraft.options.hideGui = request.originalHideGui;
                writeResult(request.runDirectory, request.id, request.filename, "failed",
                    "World unloaded before the requested framebuffer capture", null);
            } else if (isReviewScreen(minecraft) && --pending.framesRemaining <= 0) {
                capture(minecraft);
            }
            return;
        }
        if (writingScreenshot) {
            return;
        }
        // A successful visual verification must contain the loaded world,
        // rather than a title, loading, experimental-confirmation or pause page.
        if (minecraft.level == null || minecraft.player == null || !isReviewScreen(minecraft)) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextPoll) {
            return;
        }
        nextPoll = now + POLL_INTERVAL_NANOS;
        Path gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        Path runDirectory = gameDirectory.resolveSibling("run");
        Path requestPath = runDirectory.resolve("screenshot-request.json");
        if (!Files.isRegularFile(requestPath) || Files.isSymbolicLink(requestPath)) {
            return;
        }
        String id = "invalid-request";
        String filename = "";
        try {
            if (Files.size(requestPath) > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("Screenshot request exceeds 8192 bytes");
            }
            JsonObject json = JsonParser.parseString(Files.readString(requestPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
            id = json.has("id") ? json.get("id").getAsString() : "prototype-screenshot";
            filename = json.has("filename") ? json.get("filename").getAsString() : "arcane-prototype.png";
            if (id.length() > 96 || !filename.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}\\.png")) {
                throw new IllegalArgumentException("Expected a short request id and a plain .png filename");
            }
            int frames = json.has("delay_frames") ? json.get("delay_frames").getAsInt() : 10;
            if (frames < 0 || frames > 600) {
                throw new IllegalArgumentException("delay_frames must be between 0 and 600");
            }
            boolean hideGui = !json.has("hide_gui") || json.get("hide_gui").getAsBoolean();
            Path screenshot = gameDirectory.resolve("screenshots").resolve(filename);
            if (Files.exists(screenshot)) {
                throw new IllegalArgumentException("Screenshot already exists; choose a new filename");
            }
            Files.delete(requestPath);
            pending = new Pending(id, filename, runDirectory, minecraft.options.hideGui, Math.max(1, frames), minecraft.level != null);
            minecraft.options.hideGui = hideGui;
            writeResult(runDirectory, id, filename, "queued", "Waiting for rendered frames", null);
        } catch (Exception failure) {
            try {
                Files.deleteIfExists(requestPath);
            } catch (Exception deleteFailure) {
                ArcaneIndustry.LOGGER.warn("Could not consume invalid prototype screenshot request", deleteFailure);
            }
            writeResult(runDirectory, id, filename, "failed", failure.toString(), null);
        }
    }

    private static boolean isReviewScreen(Minecraft minecraft) {
        return minecraft.screen == null || minecraft.screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui");
    }

    private static void capture(Minecraft minecraft) {
        Pending request = pending;
        pending = null;
        writingScreenshot = true;
        Path expected = minecraft.gameDirectory.toPath().toAbsolutePath().normalize()
            .resolve("screenshots").resolve(request.filename);
        try {
            // At RenderTick END this reads the game's completed render target, not an OS screenshot.
            Screenshot.grab(minecraft.gameDirectory, request.filename, minecraft.getMainRenderTarget(), message -> {
                String status = Files.isRegularFile(expected) ? "captured" : "failed";
                writeResult(request.runDirectory, request.id, request.filename, status, message.getString(), expected);
                minecraft.execute(() -> writingScreenshot = false);
            });
        } catch (Exception failure) {
            writingScreenshot = false;
            writeResult(request.runDirectory, request.id, request.filename, "failed", failure.toString(), expected);
        } finally {
            // Screenshot.grab copies the framebuffer before its asynchronous PNG write.
            minecraft.options.hideGui = request.originalHideGui;
        }
    }

    private static void writeResult(Path runDirectory, String id, String filename, String status,
                                    String detail, Path output) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("filename", filename);
        result.addProperty("status", status);
        result.addProperty("detail", detail);
        result.addProperty("time", Instant.now().toString());
        result.addProperty("capture_api", "net.minecraft.client.Screenshot.grab");
        result.addProperty("prototype_only", true);
        if (output != null) {
            result.addProperty("output", output.toString());
        }
        try {
            Files.createDirectories(runDirectory);
            Files.writeString(runDirectory.resolve("screenshot-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
            ArcaneIndustry.LOGGER.info("Prototype framebuffer screenshot {}: {}", id, status);
        } catch (Exception failure) {
            ArcaneIndustry.LOGGER.error("Could not write prototype screenshot receipt", failure);
        }
    }

    private static final class Pending {
        private final String id;
        private final String filename;
        private final Path runDirectory;
        private final boolean originalHideGui;
        private int framesRemaining;
        private final boolean startedInWorld;

        private Pending(String id, String filename, Path runDirectory, boolean originalHideGui, int framesRemaining, boolean startedInWorld) {
            this.id = id;
            this.filename = filename;
            this.runDirectory = runDirectory;
            this.originalHideGui = originalHideGui;
            this.framesRemaining = framesRemaining;
            this.startedInWorld = startedInWorld;
        }
    }
}
