package app.nova.core;

import com.google.gson.*;
import java.util.Set;
import java.util.regex.Pattern;

/** Evaluates Mojang "rules" arrays: default deny, the last matching rule decides. */
public final class Rules {
    private Rules() {}

    public static String currentArch() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        if (a.contains("aarch64") || a.contains("arm64")) return "arm64";
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) return "x86";
        return "x86_64";
    }

    public static boolean allowed(JsonArray rules, Set<String> features, String os, String arch) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allow = false;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            if (matches(r, features, os, arch)) allow = "allow".equals(r.get("action").getAsString());
        }
        return allow;
    }

    private static boolean matches(JsonObject r, Set<String> features, String os, String arch) {
        if (r.has("os")) {
            JsonObject o = r.getAsJsonObject("os");
            if (o.has("name") && !o.get("name").getAsString().equals(os)) return false;
            if (o.has("arch") && !o.get("arch").getAsString().equals(arch)) return false;
            if (o.has("version") && !Pattern.compile(o.get("version").getAsString())
                    .matcher(System.getProperty("os.version", "")).find()) return false;
        }
        if (r.has("features"))
            for (var en : r.getAsJsonObject("features").entrySet())
                if (en.getValue().getAsBoolean() != features.contains(en.getKey())) return false;
        return true;
    }
}
