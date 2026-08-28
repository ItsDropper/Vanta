package org.example.launcher;

import org.example.launcher.auth.Account;

import java.nio.file.Path;
import java.util.List;

public class MinecraftLauncher {

    public static void launch(Account account) throws Exception {


        Path minecraft = MinecraftLocator.getMinecraftDirectory();

        List<Path> classpath = ClasspathBuilder.build(
                minecraft,
                "fabric-loader-0.19.3-1.21.11"
        );



        String cp = ClasspathStringBuilder.build(classpath);

        Path natives = minecraft.resolve("natives");

        NativeExtractor.extractAll(
                classpath,
                natives
        );

        ProcessBuilder processBuilder = new ProcessBuilder(
                JavaLocator.getJava(),

                "-Djava.library.path=" + natives,
                "-Dminecraft.assets.root=" + minecraft.resolve("assets"),

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
                account.username,

                "--uuid",
                account.uuid.replace("-", ""),

                "--accessToken",
                account.accessToken,

                "--userType",
                "msa"
        );
        Process process = processBuilder.start();

        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();


        System.out.println("Started Minecraft with PID: " + process.pid());

        int exitCode = process.waitFor();

        System.out.println("Minecraft exited with code: " + exitCode);
    }
}