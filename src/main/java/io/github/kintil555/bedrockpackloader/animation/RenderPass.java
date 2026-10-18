package io.github.kintil555.bedrockpackloader.animation;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One model drawn for an entity this frame: the result of an active Bedrock render controller. A null texture means
 * the player's skin; {@code rules} are the evaluated {@code part_visibility} entries in order (later ones win).
 */
public record RenderPass(Identifier model, @Nullable Identifier texture, List<Rule> rules) {
    /** A part visibility entry after Molang evaluation. */
    public record Rule(Pattern bones, boolean visible) {
    }

    /** True when the bone is drawn: it starts visible and each matching rule overrides the previous result. */
    public boolean isVisible(String bone) {
        boolean visible = true;
        for (Rule rule : this.rules) {
            if (rule.bones().matcher(bone).matches()) {
                visible = rule.visible();
            }
        }
        return visible;
    }
}
