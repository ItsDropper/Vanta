package org.example.launcher.state;

public final class SharedState {
    public enum Level { HEALTHY, ATTENTION, BROKEN }
    private final String name;
    private final Level level;
    private final int files;
    private final int broken;
    private final String summary;

    public SharedState(String name, Level level, int files, int broken, String summary) {
        this.name = name; this.level = level; this.files = files; this.broken = broken; this.summary = summary;
    }
    public String getName() { return name; }
    public Level getLevel() { return level; }
    public int getFiles() { return files; }
    public int getBroken() { return broken; }
    public String getSummary() { return summary; }
}
