package io.github.kintil555.bedrockpackloader.pack;

import java.io.InputStream;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A Java resource pack backed by a translated Bedrock pack. Translation is deferred until the first resource is
 * requested, so listing packs in the selection screen stays cheap.
 */
public final class BedrockPackResources implements PackResources {
    private final PackLocationInfo location;
    private final Supplier<TranslatedPack> translation;
    private final Component description;
    private final @Nullable IoSupplier<InputStream> icon;

    public BedrockPackResources(PackLocationInfo location, Supplier<TranslatedPack> translation, Component description,
                                @Nullable IoSupplier<InputStream> icon) {
        this.location = location;
        this.translation = translation;
        this.description = description;
        this.icon = icon;
    }

    public TranslatedPack translated() {
        return this.translation.get();
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return path.length == 1 && PACK_ICON.equals(path[0]) ? this.icon : null;
    }

    private static final String PACK_ICON = "pack.png";

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return type == PackType.CLIENT_RESOURCES ? translated().resources().get(location.getNamespace(), location.getPath()) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type == PackType.CLIENT_RESOURCES) {
            translated().resources().listUnder(namespace, directory,
                    (path, supplier) -> output.accept(Identifier.fromNamespaceAndPath(namespace, path), supplier));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? translated().resources().namespaces() : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
        if (type.equals(PackMetadataSection.CLIENT_TYPE)) {
            return (T) new PackMetadataSection(this.description,
                    SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).minorRange());
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
        // The backing archive is owned by the repository source, which reuses it across reloads.
    }
}
