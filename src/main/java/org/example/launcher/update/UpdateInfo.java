package org.example.launcher.update;

public class UpdateInfo {

    private final String currentVersion;
    private final String latestVersion;
    private final String downloadUrl;
    private final String checksumUrl;
    private final String releaseNotes;

    public UpdateInfo(
            String currentVersion,
            String latestVersion,
            String downloadUrl,
            String checksumUrl,
            String releaseNotes
    ) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.checksumUrl = checksumUrl;
        this.releaseNotes = releaseNotes;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getChecksumUrl() {
        return checksumUrl;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public boolean isUpdateAvailable() {
        return compareVersions(
                latestVersion,
                currentVersion
        ) > 0;
    }

    private int compareVersions(
            String first,
            String second
    ) {
        int[] firstParts =
                parseVersion(first);

        int[] secondParts =
                parseVersion(second);

        for (int i = 0; i < 3; i++) {

            if (firstParts[i] != secondParts[i]) {

                return Integer.compare(
                        firstParts[i],
                        secondParts[i]
                );
            }
        }

        return 0;
    }

    private int[] parseVersion(
            String version
    ) {
        String cleaned =
                version
                        .trim()
                        .replaceFirst(
                                "^[vV]",
                                ""
                        );

        String[] parts =
                cleaned.split("\\.");

        int[] result =
                new int[3];

        for (
                int i = 0;
                i < Math.min(parts.length, 3);
                i++
        ) {
            try {

                result[i] =
                        Integer.parseInt(
                                parts[i]
                        );

            } catch (NumberFormatException e) {

                throw new IllegalArgumentException(
                        "Invalid version: " + version,
                        e
                );
            }
        }

        return result;
    }
}