package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthSearchResult {

    private List<ModrinthSearchHit> hits;

    private int offset;
    private int limit;

    @JsonProperty("total_hits")
    private int totalHits;

    public ModrinthSearchResult() {
    }

    public List<ModrinthSearchHit> getHits() {
        return hits;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public int getTotalHits() {
        return totalHits;
    }
}