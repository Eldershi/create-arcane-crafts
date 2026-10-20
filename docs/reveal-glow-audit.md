# Reveal glow rendering audit — Forge 1.20.1

This audit is based on the addon sources and the exact locally cached Forge 47.4.10 mapped Minecraft client. It separates the accepted WebGL appearance from Minecraft's render pipeline. Native shader-off checks were subsequently run as recorded below; no shader-pack compatibility run is claimed.

## Findings and implementation choice

The reveal should stay inside the block entity renderer (BER): deterministic seeded cube geometry, dissolve dust, item silhouette, completion flecks and runes, all driven by the synced operation clock. The normal item stays on the native item/book renderer. This keeps the special animation at its chamber and prevents a second client-only particle lifetime from disagreeing with item consumption, unload, cancellation or consecutive upgrades.

Use two explicitly bounded render states for the effect geometry:

| Pass | Shader | Blend | Depth | Write mask | Purpose |
| --- | --- | --- | --- | --- | --- |
| Core | vanilla eyes shader | SRC_ALPHA, ONE_MINUS_SRC_ALPHA | LEQUAL | colour only | Unlit, sharp coloured cube faces, pixel silhouette, flecks, runes |
| Halo | vanilla eyes shader | SRC_ALPHA, ONE | LEQUAL | colour only | One small low-opacity local glow shell, fading with the same alpha envelope |

The core can use no culling for thin silhouette/sprite geometry. Volumetric cubes should normally emit only outward faces, or cull back faces, to avoid doubling their translucency. Use a cached render type for each fixed texture and blend mode; do not construct new render types in a frame loop. Use NEW_ENTITY vertex format to match the vanilla eyes shader and the normal Forge entity-buffer path, supplying UV, colour, overlay and full-bright UV2 even though the eyes shader does not sample the latter two.

LEQUAL keeps walls, casing, basin and ordinary items in front of hidden effects. Do not use NO_DEPTH_TEST, outline-see-through, or disable the depth test to create “glow”. Colour-only depth writes keep translucent glows from cutting rectangular holes in the effects behind them. Alpha-blended passes still have the normal Minecraft limitations when interleaved with water/glass or other render types; sort quads within a cached type and validate from both sides. Do not promise order-independent transparency.

The accepted “particle itself shines, halo weak” request maps to a bright unlit core and an extremely weak secondary halo. Keep nearest sampling on authored pixel textures. Only the halo texture should have a short soft alpha ramp; never blur the actual rune glyph or pixel item mask. The silhouette should be generated from the item's alpha coverage, not its opaque rectangular texture area.

## Exact native API evidence

The following client classes were extracted from the cached Forge development client and independently decompiled in a temporary audit directory:

- net.minecraft.client.renderer.RenderType
- net.minecraft.client.renderer.RenderStateShard
- net.minecraft.client.particle.ParticleRenderType

Relevant mapped development artifact: `net.minecraftforge:forge:1.20.1-47.4.10_mapped_official_1.20.1`, generated in the developer's ForgeGradle cache from the pinned Forge dependency. The cache location is machine-specific and is not a build requirement.

The actual shaders were read from the 1.20.1 vanilla client-extra.jar in that same Gradle cache, rather than inferred from newer-version documentation.

