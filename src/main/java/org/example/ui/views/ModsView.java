package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.example.launcher.model.Instance;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

public class ModsView extends VBox {

    private final Instance instance;

    private final Runnable onBrowseMods;
    private final Runnable onInstanceSettings;

    private final VBox modsList;
    private final Label statusLabel;

    public ModsView(
            Instance instance,
            Runnable onBrowseMods,
            Runnable onInstanceSettings
    ) {

        this.instance =
                instance;

        this.onBrowseMods =
                onBrowseMods;

        this.onInstanceSettings =
                onInstanceSettings;

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        Label title =
                new Label(
                        "Installed Mods"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        instance.getName()
                                + " • Minecraft "
                                + instance.getMinecraftVersion()
                                + " • "
                                + instance.getDisplayLoader()
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

        // =========================================================
        // NAVIGATION
        // =========================================================

        Button browseButton =
                new Button(
                        "BROWSE MODS"
                );

        browseButton.getStyleClass().add(
                "primary-button"
        );

        browseButton.setOnAction(
                event ->
                        onBrowseMods.run()
        );

        Button settingsButton =
                new Button(
                        "INSTANCE SETTINGS"
                );

        settingsButton.getStyleClass().add(
                "secondary-button"
        );

        settingsButton.setOnAction(
                event ->
                        onInstanceSettings.run()
        );

        HBox navigation =
                new HBox(
                        10,
                        browseButton,
                        settingsButton
                );

        navigation.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox header =
                new VBox(
                        14,
                        headerText,
                        navigation
                );

        // =========================================================
        // MOD LIST
        // =========================================================

        modsList =
                new VBox(
                        12
                );

        modsList.getStyleClass().add(
                "instance-list"
        );

        ScrollPane scrollPane =
                new ScrollPane(
                        modsList
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                header,
                scrollPane,
                statusLabel
        );

        loadInstalledMods();
    }

    // =============================================================
    // LOAD INSTALLED MODS
    // =============================================================

    private void loadInstalledMods() {

        modsList
                .getChildren()
                .clear();

        Path modsDirectory =
                instance.getDirectory()
                        .resolve("mods");

        try {

            Files.createDirectories(
                    modsDirectory
            );

            List<Path> mods;

            try (var stream =
                         Files.list(
                                 modsDirectory
                         )) {

                mods =
                        stream
                                .filter(
                                        path ->
                                                path.toString()
                                                        .toLowerCase()
                                                        .endsWith(".jar")
                                )
                                .sorted()
                                .toList();
            }

            if (mods.isEmpty()) {

                Label empty =
                        new Label(
                                "No mods installed."
                        );

                empty.getStyleClass().add(
                        "instances-status"
                );

                modsList
                        .getChildren()
                        .add(
                                empty
                        );

                statusLabel.setText(
                        "0 mods installed."
                );

                return;
            }

            for (Path mod : mods) {

                modsList
                        .getChildren()
                        .add(
                                createModCard(
                                        mod
                                )
                        );
            }

            statusLabel.setText(
                    mods.size()
                            + " mod"
                            + (mods.size() == 1 ? "" : "s")
                            + " installed."
            );

        } catch (IOException e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to load installed mods."
            );
        }
    }

    // =============================================================
    // MOD CARD
    // =============================================================

    private VBox createModCard(
            Path mod
    ) {

        String filename =
                mod.getFileName()
                        .toString();

        // =========================================================
        // ICON
        // =========================================================

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                52
        );

        icon.setFitHeight(
                52
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        52,
                        52
                );

        clip.setArcWidth(
                14
        );

        clip.setArcHeight(
                14
        );

