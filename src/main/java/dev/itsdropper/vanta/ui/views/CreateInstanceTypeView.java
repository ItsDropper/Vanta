package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.components.IconView;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.kit.CoverArt;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
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
    private final Runnable onBack, onCustom, onImport, onPreset, onModrinth, onVantaProfiles;

    public CreateInstanceTypeView(Runnable onBack, Runnable onCustom, Runnable onImport,
                                  Runnable onPreset, Runnable onModrinth, Runnable onVantaProfiles) {
        this.onBack = onBack;
        this.onCustom = onCustom;
        this.onImport = onImport;
        this.onPreset = onPreset;
        this.onModrinth = onModrinth;
        this.onVantaProfiles = onVantaProfiles;

        getStyleClass().addAll("page", "vanta-consistent-page", "create-instance-type-page");
        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);
        setSpacing(24);

        PageHeader header = new PageHeader(Localization.text("createType.heading"),
                Localization.text("createType.subtitle"), "page-title", "page-subtitle");
        VBox heading = new VBox(9, header);
        heading.getStyleClass().add("create-type-header");
        heading.setMaxWidth(900);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("create-type-grid");
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMaxWidth(1040);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setFillWidth(true);
        left.setMinWidth(0);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setFillWidth(true);
        right.setMinWidth(0);
        grid.getColumnConstraints().setAll(left, right);

        java.util.List<VBox> options = java.util.List.of(
                option(IconView.Type.PLUS, "custom", "create-custom", "fabric", onCustom),
                option(IconView.Type.UPLOAD, "import", "create-import", "forge", onImport),
                option(IconView.Type.PACKAGE, "preset", "create-preset", "quilt", onPreset),
                option(IconView.Type.SEARCH, "modrinth", "create-modrinth", "neoforge", onModrinth),
                option(IconView.Type.STAR, "vantaProfiles", "create-profiles", "fabric", onVantaProfiles)
        );
        options.forEach(grid.getChildren()::add);
        Runnable adaptGrid = () -> {
            boolean narrow = grid.getWidth() < 620;
            ColumnConstraints full = new ColumnConstraints();
            full.setHgrow(Priority.ALWAYS);
            full.setFillWidth(true);
            full.setMinWidth(0);
            if (narrow) {
                grid.getColumnConstraints().setAll(full);
                for (int index = 0; index < options.size(); index++) {
                    GridPane.setColumnIndex(options.get(index), 0);
                    GridPane.setRowIndex(options.get(index), index);
                }
            } else {
                grid.getColumnConstraints().setAll(left, right);
                for (int index = 0; index < options.size(); index++) {
                    GridPane.setColumnIndex(options.get(index), index % 2);
                    GridPane.setRowIndex(options.get(index), index / 2);
                }
            }
        };
        grid.widthProperty().addListener((observable, oldWidth, newWidth) -> adaptGrid.run());
        adaptGrid.run();

        VBox content = new VBox(14, grid);
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

    private VBox option(IconView.Type icon, String key, String seed, String loader, Runnable action) {
        String prefix = "createType." + key + ".";
        VBox button = new VBox();
        button.getStyleClass().addAll("create-tile", "create-option-" + key);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(true);
        button.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        button.setAccessibleText(Localization.text(prefix + "title") + ". " + Localization.text(prefix + "description"));

        CoverArt art = new CoverArt(seed, loader).sized(10, 110);
        art.setMinHeight(110);
        art.setPrefHeight(110);
        art.setMaxHeight(110);
        Region shade = new Region();
        shade.getStyleClass().add("create-tile-shade");
        StackPane plate = new StackPane(IconView.create(icon, 20));
        plate.getStyleClass().add("create-tile-plate");
        plate.setMinSize(44, 44);
        plate.setPrefSize(44, 44);
        plate.setMaxSize(44, 44);
        StackPane.setAlignment(plate, Pos.BOTTOM_LEFT);
        StackPane.setMargin(plate, new Insets(0, 0, 14, 16));
        StackPane header = new StackPane(art, shade, plate);
        header.getStyleClass().add("create-tile-header");
        header.setMinHeight(110);
        header.setPrefHeight(110);
        header.setMaxHeight(110);

        art.prefWidthProperty().bind(header.widthProperty());
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(header.widthProperty());
        clip.setHeight(110);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        header.setClip(clip);

        Label title = new Label(Localization.text(prefix + "title"));
        title.getStyleClass().add("create-tile-title");
        title.setWrapText(true);
        Label description = new Label(Localization.text(prefix + "description"));
        description.getStyleClass().add("create-tile-description");
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        VBox copy = new VBox(4, title, description);
        copy.setPadding(new Insets(14, 16, 16, 16));
        copy.setMinWidth(0);

        button.getChildren().addAll(header, copy);
        header.maxWidthProperty().bind(button.widthProperty().subtract(2));
        button.setOnMouseClicked(event -> action.run());
        button.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                action.run();
            }
        });
        return button;
    }
}
