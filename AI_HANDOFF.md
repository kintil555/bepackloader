# AI HANDOFF NOTES

> **PURPOSE OF THIS FILE:** context for an AI assistant that continues this project in a new session.
> It is a *status log and knowledge base*, not a task list to redo.
> **Everything under "DONE" is finished. Do not re-implement or re-analyse it.** Start from "NEXT".
> Humans may read it too, but it is written for AI. Update it at the end of every work session.

Last updated: 2026-10-18 (milestone 0.6.0-dev: Molang evaluator added, roughly 55% of the planned scope).

## 0. User preferences (apply these)

- Reply in Indonesian, concise, no filler.
- Always web-search things that are unfamiliar or may be outdated (Minecraft 26.2 APIs are new).
- Minecraft 26.2 is unobfuscated (Mojang names, no Yarn/mappings). Decompile with **Vineflower** (1.12.0). Do not spend time on irrelevant code.
- No placeholder code, no TODO comments. Prefer minimal edits (`str_replace`) over rewrites for small changes.
- Never commit Bedrock packs (third-party content). `.gitignore` already blocks them.

## 1. Goal

Fabric client mod for Minecraft Java **26.2** (Java 25) that loads **Bedrock resource packs directly** (instead of manual porting), reads `.brarchive` containers, and translates Bedrock geometry and animations for **GeckoLib 5.5.5**.
Related private project: `kintil555/actionandstuffport` (manual port of the same kind of pack's player animations).

## 2. DONE (verified = actually run; compiled = only built with javac)

| Area | Class(es) | Status |
|---|---|---|
| brarchive reader | `brarchive/BrArchive` | Format **verified** in Python on 17,465 entries of a real pack, 0 errors. Java code compiled only. |
| Pack on disk (folder/zip/.mcpack) | `pack/BedrockPackRoot` | compiled |
| Manifest, `.lang` | `pack/BedrockManifest`, `pack/LangFile` | compiled |
| Effective file tree (brarchive expansion + base/subpack overlay) | `pack/BedrockPackFiles` | compiled |
| Translation to Java resources | `pack/PackTranslator`, `pack/VirtualResources`, `pack/TranslationReport` | compiled |
| Texture name mapping + layout guard | `convert/TextureMapper`, `TextureAliases`, `VanillaTextureIndex`, resources `mappings/*` | alias targets validated against vanilla list; code compiled |
| TGA to PNG, PNG writer | `convert/TgaDecoder`, `PngEncoder` | compiled |
| Flipbook to `.mcmeta` | `convert/FlipbookIndex` | compiled |
| Geometry split + version normalisation | `convert/GeometrySplitter` | compiled |
| Identifier indices (geometry, animation, controller, client entity) | `index/*` | compiled |
| Java pack integration | `pack/BedrockPackResources`, `pack/BedrockPackRepositorySource`, `mixin/client/PackRepositoryMixin`, `BedrockPackRegistry`, `BedrockPackLoaderMod` | compiled |

| Molang evaluator | `molang/*` (`MolangExpression`, `MolangScope`, `MolangContext`, `MolangMath`, `MolangParser`, `Node`) | **Verified**: `MolangTest` (15 tests, run with plain javac + stub JUnit, JDK 25) and all 11,447 Molang strings of the test pack parse (0 failures, ~0.5 s). No Minecraft dependency. |

### NOT yet run anywhere
- `gradle build` (sandbox cannot reach maven.fabricmc.net; **CI is `.github/workflows/build.yml` (JDK 25, `./gradlew build`, uploads jar + test reports), same layout as MinimalsClient. Let CI build, do not try locally.** Gradle wrapper 9.5.1 is committed.)
- end-to-end translation of a real pack
- in-game launch
- unit tests: **`src/test/java` is empty** (JUnit is declared in `build.gradle` but no tests exist yet)

## 3. Verified facts (do not re-investigate)

### brarchive format (little-endian)
- Header 16 bytes: `u64 magic 0x267052A0B125277D`, `u32 entryCount`, `u32 version (=1)`.
- Entry table: 256 bytes each: `u8 nameLen`, 247 bytes name (zero padded), `u32 offset`, `u32 length`. Offsets are relative to the end of the table (`16 + 256*count`).
- Mapping: `__brarchive/<path>.brarchive` expands into folder `<path>/` (e.g. `__brarchive/textures/blocks.brarchive` -> `textures/blocks/*`, `models/entity.brarchive` -> `models/entity/*`). Subpacks have their own `subpacks/SPx/__brarchive/`.
- Entry names inside archives are often **obfuscated** (`75fe53c1.json`, `efm.json`), so indexing must use file *contents* (identifiers), never file names.

### GeckoLib 5.5.5 (decompiled)
- Requires Java >= 25, fabricloader >= 0.19, fabric-api >= 0.152.1+26.2, minecraft >= 26.2. Package `com.geckolib`.
- `GeckoLibResources` loads from the resource manager: `<ns>:geckolib/models/**.json` and `<ns>:geckolib/animations/**.json`. Cache key = path with prefix and `(.geo|.animation(s))?.json` stripped.
- It reads Bedrock format natively (`Geometry`, `ActorAnimations`), so animation files are passed through unmodified.
- **It bakes only `definitions[0]`** of a geo file. Multi-geometry files (up to 474 per file in the test pack) MUST be split. Done in `GeometrySplitter`.
- Supported geo `format_version`: 1.12.0, 1.14.0, 1.16.0, 1.19.30, 1.21.0. Test pack also has `1.26.10` (unsupported); we map it to 1.21.0 (**assumption: same schema, unverified at runtime**).
- Legacy 1.8.0 geometry (`geometry.x` top-level keys) is unsupported by GeckoLib and by us (the test pack has none).
- Maven coordinates unverified. Cloudsmith repo `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` has artifact `geckolib-fabric-26.2`, but the group id `com.geckolib` is a guess. `build.gradle` prefers a jar in `libs/`; the GeckoLib jar (MIT) is now **committed** at `libs/geckolib-fabric-26_2-5_5_5.jar` so CI does not depend on the unverified Cloudsmith coordinates.

### Minecraft 26.2 API facts
- Loom plugin id `net.fabricmc.fabric-loom` (non-remap), Loom 1.15.2, Gradle 9.5.1 (wrapper), Fabric API 0.157.0+26.2, loader 0.19.3: **same set as `kintil555/MinimalsClient`, which builds on 26.2** (the earlier 1.17-SNAPSHOT/0.161.0/0.19.5 values were unverified and were replaced). No `mappings` line. Repositories (Fabric maven, Maven Central) must be declared in `build.gradle`, `pluginManagement` only covers plugins.
- `ResourceLocation` is now `net.minecraft.resources.Identifier` (`fromNamespaceAndPath`).
- Fabric API `ResourceLoader` (v1) has **no** hook for dynamic/user packs (only `registerBuiltinPack` for mod-bundled ones). Hence the mixin on `PackRepository.<init>` that appends `BedrockPackRepositorySource` when a `ClientPackSource` is among the sources.
- Custom pack = implement `PackResources` and create via `Pack.readMetaAndCreate(PackLocationInfo, Pack.ResourcesSupplier, PackType, PackSelectionConfig)`. `getMetadataSection` returns `PackMetadataSection(description, SharedConstants.getCurrentVersion().packVersion(CLIENT_RESOURCES).minorRange())`.

### Molang facts (verified on the test pack)
- **GeckoLib's `MathParser` cannot be used for Bedrock Molang**: its regex rejects `'` (strings), `[`/`]` and it lower-cases input; the pack has ~19,000 string literals (`q.is_item_name_any('slot.weapon.mainhand', ...)`), 617 `??`, 615 `array.x[...]`, `loop`, `break`, `continue`, `return`, `cond ? { ...; return X; }` blocks, `this`, `a->b`, `1.0E-4`. Hence our own evaluator in `molang/`.
- Query usage in the pack: 168 distinct `q.*`. Top: anim_time, life_time, is_item_name_any, is_in_ui, entity_biome_has_any_identifier, state_time, is_on_ground, equipped_item_any_tag, modified_move_speed, is_pack_setting_selected, is_name_any, target_x/y_rotation, any_animation_finished, is_baby, in_range, is_in_water, delta_time, vertical_speed, is_attached, is_riding, graphics_mode_is_any, body_y_rotation, position, ground_speed, ...
- `this` is parsed as context value `"this"` (host must `scope.setContextValue("this", currentChannelValue)` before evaluating a keyframe). `a->b` evaluates to 0 (unsupported). Unsupported queries evaluate to 0 and are reported once per scope via `MolangContext.unsupportedQuery`.
- Semantics implemented: multi-statement expressions yield 0 unless `return` runs; single expression yields its value; unset variable = 0 for arithmetic but `null` for `??`; `x/0 = 0`; trig in degrees; array index floored+clamped (**assumption**, Bedrock behaviour for out-of-range not verified); loop capped at 1024 iterations.
- The test pack's player `minecraft:client_entity` (`__brarchive/entity.brarchive`, obfuscated name) has 26 geometries, 32 textures, 610 animation/controller aliases, 14 render controllers (conditional on `v.is_first_person`, `q.is_spectator`, ...), `scripts.pre_animation/initialize/animate/scale/variables`, `enable_attachables`. Pack settings are read through `q.is_pack_setting_selected/enabled('ns:id', 'option')` (manifest `settings`).
- The pack zip has a single top-level folder (`pack.name/manifest.json`); `BedrockPackRoot.ZipRoot` has prefix handling (not run).

### Test pack findings (Oreville "Actions & Stuff", used only locally)
- Manifest format 3, subpacks SP0 (Custom), SP1 (Vanilla textures + custom animations), SP2 (Full). Name is a translation key `pack.name` resolved from `texts/en_US.lang`.
- Base `__brarchive`: 126 client entities (incl. `minecraft:player`), 2,623 animation defs in 13 files, 551 geo files (up to ~1.8 MB each), 80 render controllers, 1,529 attachables.
- Many entity textures are `.tga`. PBR extras (`_mer`, `_mers`, `_normal`, `_heightmap`, `.texture_set.json`) exist and are skipped.
- Name overlap with Java is partial (about 42% of block and 41% of item texture names match exactly); the rest needs alias rules (implemented partially, see below).
- Controllers use Molang queries such as `q.modified_move_speed`, `q.has_target`, custom `v.*` variables, `blend_transition`.

## 4. Design decisions

- Pack is exposed as a **virtual Java resource pack**, one selectable pack per (pack, subpack): id `bedrock/<slug>[/<subpack>]`, namespace `bedrock_<slug>`.
- Everything is exposed under the pack namespace with original paths (lower-cased, sanitised via `PathNames.toIdentifierPath`). Textures matching a vanilla texture are *also* exposed under `minecraft:` (this is what re-skins the game).
- A Bedrock texture replaces a vanilla one only if the name maps **and** the size is layout-compatible (equal, integer upscale, or animated strip of square frames). Reason: avoids breaking entity UV layouts.
- Later layers win on identical identifiers (subpack over base): files are processed with `pathsInPriorityOrder()`.
- Translation is lazy (first resource access) and memoised per candidate; zips stay open (`BedrockPackRepositorySource` caches by path + mtime + size).
- Alias tables are data (`mappings/block_aliases.json`, `item_aliases.json`: `exact` + `families` with `{wood}`, `{color}`, `{tier}` placeholders). Extend the JSON, not the code.
- `vanilla_textures.txt` (`path width height`) was generated from the 26.2 client jar; regenerate when the target MC version changes.

## 5. NEXT (remaining ~50%), suggested order

1. **Run reality checks first** (cheap, highest value): `gradle build`; translate the test pack in a harness and read `TranslationReport`; launch the client and check the pack list. Fix whatever breaks.
2. Unit tests in `src/test/java` (BrArchive with a synthetic archive, TextureMapper, TgaDecoder, GeometrySplitter, controller parsing). Do not use real pack content.
3. **Entity binding** (next; Molang is ready to be used for `scripts.*`, controllers and render controllers; GeckoLib 5.5.5 facts: `GeoReplacedEntityRenderer<T extends GeoAnimatable, E extends Entity, R extends EntityRenderState>(Context, GeoModel<T>, T animatable)`, `GeoModel` needs `getModelResource(GeoRenderState)`, `getTextureResource(GeoRenderState)`, `getAnimationResource(T)`; animatable must implement `GeoReplacedEntity` (singleton, not an Entity); player renderer in MC 26.2 is `net.minecraft.client.renderer.entity.player.AvatarRenderer`, entity class `net.minecraft.world.entity.Avatar`; renderers are built at resource reload from `EntityRenderers` providers, so a provider must delegate to the vanilla renderer when no Bedrock definition applies): map `ClientEntityDefinition` (geometry/texture/animation short names) to a GeckoLib renderer for the matching vanilla entity type. Check how GeckoLib 5 renderers are built (decompile `com.geckolib.renderer`, `com.geckolib.model`), then the player first (see `actionandstuffport` for known player animation pitfalls).
4. **Animation controller playback**: translate `AnimationControllerDefinition` (states, transitions, blend, on_entry/on_exit) to GeckoLib animation controllers; evaluate Molang via GeckoLib's `MathParser` (`com.geckolib.loading.math`); implement the `q.*` / `v.*` query set used by the pack.
5. Render controllers (`render_controllers`), attachables (held/armor items), particles, sounds (`sounds.json` / `sound_definitions.json`), UI textures, PBR (`_mer`/`_normal`) if shaders allow.
6. Extend alias tables to raise vanilla coverage; consider `terrain_texture.json` / `item_texture.json` for authoritative names.
7. Optional: legacy 1.8.0 geometry conversion, in-game pack settings (Bedrock `settings` in manifest are currently ignored).

## 6. Unverified assumptions (check these when something fails)

- `ResourceManager.listPacks()` returns our `BedrockPackResources` instances unwrapped (used by `BedrockPackRegistry`). Compiled, not run.
- Static `@ModifyVariable` on `PackRepository.<init>` (argsOnly, HEAD) applies correctly under Fabric's Mixin.
- GeckoLib accepts geo version `1.21.0` for files that were `1.26.10`.
- `FolderRepositorySource` ignores Bedrock zips in `resourcepacks/` without breaking (it may log a warning).
- Gson in MC 26.2 treats `JsonParser.parseReader` as lenient (comments in Bedrock JSON). True for 2.10 and 2.11+ by documentation, not run.

## 7. Sandbox tips (if working in a restricted environment)

- Reachable: github.com, api.github.com (rate-limited), raw/release assets, npm, PyPI, Ubuntu apt. **Not reachable:** maven.fabricmc.net, Maven Central, libraries.minecraft.net.
- MC 26.2 jar for reference: repo `kintil555/mc-version-jar`, branch `26.2-fabric` (file `26_2-0_19_5.jar`, client jar, ~39 MB).
- Compile-check without Gradle: JDK 25 via apt (`openjdk-25-jdk-headless`), classpath = MC jar + nested jars of Fabric API (from GitHub release `0.161.0+26.2`) + apt libs (gson, guava, slf4j, fastutil) + hand-written stubs for fabric-loader, Mixin annotations, jspecify, `com.mojang.logging.LogUtils`, `com.mojang.brigadier.Message`.
- Running code that touches `Identifier` needs DFU (missing): use a stub with the same validation rules (`[a-z0-9_.-]` namespace, `[a-z0-9/._-]` path).
- Vineflower: `java -jar vineflower-1.12.0.jar -e=<library jar> <input dir> <output dir>`.

## 8. Update protocol

At the end of a session: move finished items to DONE with their verification level, adjust NEXT, record new verified facts in section 3 and new doubts in section 6.
