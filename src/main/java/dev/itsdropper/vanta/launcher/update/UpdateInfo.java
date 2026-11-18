package dev.itsdropper.vanta.launcher.update;

public class UpdateInfo {

    private final String currentVersion;
    private final String latestVersion;
    private final String downloadUrl;
    private final String checksumUrl;
    private final String releaseNotes;
    private final String sha256;
    private final long sizeBytes;
    private final String minimumLauncherVersion;

    public UpdateInfo(String currentVersion, String latestVersion, String downloadUrl,
                      String checksumUrl, String releaseNotes) {
        this(currentVersion, latestVersion, downloadUrl, checksumUrl, releaseNotes, null, -1, null);
    }

    public UpdateInfo(String currentVersion, String latestVersion, String downloadUrl,
                      String checksumUrl, String releaseNotes, String sha256,
                      long sizeBytes, String minimumLauncherVersion) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.checksumUrl = checksumUrl;
        this.releaseNotes = releaseNotes;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.minimumLauncherVersion = minimumLauncherVersion;
    }

    public String getCurrentVersion() { return currentVersion; }
    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getChecksumUrl() { return checksumUrl; }
    public String getReleaseNotes() { return releaseNotes; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public String getMinimumLauncherVersion() { return minimumLauncherVersion; }

    public boolean isUpdateAvailable() {
        return compareVersions(latestVersion, currentVersion) > 0;
    }

    public boolean isDowngrade() {
        return compareVersions(latestVersion, currentVersion) < 0;
    }

    public static int compareVersions(String first, String second) {
        int[] firstParts = parseVersion(first);
        int[] secondParts = parseVersion(second);
        for (int i = 0; i < 3; i++) {
            if (firstParts[i] != secondParts[i]) {
                return Integer.compare(firstParts[i], secondParts[i]);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String version) {
        String cleaned = version.trim().replaceFirst("^[vV]", "");
        cleaned = cleaned.replaceFirst("(?i)(?<=\\\\d)[a-z][a-z0-9.-]*$", "");
        String[] parts = cleaned.split("\\\\.");
        int[] result = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid version: " + version, e);
            }
        }
        return result;
    }
}
