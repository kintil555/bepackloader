package io.github.kintil555.bedrockpackloader.pack;

import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import io.github.kintil555.bedrockpackloader.convert.TextureMapper;
import io.github.kintil555.bedrockpackloader.util.PathNames;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Finds Bedrock packs in the configured folders and offers each (pack, subpack) combination to Minecraft's
 * resource pack screen as a regular Java pack.
 */
public final class BedrockPackRepositorySource implements RepositorySource {
    private static final PackSource BEDROCK_SOURCE = PackSource.create(
            description -> Component.translatable("pack.nameAndSource", description, Component.translatable("bedrockpackloader.pack.source"))
                    .withStyle(ChatFormatting.GRAY),
            false);
    private static final PackSelectionConfig SELECTION = new PackSelectionConfig(false, Pack.Position.TOP, false);

    private record Fingerprint(long modified, long size) {
        static Fingerprint of(Path path) throws IOException {
            return new Fingerprint(Files.getLastModifiedTime(path).toMillis(), Files.isRegularFile(path) ? Files.size(path) : 0L);
        }
    }

    /** A pack on disk with its parsed manifest; translations per subpack are created on demand and reused. */
    private static final class Candidate {
        final BedrockPackRoot root;
        final BedrockManifest manifest;
        final Fingerprint fingerprint;
        final String slug;
        final String displayName;
        final Map<String, Supplier<TranslatedPack>> translations = new HashMap<>();

        Candidate(BedrockPackRoot root, BedrockManifest manifest, Fingerprint fingerprint, String slug, String displayName) {
            this.root = root;
            this.manifest = manifest;
            this.fingerprint = fingerprint;
            this.slug = slug;
            this.displayName = displayName;
        }

        synchronized Supplier<TranslatedPack> translation(@Nullable String subpack, TextureMapper mapper) {
            return this.translations.computeIfAbsent(subpack == null ? "" : subpack, key -> memoize(() -> {
                try {
                    long start = System.nanoTime();
                    BedrockPackFiles files = BedrockPackFiles.build(this.root, subpack);
                    TranslatedPack translated = new PackTranslator(mapper).translate(files, "bedrock_" + this.slug, PackSettings.fromManifest(this.manifest));
                    BedrockPackLoaderMod.LOGGER.info("Translated Bedrock pack '{}'{} in {} ms: {}", this.displayName,
                            subpack == null ? "" : " [" + subpack + "]", (System.nanoTime() - start) / 1_000_000,
                            translated.report().summary());
                    translated.report().warnings().forEach(warning -> BedrockPackLoaderMod.LOGGER.warn("  {}", warning));
                    return translated;
                } catch (IOException ex) {
                    throw new java.io.UncheckedIOException("Failed to translate Bedrock pack " + this.displayName, ex);
                }
            }));
        }

        private static <T> Supplier<T> memoize(Supplier<T> delegate) {
            return new Supplier<>() {
                private T value;

                @Override
                public synchronized T get() {
                    if (this.value == null) {
                        this.value = delegate.get();
                    }
                    return this.value;
                }
            };
        }
    }

    private final List<Path> directories;
    private final TextureMapper mapper;
    private final Map<Path, Candidate> cache = new HashMap<>();

    public BedrockPackRepositorySource(List<Path> directories, TextureMapper mapper) {
        this.directories = List.copyOf(directories);
        this.mapper = mapper;
    }

