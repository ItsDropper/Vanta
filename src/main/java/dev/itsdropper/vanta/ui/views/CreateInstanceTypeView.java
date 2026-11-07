package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.components.PageHeader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CreateInstanceTypeView extends VBox {
    private final Runnable onBack, onCustom, onImport, onPreset, onModrinth;

    public CreateInstanceTypeView(Runnable onBack, Runnable onCustom, Runnable onImport,
                                  Runnable onPreset, Runnable onModrinth) {
        this.onBack = onBack;
        this.onCustom = onCustom;
        this.onImport = onImport;
        this.onPreset = onPreset;
        this.onModrinth = onModrinth;

        getStyleClass().addAll("page", "vanta-consistent-page", "create-instance-type-page");
        setPadding(new Insets(30, 36, 30, 36));
        setSpacing(24);

        Label eyebrow = new Label(Localization.text("createType.title"));
        eyebrow.getStyleClass().add("create-type-eyebrow");
        PageHeader header = new PageHeader(Localization.text("createType.heading"),
                Localization.text("createType.subtitle"), "page-title", "page-subtitle");
        VBox heading = new VBox(9, eyebrow, header);
        heading.getStyleClass().add("create-type-header");
        heading.setMaxWidth(900);

        Label section = new Label(Localization.text("createType.section"));
        section.getStyleClass().add("create-type-section-label");
        GridPane grid = new GridPane();
        grid.getStyleClass().add("create-type-grid");
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMaxWidth(1040);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(left, right);

        grid.add(option("01", "✦", "custom", "create-option-custom", onCustom), 0, 0);
        grid.add(option("02", "↗", "import", "create-option-import", onImport), 1, 0);
        grid.add(option("03", "▦", "preset", "create-option-preset", onPreset), 0, 1);
        grid.add(option("04", "⌕", "modrinth", "create-option-modrinth", onModrinth), 1, 1);

        VBox content = new VBox(14, section, grid);
        content.getStyleClass().add("create-type-content");
        content.setMaxWidth(1040);

        Button back = new Button(Localization.text("common.back"));
        back.getStyleClass().add("secondary-button");
        back.setPrefHeight(40);
        back.setMinWidth(112);
        back.setOnAction(event -> this.onBack.run());
        HBox footer = new HBox(back);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setMaxWidth(1040);
        footer.getStyleClass().add("create-type-footer");
        getChildren().addAll(heading, content, footer);
    }

    private Button option(String number, String symbol, String key, String variant, Runnable action) {
        String prefix = "createType." + key + ".";
        Button button = new Button();
        button.getStyleClass().addAll("create-type-option", variant);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(174);
        button.setPrefHeight(190);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(Localization.text(prefix + "title") + ". " + Localization.text(prefix + "description"));

        Label index = new Label(number);
        index.getStyleClass().add("create-type-number");
        Label icon = new Label(symbol);
        icon.getStyleClass().add("create-type-icon");
        HBox top = new HBox(10, index, icon);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(Localization.text(prefix + "title"));
        title.getStyleClass().add("create-type-option-title");
        title.setWrapText(true);
        Label description = new Label(Localization.text(prefix + "description"));
        description.getStyleClass().add("create-type-option-description");
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        Label meta = new Label(Localization.text(prefix + "meta"));
        meta.getStyleClass().add("create-type-option-meta");
        meta.setWrapText(true);

        VBox copy = new VBox(7, title, description, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label arrow = new Label("→");
        arrow.getStyleClass().add("create-type-option-arrow");
        HBox bottom = new HBox(copy, arrow);
        bottom.setAlignment(Pos.BOTTOM_CENTER);
        VBox graphic = new VBox(16, top, bottom);
        graphic.setPadding(new Insets(18, 19, 17, 19));
        graphic.getStyleClass().add("create-type-option-layout");
        button.setGraphic(graphic);
        button.setOnAction(event -> action.run());
        return button;
    }
}
