package io.github.kintil555.bedrockpackloader;

import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;
import io.github.kintil555.bedrockpackloader.pack.BedrockPackResources;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Tracks which Bedrock packs are active in the current resource manager so other systems (entity rendering,
 * animation playback) can look up Bedrock identifiers.
 */
public final class BedrockPackRegistry implements ResourceManagerReloadListener {
    public static final BedrockPackRegistry INSTANCE = new BedrockPackRegistry();

    private volatile List<BedrockAssetIndex> active = List.of();

    private BedrockPackRegistry() {
    }

    /** Indices of the active Bedrock packs, lowest priority first. */
    public List<BedrockAssetIndex> activeIndices() {
        return this.active;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        List<BedrockAssetIndex> indices = resourceManager.listPacks()
                .filter(BedrockPackResources.class::isInstance)
                .map(pack -> ((BedrockPackResources) pack).translated().index())
                .toList();
        this.active = indices;

        for (BedrockAssetIndex index : indices) {
            BedrockPackLoaderMod.LOGGER.info("Bedrock pack '{}' active: {} geometries, {} animations, {} controllers, {} client entities",
                    index.namespace(), index.geometryCount(), index.animationCount(), index.controllerCount(), index.entityCount());
        }
    }
}
