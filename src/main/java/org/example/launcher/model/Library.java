package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Library {

    public String name;

    public Map<String, String> natives;

    public List<Rule> rules;

    public Downloads downloads;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {

        public String action;

        public OperatingSystem os;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperatingSystem {

        public String name;

        public String arch;

        public String version;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Downloads {

        public Artifact artifact;

        public Map<String, Artifact> classifiers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artifact {

        public String path;

        public String url;

        public String sha1;

        public long size;
    }
}