- `entityTranslucentEmissive` uses ordinary alpha blending, NO_CULL, COLOR_WRITE, overlay, default LEQUAL depth testing, and sorted QUADS. It does not sample the lightmap, but its vertex shader still calls `minecraft_mix_light`. Thus its “emissive” name means no world-lightmap dimming, not perfectly flat unlit RGB and not an additive bloom pass. It is suitable for the existing fire shell but should not be the only mechanism for a uniformly bright white silhouette.
- `RenderType.eyes` uses the eyes shader with `ADDITIVE_TRANSPARENCY`, whose blend is ONE + ONE. The eyes fragment shader multiplies RGB and alpha separately. Lowering vertex alpha alone does not reduce added RGB with ONE + ONE. Using eyes directly and fading only alpha therefore leaves bright pixels visible or over-bright. The custom halo state should instead use `LIGHTNING_TRANSPARENCY` (SRC_ALPHA + ONE), or premultiply RGB explicitly.
- `RenderStateShard.TRANSLUCENT_TRANSPARENCY` is SRC_ALPHA / ONE_MINUS_SRC_ALPHA with separate conventional alpha factors.
- `RenderStateShard.COLOR_WRITE` disables depth writes while preserving colour writes. Default depth test is LEQUAL.
- The vanilla eyes shader uses only position, colour and UV, applies fog fading, and never samples directional lighting/lightmap. Reusing this standard shader avoids introducing a custom GLSL program or framebuffer dependency.
- Native `PARTICLE_SHEET_OPAQUE` disables blending and writes depth. Native `PARTICLE_SHEET_TRANSLUCENT` enables normal alpha blending, but also writes depth. A custom BER pass with colour-only writes avoids the depth-mask behaviour for the soft local glow.
- Vanilla particle fragment shader discards alpha below 0.1; this gives a hard cutoff near the tail. BER eyes shader has no corresponding discard and can fade the dust smoothly.

## Current addon evidence and needed changes

- `ChamberVisuals` supplies FULL_BRIGHT on all emitted head, rods, fire and rune vertices. Its manual fire/runes already use `entityTranslucentEmissive`, with a smooth upper bend and a .42-block fade. Before this implementation, its manual top followed the item and had no upper block cap. The accepted preview caps both fire and runes to Y=1.0, leaving the .42 fade and .12 bend unchanged.
- `AscensionCompletionParticle` is the old 8×8 cleanse fleck spiral: opaque sheet, getLightColor=240, mixed gold/blue sprite choice, short native lifetime and a grow-in quad formula. It lacks the accepted per-state palette, fade/shrink tail, delayed high-tier-only burst and legendary runes. Reusing its current provider would regress the preview.
- 240 is maximum block-light UV with zero skylight UV. FULL_BRIGHT (0xF000F0) specifies both. Neither value adds light to nearby blocks.
- `ChamberManualClient.State` previously emits 72 completion particles over 28 ticks. Research should be rendered by the new deterministic reveal helper; generic craft completion may use the reduced count 48, preserving the original CEI/native timing as requested.
- Create's native BlazeBurnerRenderer uses full-bright model buffers for head/rods and cutout-scrolled flame geometry. It does not install a general bloom pipeline.
- The chamber block already declares normal gameplay block light through its block state. This is the actual light-engine illumination. Do not add and remove temporary world blocks to mimic coloured lights.
- `RuntimeAssetPack` recreates resources from installed vanilla/mod assets on resource reload and clears its generated cache on close. New authored masks/glyph metadata should fit that route, or read the active resource manager in a resource-reload-bound renderer. Never retain DynamicTexture objects across reload without closing them.

## WebGL bloom and compatibility limit

The approved preview's soft-glow mode renders selected bright meshes into an off-screen target, blurs horizontally and vertically, then adds that image to the main framebuffer. Full-bright Minecraft vertices alone cannot reproduce this screen-space blur.

Minecraft 1.20.1 Forge does not supply an addon-scoped bloom composition API in the normal BER path. There is no configured bloom/post-chain integration in this addon. Replacing GameRenderer's global post chain would conflict with existing effects and shader mods and is unnecessary for the requested weak particle halo.

The production baseline should therefore use depth-tested local halo geometry with the unlit core, so it works with the standard render pipeline and remains legible without shaders. A shader pack may additionally bloom the emissive material, but the amount and coloured-light behaviour belong to that pack. Reusing vanilla shader entry points and fixed-function states improves compatibility; it does not establish universal compatibility with Oculus/Iris, Fabulous transparency, or every shader pack. Those need actual client checks. Do not describe the geometry halo as a screen-space bloom implementation.

