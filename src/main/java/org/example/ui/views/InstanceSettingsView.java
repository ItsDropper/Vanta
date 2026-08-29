package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.model.InstanceSettings;

public class InstanceSettingsView extends VBox {

    private final Instance instance;
    private final Runnable onBack;
    private final Runnable onMods;

    private final Slider ramSlider;
    private final Label ramValueLabel;

    private final TextField widthField;
    private final TextField heightField;
    private final TextField javaPathField;

    private final TextArea javaArgumentsArea;
    private final TextArea gameArgumentsArea;

    private final CheckBox fullscreenCheck;

    private final Label statusLabel;

    public InstanceSettingsView(
            Instance instance,
            Runnable onBack,
            Runnable onMods
    ) {

        this.instance =
                instance;

        this.onBack =
                onBack;

        this.onMods =
                onMods;

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                24
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Button backButton =
                new Button("← BACK");

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        Button modsButton =
                new Button(
                        "MODS"
                );

        modsButton.getStyleClass().add(
                "primary-button"
        );

        modsButton.setOnAction(
                event -> onMods.run()
        );

        HBox navigation =
                new HBox(
                        10,
                        backButton,
                        modsButton
                );

        Label title =
                new Label(
                        instance.getName()
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Configure how this instance launches."
                );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox headerText =
                new VBox(
                        5,
                        title,
                        subtitle
                );

        VBox header =
                new VBox(
                        14,
                        navigation,
                        headerText
                );

        // ---------------------------------------------------------
        // MEMORY
        // ---------------------------------------------------------

        Label memoryTitle =
                createSectionTitle(
                        "MEMORY"
                );

        ramValueLabel =
                new Label(
                        "4096 MB"
                );

        ramValueLabel.getStyleClass().add(
                "instance-setting-value"
        );

        ramSlider =
                new Slider(
                        1024,
                        16384,
                        4096
                );

        ramSlider.setBlockIncrement(
                512
        );

        ramSlider.setMajorTickUnit(
                4096
        );

        ramSlider.setMinorTickCount(
                7
        );

        ramSlider.setSnapToTicks(
                true
        );

        ramSlider.setShowTickMarks(
                true
        );

        ramSlider.setShowTickLabels(
                true
        );

        ramSlider.setMaxWidth(
                Double.MAX_VALUE
        );

        ramSlider.getStyleClass().add(
                "ram-slider"
        );

        ramSlider.valueProperty().addListener(
                (observable, oldValue, newValue) -> {

                    int ram =
                            snapRam(
                                    newValue.doubleValue()
                            );

                    ramValueLabel.setText(
                            ram + " MB"
                    );
                }
        );

        VBox ramBox =
                new VBox(
                        10,
                        ramValueLabel,
                        ramSlider
                );

        ramBox.setMaxWidth(
                Double.MAX_VALUE
        );

        VBox memoryCard =
                createCard(
                        ramBox
                );

        // ---------------------------------------------------------
        // JAVA
        // ---------------------------------------------------------

        Label javaTitle =
                createSectionTitle(
                        "JAVA"
                );

        javaPathField =
                new TextField();

        javaPathField.setPromptText(
                "Automatic"
        );

        javaPathField.getStyleClass().add(
                "create-field"
        );

        javaArgumentsArea =
                new TextArea();

        javaArgumentsArea.setPromptText(
                "Optional JVM arguments"
        );

        javaArgumentsArea.setWrapText(
                true
        );

        javaArgumentsArea.setPrefRowCount(
                3
        );

        javaArgumentsArea.getStyleClass().add(
                "create-field"
        );

        VBox javaCard =
                createCard(
                        createInput(
                                "Java executable",
                                javaPathField
                        ),
                        createInput(
                                "Java arguments",
                                javaArgumentsArea
                        )
                );

        // ---------------------------------------------------------
        // GAME
        // ---------------------------------------------------------

        Label gameTitle =
                createSectionTitle(
                        "GAME"
                );

        gameArgumentsArea =
                new TextArea();

        gameArgumentsArea.setPromptText(
                "Optional Minecraft arguments"
        );

        gameArgumentsArea.setWrapText(
                true
        );

        gameArgumentsArea.setPrefRowCount(
                3
        );

        gameArgumentsArea.getStyleClass().add(
                "create-field"
        );

        widthField =
                new TextField();

        heightField =
                new TextField();

        widthField.setPromptText(
                "1920"
        );

        heightField.setPromptText(
                "1080"
        );

        widthField.getStyleClass().add(
                "create-field"
        );

        heightField.getStyleClass().add(
                "create-field"
        );

        VBox widthInput =
                createInput(
                        "Width",
                        widthField
                );

        VBox heightInput =
                createInput(
                        "Height",
                        heightField
                );

        HBox.setHgrow(
                widthInput,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                heightInput,
                Priority.ALWAYS
        );

        HBox resolution =
                new HBox(
                        10,
                        widthInput,
                        heightInput
                );

        resolution.setAlignment(
                Pos.CENTER_LEFT
        );

        fullscreenCheck =
                new CheckBox(
                        "Fullscreen"
                );

        fullscreenCheck.getStyleClass().add(
                "settings-checkbox"
        );

        VBox gameCard =
                createCard(
                        createInput(
                                "Game arguments",
                                gameArgumentsArea
                        ),
                        resolution,
                        fullscreenCheck
                );

        // ---------------------------------------------------------
        // DIRECTORY
        // ---------------------------------------------------------

        Label directoryTitle =
                createSectionTitle(
                        "DIRECTORY"
                );

        Label directory =
                new Label(
                        instance.getDirectory()
                                .toString()
                );

        directory.setWrapText(
                true
        );

        directory.getStyleClass().add(
                "instance-directory"
        );

        VBox directoryCard =
                createCard(
                        directory
                );

        // ---------------------------------------------------------
        // ACTIONS
        // ---------------------------------------------------------

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "create-status"
        );

