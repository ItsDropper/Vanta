package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.instance.SteinModManager;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source browser and lifecycle manager for Stein .steinmod packages. */
public final class SteinModsDialog {
    private final Instance instance;
    private final Runnable onInstalledContentChanged;
    private final Dialog<Void> dialog;
    private final VBox sourceList = new VBox(8);
    private final VBox modList = new VBox(10);
    private final Label status = new Label();
    private final TextField sourceInput = new TextField();
    private final Button addSourceButton;
    private final Button refreshButton;
    private final Button updateAllButton;
    private long requestGeneration;

    private SteinModsDialog(Window owner, Instance instance, Runnable onInstalledContentChanged) {
        this.instance = Objects.requireNonNull(instance);
        this.onInstalledContentChanged = Objects.requireNonNull(onInstalledContentChanged);
        if (!"Stein".equalsIgnoreCase(instance.getLoader())) {
            throw new IllegalArgumentException("Stein mod management requires a Stein instance.");
        }

        dialog = new Dialog<>();
        dialog.setTitle(Localization.text("steinMods.title"));
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.getDialogPane().getButtonTypes().add(
                new ButtonType(Localization.text("common.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setResizable(true);

        Label warning = new Label(Localization.text("steinMods.thirdPartyWarning"));
        warning.setWrapText(true);
        warning.getStyleClass().add("instances-status");

        sourceInput.setPromptText(Localization.text("steinMods.sourcePrompt"));
        sourceInput.setPrefColumnCount(42);
        addSourceButton = new Button(Localization.text("steinMods.addSource"));
        addSourceButton.getStyleClass().add("primary-button");
        addSourceButton.setOnAction(event -> addSource());

        refreshButton = new Button(Localization.text("common.refresh"));
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(event -> refreshCatalog());

        updateAllButton = new Button(Localization.text("steinMods.updateAll"));
        updateAllButton.getStyleClass().add("secondary-button");
        updateAllButton.setOnAction(event -> updateAll());

        HBox sourceControls = new HBox(8, sourceInput, addSourceButton, refreshButton, updateAllButton);
        sourceControls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sourceInput, Priority.ALWAYS);

        Label sourcesHeading = new Label(Localization.text("steinMods.sources"));
        sourcesHeading.getStyleClass().add("section-title");
        Label modsHeading = new Label(Localization.text("steinMods.available"));
        modsHeading.getStyleClass().add("section-title");

        ScrollPane sourceScroll = new ScrollPane(sourceList);
        sourceScroll.setFitToWidth(true);
        sourceScroll.setPrefViewportHeight(110);
        sourceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sourceScroll.getStyleClass().add("instances-scroll");

        ScrollPane modScroll = new ScrollPane(modList);
        modScroll.setFitToWidth(true);
        modScroll.setPrefViewportHeight(360);
        modScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        modScroll.getStyleClass().add("instances-scroll");
        VBox.setVgrow(modScroll, Priority.ALWAYS);

        status.getStyleClass().add("instances-status");
        VBox content = new VBox(12, warning, sourceControls, sourcesHeading,
                sourceScroll, modsHeading, modScroll, status);
        content.setPadding(new Insets(16));
        content.setPrefSize(900, 650);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(940);
        dialog.getDialogPane().setPrefHeight(690);
    }

    public static void open(Window owner, Instance instance, Runnable onInstalledContentChanged) {
        SteinModsDialog manager = new SteinModsDialog(owner, instance, onInstalledContentChanged);
        manager.dialog.show();
        manager.refreshCatalog();
    }

    private void addSource() {
        String url = sourceInput.getText().trim();
        if (url.isEmpty()) {
            status.setText(Localization.text("steinMods.sourceRequired"));
            return;
        }
        setBusy(true, Localization.text("steinMods.addingSource"));
        UiExecutor.shared().execute(() -> {
            Exception failure = null;
            try {
                SteinModManager.addSource(instance, url);
            } catch (Exception error) {
                failure = error;
            }
            Exception result = failure;
            UiExecutor.shared().dispatchToUi(() -> {
                if (result != null) {
                    setBusy(false, Localization.text("steinMods.failedPrefix") + message(result));
                } else {
                    sourceInput.clear();
                    refreshCatalog();
                }
            });
        });
    }

    private void refreshCatalog() {
        long generation = ++requestGeneration;
        setBusy(true, Localization.text("steinMods.loading"));
        UiExecutor.shared().execute(() -> {
            List<SteinModManager.Release> releases = List.of();
            List<SteinModManager.Installed> installed = List.of();
            Exception failure = null;
            try {
                releases = SteinModManager.refresh(instance);
                installed = SteinModManager.loadInstalled(instance);
            } catch (Exception error) {
                failure = error;
            }
            List<SteinModManager.Release> finalReleases = releases;
            List<SteinModManager.Installed> finalInstalled = installed;
            Exception result = failure;
            UiExecutor.shared().dispatchToUi(() -> {
                if (generation != requestGeneration) {
                    return;
                }
                renderSources(finalReleases);
                renderMods(finalReleases, finalInstalled);
                setBusy(false, result == null
                        ? finalReleases.size() + " " + Localization.text("steinMods.modsAvailable")
                        : Localization.text("steinMods.failedPrefix") + message(result));
            });
        });
    }

    private void updateAll() {
        setBusy(true, Localization.text("steinMods.updating"));
        UiExecutor.shared().execute(() -> {
            Exception failure = null;
            int count = 0;
            try {
                count = SteinModManager.updateAll(instance).size();
            } catch (Exception error) {
                failure = error;
            }
            int updated = count;
            Exception result = failure;
            UiExecutor.shared().dispatchToUi(() -> {
                if (result == null) {
                    onInstalledContentChanged.run();
                    status.setText(updated + " " + Localization.text("steinMods.updatedCount"));
                    refreshCatalog();
                } else {
                    setBusy(false, Localization.text("steinMods.failedPrefix") + message(result));
                }
            });
        });
    }

    private void install(SteinModManager.Release release) {
        setBusy(true, Localization.text("steinMods.installingPrefix") + release.name());
        UiExecutor.shared().execute(() -> {
            Exception failure = null;
            try {
                SteinModManager.install(instance, release);
            } catch (Exception error) {
                failure = error;
            }
            Exception result = failure;
            UiExecutor.shared().dispatchToUi(() -> {
                if (result == null) {
                    onInstalledContentChanged.run();
                    refreshCatalog();
                } else {
                    setBusy(false, Localization.text("steinMods.failedPrefix") + message(result));
                }
            });
        });
    }

    private void remove(SteinModManager.Installed installed) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                Localization.text("steinMods.removeConfirmPrefix") + installed.name() + "?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText(Localization.text("steinMods.removeTitle"));
        confirmation.initOwner(dialog.getDialogPane().getScene().getWindow());
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored -> {
            setBusy(true, Localization.text("steinMods.removingPrefix") + installed.name());
            UiExecutor.shared().execute(() -> {
                Exception failure = null;
                try {
                    SteinModManager.removeInstalled(instance, installed.id());
                } catch (Exception error) {
                    failure = error;
                }
                Exception result = failure;
                UiExecutor.shared().dispatchToUi(() -> {
                    if (result == null) {
                        onInstalledContentChanged.run();
                        refreshCatalog();
                    } else {
                        setBusy(false, Localization.text("steinMods.failedPrefix") + message(result));
                    }
                });
            });
        });
    }

