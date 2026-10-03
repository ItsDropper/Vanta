package org.example.launcher.state;

public final class InstanceState {
    public enum Level { HEALTHY, ATTENTION, BROKEN }
    private final Level level;
    private final String title;
    private final String summary;
    private final int checksPassed;
    private final int checksTotal;
    private final int mods;
    private final int configs;
    private final String fingerprint;

    public InstanceState(Level level, String title, String summary, int checksPassed, int checksTotal, int mods, int configs, String fingerprint) {
        this.level=level; this.title=title; this.summary=summary; this.checksPassed=checksPassed; this.checksTotal=checksTotal; this.mods=mods; this.configs=configs; this.fingerprint=fingerprint;
    }
    public Level getLevel(){return level;}
    public String getTitle(){return title;}
    public String getSummary(){return summary;}
    public int getChecksPassed(){return checksPassed;}
    public int getChecksTotal(){return checksTotal;}
    public int getMods(){return mods;}
    public int getConfigs(){return configs;}
    public String getFingerprint(){return fingerprint;}
    public boolean isHealthy(){return level==Level.HEALTHY;}
}