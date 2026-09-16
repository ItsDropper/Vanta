package org.example.launcher.update;

public class UpdateServiceTest {

    public static void main(String[] args) throws Exception {

        UpdateService service =
                new UpdateService();

        UpdateChecker checker =
                new UpdateChecker();

        UpdateInfo info =
                checker.check().join();

        if (info == null) {
            throw new AssertionError(
                    "UpdateService returned null."
            );
        }

        if (info.getLatestVersion() == null
                || info.getLatestVersion().isBlank()) {

            throw new AssertionError(
                    "GitHub did not return a version."
            );
        }

        System.out.println(
                "Current version: "
                        + info.getCurrentVersion()
        );

        System.out.println(
                "Latest version: "
                        + info.getLatestVersion()
        );

        System.out.println(
                "Update available: "
                        + info.isUpdateAvailable()
        );

        System.out.println(
                "UpdateService test passed."
        );
    }
}