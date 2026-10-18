package io.github.kintil555.bedrockpackloader.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/** An in-memory Java resource tree: namespace -> path -> content supplier. Entries are lazy. */
public final class VirtualResources {
    private final NavigableMap<String, NavigableMap<String, IoSupplier<InputStream>>> byNamespace = new TreeMap<>();

    /** Adds or replaces a resource. Returns false (and adds nothing) when the namespace or path is not a valid identifier. */
    public boolean put(String namespace, String path, IoSupplier<InputStream> supplier) {
        if (!Identifier.isValidNamespace(namespace) || !Identifier.isValidPath(path)) {
            return false;
        }
        this.byNamespace.computeIfAbsent(namespace, key -> new TreeMap<>()).put(path, supplier);
        return true;
    }

    public boolean putBytes(String namespace, String path, byte[] bytes) {
        return put(namespace, path, () -> new ByteArrayInputStream(bytes));
    }

    public @Nullable IoSupplier<InputStream> get(String namespace, String path) {
        NavigableMap<String, IoSupplier<InputStream>> files = this.byNamespace.get(namespace);
        return files == null ? null : files.get(path);
    }

    public boolean contains(String namespace, String path) {
        return get(namespace, path) != null;
    }

    public Set<String> namespaces() {
        return Collections.unmodifiableSet(this.byNamespace.keySet());
    }

    /** Visits every resource of {@code namespace} below {@code directory} (no trailing slash). */
    public void listUnder(String namespace, String directory, BiConsumer<String, IoSupplier<InputStream>> output) {
        NavigableMap<String, IoSupplier<InputStream>> files = this.byNamespace.get(namespace);
        if (files == null) {
            return;
        }
        String prefix = directory.isEmpty() ? "" : directory + "/";
        files.subMap(prefix, true, prefix + Character.MAX_VALUE, false).forEach(output);
    }

    public int size() {
        return this.byNamespace.values().stream().mapToInt(NavigableMap::size).sum();
    }
}