## Resource and performance requirements

- Cache item masks/cell positions and rune geometry per resource reload, not per frame. All random choices must derive from operation seed + cell/index, never frame-rate random calls.
- Cap per-chamber cube, dust and high-tier glyph counts to the approved ranges. Higher grades enable completion flecks; legendary adds twelve runes. Ordinary grades should not secretly emit the high-tier swirl.
- Reuse one or a few vertex buffers/materials. Do not flush the complete shared BufferSource or mutate RenderSystem state around buffered writes: state must be expressed in RenderType so setup/teardown occurs at the actual draw.
- Drawing helpers should take the current PoseStack and MultiBufferSource and emit vertices only. No game-data mutation during rendering. Cancelled/removed chambers should stop naturally.
- Fade and scale calculations must remain finite at zero duration, min/max cell bounds and the final frame. Restore the native output item and small idle bob after the glow tail, including repeat upgrades.

## Verification gates

Compilation and deterministic tests can prove API names, event/state wiring, no NaNs, cap height and bounded geometry counts. They do not prove the final visual pipeline.

A native client check should inspect: dark and daylight; wall occlusion; camera rotation; several simultaneous chambers; normal and reinforced colour; all grades; completion/reload/cancel/repeat-upgrade; basin closed eyes versus completed manual open eyes; Resource Reload (F3+T); Fast/Fancy and Fabulous if supported; shader off first, then any specifically installed shader configuration. The expected baseline is crisp bright pixels, tiny contained halo, and no visible geometry through an opaque wall.

## Completed native checks (2026-10-20)

The integration-stage Gradle JAR, which still contained the opt-in QA entry points, was run in the isolated Minecraft 1.20.1 / Forge 47.4.10 client with Fancy graphics, Flywheel instancing, and no shader mod. Minecraft's own `Screenshot.grab` captured the real 2560×1600 render target. Gold and cool crystal faces, seeded per-crystal colour, noisy imprint, native completion flecks, legendary runes, rare/epic grade differences, daylight/night, a complete eight-second reveal, and return to the real output were inspected. An opaque stone wall fully hid the reveal; resource reload completed and the effect rendered correctly afterwards. The local evidence in `testing/reveal-integration-20261020/` identifies this artifact as SHA-256 `c3586985895caa32f586f984c42229d13bb1804f2114a5ef128b413cf0f2d336`.

The final release separates opt-in fixtures into `prototypeJar` and excludes them from the production artifact. These original screenshots do not establish a new production artifact's hash or repeat its full visual checks. Packaging checks, prototype-server reruns and production startup smoke checks have separate artifact identities in the [0.1.0 release acceptance](releases/0.1.0.md) and its [verification record](releases/0.1.0-verification.json).

The client run also exposed an existing recipe-login failure unrelated to the glow: network-decoded Create fluid tags have no TagKey and cannot be serialized back to JSON. The network validator and JEI display cloning were corrected, then the complete 3,229-recipe login packet and native JEI view copies passed actual runtime checks before visual inspection continued.

This does not establish Fabulous, complex glass/water sorting, or third-party shader-pack compatibility. The temporary framebuffer observation mod was removed after testing and is absent from the production JAR.

## Implementation delivered in this change

