package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.service.InstanceUsageManager;
import dev.itsdropper.vanta.ui.kit.Badge;
import dev.itsdropper.vanta.ui.kit.CoverArt;
import dev.itsdropper.vanta.ui.kit.KeyValueRow;
import dev.itsdropper.vanta.ui.Localization;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Persistent side panel showing the selected instance with one large action. */
final class InstanceDetailPanel extends VBox {

    static final double WIDTH = 300;

    private final Runnable onPlay;
    private final Runnable onSettings;

    InstanceDetailPanel(Instance instance, Runnable onPlay, Runnable onSettings) {
        super(14);
        this.onPlay = onPlay;
        this.onSettings = onSettings;
        getStyleClass().add("instance-detail");
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);
        setPadding(new Insets(14));

        CoverArt art = new CoverArt(instance.getName(), instance.getLoader())
                .rounded(10).sized(WIDTH - 28, 150);
        StackPane artBox = new StackPane(art);
        artBox.setMaxWidth(WIDTH - 28);

        Label name = new Label(instance.getName());
        name.getStyleClass().add("instance-detail-name");
        name.setWrapText(true);

        HBox badges = new HBox(6, Badge.loader(instance.getDisplayLoader()),
                Badge.mono(instance.getMinecraftVersion()));
        badges.setAlignment(Pos.CENTER_LEFT);

        long seconds = InstanceUsageManager.getPlaytimeSeconds(instance);
        long last = InstanceUsageManager.getLastPlayed(instance);
        String lastText = last <= 0 ? "Never" : DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()));
        VBox facts = new VBox(8,
                new KeyValueRow("Playtime", InstanceUsageManager.formatPlaytime(seconds)),
                new KeyValueRow("Last played", lastText),
                new KeyValueRow("Loader version", instance.getLoaderVersion() == null
                        || instance.getLoaderVersion().isBlank() ? "None" : instance.getLoaderVersion()));
        facts.getStyleClass().add("instance-detail-facts");

        Button play = new Button(Localization.text("common.play"));
        play.getStyleClass().addAll("vanta-button-primary", "instance-detail-play");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setMinHeight(46);
        play.setPrefHeight(46);
        play.setOnAction(event -> onPlay.run());

        Button settings = new Button("Instance settings");
        settings.getStyleClass().add("vanta-button-secondary");
        settings.setMaxWidth(Double.MAX_VALUE);
        settings.setOnAction(event -> onSettings.run());

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(artBox, name, badges, facts, spacer, play, settings);
    }
}
