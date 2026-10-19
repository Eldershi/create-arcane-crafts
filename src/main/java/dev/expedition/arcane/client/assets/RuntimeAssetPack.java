package dev.expedition.arcane.client.assets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Required, memory-only pack containing transformations of the player's installed dependencies. */
public final class RuntimeAssetPack extends AbstractPackResources {
    private static final String ID = "arcane_industry:installed_dependency_materials";
    private static final byte[] METADATA = ("{\"pack\":{\"pack_format\":15,\"description\":\"Create: Arcane Crafts runtime materials\"}}")
            .getBytes(StandardCharsets.UTF_8);
    private Map<ResourceLocation, byte[]> generated;

    @FunctionalInterface
    public interface Source { byte[] read(ResourceLocation id) throws IOException; }

    public RuntimeAssetPack(String id) { super(id, true); }

    /** Bind to the MOD event bus on the physical client. */
    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(ID, Component.literal("Create: Arcane Crafts runtime materials"), true,
                    RuntimeAssetPack::new, PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    /** Never uses the active ResourceManager, so generated outputs cannot become their own inputs. */
    private static byte[] readInstalled(ResourceLocation id) throws IOException {
        if (id.getNamespace().equals("arcane_industry")) throw new IOException("Runtime asset source must be an installed dependency: " + id);
        if (id.getNamespace().equals("minecraft")) {
            IoSupplier<InputStream> input = Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, id);
            if (input == null) throw new IOException("Missing vanilla asset " + id);
            try (InputStream stream = input.get()) { return stream.readAllBytes(); }
        }
        var mod = ModList.get().getModFileById(id.getNamespace());
        if (mod == null) throw new IOException("Required dependency is not installed: " + id.getNamespace());
        Path file = mod.getFile().findResource("assets", id.getNamespace(), id.getPath());
        if (!Files.isRegularFile(file)) throw new IOException("Installed dependency is missing asset " + id);
        return Files.readAllBytes(file);
    }

    private synchronized Map<ResourceLocation, byte[]> assets() {
        if (generated == null) {
            Map<ResourceLocation, byte[]> result = new LinkedHashMap<>();
            try {
                Source source = RuntimeAssetPack::readInstalled;
                RuntimeTextureAssets.generate(source, result);
                RuntimeModelAssets.generate(source, result);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot build Arcane Crafts materials from the installed dependency versions", exception);
            }
            generated = Map.copyOf(result);
        }
        return generated;
    }

    @Override public IoSupplier<InputStream> getRootResource(String... elements) {
        return elements.length == 1 && "pack.mcmeta".equals(elements[0]) ? () -> new ByteArrayInputStream(METADATA) : null;
    }
    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        byte[] bytes = assets().get(id);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }
    @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;
        assets().forEach((id, bytes) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) output.accept(id, () -> new ByteArrayInputStream(bytes));
        });
    }
    @Override public Set<String> getNamespaces(PackType type) { return type == PackType.CLIENT_RESOURCES ? Set.of("arcane_industry") : Set.of(); }
    @Override public synchronized void close() { generated = null; }
}
