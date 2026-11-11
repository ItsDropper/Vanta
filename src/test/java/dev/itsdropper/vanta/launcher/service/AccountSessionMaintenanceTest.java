package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.account.Account;
import net.raphimc.minecraftauth.step.java.StepMCProfile;
import net.raphimc.minecraftauth.step.java.StepMCToken;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.xbl.StepXblXstsToken;
import net.raphimc.minecraftauth.step.xbl.session.StepFullXblSession;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountSessionMaintenanceTest {
    @Test
    void missingSessionDoesNotTriggerBackgroundRefresh() {
        assertFalse(AccountService.isSessionRefreshDue(null));
        assertFalse(AccountService.isSessionRefreshDue(new Account()));
    }

    @Test
    void detectsExpiredUpstreamXboxTokenEvenWhenMinecraftTokenHasNotExpired() {
        Account account = accountWithTokenChain(true);

        assertTrue(account.getSession().getMcProfile().getMcToken().getExpireTimeMs()
                > System.currentTimeMillis());
        assertTrue(AccountService.isSessionRefreshDue(account));
    }

    @Test
    void doesNotRefreshWhenEntireTokenChainIsValid() {
        assertFalse(AccountService.isSessionRefreshDue(accountWithTokenChain(false)));
    }

    private static Account accountWithTokenChain(boolean upstreamExpired) {
        TestXblXsts xsts = new TestXblXsts(upstreamExpired);
        StepMCToken.MCToken minecraftToken = new StepMCToken.MCToken(
                "test-access-token",
                "Bearer",
                System.currentTimeMillis() + 60_000,
                xsts
        );
        StepMCProfile.MCProfile profile = new StepMCProfile.MCProfile(
                UUID.randomUUID(),
                "TestPlayer",
                "",
                minecraftToken
        );
        return new Account(new StepFullJavaSession.FullJavaSession(profile, null));
    }

    private static final class TestXblXsts
            extends StepXblXstsToken.XblXsts<StepFullXblSession.FullXblSession> {
        private final boolean expired;

        private TestXblXsts(boolean expired) {
            this.expired = expired;
        }

        @Override
        public long getExpireTimeMs() {
            return expired ? 0 : System.currentTimeMillis() + 60_000;
        }

        @Override
        public String getToken() {
            return "test-xsts-token";
        }

        @Override
        public String getUserHash() {
            return "test-user";
        }

        @Override
        public Map<String, String> getDisplayClaims() {
            return Map.of();
        }

        @Override
        public StepFullXblSession.FullXblSession getFullXblSession() {
            return null;
        }

        @Override
        protected StepFullXblSession.FullXblSession prevResult() {
            return null;
        }

        @Override
        public boolean isExpired() {
            return expired;
        }
    }
}
