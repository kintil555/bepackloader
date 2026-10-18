package io.github.kintil555.bedrockpackloader.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.kintil555.bedrockpackloader.index.AnimationControllerDefinition;
import io.github.kintil555.bedrockpackloader.molang.MolangExpression;
import io.github.kintil555.bedrockpackloader.molang.MolangScope;
import io.github.kintil555.bedrockpackloader.pack.PackSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ControllerMachineTest {
    private static final String CONTROLLER = """
            {"animation_controllers":{"controller.animation.test":{
              "initial_state":"idle",
              "states":{
                "idle":{"animations":["idle_anim","extra"],"transitions":[{"walk":"q.ground_speed > 0.5"}],"blend_transition":0.25},
                "walk":{"animations":["walk_anim"],"transitions":[{"idle":"q.ground_speed <= 0.5"}],
                        "on_entry":["v.entered = 1;"],"on_exit":["v.entered = 0;"]}
              }}}}""";

    private static AnimationControllerDefinition definition() {
        JsonObject root = JsonParser.parseString(CONTROLLER).getAsJsonObject();
        return AnimationControllerDefinition.parseFile(root).get(0);
    }

    private static MolangScope scope(double speed) {
        return new MolangScope(new EntityQuerySnapshot(true, false, false, false, false, false, false,
                speed, 0, 0, 0, 0, 0, PackSettings.NONE));
    }

    private static ControllerMachine machine() {
        Map<String, String> ids = Map.of("idle_anim", "animation.idle", "walk_anim", "animation.walk");
        return new ControllerMachine(definition(), ids::get, MolangExpression::compile);
    }

    @Test
    void startsInInitialStateAndReportsUnresolvedNames() {
        ControllerMachine machine = machine();
        ControllerMachine.Step step = machine.step(new Object(), scope(0));

        assertEquals("idle", machine.state());
        assertFalse(step.stateChanged());
        assertEquals(2, step.animations().size());
        assertEquals("animation.idle", step.animations().get(0));
        assertEquals(null, step.animations().get(1));
        assertEquals(5, step.blendTicks());
    }

    @Test
    void transitionsWhenConditionHolds() {
        ControllerMachine machine = machine();
        machine.step(new Object(), scope(0));

        MolangScope moving = scope(2);
        ControllerMachine.Step walking = machine.step(new Object(), moving);
        assertEquals("walk", machine.state());
        assertTrue(walking.stateChanged());
        assertEquals("animation.walk", walking.animations().get(0));
        assertEquals(1.0, moving.variable("entered") instanceof Double d ? d : -1.0);

        ControllerMachine.Step idle = machine.step(new Object(), scope(0));
        assertEquals("idle", machine.state());
        assertTrue(idle.stateChanged());
    }

    @Test
    void sameFrameReturnsSameStep() {
        ControllerMachine machine = machine();
        Object frame = new Object();
        ControllerMachine.Step first = machine.step(frame, scope(0));
        ControllerMachine.Step again = machine.step(frame, scope(5));

        assertTrue(first == again);
        assertEquals("idle", machine.state());
    }

    @Test
    void maxAnimationsCountsLargestState() {
        assertEquals(2, ControllerMachine.maxAnimations(definition()));
    }

    @Test
    void snapshotAnswersQueriesAndPackSettings() {
        PackSettings settings = new PackSettings(Map.of("ns:toggle", "true", "ns:drop", "fox"));
        MolangScope scope = new MolangScope(new EntityQuerySnapshot(true, false, false, false, false, false, false,
                1.5, 0, 0, 0, 0, 0, settings));

        assertEquals(26.5, MolangExpression.compile("q.is_pack_setting_enabled('ns:toggle') ? 26.5 : 24.5").evaluateNumber(scope));
        assertEquals(1.0, MolangExpression.compile("q.is_pack_setting_selected('ns:drop', 'fox')").evaluateNumber(scope));
        assertEquals(0.0, MolangExpression.compile("q.is_pack_setting_selected('ns:drop', 'slime')").evaluateNumber(scope));
        assertEquals(1.5, MolangExpression.compile("q.ground_speed").evaluateNumber(scope));
    }
}
