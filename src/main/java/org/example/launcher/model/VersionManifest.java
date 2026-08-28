package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionManifest {

    public String id;

    public String inheritsFrom;

    public String mainClass;

    public List<Library> libraries;
}