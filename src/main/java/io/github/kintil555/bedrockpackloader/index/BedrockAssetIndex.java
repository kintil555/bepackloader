package io.github.kintil555.bedrockpackloader.index;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Lookup tables from Bedrock identifiers to the resources the translated pack exposes.
 *
 * <p>Bedrock refers to everything by identifier ({@code geometry.humanoid}, {@code animation.player.sneak}) while
 * GeckoLib refers to files. Pack authors often obfuscate file names, so this index is built from file
 * <em>contents</em>. When several files define the same identifier the one from the higher-priority layer wins.
 */
public final class BedrockAssetIndex {
    /** A GeckoLib animation: the animation file and the animation's name inside it. */
    public record AnimationRef(Identifier file, String name) {
    }

    private final String namespace;
    private final Map<String, Identifier> geometry;
    private final Map<String, AnimationRef> animations;
    private final Map<String, AnimationControllerDefinition> controllers;
    private final Map<String, ClientEntityDefinition> entities;

    private BedrockAssetIndex(Builder builder) {
        this.namespace = builder.namespace;
        this.geometry = Map.copyOf(builder.geometry);
        this.animations = Map.copyOf(builder.animations);
        this.controllers = Map.copyOf(builder.controllers);
        this.entities = Map.copyOf(builder.entities);
    }

    public static Builder builder(String namespace) {
        return new Builder(namespace);
    }

    /** Namespace that holds the pack's own assets, e.g. {@code bedrock_my_pack}. */
    public String namespace() {
        return this.namespace;
    }

    /** The GeckoLib model id (as used with GeckoLib's model cache) for a Bedrock geometry identifier. */
    public Optional<Identifier> geometry(String geometryId) {
        return Optional.ofNullable(this.geometry.get(geometryId));
    }

    public Optional<AnimationRef> animation(String animationId) {
        return Optional.ofNullable(this.animations.get(animationId));
    }

    public Optional<AnimationControllerDefinition> controller(String controllerId) {
        return Optional.ofNullable(this.controllers.get(controllerId));
    }

    public Optional<ClientEntityDefinition> entity(String entityIdentifier) {
        return Optional.ofNullable(this.entities.get(entityIdentifier));
    }

    public Map<String, ClientEntityDefinition> entities() {
        return this.entities;
    }

    public int geometryCount() {
        return this.geometry.size();
    }

    public int animationCount() {
        return this.animations.size();
    }

    public int controllerCount() {
        return this.controllers.size();
    }

    public int entityCount() {
        return this.entities.size();
    }

    public static final class Builder {
        private final String namespace;
        private final Map<String, Identifier> geometry = new LinkedHashMap<>();
        private final Map<String, AnimationRef> animations = new LinkedHashMap<>();
        private final Map<String, AnimationControllerDefinition> controllers = new LinkedHashMap<>();
        private final Map<String, ClientEntityDefinition> entities = new LinkedHashMap<>();

        private Builder(String namespace) {
            this.namespace = namespace;
        }

        /** Later calls replace earlier ones with the same key, so register low-priority files first. */
        public Builder geometry(String geometryId, Identifier model) {
            this.geometry.put(geometryId, model);
            return this;
        }

        public Builder animation(String animationId, AnimationRef ref) {
            this.animations.put(animationId, ref);
            return this;
        }

        public Builder controller(AnimationControllerDefinition controller) {
            this.controllers.put(controller.id(), controller);
            return this;
        }

        public Builder entity(ClientEntityDefinition entity) {
            this.entities.put(entity.identifier(), entity);
            return this;
        }

        public BedrockAssetIndex build() {
            return new BedrockAssetIndex(this);
        }
    }
}
