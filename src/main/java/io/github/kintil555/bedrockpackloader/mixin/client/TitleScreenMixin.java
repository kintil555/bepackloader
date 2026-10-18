package io.github.kintil555.bedrockpackloader.mixin.client;

import io.github.kintil555.bedrockpackloader.screen.BedrockHelpScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "?" button right of the small icon row (friends, language, accessibility) on the title screen. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    private FriendsButton friends;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bedrockpackloader$addHelpButton(CallbackInfo ci) {
        Screen screen = this;
        // the row has three 20px buttons spaced by 4px; the new one goes after the third
        int x = friends.getX() + 3 * 24;
        addRenderableWidget(Button.builder(Component.literal("?"), button -> Minecraft.getInstance().gui.setScreen(new BedrockHelpScreen(screen)))
                .bounds(x, friends.getY(), 20, 20)
                .tooltip(Tooltip.create(Component.translatable("bedrockpackloader.help.tooltip")))
                .build());
    }
}
