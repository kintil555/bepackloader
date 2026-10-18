package io.github.kintil555.bedrockpackloader.pack;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parser for Bedrock {@code .lang} files ({@code key=value}, {@code #} comments, tab-separated trailing comments). */
public final class LangFile {
    private LangFile() {
    }

    public static Map<String, String> parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        Map<String, String> entries = new LinkedHashMap<>();
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }

            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1);
            int comment = value.indexOf("\t#");
            if (comment >= 0) {
                value = value.substring(0, comment);
            }
            entries.put(key, value.strip());
        }
        return entries;
    }
}
