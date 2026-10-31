package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
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
        HOME("Home", 1, IconView.Type.HOME, "Return to your launcher dashboard"),
        INSTANCES("Instances", 2, IconView.Type.INSTANCES, "Manage, back up and restore installations"),
        MODS("Mods", 3, IconView.Type.MODS, "Manage global mods and browse Modrinth"),
        STATE("State Center", 4, IconView.Type.SHIELD, "Inspect and repair Minecraft installations"),
        SKIN("Skin Changer", 5, IconView.Type.SKIN, "Manage your Minecraft appearance"),
        ACCOUNTS("Accounts", 6, IconView.Type.ACCOUNTS, "Switch or manage Microsoft accounts"),
        SETTINGS("Settings", 7, IconView.Type.SETTINGS, "Configure Vanta and its runtime"),
        DOWNLOADS("Download Center", 8, IconView.Type.DOWNLOAD, "Review active and completed downloads"),
        SERVERS("Server Manager", 9, IconView.Type.TERMINAL, "Manage and connect to Minecraft servers");

        private final String title;
        private final int shortcutNumber;
        private final IconView.Type iconType;
        private final String description;

        Page(String title, int shortcutNumber, IconView.Type iconType, String description) {
            this.title = title;
            this.shortcutNumber = shortcutNumber;
            this.iconType = iconType;
            this.description = description;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
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
    private final Map<Page, String> navigationTitles = new EnumMap<>(Page.class);
    private final java.util.List<Label> sectionLabels = new java.util.ArrayList<>();
    private VBox branding;
    private Label logo;
    private Label subtitle;
    private Label build;
    private boolean compact;

    public Sidebar() {

        getStyleClass().add(
                "sidebar"
        );

        setPadding(
                new Insets(22, 14, 18, 14)
        );

        setSpacing(6);

        setPrefWidth(224);
        setMinWidth(204);

        // ---------------------------------------------------------
        // BRAND
        // ---------------------------------------------------------

        logo = new Label("VANTA");

        logo.getStyleClass().add(
                "sidebar-logo"
        );

        subtitle = new Label("MINECRAFT LAUNCHER");

        subtitle.getStyleClass().add(
                "sidebar-subtitle"
        );

        branding = new VBox(
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

        addSectionLabel("OVERVIEW");
        addButton(Page.HOME);

        addSectionLabel("LIBRARY");
        addButton(Page.INSTANCES);
        addButton(Page.MODS);

        addSectionLabel("MANAGE");
        addButton(Page.STATE);
        addButton(Page.SERVERS);
        addButton(Page.DOWNLOADS);

        addSectionLabel("PROFILE");
        addButton(Page.SKIN);
        addButton(Page.ACCOUNTS);

        Region spacer =
                new Region();

        VBox.setVgrow(
                spacer,
                Priority.ALWAYS
        );

        getChildren().add(
                spacer
        );

        addButton(Page.SETTINGS);

        build = new Label(
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

    private void addSectionLabel(String title) {
        Label label = new Label(title);
        sectionLabels.add(label);
        label.getStyleClass().add("sidebar-section-label");
        label.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(label, new Insets(12, 8, 2, 8));
        getChildren().add(label);
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
        navigationTitles.put(page, page.title());

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

    /**
     * Switches the navigation rail between its full and icon-only layouts.
     * Tooltips and accessible names remain available in compact mode.
     */
    public void setCompact(boolean compact) {
        if (this.compact == compact) {
            return;
        }
        this.compact = compact;

        if (compact) {
            getStyleClass().add("sidebar-compact");
            setPadding(new Insets(16, 8, 14, 8));
            setPrefWidth(76);
            setMinWidth(72);
            logo.setText("V");
            subtitle.setVisible(false);
            subtitle.setManaged(false);
            branding.setPadding(new Insets(4, 0, 20, 0));
            build.setVisible(false);
            build.setManaged(false);
            sectionLabels.forEach(label -> {
                label.setVisible(false);
                label.setManaged(false);
            });
        } else {
            getStyleClass().remove("sidebar-compact");
            setPadding(new Insets(22, 14, 18, 14));
            setPrefWidth(224);
            setMinWidth(204);
            logo.setText("VANTA");
            subtitle.setVisible(true);
            subtitle.setManaged(true);
            branding.setPadding(new Insets(4, 8, 28, 8));
            build.setVisible(true);
            build.setManaged(true);
            sectionLabels.forEach(label -> {
                label.setVisible(true);
                label.setManaged(true);
            });
        }

        navigationButtons.forEach((page, button) -> {
            button.setText(compact ? "" : navigationTitles.get(page));
            button.setContentDisplay(compact
                    ? ContentDisplay.GRAPHIC_ONLY
                    : ContentDisplay.LEFT);
            button.setGraphicTextGap(compact ? 0 : 10);
            button.setAlignment(compact ? Pos.CENTER : Pos.CENTER_LEFT);
            button.setMinHeight(compact ? 42 : Region.USE_COMPUTED_SIZE);
        });
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
