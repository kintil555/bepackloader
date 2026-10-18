package io.github.kintil555.bedrockpackloader.pack;

import io.github.kintil555.bedrockpackloader.brarchive.BrArchive;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The effective file tree of a Bedrock pack for one subpack selection.
 *
 * <p>Three Bedrock behaviours are resolved here so every later stage can treat the pack as a plain folder:
 * <ul>
 *   <li>{@code __brarchive/<name>.brarchive} files are expanded into the folder {@code <name>/};</li>
 *   <li>files of the selected subpack ({@code subpacks/<folder>/...}) override files of the base pack;</li>
 *   <li>files of subpacks that were not selected are dropped.</li>
 * </ul>
 */
public final class BedrockPackFiles {
    public static final String BRARCHIVE_FOLDER = "__brarchive/";
    public static final String BRARCHIVE_EXTENSION = ".brarchive";
    private static final String SUBPACKS = "subpacks/";

    /** Where a file came from; later layers win when two files define the same Bedrock identifier. */
    public enum Layer {
        BASE,
        SUBPACK
    }

    private record Node(IoSupplier<InputStream> supplier, Layer layer) {
    }

    private final NavigableMap<String, Node> files;

    private BedrockPackFiles(NavigableMap<String, Node> files) {
        this.files = files;
    }

    /**
     * Builds the tree.
     *
     * @param subpack folder name of the selected subpack, or {@code null} to use the base pack only
     */
    public static BedrockPackFiles build(BedrockPackRoot root, @Nullable String subpack) throws IOException {
        List<String> basePaths = new ArrayList<>();
        List<String> subpackPaths = new ArrayList<>();
        String subpackPrefix = subpack == null ? null : SUBPACKS + subpack + "/";

        for (String path : root.files()) {
            if (path.startsWith(SUBPACKS)) {
                if (subpackPrefix != null && path.startsWith(subpackPrefix)) {
                    subpackPaths.add(path.substring(subpackPrefix.length()));
                }
            } else {
                basePaths.add(path);
            }
        }

        NavigableMap<String, Node> merged = new TreeMap<>();
        addLayer(merged, root, "", basePaths, Layer.BASE);
        if (subpackPrefix != null) {
            addLayer(merged, root, subpackPrefix, subpackPaths, Layer.SUBPACK);
        }
        return new BedrockPackFiles(merged);
    }

    private static void addLayer(NavigableMap<String, Node> target, BedrockPackRoot root, String physicalPrefix,
                                 List<String> layerPaths, Layer layer) throws IOException {
        for (String path : layerPaths) {
            IoSupplier<InputStream> supplier = root.open(physicalPrefix + path);
            if (isBrArchive(path)) {
                expand(target, path, supplier, layer);
            } else if (!path.contains(BRARCHIVE_FOLDER)) {
                target.put(path, new Node(supplier, layer));
            }
        }
    }

    private static boolean isBrArchive(String path) {
        return path.endsWith(BRARCHIVE_EXTENSION) && (path.startsWith(BRARCHIVE_FOLDER) || path.contains("/" + BRARCHIVE_FOLDER));
    }

    private static void expand(NavigableMap<String, Node> target, String archivePath, IoSupplier<InputStream> supplier,
                               Layer layer) throws IOException {
        int marker = archivePath.indexOf(BRARCHIVE_FOLDER);
        String parent = archivePath.substring(0, marker);
        String inner = archivePath.substring(marker + BRARCHIVE_FOLDER.length(),
                archivePath.length() - BRARCHIVE_EXTENSION.length());
        String directory = parent + inner + "/";

        byte[] bytes;
        try (InputStream in = supplier.get()) {
            bytes = in.readAllBytes();
        }

        BrArchive archive = BrArchive.parse(bytes);
        for (BrArchive.Entry entry : archive.entries()) {
            if (entry.length() == 0) {
                continue; // Placeholder stubs: the real file ships loose next to the archive and must not be replaced by nothing.
            }
            target.put(directory + entry.name(), new Node(() -> archive.open(entry), layer));
        }
    }

    public boolean contains(String path) {
        return this.files.containsKey(path);
    }

    public @Nullable IoSupplier<InputStream> get(String path) {
        Node node = this.files.get(path);
        return node == null ? null : node.supplier();
    }

    public Layer layerOf(String path) {
        Node node = this.files.get(path);
        return node == null ? Layer.BASE : node.layer();
    }

    public byte[] readBytes(String path) throws IOException {
        IoSupplier<InputStream> supplier = get(path);
        if (supplier == null) {
            throw new java.io.FileNotFoundException(path);
        }
        try (InputStream in = supplier.get()) {
            return in.readAllBytes();
        }
    }

    public int size() {
        return this.files.size();
    }

    /** All paths in ascending order. Files of the subpack layer are not necessarily listed last. */
    public Set<String> paths() {
        return Collections.unmodifiableSet(this.files.keySet());
    }

    /** Paths below {@code directory} (which must end with {@code /}). */
    public Set<String> pathsUnder(String directory) {
        return Collections.unmodifiableSet(this.files.subMap(directory, true, directory + Character.MAX_VALUE, false).keySet());
    }

    /** Paths ordered so that files from the base layer come before files from the subpack layer. */
    public List<String> pathsInPriorityOrder() {
        List<String> base = new ArrayList<>();
        List<String> subpack = new ArrayList<>();
        this.files.forEach((path, node) -> (node.layer() == Layer.BASE ? base : subpack).add(path));
        base.addAll(subpack);
        return base;
    }
}
