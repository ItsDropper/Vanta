package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.VantaUI;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shared language preference UI used by Overview and Settings.
 *
 * The preference is persisted immediately, while Localization remains on the
 * currently active locale until the next application startup.
 */
public final class LanguagePreferenceControl extends VBox {

    private final ComboBox<Localization.Language> languagePicker = new ComboBox<>();
    private final Label restartNotice = new Label();

    public LanguagePreferenceControl() {
        super(8);
        getStyleClass().addAll("settings-card", "language-preference-card");
        setPadding(new Insets(16));
        VantaUI.applySurface(this, VantaUI.SurfaceRole.CARD);

        Label title = new Label(Localization.text("language.title"));
        title.getStyleClass().add("settings-card-title");

        Label description = new Label(Localization.text("language.description"));
        description.getStyleClass().add("settings-card-description");
        description.setWrapText(true);

        languagePicker.getItems().setAll(Localization.getSupportedLanguages());
        languagePicker.setMaxWidth(320);
        languagePicker.setAccessibleText(Localization.text("language.accessibleLabel"));
        languagePicker.setAccessibleHelp(Localization.text("language.tooltip"));
        languagePicker.setTooltip(new Tooltip(Localization.text("language.tooltip")));

        String selectedCode = LauncherSettings.getPreferredLanguageCode();
        Localization.Language selected = Localization.getSupportedLanguages().stream()
                .filter(language -> language.code().equals(selectedCode))
                .findFirst()
                .orElse(Localization.getSupportedLanguages().getFirst());
        languagePicker.setValue(selected);

        restartNotice.getStyleClass().add("settings-card-description");
        restartNotice.setWrapText(true);
        restartNotice.setAccessibleText(Localization.text("language.restartRequired"));

        languagePicker.setOnAction(event -> {
            Localization.Language choice = languagePicker.getValue();
            if (choice == null) return;
            LauncherSettings.setPreferredLanguageCode(choice.code());
            updateRestartNotice(choice.code());
        });

        HBox pickerRow = new HBox(12, languagePicker);
        pickerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(languagePicker, Priority.NEVER);

        getChildren().addAll(title, description, pickerRow, restartNotice);
        updateRestartNotice(selectedCode);
    }

    private void updateRestartNotice(String selectedCode) {
        boolean restartRequired = !selectedCode.equals(Localization.getActiveLanguageCode());
        restartNotice.setText(restartRequired
                ? Localization.text("language.restartRequired")
                : Localization.text("language.restartNotRequired"));
        restartNotice.setVisible(restartRequired);
        restartNotice.setManaged(restartRequired);
    }
}
