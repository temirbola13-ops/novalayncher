package app.nova.core;

import com.google.gson.*;
import java.util.*;

/** Parsed official version JSON (vanilla, no inheritsFrom). */
public final class VersionJson {
    public record Artifact(String path, String url, String sha1, long size) {}
    public record Library(Artifact classpath, Artifact nativesJar, List<String> extractExcludes) {}

    public final JsonObject raw;
    public final String id, type, mainClass, os, arch;
    public final Artifact assetIndex, client;
    public final String assetsId;
    public final int javaMajor;
    public final String javaComponent;
    public final List<Library> libraries = new ArrayList<>();

    public static VersionJson parse(String json) { return parse(json, "windows", Rules.currentArch()); }

    public static VersionJson parse(String json, String os, String arch) {
        return new VersionJson(JsonParser.parseString(json).getAsJsonObject(), os, arch);
    }

    private VersionJson(JsonObject o, String os, String arch) {
        this.raw = o; this.os = os; this.arch = arch;
        id = o.get("id").getAsString();
        type = o.has("type") ? o.get("type").getAsString() : "release";
        mainClass = o.get("mainClass").getAsString();
        JsonObject ai = o.getAsJsonObject("assetIndex");
        assetsId = ai.get("id").getAsString();
        assetIndex = new Artifact(null, ai.get("url").getAsString(), ai.get("sha1").getAsString(), ai.get("size").getAsLong());
        client = art(o.getAsJsonObject("downloads").getAsJsonObject("client"));
        javaMajor = o.has("javaVersion") ? o.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
        javaComponent = o.has("javaVersion") && o.getAsJsonObject("javaVersion").has("component")
                ? o.getAsJsonObject("javaVersion").get("component").getAsString() : "jre-legacy";
        for (JsonElement e : o.getAsJsonArray("libraries")) {
            JsonObject l = e.getAsJsonObject();
            if (!Rules.allowed(l.getAsJsonArray("rules"), Set.of(), os, arch)) continue;
            JsonObject dl = l.has("downloads") ? l.getAsJsonObject("downloads") : null;
            if (dl == null) continue;
            Artifact cp = dl.has("artifact") ? art(dl.getAsJsonObject("artifact")) : null;
            Artifact nat = null; List<String> ex = new ArrayList<>();
            if (l.has("natives") && l.getAsJsonObject("natives").has(os) && dl.has("classifiers")) {
                String c = l.getAsJsonObject("natives").get(os).getAsString().replace("${arch}", arch.equals("x86") ? "32" : "64");
                if (dl.getAsJsonObject("classifiers").has(c)) nat = art(dl.getAsJsonObject("classifiers").getAsJsonObject(c));
                if (l.has("extract") && l.getAsJsonObject("extract").has("exclude"))
                    l.getAsJsonObject("extract").getAsJsonArray("exclude").forEach(x -> ex.add(x.getAsString()));
            }
            if (cp != null || nat != null) libraries.add(new Library(cp, nat, ex));
        }
    }

    private static Artifact art(JsonObject a) {
        return new Artifact(a.has("path") ? a.get("path").getAsString() : null, a.get("url").getAsString(),
                a.has("sha1") ? a.get("sha1").getAsString() : null, a.has("size") ? a.get("size").getAsLong() : 0);
    }
}
