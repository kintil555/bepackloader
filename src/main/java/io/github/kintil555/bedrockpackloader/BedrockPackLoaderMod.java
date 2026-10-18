package io.github.kintil555.bedrockpackloader;

import com.mojang.logging.LogUtils;
import io.github.kintil555.bedrockpackloader.animation.BedrockEntityBinding;
import io.github.kintil555.bedrockpackloader.convert.TextureMapper;
import io.github.kintil555.bedrockpackloader.pack.BedrockPackRepositorySource;
import io.github.kintil555.bedrockpackloader.screen.TitleHelpButton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import org.slf4j.Logger;

public final class BedrockPackLoaderMod implements ClientModInitializer {
    public static final String MOD_ID = "bedrockpackloader";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PACK_FOLDER = "bedrock_packs";

    private static RepositorySource packSource;

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        try {
            Files.createDirectories(gameDir.resolve(PACK_FOLDER));
        } catch (IOException ex) {
            LOGGER.warn("Could not create the {} folder", PACK_FOLDER, ex);
        }

        BedrockEntityBinding.install();
        TitleHelpButton.register();
        ResourceLoader.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(Identifier.fromNamespaceAndPath(MOD_ID, "bedrock_packs"), BedrockPackRegistry.INSTANCE);
        LOGGER.info("Bedrock Pack Loader ready. Put Bedrock packs (.mcpack, .zip or folders) into <game dir>/{}", PACK_FOLDER);
    }

    /** The shared repository source, created on first use and injected into the client pack repository by a mixin. */
    public static synchronized RepositorySource packSource() {
        if (packSource == null) {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            packSource = new BedrockPackRepositorySource(
                    List.of(gameDir.resolve(PACK_FOLDER), gameDir.resolve("resourcepacks")), TextureMapper.createBundled());
        }
        return packSource;
    }
}
