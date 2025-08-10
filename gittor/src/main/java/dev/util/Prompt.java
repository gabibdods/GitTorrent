package dev.util;

import java.io.Console;

public class Prompt {
    public static boolean confirm(String msg, boolean defYes) {
        Console c = System.console();
        if (c == null) return defYes;
        String hint = defYes ? "Y/n" : "y/N";
        String s = c.readLine("%s [%s]: ", msg, hint);
        if (s == null || s.isBlank()) return defYes;
        s = s.trim().toLowerCase();
        return s.startsWith("y");
    }
}