    @Override
    public synchronized void loadPacks(Consumer<Pack> result) {
        for (Path directory : this.directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> children = Files.list(directory)) {
                children.sorted().forEach(child -> offer(child, result));
            } catch (IOException ex) {
                BedrockPackLoaderMod.LOGGER.warn("Cannot list Bedrock pack folder {}", directory, ex);
            }
        }
    }

    private void offer(Path path, Consumer<Pack> result) {
        Candidate candidate = candidateFor(path);
        if (candidate == null) {
            return;
        }

        if (candidate.manifest.subpacks().isEmpty()) {
            create(candidate, null, result);
        } else {
            for (BedrockManifest.Subpack subpack : candidate.manifest.subpacks()) {
                create(candidate, subpack, result);
            }
        }
    }

    private @Nullable Candidate candidateFor(Path path) {
        try {
            Fingerprint fingerprint = Fingerprint.of(path);
            Candidate cached = this.cache.get(path);
            if (cached != null && cached.fingerprint.equals(fingerprint)) {
                return cached;
            }
            if (cached != null) {
                cached.root.close();
                this.cache.remove(path);
            }

            BedrockPackRoot root = BedrockPackRoot.open(path);
            if (root == null) {
                return null;
            }

            BedrockManifest manifest = BedrockManifest.parse(readManifest(root));
            if (!manifest.resourcePack()) {
                root.close();
                return null;
            }

            String fileName = path.getFileName().toString();
            String slug = PathNames.toNamespace(PathNames.stripExtension(fileName));
            Candidate candidate = new Candidate(root, manifest, fingerprint, slug, resolveName(root, manifest));
            this.cache.put(path, candidate);
            return candidate;
        } catch (IOException | RuntimeException ex) {
            BedrockPackLoaderMod.LOGGER.warn("Skipping {}: not a usable Bedrock pack ({})", path, ex.getMessage());
            return null;
        }
    }

    private static byte[] readManifest(BedrockPackRoot root) throws IOException {
        try (InputStream in = root.open(BedrockPackRoot.MANIFEST).get()) {
            return in.readAllBytes();
        }
    }

    /** Manifest names are often translation keys such as {@code pack.name}; resolve them from the pack's English language file. */
    private static String resolveName(BedrockPackRoot root, BedrockManifest manifest) {
        String name = manifest.name();
        if (name.contains(" ") || !name.contains(".")) {
            return name;
        }
        try {
            BedrockPackFiles files = BedrockPackFiles.build(root, null);
            for (String path : files.paths()) {
                if (path.equalsIgnoreCase("texts/en_us.lang")) {
                    String resolved = LangFile.parse(files.readBytes(path)).get(name);
                    if (resolved != null && !resolved.isBlank()) {
                        return resolved;
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            BedrockPackLoaderMod.LOGGER.debug("Could not resolve pack name '{}'", name, ex);
        }
        return name;
    }

    private void create(Candidate candidate, BedrockManifest.@Nullable Subpack subpack, Consumer<Pack> result) {
        String folder = subpack == null ? null : subpack.folderName();
        String id = "bedrock/" + candidate.slug + (folder == null ? "" : "/" + folder.toLowerCase(Locale.ROOT));
        String subpackName = subpack == null ? null : ChatFormatting.stripFormatting(subpack.name());

        Component title = Component.literal(candidate.displayName + (subpackName == null ? "" : " - " + subpackName));
        Component description = Component.literal(subpackName != null ? subpackName : "Bedrock pack converted by Bedrock Pack Loader");

        PackLocationInfo location = new PackLocationInfo(id, title, BEDROCK_SOURCE, Optional.empty());
        Supplier<TranslatedPack> translation = candidate.translation(folder, this.mapper);
        IoSupplier<InputStream> icon = iconOf(candidate);

        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new BedrockPackResources(info, translation, description, icon);
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new BedrockPackResources(info, translation, description, icon);
            }
        };

        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, SELECTION);
        if (pack != null) {
            result.accept(pack);
        }
    }

    private static @Nullable IoSupplier<InputStream> iconOf(Candidate candidate) {
        return candidate.root.files().contains("pack_icon.png") ? candidate.root.open("pack_icon.png") : null;
    }

    /** Collects all candidate pack paths; useful for diagnostics. */
    public synchronized List<String> knownPackIds() {
        List<String> ids = new ArrayList<>();
        this.cache.values().forEach(candidate -> ids.add(candidate.slug));
        return ids;
    }
}
