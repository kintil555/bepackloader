package io.github.kintil555.bedrockpackloader.mixin.client;

import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds the Bedrock pack source to the client's resource pack repository (and only to that one). */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static RepositorySource[] bedrockpackloader$addBedrockSource(RepositorySource[] sources) {
        for (RepositorySource source : sources) {
            if (source instanceof ClientPackSource) {
                RepositorySource[] extended = java.util.Arrays.copyOf(sources, sources.length + 1);
                extended[sources.length] = BedrockPackLoaderMod.packSource();
                return extended;
            }
        }
        return sources;
    }
}
