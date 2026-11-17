package dev.itsdropper.vanta.launcher;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftLauncherTest {

    @Test
    void ensuresLaunchWrapperOptionExistsExactlyOnceAfterMainClass() {
        List<String> command = new ArrayList<>(List.of(
                "java", "-Xmx2G", "-cp", "libraries", "net.minecraft.launchwrapper.Launch",
                "--gameDir", "old-game", "--version", "old-version", "--tweakClass", "example.Tweaker"
        ));

        MinecraftLauncher.ensureLaunchWrapperOption(
                command, "net.minecraft.launchwrapper.Launch", "--assetsDir", "C:/Minecraft/assets");
        MinecraftLauncher.ensureLaunchWrapperOption(
                command, "net.minecraft.launchwrapper.Launch", "--gameDir", "C:/Instances/rift test");
        MinecraftLauncher.ensureLaunchWrapperOption(
                command, "net.minecraft.launchwrapper.Launch", "--version", "1.13");

        int mainClass = command.indexOf("net.minecraft.launchwrapper.Launch");
        List<String> gameArguments = command.subList(mainClass + 1, command.size());
        assertEquals(1, gameArguments.stream().filter("--assetsDir"::equals).count());
        assertEquals(1, gameArguments.stream().filter("--gameDir"::equals).count());
        assertEquals(1, gameArguments.stream().filter("--version"::equals).count());
        assertEquals(List.of(
                "--tweakClass", "example.Tweaker",
                "--assetsDir", "C:/Minecraft/assets",
                "--gameDir", "C:/Instances/rift test",
                "--version", "1.13"
        ), gameArguments);
    }

    @Test
    void extractsRiftTweakersWithoutDroppingAuthenticationArgumentsFromNormalLaunchPath() {
        assertEquals(List.of(
                "--tweakClass", "org.dimdev.riftloader.launch.RiftLoaderClientTweaker"
        ), MinecraftLauncher.riftTweakerArguments(
                "--tweakClass \"org.dimdev.riftloader.launch.RiftLoaderClientTweaker\""));
    }

    @Test
    void rejectsRiftProfileWithoutTweaker() {
        assertThrows(IllegalStateException.class, () ->
                MinecraftLauncher.riftTweakerArguments("--someOtherOption value"));
    }

    @Test
    void rejectsRiftTweakerWithoutClassName() {
        assertThrows(IllegalStateException.class, () ->
                MinecraftLauncher.riftTweakerArguments("--tweakClass"));
    }

    @Test
    void disablesVulnerableBase64IntrinsicForJava17AndNewerOnX64() {
        assertEquals(List.of("-XX:-UseBASE64Intrinsics"),
                MinecraftLauncher.java4ShellMitigationArguments(17, "amd64"));
        assertEquals(List.of("-XX:-UseBASE64Intrinsics"),
                MinecraftLauncher.java4ShellMitigationArguments(25, "x86_64"));
        assertEquals(List.of("-XX:-UseBASE64Intrinsics"),
                MinecraftLauncher.java4ShellMitigationArguments(21, "x64"));
    }

    @Test
    void doesNotPassJava4ShellMitigationToJava8OrArmRuntimes() {
        assertEquals(List.of(), MinecraftLauncher.java4ShellMitigationArguments(8, "amd64"));
        assertEquals(List.of(), MinecraftLauncher.java4ShellMitigationArguments(21, "aarch64"));
        assertEquals(List.of(), MinecraftLauncher.java4ShellMitigationArguments(21, null));
    }

    @Test
    void rejectsMissingLaunchWrapperOptionValue() {
        List<String> command = new ArrayList<>(List.of(
                "java", "net.minecraft.launchwrapper.Launch"
        ));
        assertThrows(IllegalStateException.class, () ->
                MinecraftLauncher.ensureLaunchWrapperOption(
                        command, "net.minecraft.launchwrapper.Launch", "--assetsDir", null));
    }
}
