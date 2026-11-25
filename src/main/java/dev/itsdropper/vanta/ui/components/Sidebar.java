package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.ui.Localization;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import dev.itsdropper.vanta.ui.VantaUI;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.io.InputStream;
import java.util.Properties;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    private static final double EXPANDED_WIDTH = 216;
    private static final double COMPACT_WIDTH = 56;

    public enum Page {
        HOME("Home", 1, IconView.Type.HOME, "Return to your launcher dashboard"),
        INSTANCES("Instances", 2, IconView.Type.INSTANCES, "Manage, back up and restore installations"),
        MODS("Mods", 3, IconView.Type.MODS, "Manage global mods and browse Modrinth"),
        STATE("State Center", 4, IconView.Type.SHIELD, "Inspect and repair Minecraft installations"),
        CRASH_LAB("Crash Lab", 0, IconView.Type.ACTIVITY, "Review launch history and compare crash evidence"),
        SKIN("Skin Changer", 5, IconView.Type.SKIN, "Manage your Minecraft appearance"),
        ACCOUNTS("Accounts", 6, IconView.Type.ACCOUNTS, "Switch or manage Microsoft accounts"),
        SETTINGS("Settings", 7, IconView.Type.SETTINGS, "Configure Vanta and its runtime"),
        DOWNLOADS("Download Center", 8, IconView.Type.DOWNLOAD, "Review active and completed downloads"),
        SERVERS("Server Manager", 9, IconView.Type.TERMINAL, "Manage and connect to Minecraft servers"),
        EXPERIMENTAL(Localization.text("sidebar.experimental"), 0, IconView.Type.EXPERIMENTAL, Localization.text("sidebar.experimental.description"));

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
                if (page.shortcutNumber > 0 && page.shortcutNumber == shortcutNumber) {
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
    private HBox branding;
    private Label logo;
    private Label build;
    private boolean compact;

    public Sidebar() {

        getStyleClass().add(
                "sidebar"
        );

        setPadding(expandedPadding());

        setSpacing(2);

        setPrefWidth(EXPANDED_WIDTH);
        setMinWidth(EXPANDED_WIDTH);
        setMaxWidth(EXPANDED_WIDTH);

        // ---------------------------------------------------------
        // BRAND
        // ---------------------------------------------------------

        SVGPath mark = new SVGPath();
        mark.setContent("M3 4h4.2L12 14.2 16.8 4H21L13.9 20h-3.8z");
        mark.getStyleClass().add("sidebar-mark");
        StackPane markBox = new StackPane(mark);
        markBox.setMinSize(24, 24);
        markBox.setPrefSize(24, 24);
        markBox.setMaxSize(24, 24);
        markBox.setMouseTransparent(true);

        logo = new Label("Vanta");
        logo.getStyleClass().add(
                "sidebar-logo"
        );

        branding = new HBox(
                VantaUI.spacing(2),
                markBox,
                logo
        );
        branding.setAlignment(Pos.CENTER_LEFT);
        branding.setPadding(brandingPadding());
        branding.setAccessibleText("Vanta");

        getChildren().add(
                branding
        );

        // ---------------------------------------------------------
        // NAVIGATION
        // Groups are separated by space rather than labels or rules.
        // ---------------------------------------------------------

        addButton(Page.HOME);
        addButton(Page.INSTANCES);
        addButton(Page.MODS);

        addGroupGap();
        addToolsGroup();

        addGroupGap();
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

        addButton(Page.EXPERIMENTAL);
        addButton(Page.SETTINGS);

        String version = loadVersion();
        build = new Label("v" + version);

        build.getStyleClass().add(
                "sidebar-build"
        );
        VantaUI.styleText(build, VantaUI.TextRole.METADATA);

        build.setWrapText(true);
        build.setAccessibleText(Localization.text("sidebar.versionPrefix") + version
                + ". Navigate with Control plus numbers one through nine.");
        build.setTooltip(new Tooltip(Localization.text("sidebar.shortcutsPrefix")
                + "Ctrl+3 Mods · Ctrl+4 State · Ctrl+5 Skin · Ctrl+6 Accounts · Ctrl+7 Settings · Ctrl+8 Downloads · Ctrl+9 Server Manager"));

        VBox.setMargin(build, new Insets(VantaUI.spacing(2), 0, 0, 0));
        getChildren().add(
                build
        );
    }

    private static Insets expandedPadding() {
        return new Insets(
                VantaUI.spacing(1),
                VantaUI.spacing(2),
                VantaUI.spacing(3),
                VantaUI.spacing(2)
        );
    }

    private static Insets brandingPadding() {
        return new Insets(
                VantaUI.spacing(1),
                VantaUI.spacing(2),
                VantaUI.spacing(3),
                VantaUI.spacing(2)
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

    private void addGroupGap() {
        Region gap = new Region();
        gap.setMinHeight(VantaUI.spacing(2));
        gap.setPrefHeight(VantaUI.spacing(2));
        gap.setMaxHeight(VantaUI.spacing(2));
        gap.setMouseTransparent(true);
        getChildren().add(gap);
    }

    private static final java.util.List<Page> TOOL_PAGES =
            java.util.List.of(Page.STATE, Page.CRASH_LAB, Page.SERVERS, Page.DOWNLOADS);

    private VBox toolsBody;
    private Button toolsToggle;
    private SVGPath toolsChevron;
    private boolean toolsOpen;

    private void addToolsGroup() {
        toolsChevron = new SVGPath();
        toolsChevron.setContent("M2 3.5 L5 6.5 L8 3.5");
        toolsChevron.getStyleClass().add("sidebar-chevron");
        toolsChevron.setFill(null);

        toolsToggle = new Button("Tools", toolsChevron);
        toolsToggle.setContentDisplay(ContentDisplay.RIGHT);
        toolsToggle.setGraphicTextGap(6);
        toolsToggle.setMaxWidth(Double.MAX_VALUE);
        toolsToggle.setAlignment(Pos.CENTER_LEFT);
        toolsToggle.getStyleClass().add("sidebar-group-toggle");
        toolsToggle.setAccessibleText("Tools. Toggle group.");
        toolsToggle.setOnAction(event -> setToolsOpen(!toolsOpen));

        toolsBody = new VBox(2);
        toolsBody.getStyleClass().add("sidebar-group-body");
        TOOL_PAGES.forEach(page -> addButton(page, toolsBody));

        getChildren().addAll(toolsToggle, toolsBody);
        setToolsOpen(false);
    }

    private void setToolsOpen(boolean open) {
        toolsOpen = open;
        boolean show = open || compact;
        toolsBody.setVisible(show);
        toolsBody.setManaged(show);
        toolsChevron.setRotate(open ? 0 : -90);
        toolsToggle.setVisible(!compact);
        toolsToggle.setManaged(!compact);
        toolsToggle.setAccessibleText("Tools, " + (open ? "expanded" : "collapsed"));
    }

    private void addButton(Page page) {
        addButton(page, null);
    }

    private void addButton(Page page, VBox container) {

        Button button =
                new Button(
                        page.title(),
                        IconView.create(page.iconType(), 18)
                );

        button.setGraphicTextGap(10);
        int shortcutNumber = page.shortcutNumber();
        if (shortcutNumber > 0) {
            button.setTooltip(new Tooltip(page.title() + "  ·  Ctrl+" + shortcutNumber));
            button.setAccessibleText(page.title() + " page. Keyboard shortcut Control plus " + shortcutNumber);
        } else {
            button.setTooltip(new Tooltip(page.title()));
            button.setAccessibleText(page.title() + " page.");
        }

        button.setMaxWidth(
                Double.MAX_VALUE
        );

        button.setAlignment(
                Pos.CENTER_LEFT
        );

        button.getStyleClass().add(
                "sidebar-button"
        );
        VantaUI.styleButton(button, VantaUI.ButtonVariant.NAVIGATION);

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

        if (container != null) {
            container.getChildren().add(button);
        } else {
            getChildren().add(button);
        }
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
        if (toolsBody != null) {
            setToolsOpen(toolsOpen);
        }

        if (compact) {
            getStyleClass().add("sidebar-compact");
            setPrefWidth(COMPACT_WIDTH);
            setMinWidth(COMPACT_WIDTH);
            setMaxWidth(COMPACT_WIDTH);
            logo.setVisible(false);
            logo.setManaged(false);
            branding.setAlignment(Pos.CENTER);
            branding.setPadding(new Insets(
                    VantaUI.spacing(1), 0, VantaUI.spacing(3), 0
            ));
            build.setVisible(false);
            build.setManaged(false);
        } else {
            getStyleClass().remove("sidebar-compact");
            setPrefWidth(EXPANDED_WIDTH);
            setMinWidth(EXPANDED_WIDTH);
            setMaxWidth(EXPANDED_WIDTH);
            logo.setVisible(true);
            logo.setManaged(true);
            branding.setAlignment(Pos.CENTER_LEFT);
            branding.setPadding(brandingPadding());
            build.setVisible(true);
            build.setManaged(true);
        }

        navigationButtons.forEach((page, button) -> {
            button.setText(compact ? "" : navigationTitles.get(page));
            button.setContentDisplay(compact
                    ? ContentDisplay.GRAPHIC_ONLY
                    : ContentDisplay.LEFT);
            button.setGraphicTextGap(compact ? 0 : 10);
            button.setAlignment(compact ? Pos.CENTER : Pos.CENTER_LEFT);
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

        if (toolsBody != null && TOOL_PAGES.contains(selectedPage) && !toolsOpen) {
            setToolsOpen(true);
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
