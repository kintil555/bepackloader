package io.github.kintil555.bedrockpackloader.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Converts free-form Bedrock names into strings that are valid in a Java {@code Identifier}. */
public final class PathNames {
    private PathNames() {
    }

    /** Lower-cases the path and replaces every character that is illegal in an identifier path with an underscore. */
    public static String toIdentifierPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/';
            out.append(valid ? c : '_');
        }
        return out.toString();
    }

    /** Same as {@link #toIdentifierPath(String)} but for a namespace, where {@code /} is not allowed either. */
    public static String toNamespace(String name) {
        String path = toIdentifierPath(name).replace('/', '_');
        String trimmed = path.replaceAll("^[_.\\-]+|[_.\\-]+$", "");
        return trimmed.isEmpty() ? "pack" : trimmed;
    }

    /** Eight hexadecimal characters derived from the text; used to disambiguate colliding sanitised names. */
    public static String shortHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is required by the Java platform", ex);
        }
    }

    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String baseName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