    private void removeSource(String url, String name) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                Localization.text("steinMods.removeSourceConfirmPrefix") + name + "?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText(Localization.text("steinMods.removeSourceTitle"));
        confirmation.initOwner(dialog.getDialogPane().getScene().getWindow());
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored -> {
            setBusy(true, Localization.text("steinMods.removingSource"));
            UiExecutor.shared().execute(() -> {
                Exception failure = null;
                try {
                    SteinModManager.removeSource(instance, url);
                } catch (Exception error) {
                    failure = error;
                }
                Exception result = failure;
                UiExecutor.shared().dispatchToUi(() -> {
                    if (result == null) {
                        refreshCatalog();
                    } else {
                        setBusy(false, Localization.text("steinMods.failedPrefix") + message(result));
                    }
                });
            });
        });
    }

    private void renderSources(List<SteinModManager.Release> releases) {
        sourceList.getChildren().clear();
        Map<String, SteinModManager.Release> sources = new LinkedHashMap<>();
        for (SteinModManager.Release release : releases) {
            sources.putIfAbsent(release.sourceUrl(), release);
        }
        if (sources.isEmpty()) {
            Label empty = new Label(Localization.text("steinMods.noSources"));
            empty.getStyleClass().add("instances-status");
            sourceList.getChildren().add(empty);
            return;
        }
        for (SteinModManager.Release release : sources.values()) {
            Label details = new Label(release.sourceName() + " • " + release.sourceUrl());
            details.setWrapText(true);
            HBox.setHgrow(details, Priority.ALWAYS);
            Button remove = new Button(Localization.text("steinMods.removeSource"));
            remove.getStyleClass().add("secondary-button");
            remove.setOnAction(event -> removeSource(release.sourceUrl(), release.sourceName()));
            HBox row = new HBox(8, details, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("instance-card");
            sourceList.getChildren().add(row);
        }
    }

    private void renderMods(List<SteinModManager.Release> releases,
                            List<SteinModManager.Installed> installed) {
        modList.getChildren().clear();
        Map<String, SteinModManager.Installed> installedById = new LinkedHashMap<>();
        for (SteinModManager.Installed item : installed) {
            installedById.put(item.id(), item);
        }
        if (releases.isEmpty() && installed.isEmpty()) {
            Label empty = new Label(Localization.text("steinMods.noMods"));
            empty.getStyleClass().add("instances-status");
            modList.getChildren().add(empty);
            return;
        }

        for (SteinModManager.Release release : releases) {
            SteinModManager.Installed current = installedById.remove(release.id());
            boolean isInstalled = current != null;
            boolean needsUpdate = isInstalled && (!release.version().equals(current.version())
                    || !release.sha256().equalsIgnoreCase(current.sha256()));

            Label title = new Label(release.name());
            title.getStyleClass().add("instance-name");
            Label publisher = new Label(Localization.text("steinMods.publisherPrefix")
                    + release.sourceName() + " • " + release.sourceUrl());
            publisher.setWrapText(true);
            Label version = new Label(Localization.text("steinMods.versionPrefix")
                    + release.version() + " • " + Localization.text("steinMods.requiresLoaderPrefix")
                    + release.loader());
            Label description = new Label(release.description());
            description.setWrapText(true);
            VBox info = new VBox(5, title, publisher, version, description);
            HBox.setHgrow(info, Priority.ALWAYS);

            List<Node> controls = new ArrayList<>();
            if (isInstalled) {
                Label installedLabel = new Label(Localization.text("steinMods.installed")
                        + (needsUpdate ? " • " + Localization.text("steinMods.updateAvailable") : ""));
                installedLabel.getStyleClass().add("instances-status");
                controls.add(installedLabel);
                Button remove = new Button(Localization.text("steinMods.remove"));
                remove.getStyleClass().add("secondary-button");
                remove.setOnAction(event -> remove(current));
                controls.add(remove);
            }
            if (!isInstalled || needsUpdate) {
                Button install = new Button(Localization.text(isInstalled
                        ? "steinMods.update" : "steinMods.install"));
                install.getStyleClass().add("primary-button");
                install.setOnAction(event -> install(release));
                controls.add(install);
            }
            VBox actions = new VBox(6, controls.toArray(Node[]::new));
            actions.setAlignment(Pos.CENTER_RIGHT);
            HBox card = new HBox(14, info, actions);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(14));
            card.getStyleClass().add("instance-card");
            modList.getChildren().add(card);
        }

        for (SteinModManager.Installed orphan : installedById.values()) {
            Label title = new Label(orphan.name() + " • " + orphan.version());
            title.getStyleClass().add("instance-name");
            Label detail = new Label(Localization.text("steinMods.sourceUnavailable"));
            Button remove = new Button(Localization.text("steinMods.remove"));
            remove.getStyleClass().add("secondary-button");
            remove.setOnAction(event -> remove(orphan));
            HBox card = new HBox(12, title, detail, remove);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(12));
            card.getStyleClass().add("instance-card");
            modList.getChildren().add(card);
        }
    }

    private void setBusy(boolean busy, String message) {
        status.setText(message);
        addSourceButton.setDisable(busy);
        refreshButton.setDisable(busy);
        updateAllButton.setDisable(busy);
        sourceInput.setDisable(busy);
        sourceList.setDisable(busy);
        modList.setDisable(busy);
    }

    private static String message(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
