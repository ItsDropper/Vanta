package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Library {

    public String name;

    public Map<String, String> natives;
}