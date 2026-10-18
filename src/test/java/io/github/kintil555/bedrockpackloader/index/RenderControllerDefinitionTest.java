package io.github.kintil555.bedrockpackloader.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.kintil555.bedrockpackloader.molang.MolangContext;
import io.github.kintil555.bedrockpackloader.molang.MolangExpression;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderControllerDefinitionTest {
    private static final String FILE = """
            {"render_controllers":{
              "controller.render.a":{"geometry":"Geometry.default","textures":["Texture.default"],"materials":[{"*":"Material.default"}]},
              "controller.render.b":{
                "arrays":{"geometries":{"Array.g":["Geometry.default","Geometry.Cape"]},"textures":{"Array.t":["Texture.default","Texture.cape"]}},
                "geometry":"Array.g[v.i]","textures":["v.i > 0 ? Array.t[v.i] : Texture.default"]},
              "controller.render.c":{"textures":["Texture.default"]}
            }}""";

    @Test
    void parsesGeometryTextureAndArrays() {
        JsonObject root = JsonParser.parseString(FILE).getAsJsonObject();
        List<RenderControllerDefinition> parsed = RenderControllerDefinition.parseFile(root);

        assertEquals(2, parsed.size());
        assertEquals("Geometry.default", parsed.get(0).geometry());
        assertEquals(List.of("geometry.default", "geometry.cape"), parsed.get(1).arrays().get("g"));
    }

    @Test
    void arrayExpressionsResolveToLowerCaseKeys() {
        RenderControllerDefinition controller = RenderControllerDefinition.parseFile(JsonParser.parseString(FILE).getAsJsonObject()).get(1);
        Map<String, java.util.List<Object>> arrays = controller.arrays();
        MolangContext context = new MolangContext() {
            @Override
            public Object query(String name, Object[] args) {
                return null;
            }

            @Override
            public List<Object> array(String name) {
                return arrays.get(name);
            }
        };
        MolangScope scope = new MolangScope(context);
        scope.setVariable("i", 1.0);

        assertEquals("geometry.cape", MolangExpression.compile(controller.geometry()).evaluate(scope));
        assertEquals("texture.cape", MolangExpression.compile(controller.texture()).evaluate(scope));
    }
}
