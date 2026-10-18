package io.github.kintil555.bedrockpackloader.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Explains which Bedrock pack formats are accepted and where to put them. */
public final class BedrockHelpScreen extends Screen {
    private static final Component TITLE = Component.translatable("bedrockpackloader.help.title");
    private static final String[] SECTIONS = {"formats", "location", "enable", "notes"};
    private static final int CONTENT_WIDTH = 300;

    private final Screen lastScreen;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public BedrockHelpScreen(Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(TITLE, font);
        LinearLayout content = layout.addToContents(LinearLayout.vertical()).spacing(6);
        content.defaultCellSetting().alignHorizontallyCenter();
        for (String section : SECTIONS) {
            content.addChild(new MultiLineTextWidget(Component.translatable("bedrockpackloader.help." + section), font)
                    .setMaxWidth(CONTENT_WIDTH));
        }
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.arrangeElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(lastScreen);
    }
}
