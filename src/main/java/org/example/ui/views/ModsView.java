package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.example.launcher.model.Instance;
import org.example.ui.components.IconView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModsView extends VBox {

    private final Instance instance;

    private final Runnable onBrowseContent;
    private final Runnable onInstanceSettings;
    private final Runnable onScreenshots;

    private final VBox contentList;
    private final Label statusLabel;
    private final TextField searchField;

    private ContentType selectedType;

    private enum ContentType {
        MODS,
        RESOURCE_PACKS,
        SHADERS
    }

    public ModsView(
            Instance instance,
            Runnable onBrowseContent,
            Runnable onInstanceSettings,
            Runnable onScreenshots
    ) {

        this.instance =
                instance;

        this.onBrowseContent =
                onBrowseContent;

        this.onInstanceSettings =
                onInstanceSettings;

        this.onScreenshots =
                onScreenshots;

        this.selectedType =
                ContentType.MODS;

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
                        "Installed Content"
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

        searchField = new TextField();
        searchField.setPromptText("Search installed content...");
        searchField.getStyleClass().add("instances-search");
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> loadInstalledContent()
        );

        // =========================================================
        // NAVIGATION
        // =========================================================

        Button modsButton =
                new Button(
                        "MODS",
                        IconView.create(IconView.Type.PACKAGE, 16)
                );

        modsButton.getStyleClass().add(
                "primary-button"
        );

        Button packsButton =
                new Button(
                        "PACKS",
                        IconView.create(IconView.Type.PACKAGE, 16)
                );

        packsButton.getStyleClass().add(
                "secondary-button"
        );

        Button shadersButton =
                new Button(
                        "SHADERS",
                        IconView.create(IconView.Type.SHIELD, 16)
                );

        shadersButton.getStyleClass().add(
                "secondary-button"
        );

        Button browseButton =
                new Button(
                        "BROWSE CONTENT",
                        IconView.create(IconView.Type.SEARCH, 16)
                );

        browseButton.getStyleClass().add(
                "primary-button"
        );

        Button screenshotsButton =
                new Button(
                        "SCREENSHOTS",
                        IconView.create(IconView.Type.PACKAGE, 16)
                );

        screenshotsButton.getStyleClass().add(
                "secondary-button"
        );

        Button settingsButton =
                new Button(
                        "INSTANCE SETTINGS",
                        IconView.create(IconView.Type.SETTINGS, 16)
                );

        settingsButton.getStyleClass().add(
                "secondary-button"
        );

        modsButton.setOnAction(
                event ->
                        showContent(
                                ContentType.MODS,
                                modsButton,
                                packsButton,
                                shadersButton
                        )
        );

        packsButton.setOnAction(
                event ->
                        showContent(
                                ContentType.RESOURCE_PACKS,
                                modsButton,
                                packsButton,
                                shadersButton
                        )
        );

        shadersButton.setOnAction(
                event ->
                        showContent(
                                ContentType.SHADERS,
                                modsButton,
                                packsButton,
                                shadersButton
                        )
        );

        browseButton.setOnAction(
                event ->
                        onBrowseContent.run()
        );

        screenshotsButton.setOnAction(
                event ->
                        onScreenshots.run()
        );

        settingsButton.setOnAction(
                event ->
                        onInstanceSettings.run()
        );

        Button refreshButton =
                new Button(
                        "REFRESH",
                        IconView.create(IconView.Type.REFRESH, 16)
                );
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(event -> loadInstalledContent());

        Button folderButton =
                new Button(
                        "OPEN FOLDER",
                        IconView.create(IconView.Type.FOLDER, 16)
                );
        folderButton.getStyleClass().add("secondary-button");
        folderButton.setOnAction(event -> openContentFolder());

        HBox navigation =
                new HBox(
                        10,
                        modsButton,
                        packsButton,
                        shadersButton
                );
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getStyleClass().add("instance-tabs");

        HBox actions =
                new HBox(
                        10,
                        searchField,
                        browseButton,
                        folderButton,
                        refreshButton,
                        screenshotsButton,
                        settingsButton
                );
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox header =
                new VBox(
                        14,
                        headerText,
                        navigation,
                        actions
                );

        // =========================================================
        // CONTENT LIST
        // =========================================================

        contentList =
                new VBox(
                        12
                );

        contentList.getStyleClass().add(
                "instance-list"
        );

        ScrollPane scrollPane =
                new ScrollPane(
                        contentList
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

        loadInstalledContent();
    }

    // =============================================================
    // SHOW CONTENT
    // =============================================================

    private void showContent(
            ContentType type,
            Button modsButton,
            Button packsButton,
            Button shadersButton
    ) {

        selectedType =
                type;

        setButtonStyle(
                modsButton,
                type == ContentType.MODS
        );

        setButtonStyle(
                packsButton,
                type == ContentType.RESOURCE_PACKS
        );

        setButtonStyle(
                shadersButton,
                type == ContentType.SHADERS
        );

        loadInstalledContent();
    }

    private void setButtonStyle(
            Button button,
            boolean selected
    ) {

        button.getStyleClass().remove(
                "primary-button"
        );

        button.getStyleClass().remove(
                "secondary-button"
        );

        button.getStyleClass().add(
                selected
                        ? "primary-button"
                        : "secondary-button"
        );
    }

    // =============================================================
    // LOAD CONTENT
    // =============================================================

    private void loadInstalledContent() {

        contentList
                .getChildren()
                .clear();

        Path directory;

        String emptyMessage;

        String statusName;

        switch (selectedType) {

            case MODS -> {

                directory =
                        instance.getDirectory()
                                .resolve("mods");

                emptyMessage =
                        "No mods installed.";

                statusName =
                        "mod";
            }

            case RESOURCE_PACKS -> {

                directory =
                        instance.getDirectory()
                                .resolve("resourcepacks");

                emptyMessage =
                        "No resource packs installed.";

                statusName =
                        "resource pack";
            }

            case SHADERS -> {

                directory =
                        instance.getDirectory()
                                .resolve("shaderpacks");

                emptyMessage =
                        "No shaders installed.";

                statusName =
                        "shader";
            }

            default -> {
                return;
            }
        }

        try {

            Files.createDirectories(
                    directory
            );

            List<Path> files;

            try (var stream =
                         Files.list(
                                 directory
                         )) {

                files =
                        stream
                                .filter(
                                        path ->
                                                !Files.isDirectory(
                                                        path
                                                )
                                )
                                .filter(
                                        this::isValidContentFile
                                )
                                .filter(path -> {
                                    String query = searchField.getText()
                                            .trim()
                                            .toLowerCase();
                                    return query.isEmpty()
                                            || path.getFileName().toString()
                                            .toLowerCase()
                                            .contains(query);
                                })
                                .sorted()
                                .toList();
            }

            if (files.isEmpty()) {

                Label empty =
                        new Label(
                                emptyMessage
                        );

                empty.getStyleClass().add(
                        "instances-status"
                );

                contentList
                        .getChildren()
                        .add(
                                empty
                        );

                statusLabel.setText(
                        "0 "
                                + statusName
                                + "s installed."
                );

                return;
            }

            for (Path file : files) {

                if (selectedType ==
                        ContentType.MODS) {

                    contentList
                            .getChildren()
                            .add(
                                    createModCard(
                                            file
                                    )
                            );

                } else {

                    contentList
                            .getChildren()
                            .add(
                                    createFileCard(
                                            file
                                    )
                            );
                }
            }

            statusLabel.setText(
                    files.size()
                            + " "
                            + statusName
                            + (
                            files.size() == 1
                                    ? ""
                                    : "s"
                    )
                            + " installed."
            );

        } catch (IOException e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to load installed "
                            + statusName
                            + "s."
            );
        }
    }

    private boolean isValidContentFile(
            Path file
    ) {

        String name =
                file.getFileName()
                        .toString()
                        .toLowerCase();

        return switch (selectedType) {

            case MODS ->
                    name.endsWith(".jar")
                            || name.endsWith(".jar.disabled");

            case RESOURCE_PACKS, SHADERS ->
                    name.endsWith(".zip");
        };
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

        ImageView icon =
                createIcon();

        VBox iconBox =
                createIconBox();

        iconBox.getChildren().add(
                icon
        );

        loadJarIcon(
                mod,
                icon
        );

        Label title =
                new Label(
                        cleanName(
                                filename
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

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

        boolean disabled =
                filename.toLowerCase().endsWith(".jar.disabled");

        Button toggleButton =
                new Button(
                        disabled ? "ENABLE" : "DISABLE",
                        IconView.create(
                                disabled
                                        ? IconView.Type.CHECK
                                        : IconView.Type.PAUSE,
                                15
                        )
                );
        toggleButton.getStyleClass().add("secondary-button");
        toggleButton.setTooltip(
                new Tooltip(
                        disabled
                                ? "Enable this mod"
                                : "Disable this mod"
                )
        );
        toggleButton.setOnAction(
                event -> toggleMod(mod)
        );

        Button removeButton =
                new Button(
                        "REMOVE",
                        IconView.create(IconView.Type.TRASH, 15)
                );
        removeButton.getStyleClass().add("secondary-button");
        removeButton.setTooltip(new Tooltip("Remove this mod"));
        removeButton.setOnAction(event -> removeFile(mod));

        HBox buttons =
                new HBox(
                        8,
                        toggleButton,
                        removeButton
                );

        HBox row =
                new HBox(
                        16,
                        iconBox,
                        information,
                        buttons
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

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
    // RESOURCE PACK / SHADER CARD
    // =============================================================

    private VBox createFileCard(
            Path file
    ) {

        String filename =
                file.getFileName()
                        .toString();

        ImageView icon =
                createIcon();

        VBox iconBox =
                createIconBox();

        iconBox.getChildren().add(
                icon
        );

        loadZipIcon(
                file,
                icon
        );

        Label title =
                new Label(
                        cleanName(
                                filename
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

        String typeName =
                selectedType ==
                        ContentType.RESOURCE_PACKS
                        ? "RESOURCE PACK"
                        : "SHADER";

        Label metadata =
                new Label(
                        typeName
                                + " • "
                                + formatSize(
                                getFileSize(file)
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

        Button removeButton =
                new Button(
                        "REMOVE"
                );

        removeButton.getStyleClass().add(
                "secondary-button"
        );

        removeButton.setOnAction(
                event ->
                        removeFile(
                                file
                        )
        );

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
    // ICON
    // =============================================================

    private ImageView createIcon() {

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

        return icon;
    }

    private VBox createIconBox() {

        VBox iconBox =
                new VBox();

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

        return iconBox;
    }

    // =============================================================
    // LOAD MOD ICON
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

                            Image image =
                                    bufferedImageToImage(
                                            bufferedImage
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
    // LOAD ZIP ICON
    // =============================================================

    private void loadZipIcon(
            Path file,
            ImageView imageView
    ) {

        Thread thread =
                new Thread(() -> {

                    try {

                        try (ZipFile zip =
                                     new ZipFile(
                                             file.toFile()
                                     )) {

                            String iconPath =
                                    findZipIconPath(
                                            zip
                                    );

                            if (iconPath == null) {
                                return;
                            }

                            ZipEntry entry =
                                    zip.getEntry(
                                            iconPath
                                    );

                            if (entry == null) {
                                return;
                            }

                            BufferedImage bufferedImage;

                            try (InputStream input =
                                         zip.getInputStream(
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

                            Image image =
                                    bufferedImageToImage(
                                            bufferedImage
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

    private Image bufferedImageToImage(
            BufferedImage bufferedImage
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ImageIO.write(
                bufferedImage,
                "png",
                output
        );

        return new Image(
                new ByteArrayInputStream(
                        output.toByteArray()
                )
        );
    }

    // =============================================================
    // FIND ZIP ICON
    // =============================================================

    private String findZipIconPath(
            ZipFile zip
    ) {

        if (selectedType ==
                ContentType.RESOURCE_PACKS) {

            String[] resourcePackIcons = {
                    "pack.png",
                    "assets/minecraft/pack.png",
                    "icon.png"
            };

            for (String path :
                    resourcePackIcons) {

                ZipEntry entry =
                        zip.getEntry(
                                path
                        );

                if (entry != null
                        && !entry.isDirectory()) {

                    return path;
                }
            }

            return findAnyPng(
                    zip
            );
        }

        String[] shaderIcons = {
                "shaders/icon.png",
                "icon.png",
                "preview.png",
                "thumbnail.png"
        };

        for (String path :
                shaderIcons) {

            ZipEntry entry =
                    zip.getEntry(
                            path
                    );

            if (entry != null
                    && !entry.isDirectory()) {

                return path;
            }
        }

        return findAnyPng(
                zip
        );
    }

    private String findAnyPng(
            ZipFile zip
    ) {

        var entries =
                zip.entries();

        while (entries.hasMoreElements()) {

            ZipEntry entry =
                    entries.nextElement();

            if (entry.isDirectory()) {
                continue;
            }

            String name =
                    entry.getName()
                            .toLowerCase();

            if (name.endsWith(".png")) {

                return entry.getName();
            }
        }

        return null;
    }

    // =============================================================
    // FIND JAR ICON
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
                                    StandardCharsets.UTF_8
                            );

                    Pattern pattern =
                            Pattern.compile(
                                    "\"icon\"\\s*:\\s*\"([^\"]+)\""
                            );

                    Matcher matcher =
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

                    pattern =
                            Pattern.compile(
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

        String[] commonNames = {
                "icon.png",
                "icon.jpg",
                "icon.jpeg",
                "assets/icon.png"
        };

        for (String name :
                commonNames) {

            if (jar.getJarEntry(name) != null) {
                return name;
            }
        }

        return null;
    }

    // =============================================================
    // REMOVE
    // =============================================================

    private void toggleMod(Path file) {
        try {
            String name = file.getFileName().toString();
            Path target;

            if (name.toLowerCase().endsWith(".jar.disabled")) {
                target = file.resolveSibling(
                        name.substring(0, name.length() - ".disabled".length())
                );
            } else {
                target = file.resolveSibling(name + ".disabled");
            }

            Files.move(file, target);
            loadInstalledContent();
        } catch (IOException e) {
            statusLabel.setText(
                    "Could not change mod state."
            );
        }
    }

    private void openContentFolder() {
        Path directory = switch (selectedType) {
            case MODS -> instance.getDirectory().resolve("mods");
            case RESOURCE_PACKS -> instance.getDirectory().resolve("resourcepacks");
            case SHADERS -> instance.getDirectory().resolve("shaderpacks");
        };

        try {
            Files.createDirectories(directory);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory.toFile());
            }
        } catch (Exception e) {
            statusLabel.setText("Could not open content folder.");
        }
    }

    private void removeFile(
            Path file
    ) {

        try {

            Files.deleteIfExists(
                    file
            );

            loadInstalledContent();

        } catch (IOException e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to remove "
                            + file.getFileName()
                            + "."
            );
        }
    }

    // =============================================================
    // NAME
    // =============================================================

    private String cleanName(
            String filename
    ) {

        String lower =
                filename.toLowerCase();

        if (lower.endsWith(".jar.disabled")) {
            return filename.substring(
                    0,
                    filename.length() - ".jar.disabled".length()
            );
        }

        if (lower.endsWith(".jar")
                || lower.endsWith(".zip")) {

            return filename.substring(
                    0,
                    filename.length() - 4
            );
        }

        return filename;
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