package io.github.kintil555.bedrockpackloader.pack;

import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;

/** Result of translating one Bedrock pack (for one subpack selection) into Java resources. */
public record TranslatedPack(String namespace, VirtualResources resources, BedrockAssetIndex index, TranslationReport report) {
}