        icon.setClip(
                clip
        );

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                52,
                52
        );

        iconBox.setPrefSize(
                52,
                52
        );

        iconBox.setMaxSize(
                52,
                52
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        // ---------------------------------------------------------
        // Load icon from JAR
        // ---------------------------------------------------------

        loadJarIcon(
                mod,
                icon
        );

        // =========================================================
        // TITLE
        // =========================================================

        Label title =
                new Label(
                        cleanModName(
                                filename
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

        // =========================================================
        // METADATA
        // =========================================================

        Label metadata =
                new Label(
                        "JAR • "
                                + formatSize(
                                getFileSize(mod)
                        )
                );

        metadata.getStyleClass().add(
                "instance-loader"
        );

        VBox information =
                new VBox(
                        5,
                        title,
                        metadata
                );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        // =========================================================
        // REMOVE
        // =========================================================

        Button removeButton =
                new Button(
                        "REMOVE"
                );

        removeButton.getStyleClass().add(
                "secondary-button"
        );

        removeButton.setOnAction(
                event ->
                        removeMod(
                                mod
                        )
        );

        // =========================================================
        // ROW
        // =========================================================

        HBox row =
                new HBox(
                        16,
                        iconBox,
                        information,
                        removeButton
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // CARD
        // =========================================================

        VBox card =
                new VBox(
                        row
                );

        card.setPadding(
                new Insets(
                        16,
                        18,
                        16,
                        18
                )
        );

        card.getStyleClass().add(
                "instance-card"
        );

        return card;
    }

    // =============================================================
    // LOAD ICON FROM JAR
    // =============================================================

    private void loadJarIcon(
            Path mod,
            ImageView imageView
    ) {

        Thread thread =
                new Thread(() -> {

                    try {

                        try (JarFile jar =
                                     new JarFile(
                                             mod.toFile()
                                     )) {

                            String iconPath =
                                    findIconPath(
                                            jar
                                    );

                            if (iconPath == null) {


                                return;
                            }

                            var entry =
                                    jar.getJarEntry(
                                            iconPath
                                    );

                            if (entry == null) {

                                return;
                            }

                            BufferedImage bufferedImage;

                            try (InputStream input =
                                         jar.getInputStream(
                                                 entry
                                         )) {

                                bufferedImage =
                                        ImageIO.read(
                                                input
                                        );
                            }

                            if (bufferedImage == null) {


                                return;
                            }

                            ByteArrayOutputStream output =
                                    new ByteArrayOutputStream();

                            ImageIO.write(
                                    bufferedImage,
                                    "png",
                                    output
                            );

                            Image image =
                                    new Image(
                                            new ByteArrayInputStream(
                                                    output.toByteArray()
                                            )
                                    );

                            Platform.runLater(() -> {

                                if (!image.isError()) {

                                    imageView.setImage(
                                            image
                                    );
                                }
                            });
                        }

                    } catch (Throwable ex) {


                        ex.printStackTrace();
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // FIND ICON PATH
    // =============================================================

    private String findIconPath(
            JarFile jar
    ) {

        try {

            var fabricModJson =
                    jar.getJarEntry(
                            "fabric.mod.json"
                    );

            if (fabricModJson != null) {

                try (InputStream input =
                             jar.getInputStream(
                                     fabricModJson
                             )) {

                    String json =
                            new String(
                                    input.readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8
                            );

                    // -------------------------------------------------
                    // Fabric's "icon" field
                    // -------------------------------------------------

                    java.util.regex.Pattern pattern =
                            java.util.regex.Pattern.compile(
                                    "\"icon\"\\s*:\\s*\"([^\"]+)\""
                            );

                    java.util.regex.Matcher matcher =
                            pattern.matcher(
                                    json
                            );

                    if (matcher.find()) {

                        String icon =
                                matcher.group(1);

                        if (jar.getJarEntry(icon) != null) {

                            return icon;
                        }
                    }

                    // -------------------------------------------------
                    // Fabric also supports icon as an object:
                    //
                    // "icon": {
                    //     "16": "assets/mod/icon.png",
                    //     "32": "assets/mod/icon.png"
                    // }
                    // -------------------------------------------------

                    pattern =
                            java.util.regex.Pattern.compile(
                                    "\"(?:16|32|64|128)\"\\s*:\\s*\"([^\"]+)\""
                            );

                    matcher =
                            pattern.matcher(
                                    json
                            );

                    while (matcher.find()) {

                        String icon =
                                matcher.group(1);

                        if (jar.getJarEntry(icon) != null) {

                            return icon;
                        }
                    }
                }
            }

        } catch (Throwable ex) {

            ex.printStackTrace();
        }

        // =========================================================
        // FALLBACK
        // =========================================================

        String[] commonNames = {
                "icon.png",
                "icon.jpg",
                "icon.jpeg",
                "assets/icon.png"
        };

        for (String name : commonNames) {

            if (jar.getJarEntry(name) != null) {

                return name;
            }
        }

        return null;
    }

    // =============================================================
    // CLEAN MOD NAME
    // =============================================================

    private String cleanModName(
            String filename
    ) {

        if (filename.toLowerCase().endsWith(".jar")) {

            filename =
                    filename.substring(
                            0,
                            filename.length() - 4
                    );
        }

        return filename;
    }

    // =============================================================
    // REMOVE
    // =============================================================

    private void removeMod(
            Path mod
    ) {

        try {

            Files.deleteIfExists(
                    mod
            );

            loadInstalledMods();

        } catch (IOException e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to remove "
                            + mod.getFileName()
                            + "."
            );
        }
    }

    // =============================================================
    // FILE SIZE
    // =============================================================

    private long getFileSize(
            Path path
    ) {

        try {

            return Files.size(
                    path
            );

        } catch (IOException e) {

            return 0;
        }
    }

    private String formatSize(
            long bytes
    ) {

        if (bytes < 1024) {

            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {

            return String.format(
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
        );
    }
}