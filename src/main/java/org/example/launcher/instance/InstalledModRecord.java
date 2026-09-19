package org.example.launcher.instance;

public class InstalledModRecord {

    private String projectId;
    private String versionId;
    private String modId;
    private String version;
    private String filename;

    public InstalledModRecord() {
    }

    public InstalledModRecord(
            String projectId,
            String versionId,
            String modId,
            String version,
            String filename
    ) {
        this.projectId = projectId;
        this.versionId = versionId;
        this.modId = modId;
        this.version = version;
        this.filename = filename;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getModId() {
        return modId;
    }

    public String getVersion() {
        return version;
    }

    public String getFilename() {
        return filename;
    }
}