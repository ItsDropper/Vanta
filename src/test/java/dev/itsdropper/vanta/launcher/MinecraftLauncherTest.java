package dev.itsdropper.vanta.launcher;

import org.junit.jupiter.api.Test;

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
    void rejectsMissingLaunchWrapperOptionValue() {
        List<String> command = new ArrayList<>(List.of(
                "java", "net.minecraft.launchwrapper.Launch"
        ));
        assertThrows(IllegalStateException.class, () ->
                MinecraftLauncher.ensureLaunchWrapperOption(
                        command, "net.minecraft.launchwrapper.Launch", "--assetsDir", null));
    }
}
