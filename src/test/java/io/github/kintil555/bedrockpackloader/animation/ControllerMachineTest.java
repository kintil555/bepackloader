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

    private static final String TIMED = """
            {"animation_controllers":{"controller.animation.timed":{
              "initial_state":"swing",
              "states":{
                "swing":{"animations":["swing_anim"],"transitions":[{"done":"q.any_animation_finished"}]},
                "done":{"animations":["idle_anim"],"transitions":[{"swing":"q.state_time > 5"}]}
              }}}}""";

    private static final class Clock implements io.github.kintil555.bedrockpackloader.molang.MolangContext, ControllerTimes {
        double life;
        double stateTime;
        boolean any;

        @Override
        public Object query(String name, Object[] args) {
            return switch (name) {
                case "life_time" -> life;
                case "state_time" -> stateTime;
                case "any_animation_finished" -> any ? 1.0 : 0.0;
                default -> null;
            };
        }

        @Override
        public void setControllerTimes(double stateTime, double animTime, boolean anyAnimationFinished, boolean allAnimationsFinished) {
            this.stateTime = stateTime;
            this.any = anyAnimationFinished;
        }
    }

    @Test
    void finishedAnimationTriggersTransition() {
        JsonObject root = JsonParser.parseString(TIMED).getAsJsonObject();
        Map<String, String> ids = Map.of("swing_anim", "animation.swing", "idle_anim", "animation.idle");
        ControllerMachine machine = new ControllerMachine(AnimationControllerDefinition.parseFile(root).get(0), ids::get,
                MolangExpression::compile, id -> id.equals("animation.swing") ? new ControllerMachine.Timing(0.5, false) : null);
        Clock clock = new Clock();
        MolangScope scope = new MolangScope(clock);

        clock.life = 10;
        machine.step(new Object(), scope);
        clock.life = 10.3;
        machine.step(new Object(), scope);
        assertEquals("swing", machine.state());
        clock.life = 10.6;
        machine.step(new Object(), scope);
        assertEquals("done", machine.state());
        assertEquals(0.0, clock.stateTime);
    }

    @Test
    void equipmentQueriesUseTheSlotLookup() {
        EntityQuerySnapshot.Equipment equipment = new EntityQuerySnapshot.Equipment() {
            @Override
            public String itemId(String slot) {
                return slot.equals("slot.weapon.mainhand") ? "minecraft:diamond_sword" : null;
            }

            @Override
            public boolean hasTag(String slot, String tag) {
                return slot.equals("slot.weapon.mainhand") && tag.equals("minecraft:is_sword");
            }
        };
        MolangScope scope = new MolangScope(new EntityQuerySnapshot(true, false, false, false, false, false, false,
                0, 0, 0, 0, 0, 0, PackSettings.NONE, 0.5, 1, 2, 3, "Steve", equipment));

        assertEquals(1.0, MolangExpression.compile("q.is_item_name_any('slot.weapon.mainhand', 0, 'minecraft:diamond_sword')").evaluateNumber(scope));
        assertEquals(0.0, MolangExpression.compile("q.is_item_name_any('slot.weapon.offhand', 0, 'minecraft:diamond_sword')").evaluateNumber(scope));
        assertEquals(1.0, MolangExpression.compile("q.equipped_item_any_tag('slot.weapon.mainhand', 'minecraft:is_axe', 'minecraft:is_sword')").evaluateNumber(scope));
        assertEquals(1.0, MolangExpression.compile("q.is_name_any('Steve')").evaluateNumber(scope));
        assertEquals(0.5, MolangExpression.compile("q.modified_move_speed").evaluateNumber(scope));
        assertEquals(2.0, MolangExpression.compile("q.position(1)").evaluateNumber(scope));
    }
}
