# Bedrock Pack Loader

Fabric mod for Minecraft Java 26.2 that loads Minecraft Bedrock resource packs directly, instead of porting them by hand.

## Status: ~50%

Implemented:
- `.brarchive` reader (verified on 17,465 entries of a real pack)
- Pack discovery (`bedrock_packs/` and `resourcepacks/`): folders, `.zip`, `.mcpack`; each subpack is a selectable pack
- Base + subpack overlay, translation-key pack names
- Texture translation: Bedrock to vanilla Java names (alias tables + rules), layout-size guard, TGA to PNG, flipbook to `.mcmeta`
- Geometry: split multi-geometry files for GeckoLib, normalise unsupported format versions
- Animation files exposed to GeckoLib; identifier indices for geometry, animations, animation controllers, client entities

Not yet (remaining ~50%): entity renderer binding, Molang-driven animation controller playback, render controllers, attachables, particles, sounds, UI, PBR, legacy 1.8.0 geometry.

## Build

Java 25. Put `geckolib-fabric-26_2-5_5_5.jar` into `libs/` (or let Gradle use the Cloudsmith repo), then `gradle build`.

## Use

Drop a Bedrock pack into `<game dir>/bedrock_packs/`, then enable it in the resource pack screen.

Bedrock packs are third-party content: never commit them.

## License

MIT
