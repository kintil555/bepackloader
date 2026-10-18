package io.github.kintil555.bedrockpackloader.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Lenient JSON reading for Bedrock files, which commonly carry a BOM and {@code //} comments.
 * {@code JsonParser.parseReader} reads leniently, which accepts those comments.
 */
public final class JsonIO {
    private JsonIO() {
    }

    public static JsonElement parse(byte[] bytes) throws IOException {
        int start = hasUtf8Bom(bytes) ? 3 : 0;
        String text = new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            return com.google.gson.JsonParser.parseReader(reader);
        } catch (JsonParseException ex) {
            throw new IOException("Malformed JSON: " + ex.getMessage(), ex);
        }
    }

    public static JsonElement parse(InputStream stream) throws IOException {
        return parse(stream.readAllBytes());
    }

    /** Parses the bytes and returns the root object, or {@code null} when the root is not an object. */
    public static @Nullable JsonObject parseObject(byte[] bytes) throws IOException {
        JsonElement element = parse(bytes);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }
}
