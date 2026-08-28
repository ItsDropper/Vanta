package org.example.launcher;

public class JavaLocator {

    public static String getJava() {
        return System.getProperty("java.home")
                + "\\bin\\java.exe";
    }
}