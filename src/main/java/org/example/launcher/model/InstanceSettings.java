package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InstanceSettings {

    private int ramMb;
    private String javaArguments;
    private String gameArguments;
    private int width;
    private int height;
    private boolean fullscreen;
    private String javaPath;

    @JsonCreator
    public InstanceSettings(
            @JsonProperty("ramMb")
            int ramMb,

            @JsonProperty("javaArguments")
            String javaArguments,

            @JsonProperty("gameArguments")
            String gameArguments,

            @JsonProperty("width")
            int width,

            @JsonProperty("height")
            int height,

            @JsonProperty("fullscreen")
            boolean fullscreen,

            @JsonProperty("javaPath")
            String javaPath
    ) {

        this.ramMb =
                ramMb > 0
                        ? ramMb
                        : 4096;

        this.javaArguments =
                javaArguments != null
                        ? javaArguments
                        : "";

        this.gameArguments =
                gameArguments != null
                        ? gameArguments
                        : "";

        this.width =
                width > 0
                        ? width
                        : 1280;

        this.height =
                height > 0
                        ? height
                        : 720;

        this.fullscreen =
                fullscreen;

        this.javaPath =
                javaPath != null
                        ? javaPath
                        : "";
    }

    public InstanceSettings() {

        this(
                4096,
                "",
                "",
                1280,
                720,
                false,
                ""
        );
    }

    public int getRamMb() {
        return ramMb;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = ramMb;
    }

    public String getJavaArguments() {
        return javaArguments;
    }

    public void setJavaArguments(String javaArguments) {
        this.javaArguments =
                javaArguments != null
                        ? javaArguments
                        : "";
    }

    public String getGameArguments() {
        return gameArguments;
    }

    public void setGameArguments(String gameArguments) {
        this.gameArguments =
                gameArguments != null
                        ? gameArguments
                        : "";
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath =
                javaPath != null
                        ? javaPath
                        : "";
    }
}