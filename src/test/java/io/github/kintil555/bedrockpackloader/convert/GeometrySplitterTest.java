package io.github.kintil555.bedrockpackloader.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class GeometrySplitterTest {
    @Test
    void repairsUndefinedParentsAndMissingUv() {
        JsonObject geometry = JsonParser.parseString("""
                {"bones":[
                  {"name":"a","parent":"missing","cubes":[{"origin":[0,0,0],"size":[1,1,1]}]},
                  {"name":"b","parent":"a"}]}""").getAsJsonObject();
        GeometrySplitter.repairBones(geometry);

        JsonObject a = geometry.getAsJsonArray("bones").get(0).getAsJsonObject();
        assertFalse(a.has("parent"));
        assertTrue(a.getAsJsonArray("cubes").get(0).getAsJsonObject().has("uv"));
        assertEquals("a", geometry.getAsJsonArray("bones").get(1).getAsJsonObject().get("parent").getAsString());
    }

    @Test
    void builtinHumanoidHasTheBonesPacksAnimate() {
        String json = new String(BuiltinGeometry.all().get("geometry.humanoid.custom"), java.nio.charset.StandardCharsets.UTF_8);
        for (String bone : new String[]{"root", "waist", "body", "head", "hat", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem"}) {
            assertTrue(json.contains("\"name\":\"" + bone + "\""), bone);
        }
    }
}
