# Runtime materials

The addon references the player's installed dependencies instead of shipping their copied or recolored PNGs. A required client resource pack builds 75 PNGs and 16 animation declarations in memory. The approved 1.20.1 prototype has 83 PNGs; the other eight are original casing/core patterns and rune sheets.

`RuntimeAssetPack` reads source assets directly from the installed mod files and the running Minecraft 1.20.1 vanilla pack. It does not read its own outputs through the resource manager, download assets, modify dependency JARs, or write generated assets to disk. Normal resource reload rebuilds the pack. The displayed materials were verified against the versions listed in `runtime-texture-reference.json`.

`assets/arcane_industry/runtime/texture_transforms.json` contains authored color maps, native UV crops, nearest-neighbor sampling rules, material masks, and small original glyph/eye strokes. It contains no complete source pixel layout, compressed image payload, or target texel-index table. Container glass, fluid animation patterns, particles, upgrade frames and parchment remain supplied by their installed dependencies. The two eyebrow strokes are drawn from short geometric stair segments, with authored face-oriented shading, so Minecraft 1.21 Breeze assets are not required.

`RuntimeModelAssets` independently derives model resources from installed native geometry and the addon's authored transforms. Texture references continue to use the same addon IDs, preserving existing renderers and saved worlds.

The approved texture reference records dimensions and SHA-256 hashes of decoded RGBA pixels. PNG compression and ancillary metadata may differ without changing appearance. `tools/VerifyRuntimeTextures.java` calls the actual runtime texture generator and checks all 218,112 generated pixels, plus parsed animation metadata, without launching the game. Compile it against the development runtime classpath and built addon classes, and supply `docs/runtime-texture-reference.json` followed by the installed Minecraft 1.20.1 assets JAR and the dependency JARs listed in the reference.

The runtime material approach avoids bundling upstream art; it does not grant permission to redistribute the upstream mods or their resources. See `THIRD_PARTY_NOTICES.md` for source projects and license terms.
