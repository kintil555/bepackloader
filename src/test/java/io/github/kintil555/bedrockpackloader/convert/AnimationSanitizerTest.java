package io.github.kintil555.bedrockpackloader.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnimationSanitizerTest {
    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static AnimationSanitizer sanitizer(List<String> seen) {
        return new AnimationSanitizer(expression -> {
            seen.add(expression);
            return "variable.bpl_" + (seen.size() - 1);
        });
    }

    @Test
    void rewritesOnlyExpressionsGeckoLibRejects() {
        JsonObject file = parse("""
                {"animations":{"a":{"bones":{"arm":{
                  "rotation":["q.anim_time*2", "q.is_item_name_any('slot.weapon.mainhand','x')?1:0", 5],
                  "position":{"0.0":[0,"v.a[1]",0],"1.0":{"post":[1,2,"v.x??3"],"lerp_mode":"catmullrom"}}
                }}}}}""");
        List<String> seen = new ArrayList<>();
        AnimationSanitizer sanitizer = sanitizer(seen);
        sanitizer.sanitize(file);

        assertEquals(3, sanitizer.replaced());
        JsonObject bone = file.getAsJsonObject("animations").getAsJsonObject("a").getAsJsonObject("bones").getAsJsonObject("arm");
        assertEquals("q.anim_time*2", bone.getAsJsonArray("rotation").get(0).getAsString());
        assertEquals("variable.bpl_0", bone.getAsJsonArray("rotation").get(1).getAsString());
        assertEquals(5, bone.getAsJsonArray("rotation").get(2).getAsInt());
        assertEquals("variable.bpl_1", bone.getAsJsonObject("position").getAsJsonArray("0.0").get(1).getAsString());
        assertEquals("variable.bpl_2", bone.getAsJsonObject("position").getAsJsonObject("1.0").getAsJsonArray("post").get(2).getAsString());
        assertEquals("catmullrom", bone.getAsJsonObject("position").getAsJsonObject("1.0").get("lerp_mode").getAsString());
    }

    @Test
    void keepsInterpolationNamesUntouched() {
        JsonObject file = parse("""
                {"animations":{"a":{"bones":{"b":{"rotation":{"1.0":{"post":[0,"v.x??3",0],"lerp_mode":"catmullrom"}}}}}}}""");
        List<String> seen = new ArrayList<>();
        sanitizer(seen).sanitize(file);

        JsonObject keyframe = file.getAsJsonObject("animations").getAsJsonObject("a").getAsJsonObject("bones")
                .getAsJsonObject("b").getAsJsonObject("rotation").getAsJsonObject("1.0");
        assertEquals("catmullrom", keyframe.get("lerp_mode").getAsString());
        assertEquals(List.of("v.x??3"), seen);
    }

    @Test
    void needsRewriteDetectsQuotesAndBrackets() {
        assertTrue(AnimationSanitizer.needsRewrite("q.a('x')"));
        assertTrue(AnimationSanitizer.needsRewrite("array.x[0]"));
        assertFalse(AnimationSanitizer.needsRewrite("math.sin(q.anim_time*90)"));
    }

    @Test
    void flattensObjectRelativeToAndEffectArrays() {
        JsonObject file = parse("""
                {"animations":{"a":{
                  "bones":{"b":{"relative_to":{"rotation":"entity"},"rotation":[1,2,3]}},
                  "particle_effects":{"0.5":[{"effect":"x"},{"effect":"y"}],"1.0":{"effect":"z"}},
                  "timeline":{"0.0":["v.a=1","v.b=2"]}
                }}}""");
        sanitizer(new ArrayList<>()).sanitize(file);

        JsonObject animation = file.getAsJsonObject("animations").getAsJsonObject("a");
        assertEquals("entity", animation.getAsJsonObject("bones").getAsJsonObject("b").get("relative_to").getAsString());
        JsonObject particles = animation.getAsJsonObject("particle_effects");
        assertEquals(3, particles.size());
        assertEquals("x", particles.getAsJsonObject("0.5").get("effect").getAsString());
        assertEquals("y", particles.getAsJsonObject("0.501").get("effect").getAsString());
        assertEquals("v.a=1;v.b=2;", animation.getAsJsonObject("timeline").get("0.0").getAsString());
    }
}
