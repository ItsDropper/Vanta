package org.example.launcher.account;

import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;

public class Account {

    private StepFullJavaSession.FullJavaSession session;

    public Account() {
    }

    public Account(StepFullJavaSession.FullJavaSession session) {
        this.session = session;
    }

    public StepFullJavaSession.FullJavaSession getSession() {
        return session;
    }

    public void setSession(StepFullJavaSession.FullJavaSession session) {
        this.session = session;
    }

    public String getUsername() {
        return session.getMcProfile().getName();
    }

    public String getUuid() {
        return session.getMcProfile().getId().toString();
    }

    public String getAccessToken() {
        return session.getMcProfile()
                .getMcToken()
                .getAccessToken();
    }
}