        Button cancelButton =
                new Button(
                        "CANCEL"
                );

        cancelButton.getStyleClass().add(
                "secondary-button"
        );

        cancelButton.setOnAction(
                event -> onBack.run()
        );

        Button saveButton =
                new Button(
                        "SAVE CHANGES"
                );

        saveButton.getStyleClass().add(
                "primary-button"
        );

        saveButton.setOnAction(
                event -> saveSettings()
        );

        HBox actions =
                new HBox(
                        10,
                        statusLabel,
                        cancelButton,
                        saveButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        HBox.setHgrow(
                statusLabel,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

        VBox content =
                new VBox(
                        24,

                        header,

                        memoryTitle,
                        memoryCard,

                        javaTitle,
                        javaCard,

                        gameTitle,
                        gameCard,

                        directoryTitle,
                        directoryCard,

                        actions
                );

        content.setPadding(
                new Insets(36)
        );

        content.setFillWidth(
                true
        );

        // ---------------------------------------------------------
        // SCROLL
        // ---------------------------------------------------------

        ScrollPane scrollPane =
                new ScrollPane(
                        content
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setFitToHeight(
                false
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "settings-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        getChildren().add(
                scrollPane
        );

        loadSettings();
    }

    // =============================================================
    // LOAD
    // =============================================================

    private void loadSettings() {

        try {

            InstanceSettings settings =
                    InstanceManager.loadSettings(
                            instance
                    );

            int ram =
                    settings.getRamMb();

            ramSlider.setValue(
                    clampRam(ram)
            );

            ramValueLabel.setText(
                    snapRam(
                            ramSlider.getValue()
                    )
                            + " MB"
            );

            widthField.setText(
                    String.valueOf(
                            settings.getWidth()
                    )
            );

            heightField.setText(
                    String.valueOf(
                            settings.getHeight()
                    )
            );

            javaPathField.setText(
                    settings.getJavaPath()
            );

            javaArgumentsArea.setText(
                    settings.getJavaArguments()
            );

            gameArgumentsArea.setText(
                    settings.getGameArguments()
            );

            fullscreenCheck.setSelected(
                    settings.isFullscreen()
            );

            statusLabel.setText(
                    "Settings loaded."
            );

        } catch (Exception e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to load settings."
            );
        }
    }

    // =============================================================
    // SAVE
    // =============================================================

    private void saveSettings() {

        try {

            int ram =
                    snapRam(
                            ramSlider.getValue()
                    );

            int width =
                    parsePositive(
                            widthField.getText(),
                            "Width"
                    );

            int height =
                    parsePositive(
                            heightField.getText(),
                            "Height"
                    );

            InstanceSettings settings =
                    new InstanceSettings();

            settings.setRamMb(
                    ram
            );

            settings.setWidth(
                    width
            );

            settings.setHeight(
                    height
            );

            settings.setJavaPath(
                    javaPathField.getText()
            );

            settings.setJavaArguments(
                    javaArgumentsArea.getText()
            );

            settings.setGameArguments(
                    gameArgumentsArea.getText()
            );

            settings.setFullscreen(
                    fullscreenCheck.isSelected()
            );

            InstanceManager.saveSettings(
                    instance,
                    settings
            );

            statusLabel.setText(
                    "Settings saved."
            );

        } catch (NumberFormatException e) {

            statusLabel.setText(
                    e.getMessage()
            );

        } catch (Exception e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to save settings."
            );
        }
    }

    // =============================================================
    // RAM
    // =============================================================

    private int snapRam(
            double value
    ) {

        int ram =
                (int) Math.round(
                        value / 512.0
                ) * 512;

        return clampRam(
                ram
        );
    }

    private int clampRam(
            int ram
    ) {

        return Math.max(
                1024,
                Math.min(
                        16384,
                        ram
                )
        );
    }

    // =============================================================
    // VALIDATION
    // =============================================================

    private int parsePositive(
            String value,
            String name
    ) {

        try {

            int number =
                    Integer.parseInt(
                            value.trim()
                    );

            if (number <= 0) {

                throw new NumberFormatException(
                        name + " must be greater than 0."
                );
            }

            return number;

        } catch (NumberFormatException e) {

            if (e.getMessage() != null
                    && !e.getMessage().startsWith(
                    "For input string"
            )) {

                throw e;
            }

            throw new NumberFormatException(
                    name + " must be a valid number."
            );
        }
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private Label createSectionTitle(
            String text
    ) {

        Label label =
                new Label(
                        text
                );

        label.getStyleClass().add(
                "settings-section-title"
        );

        return label;
    }

    private VBox createCard(
            javafx.scene.Node... nodes
    ) {

        VBox card =
                new VBox(
                        16,
                        nodes
                );

        card.setPadding(
                new Insets(22)
        );

        card.getStyleClass().add(
                "instance-settings-card"
        );

        return card;
    }

    private VBox createInput(
            String labelText,
            javafx.scene.Node input
    ) {

        Label label =
                new Label(
                        labelText
                );

        label.getStyleClass().add(
                "instance-setting-label"
        );

        VBox box =
                new VBox(
                        6,
                        label,
                        input
                );

        VBox.setVgrow(
                input,
                Priority.NEVER
        );

        return box;
    }
}