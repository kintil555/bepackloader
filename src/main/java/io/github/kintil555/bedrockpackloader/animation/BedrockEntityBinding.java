package io.github.kintil555.bedrockpackloader.animation;

import com.geckolib.animation.state.ControllerState;
import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.renderer.base.GeoRenderer;
import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import io.github.kintil555.bedrockpackloader.BedrockPackRegistry;
import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * Binds the Bedrock {@code minecraft:player} client entity of the active packs to a GeckoLib renderer. The renderer
 * is created lazily from the {@link EntityRendererProvider.Context} captured when the avatar renderers are built and
 * is dropped on every resource reload.
 */
public final class BedrockEntityBinding {
    private static final String PLAYER = "minecraft:player";

    private static volatile EntityRendererProvider.@Nullable Context context;
    private static volatile @Nullable Resolved resolved;

    private BedrockEntityBinding() {
    }

    private record Resolved(@Nullable GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> renderer) {
    }

    /** Called when the vanilla avatar renderers are (re)built. */
    public static void captureContext(EntityRendererProvider.Context newContext) {
        context = newContext;
        resolved = null;
    }

    /** Called on resource reload: the packs may have changed. */
    public static void reset() {
        resolved = null;
    }

    /** Installs the Molang scope lookup GeckoLib uses for sanitised keyframe expressions. */
    public static void install() {
        MolangVariableBridge.setScopeProvider(BedrockEntityBinding::scopeOf);
    }

    private static MolangScope scopeOf(ControllerState state) {
        MolangScope scope = state.renderState().getGeckolibData(BedrockDataTickets.SCOPE);
        return scope != null ? scope : new MolangScope(MolangContext.EMPTY);
    }

    /** The GeckoLib renderer for players, or null when no active Bedrock pack defines a usable player. */
    public static @Nullable GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> playerRenderer() {
        Resolved current = resolved;
        if (current == null) {
            EntityRendererProvider.Context ctx = context;
            if (ctx == null) {
                return null;
            }
            current = new Resolved(createPlayerRenderer(ctx));
            resolved = current;
        }
        return current.renderer();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addHeldItemLayer(GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> renderer,
                                         EntityRendererProvider.Context ctx) {
        ((GeoReplacedEntityRenderer) renderer).withRenderLayer(new BedrockHeldItemLayer(ctx, (GeoRenderer) renderer));
    }

    private static @Nullable GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> createPlayerRenderer(
            EntityRendererProvider.Context ctx) {
        List<BedrockAssetIndex> indices = BedrockPackRegistry.INSTANCE.activeIndices();
        for (int i = indices.size() - 1; i >= 0; i--) {
            BedrockAssetIndex index = indices.get(i);
            Optional<ClientEntityDefinition> definition = index.entity(PLAYER);
            if (definition.isEmpty()) {
                continue;
            }
            Optional<BedrockGeoModel> model = BedrockGeoModel.create(index, definition.get());
            if (model.isEmpty()) {
                BedrockPackLoaderMod.LOGGER.warn("Bedrock pack '{}' defines minecraft:player but its geometry or animations cannot be resolved; keeping the vanilla player",
                        index.namespace());
                continue;
            }
            BedrockPackLoaderMod.LOGGER.info("Bedrock pack '{}' now renders the player", index.namespace());
            GeoReplacedEntityRenderer<BedrockEntityAnimatable, Avatar, AvatarRenderState> renderer =
                    new GeoReplacedEntityRenderer<>(ctx, model.get(), new BedrockEntityAnimatable(definition.get(), index));
            addHeldItemLayer(renderer, ctx);
            return renderer;
        }
        return null;
    }
}
