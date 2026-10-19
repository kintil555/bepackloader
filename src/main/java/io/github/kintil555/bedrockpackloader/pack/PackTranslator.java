package io.github.kintil555.bedrockpackloader.pack;

import com.google.gson.JsonObject;
import io.github.kintil555.bedrockpackloader.animation.MolangVariableBridge;
import io.github.kintil555.bedrockpackloader.convert.AnimationSanitizer;
import io.github.kintil555.bedrockpackloader.convert.BuiltinAnimations;
import io.github.kintil555.bedrockpackloader.convert.BuiltinGeometry;
import io.github.kintil555.bedrockpackloader.convert.FlipbookIndex;
import io.github.kintil555.bedrockpackloader.convert.GeometrySplitter;
import io.github.kintil555.bedrockpackloader.convert.PngEncoder;
import io.github.kintil555.bedrockpackloader.convert.TextureMapper;
import io.github.kintil555.bedrockpackloader.convert.TgaDecoder;
import io.github.kintil555.bedrockpackloader.index.AnimationControllerDefinition;
import io.github.kintil555.bedrockpackloader.index.BedrockAssetIndex;
import io.github.kintil555.bedrockpackloader.index.ClientEntityDefinition;
import io.github.kintil555.bedrockpackloader.index.RenderControllerDefinition;
import io.github.kintil555.bedrockpackloader.util.JsonIO;
import io.github.kintil555.bedrockpackloader.util.PathNames;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Turns the effective file tree of a Bedrock pack into Java resources.
 *
 * <p>Everything the pack ships is exposed under its own namespace with its original paths (so models and
 * entity definitions can reference textures the way Bedrock does). Textures that correspond to a vanilla texture
 * are additionally exposed as {@code minecraft:} resources, which is what makes the pack re-skin the game.
 * Geometry and animation files are placed where GeckoLib's resource loader looks for them.
 */
public final class PackTranslator {
    private static final Pattern PBR_SUFFIX = Pattern.compile("_(mer|mers|normal|heightmap)\\.(png|tga)$");
    private static final String GECKOLIB_MODELS = "geckolib/models/";
    private static final String GECKOLIB_ANIMATIONS = "geckolib/animations/";

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final TextureMapper mapper;

    public PackTranslator(TextureMapper mapper) {
        this.mapper = mapper;
    }

    public TranslatedPack translate(BedrockPackFiles files, String namespace) {
        return translate(files, namespace, PackSettings.NONE);
    }

