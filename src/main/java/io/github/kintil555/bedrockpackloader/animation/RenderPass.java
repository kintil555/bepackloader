package io.github.kintil555.bedrockpackloader.animation;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** One model drawn for an entity this frame: the result of an active Bedrock render controller. A null texture means the player's skin. */
public record RenderPass(Identifier model, @Nullable Identifier texture) {
}
