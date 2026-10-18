# Changelog

## Unreleased
- Built-in player poses the pack references but does not define (`animation.player.riding.arms`, `.riding.legs`, `.sleeping`, `.look_at_target.ui`) are generated (`BuiltinAnimations`); riding uses the Java model's constant offsets (arms -36 deg, legs -81 deg), sleeping and the UI pose stay at rest. Added to the translated pack only when the pack lacks them.
- Cape: the `cape` texture slot of a render controller now uses the player's real cape texture (the pack only names `cape_invisible`, a placeholder the engine replaces in Bedrock) and draws nothing when the player has no cape or it is hidden.
- More vanilla texture coverage: item aliases for bundles (all colours, open/back/front), harnesses, music discs and 16 single items (`totem`, `reeds`, `sign*`, `door_wood`...); blocks named `<name>_0` map to the Java `<name>` texture. Test pack SP2: 1,478 -> 1,606 textures mapped (items 519 -> 613, blocks 877 -> 911). The rest is mostly Bedrock-only (UI, particle sheets, pottery patterns, `carried/` icons) or has no 1:1 Java texture.
- Fix animations/models rejected by GeckoLib (seen in the log: 5 of 14 animation files and ~560 of 3,000 models failed to load): `relative_to` objects become strings; effect arrays at one timestamp are spread over nearby timestamps; timeline statement arrays are joined with `;`; bones whose parent lives in another geometry become top-level; cubes without `uv` get `[0,0]`. Checked with the real GeckoLib 5.5.5 deserializers on the test pack: 13 of 14 animation files load (the 5.7 MB one could not be run in the sandbox, missing DFU classes).
- Add built-in Bedrock geometries `geometry.humanoid`, `.humanoid.custom`, `.humanoid.customSlim`, `geometry.cape` (`BuiltinGeometry`): the pack's `minecraft:player` references them without defining them, which made the player binding fail ("geometry or animations cannot be resolved").
- Fix: textures/animations were empty in game ("PNG header missing", 0 textures mapped). Real packs ship zero-length `.brarchive` stub entries next to the loose files; they replaced the real files. `BedrockPackFiles.expand` now skips zero-length entries. Test `BedrockPackFilesTest`.
- Fix: `.tga` files that actually contain PNG data (65 in the test pack) are passed through; type is detected from content, not extension.
- Title screen: "?" button in the first free slot right of the small icon row (late Fabric phase, avoids Mod Menu's button) opens `BedrockHelpScreen` (accepted formats, folders, how to enable; en_us and id_id).
- Held items: `BedrockHeldItemLayer` draws main/off-hand items on the Bedrock player through the bones `rightItem` / `leftItem` (bones missing from the model are skipped).  Bone names are matched case-insensitively.
- Render controller `part_visibility` (bone patterns with wildcards, Molang conditions) applied per pass.
- Render controllers: parse `render_controllers/`, evaluate geometry/texture selection (arrays, conditions) and draw one pass per active controller for the player.
- Controller timing queries (`state_time`, `anim_time`, `any_animation_finished`) and more entity queries (move speed, position, name, held item and item tag).
- Wire the Bedrock player into rendering: mixins on `EntityRenderers.createAvatarRenderers`, `AvatarRenderer.extractRenderState` and `LivingEntityRenderer.submit` delegate to a lazily built GeckoLib renderer (`BedrockEntityBinding`); Molang scope provider installed; binding reset on resource reload.
- Add pack settings (manifest defaults for `q.is_pack_setting_*`), entity query snapshot, controller state machine and the GeckoLib animatable/model/runtime classes for entity binding (not wired in yet).
- Sanitise Bedrock keyframe Molang that GeckoLib rejects (quotes, brackets, `??`): expressions become `variable.bpl_N` evaluated by the own Molang evaluator, so those animation files no longer fail to load. Tests in `AnimationSanitizerTest`.
- Add Gradle wrapper (9.5.1), CI workflow aligned with MinimalsClient, Maven repositories, committed GeckoLib 5.5.5 jar (MIT); use Loom 1.15.2 / Fabric API 0.157.0+26.2 / loader 0.19.3.
- Add Bedrock Molang evaluator (`molang` package): strings, `??`, ternary blocks, `loop`/`break`/`continue`/`return`, `math.*` incl. all easing functions, host-provided queries. Unit tests in `MolangTest`.

## 0.5.0
- Initial 50% milestone: brarchive reader, pack discovery, texture/geometry/animation translation, asset index.
