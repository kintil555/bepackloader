package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import io.github.kintil555.bedrockpackloader.util.PathNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Resolves the geometry, texture and animation files of a Bedrock client entity. */
public final class BedrockGeoModel extends GeoModel<BedrockEntityAnimatable> {
    private final BedrockAssetIndex index;
    private final ClientEntityDefinition definition;
    private final Identifier model;
    private final @Nullable Identifier texture;
    private final List<Identifier> animationFiles;

    private BedrockGeoModel(BedrockAssetIndex index, ClientEntityDefinition definition, Identifier model, @Nullable Identifier texture,
                            List<Identifier> animationFiles) {
        this.index = index;
        this.definition = definition;
        this.model = model;
        this.texture = texture;
        this.animationFiles = animationFiles;
    }

    /**
     * Builds the model of {@code definition}, or returns empty when its geometry or animations cannot be resolved
     * (the vanilla renderer is then kept).
     */
    public static Optional<BedrockGeoModel> create(BedrockAssetIndex index, ClientEntityDefinition definition) {
        String geometryId = definition.geometry().get("default");
        if (geometryId == null && !definition.geometry().isEmpty()) {
            geometryId = definition.geometry().values().iterator().next();
        }
        if (geometryId == null) {
            return Optional.empty();
        }
        Optional<Identifier> model = index.geometry(geometryId);
        if (model.isEmpty()) {
            return Optional.empty();
        }

        Set<Identifier> files = new LinkedHashSet<>();
        for (String animationId : definition.animations().values()) {
            index.animation(animationId).ifPresent(ref -> files.add(ref.file()));
        }
        if (files.isEmpty()) {
            return Optional.empty();
        }

        String texturePath = definition.textures().get("default");
        if (texturePath == null && !definition.textures().isEmpty()) {
            texturePath = definition.textures().values().iterator().next();
        }
        Identifier texture = texturePath == null ? null
                : Identifier.fromNamespaceAndPath(index.namespace(), PathNames.toIdentifierPath(texturePath) + ".png");
        return Optional.of(new BedrockGeoModel(index, definition, model.get(), texture, new ArrayList<>(files)));
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        RenderPass pass = renderState.getGeckolibData(BedrockDataTickets.PASS);
        return pass != null ? pass.model() : this.model;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        RenderPass pass = renderState.getGeckolibData(BedrockDataTickets.PASS);
        if (pass != null && pass.texture() != null) {
            return pass.texture();
        }
        if (renderState instanceof AvatarRenderState avatar) {
            return avatar.skin.body().texturePath();
        }
        if (this.texture == null) {
            throw new IllegalStateException("Bedrock entity " + this.definition.identifier() + " has no texture");
        }
        return this.texture;
    }

    @Override
    public Identifier getAnimationResource(BedrockEntityAnimatable animatable) {
        return this.animationFiles.get(0);
    }

    @Override
    public Identifier[] getAnimationResourceFallbacks(BedrockEntityAnimatable animatable) {
        return this.animationFiles.subList(1, this.animationFiles.size()).toArray(new Identifier[0]);
    }

    @Override
    public void addAdditionalStateData(BedrockEntityAnimatable animatable, @Nullable Object relatedObject, GeoRenderState renderState) {
        if (relatedObject instanceof Entity entity) {
            renderState.addGeckolibData(BedrockDataTickets.ENTITY_ID, entity.getId());
            renderState.addGeckolibData(BedrockDataTickets.SNAPSHOT,
                    EntityQueryCapture.capture(entity, renderState.getPartialTick(), this.index.settings()));
        }
    }
}
