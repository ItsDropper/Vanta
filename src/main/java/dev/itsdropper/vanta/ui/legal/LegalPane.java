package dev.itsdropper.vanta.ui.legal;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.kit.SegmentedControl;
import dev.itsdropper.vanta.ui.markdown.MarkdownRenderer;

import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** The Terms of Service and Privacy Policy in two tabs, with the checkbox that accepts both. */
public final class LegalPane extends VBox {

    private static final String TERMS = "terms";
    private static final String PRIVACY = "privacy";

    private final CheckBox accept = new CheckBox(Localization.text("legal.accept"));
    private final ScrollPane reader = new ScrollPane();
    private final MarkdownRenderer renderer = new MarkdownRenderer();

    public LegalPane(double readerHeight) {
        super(12);
        getStyleClass().add("legal-pane");

        SegmentedControl<String> tabs = new SegmentedControl<>(
                List.of(TERMS, PRIVACY),
                key -> Localization.text(TERMS.equals(key) ? "legal.tab.terms" : "legal.tab.privacy"),
                TERMS);

        reader.setFitToWidth(true);
        reader.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reader.getStyleClass().add("legal-reader");
        reader.setMinHeight(readerHeight);
        reader.setPrefHeight(readerHeight);
        VBox.setVgrow(reader, Priority.ALWAYS);
        show(TERMS);
        tabs.valueProperty().addListener((observable, previous, next) -> show(next));

        accept.getStyleClass().add("settings-checkbox");
        accept.setWrapText(true);
        accept.setAccessibleText(Localization.text("legal.accept"));

        getChildren().addAll(tabs, reader, accept);
    }

    public BooleanProperty acceptedProperty() {
        return accept.selectedProperty();
    }

    public boolean isAccepted() {
        return accept.isSelected();
    }

    private void show(String which) {
        LegalDocuments.Document document = TERMS.equals(which) ? LegalDocuments.terms() : LegalDocuments.privacy();
        VBox content = renderer.render(LegalDocuments.forDisplay(document.markdown()));
        content.setPadding(new Insets(16, 18, 16, 18));
        content.getStyleClass().addAll("legal-document", "mod-details-markdown");
        reader.setContent(new StackPane(content));
        reader.setVvalue(0);
    }
}
