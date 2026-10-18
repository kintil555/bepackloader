package io.github.kintil555.bedrockpackloader.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.kintil555.bedrockpackloader.util.JsonIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates Bedrock texture file names into candidate Java names.
 *
 * <p>The rules come from a JSON file with an {@code exact} table and a list of {@code families}. A family is a
 * pattern such as {@code planks_{wood}} → {@code {wood}_planks}; each placeholder draws its values from the
 * {@code values} table, which also renames them (for example Bedrock {@code silver} is Java {@code light_gray}).
 */
public final class TextureAliases {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final Map<String, String> exact;
    private final List<Family> families;

    private record Family(Pattern bedrock, List<String> variables, String java, Map<String, Map<String, String>> values) {
        String translate(String name) {
            Matcher matcher = this.bedrock.matcher(name);
            if (!matcher.matches()) {
                return null;
            }

            String result = this.java;
            for (int i = 0; i < this.variables.size(); i++) {
                String variable = this.variables.get(i);
                String renamed = this.values.get(variable).get(matcher.group(i + 1));
                result = result.replace("{" + variable + "}", renamed);
            }
            return result;
        }
    }

    private TextureAliases(Map<String, String> exact, List<Family> families) {
        this.exact = exact;
        this.families = families;
    }

    public static TextureAliases load(String resourcePath) {
        try (InputStream stream = TextureAliases.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled resource " + resourcePath);
            }
            return parse(stream.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static TextureAliases parse(byte[] json) throws IOException {
        JsonObject root = JsonIO.parseObject(json);
        if (root == null) {
            throw new IOException("Alias file is not a JSON object");
        }

        Map<String, Map<String, String>> values = new HashMap<>();
        if (root.has("values")) {
            for (Map.Entry<String, JsonElement> group : root.getAsJsonObject("values").entrySet()) {
                Map<String, String> renames = new HashMap<>();
                for (Map.Entry<String, JsonElement> value : group.getValue().getAsJsonObject().entrySet()) {
                    renames.put(value.getKey(), value.getValue().getAsString());
                }
                values.put(group.getKey(), renames);
            }
        }

        Map<String, String> exact = new HashMap<>();
        if (root.has("exact")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("exact").entrySet()) {
                exact.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        List<Family> families = new ArrayList<>();
        if (root.has("families")) {
            for (JsonElement element : root.getAsJsonArray("families")) {
                JsonObject family = element.getAsJsonObject();
                families.add(compile(family.get("bedrock").getAsString(), family.get("java").getAsString(), values));
            }
        }
        return new TextureAliases(exact, families);
    }

    private static Family compile(String bedrockPattern, String java, Map<String, Map<String, String>> values) {
        List<String> variables = new ArrayList<>();
        StringBuilder regex = new StringBuilder();
        Matcher matcher = PLACEHOLDER.matcher(bedrockPattern);
        int last = 0;
        while (matcher.find()) {
            String variable = matcher.group(1);
            Map<String, String> allowed = values.get(variable);
            if (allowed == null) {
                throw new IllegalArgumentException("Unknown placeholder {" + variable + "} in '" + bedrockPattern + "'");
            }

            regex.append(Pattern.quote(bedrockPattern.substring(last, matcher.start())));
            List<String> alternatives = new ArrayList<>(allowed.keySet());
            alternatives.sort(Comparator.comparingInt(String::length).reversed());
            regex.append('(');
            for (int i = 0; i < alternatives.size(); i++) {
                if (i > 0) {
                    regex.append('|');
                }
                regex.append(Pattern.quote(alternatives.get(i)));
            }
            regex.append(')');
            variables.add(variable);
            last = matcher.end();
        }
        regex.append(Pattern.quote(bedrockPattern.substring(last)));
        return new Family(Pattern.compile(regex.toString()), variables, java, values);
    }

    /** Candidate Java names for a Bedrock file name (without extension), most specific first. The name itself is always included. */
    public List<String> candidates(String bedrockName) {
        Set<String> result = new LinkedHashSet<>();

        String exactMatch = this.exact.get(bedrockName);
        if (exactMatch != null) {
            result.add(exactMatch);
        }
        for (Family family : this.families) {
            String translated = family.translate(bedrockName);
            if (translated != null) {
                result.add(translated);
            }
        }
        result.add(bedrockName);
        return List.copyOf(result);
    }
}
