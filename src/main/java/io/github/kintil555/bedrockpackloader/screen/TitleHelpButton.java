package io.github.kintil555.bedrockpackloader.screen;

import io.github.kintil555.bedrockpackloader.BedrockPackLoaderMod;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Adds a "?" button to the row of small icon buttons on the title screen. It runs in a late Fabric screen phase,
 * after mods such as Mod Menu added their widgets, and takes the first free 20px slot to the right of the row, so it
 * never covers a button from another mod.
 */
public final class TitleHelpButton {
    private static final Identifier LATE_PHASE = Identifier.fromNamespaceAndPath(BedrockPackLoaderMod.MOD_ID, "title_help_late");
    private static final int SIZE = 20;
    private static final int STEP = SIZE + 4;
    private static final int VANILLA_ICONS = 3;
    private static final int MAX_SLOTS = 8;

    private TitleHelpButton() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
        ScreenEvents.AFTER_INIT.register(LATE_PHASE, (client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                install(screen, width);
            }
        });
    }

    private static void install(Screen screen, int screenWidth) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        FriendsButton anchor = null;
        for (AbstractWidget widget : widgets) {
            if (widget instanceof FriendsButton friends) {
                anchor = friends;
                break;
            }
        }
        if (anchor == null) {
            return;
        }
        int y = anchor.getY();
        int x = anchor.getX() + VANILLA_ICONS * STEP;
        for (int slot = 0; slot < MAX_SLOTS && x + SIZE <= screenWidth; slot++, x += STEP) {
            if (isFree(widgets, x, y)) {
                widgets.add(Button.builder(Component.literal("?"), button -> Minecraft.getInstance().gui.setScreen(new BedrockHelpScreen(screen)))
                        .bounds(x, y, SIZE, SIZE)
                        .tooltip(Tooltip.create(Component.translatable("bedrockpackloader.help.tooltip")))
                        .build());
                return;
            }
        }
    }

    private static boolean isFree(List<AbstractWidget> widgets, int x, int y) {
        for (AbstractWidget widget : widgets) {
            if (!widget.visible) {
                continue;
            }
            boolean overlapsX = x < widget.getX() + widget.getWidth() && x + SIZE > widget.getX();
            boolean overlapsY = y < widget.getY() + widget.getHeight() && y + SIZE > widget.getY();
            if (overlapsX && overlapsY) {
                return false;
            }
        }
        return true;
    }
}
