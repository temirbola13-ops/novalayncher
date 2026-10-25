package app.nova.core;

import app.nova.core.Installer.Job;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads the official Mojang Java runtime (jre-legacy, java-runtime-gamma, ...) so users need no own Java. */
public final class JavaRuntime {
    static final URI ALL = URI.create(
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json");
    private JavaRuntime() {}

    static String platform() {
        return switch (Rules.currentArch()) { case "arm64" -> "windows-arm64"; case "x86" -> "windows-x86"; default -> "windows-x64"; };
    }

    /** Returns java.exe of the installed runtime, downloading and verifying it first if needed. */
    public static Path ensure(String component, Path root, int parallel, AtomicBoolean cancel, Installer.Listener l)
            throws Exception {
        Path home = SafePath.resolve(root, "runtime/" + component + "/" + platform());
        Path exe = home.resolve("bin").resolve("java.exe");
        Path ok = home.resolve(".nova-ok");
        if (Files.isRegularFile(ok) && Files.isRegularFile(exe)) return exe; // works offline once installed

        l.stage("java");
        Downloader dl = new Downloader();
        Path all = SafePath.resolve(root, "runtime/all.json");
        dl.download(ALL, all, null, cancel, null);
        JsonObject m = findManifest(Files.readString(all), platform(), component);
        Path mf = SafePath.resolve(root, "runtime/" + component + "/manifest.json");
        dl.download(URI.create(m.get("url").getAsString()), mf, m.get("sha1").getAsString(), cancel, null);
        Files.deleteIfExists(ok);
        Installer.runAll(jobs(Files.readString(mf), home), parallel, cancel, dl, l);
        if (!Files.isRegularFile(exe)) throw new IOException("Runtime installed but java.exe is missing");
        Files.writeString(ok, "ok");
        return exe;
    }

    static JsonObject findManifest(String allJson, String platform, String component) throws IOException {
        JsonObject p = JsonParser.parseString(allJson).getAsJsonObject();
        if (!p.has(platform)) throw new IOException("No Mojang Java runtime for platform " + platform);
        JsonArray a = p.getAsJsonObject(platform).getAsJsonArray(component);
        if (a == null || a.isEmpty()) throw new IOException("No Mojang Java runtime '" + component + "' for " + platform);
        return a.get(0).getAsJsonObject().getAsJsonObject("manifest");
    }

    static List<Job> jobs(String manifestJson, Path home) throws IOException {
        List<Job> jobs = new ArrayList<>();
        for (var en : JsonParser.parseString(manifestJson).getAsJsonObject().getAsJsonObject("files").entrySet()) {
            JsonObject f = en.getValue().getAsJsonObject();
            Path dest = SafePath.resolve(home, en.getKey());
            switch (f.get("type").getAsString()) {
                case "directory" -> Files.createDirectories(dest);
                case "file" -> {
                    JsonObject raw = f.getAsJsonObject("downloads").getAsJsonObject("raw");
                    jobs.add(new Job(URI.create(raw.get("url").getAsString()), dest, raw.get("sha1").getAsString(), raw.get("size").getAsLong()));
                }
                default -> { /* links do not occur on Windows runtimes */ }
            }
        }
        return jobs;
    }
}
