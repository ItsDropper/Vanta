package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthSearchResult {

    private List<ModrinthProject> hits;
    private int offset;
    private int limit;
    private int total_hits;

    public List<ModrinthProject> getHits() {
        return hits;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public int getTotalHits() {
        return total_hits;
    }
}