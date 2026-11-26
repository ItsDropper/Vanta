package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.bundle.VantaBundleManifest;
import dev.itsdropper.vanta.launcher.bundle.VantaBundleRepository;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.ResponsiveLayout;
import dev.itsdropper.vanta.ui.components.PageHeader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** UI for importing and configuring a versioned Vanta profile manifest. */
public final class VantaProfilesView extends VBox {
    @FunctionalInterface
    public interface CreateHandler {
        void create(VantaBundleManifest manifest, String name, String minecraftVersion,
                    Set<String> selectedBundles, Set<String> selectedOptionalMods);
    }

    private final Runnable onBack;
    private final CreateHandler onCreate;
    private final TextField nameField = new TextField();
    private final ComboBox<String> versionBox = new ComboBox<>();
    private final Label status = new Label();
    private final VBox bundleList = new VBox(12);
    private final Button createButton = new Button(Localization.text("common.createInstance"));
    private VantaBundleManifest manifest;

    public VantaProfilesView(Runnable onBack, CreateHandler onCreate) {
        this.onBack = onBack;
        this.onCreate = onCreate;
        getStyleClass().addAll("page", "vanta-consistent-page", "vanta-profiles-page");
        ResponsiveLayout.bindPagePadding(this);
        setSpacing(20);

        PageHeader header = new PageHeader(
                Localization.text("vantaProfiles.title"),
                Localization.text("vantaProfiles.subtitle"),
                "page-title", "page-subtitle");

        VBox panel = new VBox(16);
        panel.getStyleClass().addAll("create-instance-panel", "vanta-profiles-panel");
        panel.setPadding(new Insets(24));
        panel.setMaxWidth(860);

        Label section = new Label("Available profile");
        section.getStyleClass().add("create-panel-title");
        Label help = new Label("Start with the built-in Vanta Fabric profile. Vanta selects compatible dependencies automatically; importing a custom manifest is optional.");
        help.getStyleClass().add("create-panel-subtitle");
        help.setWrapText(true);

        Button importButton = new Button("Import custom profile…");
        importButton.getStyleClass().add("secondary-button");
        importButton.setOnAction(event -> chooseManifest(importButton.getScene() == null
                ? null : importButton.getScene().getWindow()));

        status.getStyleClass().add("create-status");
        status.setWrapText(true);

        nameField.setPromptText(Localization.text("vantaProfiles.namePrompt"));
        nameField.getStyleClass().add("create-field");
        nameField.setPrefHeight(44);

        versionBox.getStyleClass().add("create-combo");
        versionBox.setMaxWidth(Double.MAX_VALUE);
        versionBox.setPrefHeight(44);
        VBox versionField = new VBox(7,
                label(Localization.text("createInstance.minecraftVersion")), versionBox);
        VBox nameFieldGroup = new VBox(7,
                label(Localization.text("vantaProfiles.instanceName")), nameField);
        HBox fields = new HBox(16, versionField, nameFieldGroup);
        fields.getStyleClass().add("create-instance-environment-fields");
        HBox.setHgrow(versionField, Priority.ALWAYS);
        HBox.setHgrow(nameFieldGroup, Priority.ALWAYS);
        versionField.setMaxWidth(Double.MAX_VALUE);
        nameFieldGroup.setMaxWidth(Double.MAX_VALUE);

        Label bundlesHeading = new Label(Localization.text("vantaProfiles.bundles"));
        bundlesHeading.getStyleClass().add("create-panel-title");
        ScrollPane bundleScroll = new ScrollPane(bundleList);
        bundleScroll.getStyleClass().add("preset-config-scroll");
        bundleScroll.setFitToWidth(true);
        bundleScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bundleScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bundleScroll.setPrefViewportHeight(300);
        bundleScroll.setMinHeight(140);
        bundleScroll.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(bundleScroll, Priority.ALWAYS);

        Button back = new Button(Localization.text("common.back"));
        back.getStyleClass().add("secondary-button");
        back.setOnAction(event -> this.onBack.run());
        createButton.getStyleClass().add("primary-button");
        createButton.setPrefHeight(44);
        createButton.setDisable(true);
        createButton.setOnAction(event -> create());
        FlowPane actions = new FlowPane(12, 8, back, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        panel.getChildren().addAll(section, help, importButton, status,
                fields, bundlesHeading, bundleScroll, actions);
        getChildren().addAll(header, panel);

        loadBuiltInProfile();
    }

    private void loadBuiltInProfile() {
        try {
            VantaBundleManifest builtIn = new VantaBundleRepository()
                    .loadBundled("profiles/vanta-fabric-starter.json");
            applyManifest(builtIn);
            nameField.setText("Vanta Fabric Starter");
            status.setText("Vanta Fabric Starter · Fabric · " + builtIn.minecraftVersions().get(0)
                    + " · " + builtIn.bundles().size() + " feature bundle");
            createButton.setDisable(false);
        } catch (Exception failure) {
            manifest = null;
            bundleList.getChildren().clear();
            versionBox.getItems().clear();
            createButton.setDisable(true);
            status.setText("The built-in Vanta profile could not be loaded. "
                    + (failure.getMessage() == null ? "" : failure.getMessage()));
        }
    }

    private void applyManifest(VantaBundleManifest loaded) {
        manifest = loaded;
        versionBox.getItems().setAll(loaded.minecraftVersions());
        versionBox.getSelectionModel().selectFirst();
        nameField.setText(loaded.profileId());
        renderBundles();
        createButton.setDisable(false);
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("create-label");
        return label;
    }

    private void chooseManifest(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("vantaProfiles.chooseManifestTitle"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Localization.text("vantaProfiles.manifestFiles"), "*.json"));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) return;
        try {
            VantaBundleManifest loaded = new VantaBundleRepository().load(selected.toPath());
            applyManifest(loaded);
            status.setText(loaded.profileId() + " · " + loaded.profileVersion()
                    + " · " + loaded.loader() + " · "
                    + loaded.bundles().size() + " " + Localization.text("vantaProfiles.bundleCount"));
        } catch (Exception failure) {
            manifest = null;
            bundleList.getChildren().clear();
            versionBox.getItems().clear();
            createButton.setDisable(true);
            status.setText(Localization.text("vantaProfiles.invalidManifest") + " "
                    + (failure.getMessage() == null ? "" : failure.getMessage()));
        }
    }

    private void renderBundles() {
        bundleList.getChildren().clear();
        for (VantaBundleManifest.Bundle bundle : manifest.bundles()) {
            CheckBox enabled = new CheckBox(bundle.name());
            enabled.setSelected(bundle.required() || bundle.defaultEnabled());
            enabled.setDisable(bundle.required());
            enabled.getStyleClass().add("vanta-profile-bundle-toggle");
            Label description = new Label(bundle.description());
            description.getStyleClass().add("create-option-description");
            description.setWrapText(true);
            VBox card = new VBox(8, enabled, description);
            card.getStyleClass().addAll("preset-config-section", "vanta-profile-bundle");
            VBox mods = new VBox(5);
            for (VantaBundleManifest.Mod mod : bundle.mods()) {
                CheckBox modToggle = new CheckBox(mod.projectId());
                modToggle.setSelected(mod.required() || mod.defaultEnabled());
                modToggle.setDisable(mod.required());
                modToggle.setUserData(mod);
                mods.getChildren().add(modToggle);
            }
            enabled.selectedProperty().addListener((observable, oldValue, selected) -> {
                mods.setDisable(!selected);
                mods.setOpacity(selected ? 1.0 : 0.48);
            });
            mods.setDisable(!enabled.isSelected());
            mods.getStyleClass().add("vanta-profile-mod-list");
            card.getChildren().add(mods);
            card.setUserData(enabled);
            card.setId(bundle.id());
            bundleList.getChildren().add(card);
        }
    }

    private void create() {
        if (manifest == null) return;
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String version = versionBox.getValue();
        if (name.isBlank()) {
            status.setText(Localization.text("createInstance.validationName"));
            nameField.requestFocus();
            return;
        }
        if (version == null || version.isBlank()) {
            status.setText(Localization.text("createInstance.validationVersion"));
            versionBox.requestFocus();
            return;
        }
        Set<String> bundles = new LinkedHashSet<>();
        Set<String> optionalMods = new LinkedHashSet<>();
        for (javafx.scene.Node node : bundleList.getChildren()) {
            if (!(node instanceof VBox card) || !(card.getUserData() instanceof CheckBox enabled)
                    || !enabled.isSelected()) continue;
            String bundleId = card.getId();
            if (bundleId != null && !bundleId.isBlank()) bundles.add(bundleId);
            for (javafx.scene.Node modNode : ((VBox) card.getChildren().get(2)).getChildren()) {
                if (modNode instanceof CheckBox modToggle && modToggle.isSelected()
                        && modToggle.getUserData() instanceof VantaBundleManifest.Mod mod
                        && !mod.required()) optionalMods.add(mod.projectId());
            }
        }
        createButton.setDisable(true);
        onCreate.create(manifest, name, version, Set.copyOf(bundles), Set.copyOf(optionalMods));
    }
}
