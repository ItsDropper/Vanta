package org.example.launcher;

import org.example.launcher.account.Account;

import java.nio.file.Path;
import java.util.List;

public class MinecraftLauncher {

    public static Process launch(Account account) throws Exception {

        Path minecraft =
                MinecraftLocator.getMinecraftDirectory();

        System.out.println("Building Minecraft classpath...");

        List<Path> classpath =
                ClasspathBuilder.build(
                        minecraft,
                        "fabric-loader-0.19.3-1.21.11"
                );

        String cp =
                ClasspathStringBuilder.build(classpath);

        System.out.println("Extracting natives...");

        Path natives =
                minecraft.resolve("natives");

        NativeExtractor.extractAll(
                classpath,
                natives
        );

        System.out.println("Starting Minecraft...");

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        JavaLocator.getJava(),

                        "-Djava.library.path=" + natives,

                        "-Dminecraft.assets.root="
                                + minecraft.resolve("assets"),

                        "-cp",
                        cp,

                        "net.fabricmc.loader.impl.launch.knot.KnotClient",

                        "--gameDir",
                        minecraft.toString(),

                        "--assetsDir",
                        minecraft.resolve("assets").toString(),

                        "--version",
                        "1.21.11",

                        "--assetIndex",
                        "26",

                        "--username",
                        account.getUsername(),

                        "--uuid",
                        account.getUuid().replace("-", ""),

                        "--accessToken",
                        account.getAccessToken(),

                        "--userType",
                        "msa"
                );

        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();

        Process process =
                processBuilder.start();

        System.out.println(
                "Started Minecraft with PID: "
                        + process.pid()
        );

        return process;
    }
}

