import org.example.launcher.NativeExtractor;

import java.nio.file.Path;

public class TestNatives {

    public static void main(String[] args) throws Exception {

        Path minecraft = Path.of(
                System.getProperty("user.home"),
                "AppData",
                "Roaming",
                ".minecraft"
        );

        Path natives = minecraft.resolve("natives");

        Path nativeJar = minecraft
                .resolve("libraries")
                .resolve("org")
                .resolve("lwjgl")
                .resolve("lwjgl-glfw")
                .resolve("3.3.3")
                .resolve("lwjgl-glfw-3.3.3-natives-windows.jar");

        NativeExtractor.extract(nativeJar, natives);

        System.out.println("Done");
    }
}