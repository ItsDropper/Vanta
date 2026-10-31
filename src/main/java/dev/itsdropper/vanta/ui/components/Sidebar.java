package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.util.Properties;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    public enum Page {
        HOME("Home", 1, IconView.Type.HOME),
        INSTANCES("Instances", 2, IconView.Type.INSTANCES),
        MODS("Mods", 3, IconView.Type.MODS),
        STATE("State", 4, IconView.Type.SHIELD),
        SKIN("Skin changer", 5, IconView.Type.SKIN),
        ACCOUNTS("Accounts", 6, IconView.Type.ACCOUNTS),
        SETTINGS("Settings", 7, IconView.Type.SETTINGS),
        DOWNLOADS("Downloads", 8, IconView.Type.DOWNLOAD),
        SERVERS("Server Manager", 9, IconView.Type.TERMINAL);

        private final String title;
        private final int shortcutNumber;
        private final IconView.Type iconType;

        Page(String title, int shortcutNumber, IconView.Type iconType) {
            this.title = title;
            this.shortcutNumber = shortcutNumber;
            this.iconType = iconType;
        }

        public String title() {
            return title;
        }

        public int shortcutNumber() {
            return shortcutNumber;
        }

        public IconView.Type iconType() {
            return iconType;
        }

        public static Page fromShortcutNumber(int shortcutNumber) {
            for (Page page : values()) {
                if (page.shortcutNumber == shortcutNumber) {
                    return page;
                }
            }
            return null;
        }
    }

    private Consumer<Page> pageListener;
    private final Map<Page, Button> navigationButtons =
            new EnumMap<>(Page.class);

    public Sidebar() {

        getStyleClass().add(
                "sidebar"
        );

        setPadding(
                new Insets(24, 16, 24, 16)
        );

        setSpacing(8);

        setPrefWidth(210);

        // ---------------------------------------------------------
        // BRAND
        // ---------------------------------------------------------

        Label logo =
                new Label("VANTA");

        logo.getStyleClass().add(
                "sidebar-logo"
        );

        Label subtitle =
                new Label("MINECRAFT LAUNCHER");

        subtitle.getStyleClass().add(
                "sidebar-subtitle"
        );

        VBox branding =
                new VBox(
                        4,
                        logo,
                        subtitle
                );

        branding.setPadding(
                new Insets(
                        4,
                        8,
                        28,
                        8
                )
        );

        getChildren().add(
                branding
        );

        // ---------------------------------------------------------
        // NAVIGATION
        // ---------------------------------------------------------

        addButton(Page.HOME);
        addButton(Page.INSTANCES);
        addButton(Page.MODS);
        addButton(Page.STATE);
        addButton(Page.SKIN);
        addButton(Page.DOWNLOADS);
        addButton(Page.SERVERS);

        Region spacer =
                new Region();

        VBox.setVgrow(
                spacer,
                Priority.ALWAYS
        );

        getChildren().add(
                spacer
        );

        addButton(Page.ACCOUNTS);
        addButton(Page.SETTINGS);

        Label build =
                new Label(
                        "VANTA "
                                + loadVersion()
                                + "  •  LOCAL"
                );

        build.getStyleClass().add(
                "sidebar-build"
        );

        build.setWrapText(true);
        build.setAccessibleText("Vanta version " + loadVersion()
                + ". Navigate with Control plus numbers one through nine.");
        build.setTooltip(new Tooltip("Ctrl+N New instance · Ctrl+1 Home · Ctrl+2 Instances · "
                + "Ctrl+3 Mods · Ctrl+4 State · Ctrl+5 Skin · Ctrl+6 Accounts · Ctrl+7 Settings · Ctrl+8 Downloads · Ctrl+9 Server Manager"));

        getChildren().add(
                build
        );
    }

    private String loadVersion() {

        Properties properties =
                new Properties();

        try (InputStream stream =
                     getClass()
                             .getResourceAsStream(
                                     "/version.properties"
                             )) {

            if (stream != null) {

                properties.load(
                        stream
                );

                String version =
                        properties.getProperty(
                                "version"
                        );

                if (version != null
                        && !version.contains("${")) {

                    return version;
                }
            }

        } catch (Exception ignored) {
        }

        return "1.1.8";
    }

    private void addButton(Page page) {

        Button button =
                new Button(
                        page.title(),
                        IconView.create(page.iconType(), 18)
                );

        button.setGraphicTextGap(10);
        int shortcutNumber = page.shortcutNumber();
        button.setTooltip(new Tooltip(page.title() + "  ·  Ctrl+" + shortcutNumber));
        button.setAccessibleText(
                page.title() + " page. Keyboard shortcut Control plus " + shortcutNumber
        );

        button.setMaxWidth(
                Double.MAX_VALUE
        );

        button.setAlignment(
                Pos.CENTER_LEFT
        );

        button.getStyleClass().add(
                "sidebar-button"
        );

        navigationButtons.put(
                page,
                button
        );

        button.setOnAction(
                event -> {

                    setSelectedPage(
                            page
                    );

                    if (pageListener != null) {
                        pageListener.accept(page);
                    }
                }
        );

        getChildren().add(
                button
        );
    }

    public void setOnPageSelected(
            Consumer<Page> listener
    ) {

        this.pageListener = listener;

        setSelectedPage(
                Page.HOME
        );
    }

    public void setSelectedPage(
            Page selectedPage
    ) {

        for (
                Map.Entry<Page, Button> entry
                : navigationButtons.entrySet()
        ) {

            entry.getValue()
                    .getStyleClass()
                    .remove(
                            "sidebar-button-active"
                    );
        }

        Button selectedButton =
                navigationButtons.get(
                        selectedPage
                );

        if (selectedButton != null) {

            selectedButton
                    .getStyleClass()
                    .add(
                            "sidebar-button-active"
                    );
        }
    }
}
