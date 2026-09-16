package org.example.launcher.update;

public class UpdateDownloaderTest {

    public static void main(String[] args) throws Exception {

        UpdateChecker checker =
                new UpdateChecker();

        UpdateInfo updateInfo =
                checker.check().join();

        if (!updateInfo.isUpdateAvailable()) {
            throw new IllegalStateException(
                    "No update is available for testing."
            );
        }

        System.out.println(
                "Downloading " +
                        updateInfo.getLatestVersion()
        );

        UpdateDownloader downloader =
                new UpdateDownloader();

        var zip =
                downloader.downloadAndVerify(
                        updateInfo
                );

        System.out.println(
                "Verified update:"
        );

        System.out.println(
                zip.toAbsolutePath()
        );

        System.out.println(
                "Size: " +
                        java.nio.file.Files.size(zip) +
                        " bytes"
        );

        System.out.println(
                "UpdateDownloader test passed."
        );
    }
}