    public TranslatedPack translate(BedrockPackFiles files, String namespace, PackSettings settings) {
        TranslationReport report = new TranslationReport();
        VirtualResources resources = new VirtualResources();
        BedrockAssetIndex.Builder index = BedrockAssetIndex.builder(namespace).settings(settings);
        Map<String, String> geometryPathOwners = new HashMap<>();

        FlipbookIndex flipbooks = loadFlipbooks(files, report);

        // Low-priority files first so that subpack files replace base files with the same identifier.
        for (String path : files.pathsInPriorityOrder()) {
            try {
                if (path.startsWith("textures/")) {
                    translateTexture(files, path, namespace, resources, flipbooks, report);
                } else if (path.startsWith("models/") && path.endsWith(".json")) {
                    translateGeometry(files, path, namespace, resources, index, geometryPathOwners, report);
                } else if (path.startsWith("animations/") && path.endsWith(".json")) {
                    translateAnimations(files, path, namespace, resources, index, report);
                } else if (path.startsWith("animation_controllers/") && path.endsWith(".json")) {
                    translateControllers(files, path, index, report);
                } else if (path.startsWith("render_controllers/") && path.endsWith(".json")) {
                    translateRenderControllers(files, path, index);
                } else if (path.startsWith("entity/") && path.endsWith(".json")) {
                    translateEntity(files, path, index, report);
                } else if (path.startsWith("texts/") && path.endsWith(".lang")) {
                    translateLang(files, path, namespace, resources, report);
                }
            } catch (IOException | RuntimeException ex) {
                report.warn(path + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        addBuiltinGeometries(namespace, resources, index, report);
        addBuiltinAnimations(namespace, resources, index, report);
        return new TranslatedPack(namespace, resources, index.build(), report);
    }

    /** Adds Bedrock's built-in geometries (the default player model, cape) that the pack references but does not define. */
    private static void addBuiltinGeometries(String namespace, VirtualResources resources, BedrockAssetIndex.Builder index,
                                             TranslationReport report) {
        BuiltinGeometry.all().forEach((identifier, json) -> {
            if (index.hasGeometry(identifier)) {
                return;
            }
            String name = "builtin_" + PathNames.toIdentifierPath(identifier.substring("geometry.".length())).replace('/', '_');
            if (resources.putBytes(namespace, GECKOLIB_MODELS + name + ".geo.json", json)) {
                index.geometry(identifier, Identifier.fromNamespaceAndPath(namespace, name));
                report.geometries++;
            }
        });
    }

    /** Adds Bedrock's built-in player poses (riding, sleeping...) that the pack references but does not define. */
    private static void addBuiltinAnimations(String namespace, VirtualResources resources, BedrockAssetIndex.Builder index,
                                             TranslationReport report) {
        byte[] json = BuiltinAnimations.json(id -> !index.hasAnimation(id));
        if (json == null || !resources.putBytes(namespace, GECKOLIB_ANIMATIONS + BuiltinAnimations.FILE + ".json", json)) {
            return;
        }
        Identifier file = Identifier.fromNamespaceAndPath(namespace, BuiltinAnimations.FILE);
        for (String id : BuiltinAnimations.identifiers()) {
            if (!index.hasAnimation(id)) {
                index.animation(id, new BedrockAssetIndex.AnimationRef(file, id, 1.0, true));
                report.animations++;
            }
        }
    }

    private static FlipbookIndex loadFlipbooks(BedrockPackFiles files, TranslationReport report) {
        String path = "textures/flipbook_textures.json";
        if (!files.contains(path)) {
            return FlipbookIndex.empty();
        }
        try {
            return FlipbookIndex.parse(files.readBytes(path));
        } catch (IOException | RuntimeException ex) {
            report.warn(path + ": " + ex.getMessage());
            return FlipbookIndex.empty();
        }
    }

    // ---- textures ---------------------------------------------------------------------------------------------

    private void translateTexture(BedrockPackFiles files, String path, String namespace, VirtualResources resources,
                                  FlipbookIndex flipbooks, TranslationReport report) {
        String extension = PathNames.extension(path);
        if (!extension.equals("tga") && !extension.equals("png")) {
            return;
        }
        if (PBR_SUFFIX.matcher(path.toLowerCase(Locale.ROOT)).find()) {
            report.texturesSkippedPbr++;
            return;
        }

        IoSupplier<InputStream> source = files.get(path);
        // Some packs ship PNG data under a .tga name, so the content decides, not the extension.
        boolean tga = extension.equals("tga") && !isPng(source);
        IoSupplier<InputStream> png = tga ? tgaToPng(source) : source;
        if (tga) {
            report.tgaConverted++;
        }

        String ownPath = PathNames.toIdentifierPath(PathNames.stripExtension(path)) + ".png";
        if (resources.put(namespace, ownPath, png)) {
            report.texturesExposed++;
        }
        putMcmeta(resources, namespace, ownPath, flipbooks.mcmetaFor(path), report);

        int[] size = readSize(source, tga);
        Optional<String> vanillaPath = size == null ? Optional.empty() : this.mapper.map(path, size[0], size[1]);
        if (vanillaPath.isPresent()) {
            String target = "textures/" + vanillaPath.get();
            resources.put("minecraft", target, png);
            putMcmeta(resources, "minecraft", target, flipbooks.mcmetaFor(path), report);
            report.texturesMappedToVanilla++;
        } else if (isMappableGroup(path)) {
            report.texturesUnmapped++;
        }
    }

    private static boolean isMappableGroup(String path) {
        return path.startsWith("textures/blocks/") || path.startsWith("textures/items/") || path.startsWith("textures/entity/");
    }

    private static void putMcmeta(VirtualResources resources, String namespace, String texturePath, @Nullable String mcmeta,
                                  TranslationReport report) {
        if (mcmeta != null && resources.putBytes(namespace, texturePath + ".mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8))) {
            report.flipbooksApplied++;
        }
    }

    private static IoSupplier<InputStream> tgaToPng(IoSupplier<InputStream> source) {
        return () -> {
            try (InputStream in = source.get()) {
                TgaDecoder.Image image = TgaDecoder.decode(in.readAllBytes());
                return new ByteArrayInputStream(PngEncoder.encode(image.width(), image.height(), image.rgba()));
            }
        };
    }

    private static boolean isPng(IoSupplier<InputStream> source) {
        try (InputStream in = source.get()) {
            byte[] magic = in.readNBytes(PNG_MAGIC.length);
            return java.util.Arrays.equals(magic, PNG_MAGIC);
        } catch (IOException ex) {
            return false;
        }
    }

    /** Reads {@code {width, height}} from the file header, or returns null when it cannot be determined. */
    private static int @Nullable [] readSize(IoSupplier<InputStream> source, boolean tga) {
        try (InputStream in = source.get()) {
            byte[] header = in.readNBytes(32);
            if (tga) {
                if (header.length < 16) {
                    return null;
                }
                ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                return new int[]{Short.toUnsignedInt(buffer.getShort(12)), Short.toUnsignedInt(buffer.getShort(14))};
            }
            if (header.length < 24 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            return new int[]{buffer.getInt(16), buffer.getInt(20)};
        } catch (IOException ex) {
            return null;
        }
    }

    // ---- geometry ---------------------------------------------------------------------------------------------

    private void translateGeometry(BedrockPackFiles files, String path, String namespace, VirtualResources resources,
                                   BedrockAssetIndex.Builder index, Map<String, String> pathOwners, TranslationReport report)
            throws IOException {
        JsonObject root = JsonIO.parseObject(files.readBytes(path));
        if (root == null || !root.has("minecraft:geometry")) {
            return; // e.g. models/mobs.json, a model mapping rather than geometry
        }

        report.geometryFiles++;
        for (GeometrySplitter.Part part : GeometrySplitter.split(root)) {
            String name = modelName(part.identifier(), pathOwners);
            String resourcePath = GECKOLIB_MODELS + name + ".geo.json";
            if (resources.putBytes(namespace, resourcePath, part.json())) {
                index.geometry(part.identifier(), Identifier.fromNamespaceAndPath(namespace, name));
                report.geometries++;
                if (part.versionChanged()) {
                    report.geometryFormatNormalised++;
                }
            } else {
                report.warn(path + ": cannot expose geometry '" + part.identifier() + "'");
            }
        }
    }

    /** A valid, collision-free model file name for a geometry identifier. */
    private static String modelName(String identifier, Map<String, String> owners) {
        String stripped = identifier.startsWith("geometry.") ? identifier.substring("geometry.".length()) : identifier;
        String name = PathNames.toIdentifierPath(stripped).replace('/', '_');
        String owner = owners.putIfAbsent(name, identifier);
        if (owner != null && !owner.equals(identifier)) {
            name = name + "_" + PathNames.shortHash(identifier);
            owners.put(name, identifier);
        }
        return name;
    }

    // ---- animations, controllers, entities --------------------------------------------------------------------

    private void translateAnimations(BedrockPackFiles files, String path, String namespace, VirtualResources resources,
                                     BedrockAssetIndex.Builder index, TranslationReport report) throws IOException {
        byte[] bytes = files.readBytes(path);
        JsonObject root = JsonIO.parseObject(bytes);
        if (root == null || !root.has("animations") || !root.get("animations").isJsonObject()) {
            return;
        }

        String relative = PathNames.toIdentifierPath(path.substring("animations/".length()));
        AnimationSanitizer sanitizer = new AnimationSanitizer(MolangVariableBridge::variableFor, MolangVariableBridge::geckoLibRejects);
        sanitizer.sanitize(root);
        if (sanitizer.replaced() > 0) {
            bytes = JsonIO.toBytes(root);
        }
        if (!resources.putBytes(namespace, GECKOLIB_ANIMATIONS + relative, bytes)) {
            report.warn(path + ": cannot expose animation file");
            return;
        }

        String stripped = relative.replaceFirst("(\\.animations?)?\\.json$", "");
        Identifier file = Identifier.fromNamespaceAndPath(namespace, stripped);
        report.animationFiles++;
        for (String animationId : root.getAsJsonObject("animations").keySet()) {
            JsonObject animation = root.getAsJsonObject("animations").get(animationId) instanceof JsonObject object ? object : new JsonObject();
            double length = animation.has("animation_length") && animation.get("animation_length").isJsonPrimitive()
                    && animation.get("animation_length").getAsJsonPrimitive().isNumber() ? animation.get("animation_length").getAsDouble() : 0.0;
            boolean loops = animation.has("loop") && animation.get("loop").isJsonPrimitive()
                    && animation.get("loop").getAsJsonPrimitive().isBoolean() && animation.get("loop").getAsBoolean();
            index.animation(animationId, new BedrockAssetIndex.AnimationRef(file, animationId, length, loops));
            report.animations++;
        }
    }

    private void translateControllers(BedrockPackFiles files, String path, BedrockAssetIndex.Builder index,
                                      TranslationReport report) throws IOException {
        JsonObject root = JsonIO.parseObject(files.readBytes(path));
        if (root == null) {
            return;
        }
        for (AnimationControllerDefinition controller : AnimationControllerDefinition.parseFile(root)) {
            index.controller(controller);
            report.controllers++;
        }
    }

    private void translateRenderControllers(BedrockPackFiles files, String path, BedrockAssetIndex.Builder index) throws IOException {
        JsonObject root = JsonIO.parseObject(files.readBytes(path));
        if (root == null) {
            return;
        }
        for (RenderControllerDefinition controller : RenderControllerDefinition.parseFile(root)) {
            index.renderController(controller);
        }
    }

    private void translateEntity(BedrockPackFiles files, String path, BedrockAssetIndex.Builder index, TranslationReport report)
            throws IOException {
        JsonObject root = JsonIO.parseObject(files.readBytes(path));
        ClientEntityDefinition entity = root == null ? null : ClientEntityDefinition.parse(root);
        if (entity != null) {
            index.entity(entity);
            report.entities++;
        }
    }

    // ---- language ---------------------------------------------------------------------------------------------

    private void translateLang(BedrockPackFiles files, String path, String namespace, VirtualResources resources,
                               TranslationReport report) throws IOException {
        String code = PathNames.stripExtension(PathNames.baseName(path)).toLowerCase(Locale.ROOT);
        JsonObject json = new JsonObject();
        LangFile.parse(files.readBytes(path)).forEach(json::addProperty);
        if (resources.putBytes(namespace, "lang/" + code + ".json", json.toString().getBytes(StandardCharsets.UTF_8))) {
            report.langFiles++;
        }
    }
}
