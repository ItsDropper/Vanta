package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftFileInstallerPlatformTest {
    @Test
    void matchesArm64RulesWithoutConfusingArmWithX8664() {
        assertTrue(MinecraftFileInstaller.matchesArchitecture("arm64", "aarch64"));
        assertTrue(MinecraftFileInstaller.matchesArchitecture("aarch64", "arm64"));
        assertTrue(MinecraftFileInstaller.matchesArchitecture("64", "aarch64"));
        assertFalse(MinecraftFileInstaller.matchesArchitecture("x86_64", "aarch64"));
        assertFalse(MinecraftFileInstaller.matchesArchitecture("arm64", "x86_64"));
    }

    @Test
    void matchesCommonX86AndGenericBitnessRules() {
        assertTrue(MinecraftFileInstaller.matchesArchitecture("x86_64", "AMD64"));
        assertTrue(MinecraftFileInstaller.matchesArchitecture("64", "x86_64"));
        assertTrue(MinecraftFileInstaller.matchesArchitecture("x86", "i686"));
        assertTrue(MinecraftFileInstaller.matchesArchitecture("32", "x86"));
        assertFalse(MinecraftFileInstaller.matchesArchitecture("x86", "aarch64"));
    }
}
