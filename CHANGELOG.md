# Changelog

## Unreleased
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