- `ChamberGlowRenderTypes` implements the two states above using the existing vanilla eyes shader. The core uses conventional alpha blending and the halo SRC_ALPHA + ONE. Both use LEQUAL and colour-only writes, no see-through effect, no custom GLSL and no post-chain replacement. Its only current textures are the vanilla white texture and native block atlas. The actual vanilla 1.20.1 client-extra.jar was checked and contains `assets/minecraft/textures/misc/white.png`.
- `ChamberScrollRevealRenderer` owns the research presentation. It renders the native item's baked quads with their actual UVs, native item tint and native foil buffer. Front/back quads are subdivided once using sprite alpha, then clipped on the CPU along the advancing organic front. The output appears from logical tick 58; its alpha-union silhouette fills from bottom to top and fades over it. This is a geometry implementation of the preview mask, not a new shader or a copied upstream item image.
- The research path deliberately uses the generated item's XY model coordinates rather than calling the usual FIXED item display transform at every frame; the approved motion's quaternion already includes its native Y turn. The renderer's normal idle path still owns non-research items. The completion-to-idle alignment therefore needs the native client check. A custom-rendered item without usable baked quads falls back to ItemRenderer and cannot receive the pixel dissolve mask.
- Installed sprite alpha is read only when a cached geometry is first built. The first unique animation frame supplies the mask; atlas animation itself keeps advancing. The authored effect grid is 16×16; a higher resolution replacement texture retains its native render UV detail but its silhouette mask is limited to that grid.
- Cube faces have no wireframe, border or edge layer. Six mild face shades give volume; the stronger smooth two-scale state-colour noise gives body variation. Each crystal also has a stable seed-derived .84–1.14 brightness factor and a small hue factor, so adjacent cubes differ without frame-random flicker. The main silhouette shares the state-colour grain.
- Main dust has 28 natural dispersers; catalyst dust has 40 with the accepted five absorbers/five dispersers in each wave. Catalyst dispersers detach using the reconstructed whole-item birth pose. Absorbers use a fixed seed-selected main-item crystal anchor; the JS reference's extra “already converted at arrival” anchor-pool filter is not currently reproduced. The individual curve/lifetime/layout inputs are preserved.
- Epic enables the delayed 48-particle completion swirl. Legendary adds twelve coarse 8×11 rune glyphs, using the original stroke topology and the shared one-cell brush with connected diagonal steps. Ordinary tiers do not render the completion swirl. MINIMAL particles keeps the main cube/silhouette presentation and removes optional dust/swirl/runes; DECREASED halves optional counts.
- Completion pixels are read from the eight runtime-generated cleanse frame images and immediately released as NativeImages. The draw uses cached per-pixel RGBA geometry; crimson applies the approved .58 luminance/tint mix rather than multiplying a purple tint over blue texels. The entire burst uses one state palette. Only these completion flecks have a tiny .04 additive local halo; rune/cube/dust cores remain crisp without an additional glow fringe.

The implementation follows Minecraft's usual buffered BER draw path. It has no independent light source attached to each cube, no coloured light-engine injection and no bundled full-screen bloom. Actual scene illumination remains the chamber block's light level.

## Reproducible layout verification

This historical reference comparison needs the local approved WebGL `art/` sources and the original offline development classpath, neither of which is supplied by a normal public clone. In that prototype workspace, run `python3 build.py`, then:

```sh
python3 tools/verify_scroll_reveal_layout.py
```

The tool reflects directly into the built production `ChamberScrollRevealRenderer.Layout`; it does not copy the Java formulas. It constructs the approved primary and secondary masks for five seeds (including zero and negative signed representations), then compares every cube and dust parameter against the original approved `luminous-crystals.js` in a Node VM.

Observed result: 10 production layouts, 8,520 numeric values compared, maximum absolute difference 5.551115123125783e-17. All cases retain 28 main dust, 40 catalyst dust, exactly 20 catalyst absorbers, and .045–.082 cube edges. Output: `testing/reveal-integration-20261020/layout.json`. Temporary compiled harness/layout files stay under `build/reveal-layout-verification`. The reference directory can be overridden with `--reference`; those local WebGL source assets are not shipped in the game JAR.

The renderer and render-state helper also passed Java 17 compilation against the project's actual pinned compile classpath. These checks establish deterministic layout and API compatibility; they do not establish native GPU appearance, frame rate, occlusion across different transparent render types, shader-pack compatibility or resource-reload behaviour.
