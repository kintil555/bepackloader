package io.github.kintil555.bedrockpackloader.convert;

import io.github.kintil555.bedrockpackloader.util.PathNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Maps a Bedrock texture path ({@code textures/blocks/stonebrick.png}) to the vanilla Java texture it replaces
 * ({@code textures/block/stone_bricks.png}).
 */
public final class TextureMapper {
    private static final Set<String> IDENTITY_GROUPS = Set.of("environment", "colormap", "misc", "map", "painting", "particle");
    private static final Map<String, String> ARMOR_MATERIALS = Map.of(
            "chain", "chainmail", "cloth", "leather", "leather", "leather", "iron", "iron",
            "gold", "gold", "diamond", "diamond", "netherite", "netherite", "copper", "copper");

    private final VanillaTextureIndex vanilla;
    private final TextureAliases blocks;
    private final TextureAliases items;

    public TextureMapper(VanillaTextureIndex vanilla, TextureAliases blocks, TextureAliases items) {
        this.vanilla = vanilla;
        this.blocks = blocks;
        this.items = items;
    }

    public static TextureMapper createBundled() {
        return new TextureMapper(
                VanillaTextureIndex.loadBundled(),
                TextureAliases.load("/assets/bedrockpackloader/mappings/block_aliases.json"),
                TextureAliases.load("/assets/bedrockpackloader/mappings/item_aliases.json"));
    }

    /** Maps by name only, ignoring texture dimensions. */
    public Optional<String> map(String bedrockPath) {
        return map(bedrockPath, this.vanilla::contains);
    }

    /**
     * Maps by name and requires the Bedrock texture (of the given size) to have a layout compatible with the
     * vanilla texture it would replace.
     *
     * @param bedrockPath path inside the pack, e.g. {@code textures/blocks/stonebrick.png}; the extension is ignored
     * @return the vanilla texture path <em>below {@code textures/}</em> including {@code .png}, if one corresponds
     */
    public Optional<String> map(String bedrockPath, int width, int height) {
        return map(bedrockPath, path -> this.vanilla.isCompatible(path, width, height));
    }

    private Optional<String> map(String bedrockPath, Predicate<String> accepts) {
        if (!bedrockPath.startsWith("textures/")) {
            return Optional.empty();
        }

        String relative = PathNames.stripExtension(bedrockPath.substring("textures/".length()));
        int slash = relative.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }

        String group = relative.substring(0, slash);
        String rest = relative.substring(slash + 1);
        String name = rest.substring(rest.lastIndexOf('/') + 1);

        return switch (group) {
            case "blocks" -> rest.contains("carried") ? Optional.empty() : firstAccepted("block/", blockCandidates(name), accepts);
            case "items" -> firstAccepted("item/", itemCandidates(rest, name), accepts);
            case "entity" -> mapEntity(rest, name, accepts);
            case "models" -> mapArmor(rest, accepts);
            default -> IDENTITY_GROUPS.contains(group) ? firstAccepted(group + "/", List.of(rest), accepts) : Optional.empty();
        };
    }

    /** Bedrock stores random-variant blocks as {@code name_0}, {@code name_1}...; the first variant stands in for the single Java texture. */
    private List<String> blockCandidates(String name) {
        List<String> candidates = new ArrayList<>(this.blocks.candidates(name));
        if (name.endsWith("_0") && name.length() > 2) {
            candidates.addAll(this.blocks.candidates(name.substring(0, name.length() - 2)));
        }
        return candidates;
    }

    private List<String> itemCandidates(String rest, String name) {
        List<String> candidates = new ArrayList<>(this.items.candidates(name));
        if (rest.startsWith("spawn_eggs/") && name.startsWith("spawn_egg_")) {
            candidates.addFirst(name.substring("spawn_egg_".length()) + "_spawn_egg");
        }
        return candidates;
    }

    private static Optional<String> firstAccepted(String folder, List<String> names, Predicate<String> accepts) {
        for (String candidate : names) {
            String path = folder + candidate + ".png";
            if (accepts.test(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private Optional<String> mapEntity(String rest, String name, Predicate<String> accepts) {
        String direct = "entity/" + rest + ".png";
        if (accepts.test(direct)) {
            return Optional.of(direct);
        }

        List<String> sameName = this.vanilla.entityTexturesNamed(name + ".png");
        if (sameName.size() == 1 && accepts.test(sameName.getFirst())) {
            return Optional.of(sameName.getFirst());
        }
        return Optional.empty();
    }

    /** {@code models/armor/chain_1} is the outer layer, {@code chain_2} the leggings layer. */
    private Optional<String> mapArmor(String rest, Predicate<String> accepts) {
        if (!rest.startsWith("armor/")) {
            return Optional.empty();
        }

        String name = rest.substring("armor/".length());
        int underscore = name.lastIndexOf('_');
        if (underscore < 0) {
            return Optional.empty();
        }

        String material = ARMOR_MATERIALS.get(name.substring(0, underscore));
        String layer = switch (name.substring(underscore + 1)) {
            case "1" -> "humanoid";
            case "2" -> "humanoid_leggings";
            default -> null;
        };
        if (material == null || layer == null) {
            return Optional.empty();
        }

        String path = "entity/equipment/" + layer + "/" + material + ".png";
        return accepts.test(path) ? Optional.of(path) : Optional.empty();
    }
}
