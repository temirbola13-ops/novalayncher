package app.nova.core;

import app.nova.core.VersionJson.*;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/** Installs a vanilla version: version json -> client jar + libraries -> asset index -> assets -> natives. */
public final class Installer {
    public interface Listener { void stage(String s); void bytes(long done, long total); }
    record Job(URI uri, Path dest, String sha1, long size) {}
    static final String ASSET_BASE = "https://resources.download.minecraft.net/";

    private Installer() {}

    public static Path versionDir(Path root, String id) { return SafePath.resolve(root, "versions/" + id); }

    public static VersionJson install(VersionManifest.Entry e, Path root, int parallel, AtomicBoolean cancel, Listener l)
            throws Exception {
        Downloader dl = new Downloader();
        l.stage("version");
        Path vdir = versionDir(root, e.id());
        Path vjson = vdir.resolve(e.id() + ".json");
        dl.download(URI.create(e.url()), vjson, e.sha1(), cancel, null);
        VersionJson v = VersionJson.parse(Files.readString(vjson));

        l.stage("assetIndex");
        Path idx = SafePath.resolve(root, "assets/indexes/" + v.assetsId + ".json");
        dl.download(URI.create(v.assetIndex.url()), idx, v.assetIndex.sha1(), cancel, null);

        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job(URI.create(v.client.url()), vdir.resolve(e.id() + ".jar"), v.client.sha1(), v.client.size()));
        for (Library lib : v.libraries)
            for (Artifact a : new Artifact[]{lib.classpath(), lib.nativesJar()})
                if (a != null) jobs.add(new Job(URI.create(a.url()), libPath(root, a), a.sha1(), a.size()));
        JsonObject objs = JsonParser.parseString(Files.readString(idx)).getAsJsonObject().getAsJsonObject("objects");
        for (var en : objs.entrySet()) {
            String h = en.getValue().getAsJsonObject().get("hash").getAsString();
            if (!h.matches("[0-9a-f]{40}")) throw new IOException("Bad asset hash in index: " + h);
            jobs.add(new Job(URI.create(ASSET_BASE + h.substring(0, 2) + "/" + h),
                    SafePath.resolve(root, "assets/objects/" + h.substring(0, 2) + "/" + h), h,
                    en.getValue().getAsJsonObject().get("size").getAsLong()));
        }
        l.stage("files");
        runAll(jobs, parallel, cancel, dl, l);

        l.stage("natives");
        Path nd = vdir.resolve("natives");
        for (Library lib : v.libraries)
            if (lib.nativesJar() != null) extractNatives(libPath(root, lib.nativesJar()), nd, lib.extractExcludes());
        return v;
    }

    public static Path libPath(Path root, Artifact a) { return SafePath.resolve(root, "libraries/" + a.path()); }

    static void runAll(List<Job> jobs, int parallel, AtomicBoolean cancel, Downloader dl, Listener l) throws Exception {
        long total = jobs.stream().mapToLong(Job::size).sum();
        AtomicLong done = new AtomicLong();
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, Math.min(parallel, 16)));
        try {
            List<Future<Void>> fs = new ArrayList<>();
            for (Job j : jobs) fs.add(ex.submit((Callable<Void>) () -> {
                if (Downloader.isValid(j.dest(), j.sha1())) { l.bytes(done.addAndGet(j.size()), total); return null; }
                dl.download(j.uri(), j.dest(), j.sha1(), cancel, n -> l.bytes(done.addAndGet(n), total));
                return null;
            }));
            for (Future<Void> f : fs) {
                try { f.get(); }
                catch (ExecutionException x) {
                    cancel.set(true); ex.shutdownNow();
                    if (x.getCause() instanceof Exception ce) throw ce;
                    throw x;
                }
            }
        } finally { ex.shutdownNow(); }
    }

    static void extractNatives(Path jar, Path dir, List<String> excludes) throws IOException {
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry en; (en = z.getNextEntry()) != null; ) {
                String n = en.getName();
                if (en.isDirectory() || n.startsWith("META-INF/") || excludes.stream().anyMatch(n::startsWith)) continue;
                Path out = SafePath.resolve(dir, n);
                Files.createDirectories(out.getParent());
                Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
