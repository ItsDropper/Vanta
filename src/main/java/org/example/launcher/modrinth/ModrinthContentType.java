package org.example.launcher.modrinth;

public enum ModrinthContentType {

    MOD(
            "mod",
            "Mod"
    ),

    MODPACK(
            "modpack",
            "Modpack"
    ),

    RESOURCE_PACK(
            "resourcepack",
            "Resource Pack"
    ),

    SHADER(
            "shader",
            "Shader"
    );

    private final String apiValue;
    private final String displayName;

    ModrinthContentType(
            String apiValue,
            String displayName
    ) {
        this.apiValue = apiValue;
        this.displayName = displayName;
    }

    public String getApiValue() {
        return apiValue;
    }

    public String getDisplayName() {
        return displayName;
    }
}