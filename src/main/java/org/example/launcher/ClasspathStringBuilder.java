package org.example.launcher;

import java.nio.file.Path;
import java.util.List;

public class ClasspathStringBuilder {

    public static String build(List<Path> paths) {

        StringBuilder builder = new StringBuilder();

        for (Path path : paths) {
            if (builder.length() > 0) {
                builder.append(";");
            }

            builder.append(path.toAbsolutePath());
        }

        return builder.toString();
    }
}