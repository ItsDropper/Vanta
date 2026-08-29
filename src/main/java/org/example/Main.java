package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.launcher.modrinth.*;
import org.example.ui.LauncherView;

import java.io.IOException;
import java.util.List;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        LauncherView launcherView =
                new LauncherView(stage);

        Scene scene =
                new Scene(
                        launcherView.getRoot(),
                        1000,
                        650
                );

        scene.getStylesheets().add(
                getClass()
                        .getResource("/launcher.css")
                        .toExternalForm()
        );

        stage.setTitle("Vanta");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        // =============================================================
        // MODRINTH TEST
        // =============================================================

        ModrinthClient client =
                new ModrinthClient();

        try {

            ModrinthSearchResult result =
                    client.search(
                            "sodium",
                            "fabric",
                            "1.21.11"
                    );

            System.out.println(
                    "Hits returned: "
                            + result.getHits().size()
            );

            // ---------------------------------------------------------
            // SEARCH RESULTS
            // ---------------------------------------------------------

            for (ModrinthProject project :
                    result.getHits()) {

                System.out.println(
                        "TITLE: "
                                + project.getTitle()
                );

                System.out.println(
                        "PROJECT ID: "
                                + project.getProjectId()
                );

                System.out.println(
                        "SLUG: "
                                + project.getSlug()
                );

                System.out.println(
                        "DESCRIPTION: "
                                + project.getDescription()
                );

                System.out.println();
            }

            // ---------------------------------------------------------
            // TEST VERSIONS OF FIRST RESULT
            // ---------------------------------------------------------

            if (!result.getHits().isEmpty()) {

                ModrinthProject project =
                        result.getHits().get(0);

                System.out.println(
                        "Testing versions for: "
                                + project.getTitle()
                );

                System.out.println(
                        "Project ID: "
                                + project.getProjectId()
                );

                List<ModrinthVersion> versions =
                        client.getVersions(
                                project.getProjectId()
                        );

                System.out.println(
                        "Versions returned: "
                                + versions.size()
                );

                for (ModrinthVersion version :
                        versions) {

                    if (version.getGameVersions()
                            .contains("1.21.11")
                            && version.getLoaders()
                            .contains("fabric")) {

                        System.out.println(
                                "Compatible version: "
                                        + version.getName()
                                        + " | "
                                        + version.getVersionNumber()
                        );

                        for (ModrinthFile file :
                                version.getFiles()) {

                            System.out.println(
                                    "  FILE: "
                                            + file.getFilename()
                                            + " | "
                                            + file.getUrl()
                            );
                        }
                    }
                }
            }

        } catch (IOException | InterruptedException e) {

            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        try {

            System.out.println(
                    "Vanta started"
            );

            launch(args);

        } catch (Throwable t) {

            t.printStackTrace();

            try {

                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(
                                "launcher-crash.txt"
                        ),
                        t.toString()
                );

            } catch (Exception ignored) {
            }

            throw t;
        }
    }
}

