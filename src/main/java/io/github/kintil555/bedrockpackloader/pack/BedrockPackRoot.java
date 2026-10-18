package io.github.kintil555.bedrockpackloader.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A Bedrock pack on disk, either a folder or a zip-like archive ({@code .zip}, {@code .mcpack}).
 * Paths are exposed relative to the folder that contains {@code manifest.json}, always with {@code /} separators.
 */
public abstract class BedrockPackRoot implements AutoCloseable {
    public static final String MANIFEST = "manifest.json";

    private final Path source;

    protected BedrockPackRoot(Path source) {
        this.source = source;
    }

    public Path source() {
        return this.source;
    }

    /** All regular files of the pack, relative to the pack root. */
    public abstract List<String> files();

    /** Supplier for a file returned by {@link #files()}. */
    public abstract IoSupplier<InputStream> open(String relativePath);

    @Override
    public abstract void close();

    /**
     * Opens a pack.
     *
     * @return the pack root, or {@code null} when {@code path} does not contain a Bedrock manifest
     */
    public static @Nullable BedrockPackRoot open(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return DirectoryRoot.tryOpen(path);
        }
        if (Files.isRegularFile(path)) {
            return ZipRoot.tryOpen(path);
        }
        return null;
    }

    private static final class DirectoryRoot extends BedrockPackRoot {
        private final Path root;

        private DirectoryRoot(Path source, Path root) {
            super(source);
            this.root = root;
        }

        static @Nullable DirectoryRoot tryOpen(Path directory) throws IOException {
            if (Files.isRegularFile(directory.resolve(MANIFEST))) {
                return new DirectoryRoot(directory, directory);
            }
            try (Stream<Path> children = Files.list(directory)) {
                for (Path child : (Iterable<Path>) children::iterator) {
                    if (Files.isDirectory(child) && Files.isRegularFile(child.resolve(MANIFEST))) {
                        return new DirectoryRoot(directory, child);
                    }
                }
            }
            return null;
        }

        @Override
        public List<String> files() {
            List<String> result = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(this.root)) {
                walk.filter(Files::isRegularFile)
                        .forEach(file -> result.add(this.root.relativize(file).toString().replace('\\', '/')));
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException("Failed to list " + this.root, ex);
            }
            return result;
        }

        @Override
        public IoSupplier<InputStream> open(String relativePath) {
            return IoSupplier.create(this.root.resolve(relativePath));
        }

        @Override
        public void close() {
            // Plain folders hold no resources.
        }
    }

    private static final class ZipRoot extends BedrockPackRoot {
        private final ZipFile zip;
        private final String prefix;

        private ZipRoot(Path source, ZipFile zip, String prefix) {
            super(source);
            this.zip = zip;
            this.prefix = prefix;
        }

        static @Nullable ZipRoot tryOpen(Path file) throws IOException {
            ZipFile zip;
            try {
                zip = new ZipFile(file.toFile());
            } catch (java.util.zip.ZipException ex) {
                return null;
            }

            String bestPrefix = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = normalise(entries.nextElement().getName());
                if (name.equals(MANIFEST) || name.endsWith("/" + MANIFEST)) {
                    String candidate = name.substring(0, name.length() - MANIFEST.length());
                    if (depth(candidate) <= 1 && (bestPrefix == null || candidate.length() < bestPrefix.length())) {
                        bestPrefix = candidate;
                    }
                }
            }

            if (bestPrefix == null) {
                zip.close();
                return null;
            }
            return new ZipRoot(file, zip, bestPrefix);
        }

        private static int depth(String prefix) {
            int depth = 0;
            for (int i = 0; i < prefix.length(); i++) {
                if (prefix.charAt(i) == '/') {
                    depth++;
                }
            }
            return depth;
        }

        private static String normalise(String entryName) {
            return entryName.replace('\\', '/');
        }

        @Override
        public List<String> files() {
            List<String> result = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = this.zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalise(entry.getName());
                if (!entry.isDirectory() && name.startsWith(this.prefix)) {
                    result.add(name.substring(this.prefix.length()));
                }
            }
            return result;
        }

        @Override
        public IoSupplier<InputStream> open(String relativePath) {
            ZipEntry entry = this.zip.getEntry(this.prefix + relativePath);
            if (entry == null) {
                // Archives written on Windows may use backslashes.
                entry = this.zip.getEntry((this.prefix + relativePath).replace('/', '\\'));
            }
            if (entry == null) {
                return () -> {
                    throw new java.io.FileNotFoundException(relativePath);
                };
            }
            return IoSupplier.create(this.zip, entry);
        }

        @Override
        public void close() {
            try {
                this.zip.close();
            } catch (IOException ignored) {
                // Nothing useful can be done if closing a read-only archive fails.
            }
        }
    }
}
