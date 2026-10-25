package app.nova.core;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

/**
 * Resumable HTTPS downloader: retries with backoff, Range-resume from a .part file,
 * manual redirect handling (every hop must be HTTPS), SHA-1 verification, cancellation.
 */
public final class Downloader {
    private static final int MAX_REDIRECTS = 5;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15)).build();
    private final boolean httpsOnly;
    private final int maxAttempts;
    private final long backoffMs;

    public Downloader() { this(true, 4, 1000); }
    /** httpsOnly=false is meant for tests against a local server only. */
    public Downloader(boolean httpsOnly, int maxAttempts, long backoffMs) {
        this.httpsOnly = httpsOnly; this.maxAttempts = maxAttempts; this.backoffMs = backoffMs;
    }

    /** True if dest exists and (when a hash is known) matches it. */
    public static boolean isValid(Path dest, String sha1) throws IOException {
        if (!Files.isRegularFile(dest)) return false;
        return sha1 == null || sha1.equalsIgnoreCase(Hashing.sha1(dest));
    }

    /** @param sha1 expected hash or null if the source provides none. */
    public void download(URI uri, Path dest, String sha1, AtomicBoolean cancel, LongConsumer onBytes)
            throws IOException, InterruptedException {
        if (sha1 != null && Files.isRegularFile(dest) && sha1.equalsIgnoreCase(Hashing.sha1(dest))) return;
        Files.createDirectories(dest.toAbsolutePath().getParent());
        Path part = dest.resolveSibling(dest.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            checkCancel(cancel);
            try {
                fetch(uri, part, cancel, onBytes);
                if (sha1 != null) {
                    String actual = Hashing.sha1(part);
                    if (!sha1.equalsIgnoreCase(actual)) {
                        Files.deleteIfExists(part);
                        throw new HashMismatchException(sha1, actual);
                    }
                }
                Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                if (attempt < maxAttempts) Thread.sleep(backoffMs * attempt);
            }
        }
        throw last;
    }

    private void fetch(URI start, Path part, AtomicBoolean cancel, LongConsumer onBytes)
            throws IOException, InterruptedException {
        URI uri = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            requireHttps(uri);
            long have = Files.isRegularFile(part) ? Files.size(part) : 0;
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET();
            if (have > 0) rb.header("Range", "bytes=" + have + "-");
            HttpResponse<InputStream> r = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            int code = r.statusCode();
            if (code >= 300 && code < 400 && code != 304) {
                r.body().close();
                String loc = r.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("Redirect without Location"));
                uri = uri.resolve(loc);
                continue;
            }
            if (code == 416) { r.body().close(); Files.deleteIfExists(part); throw new IOException("Stale partial file, restarting"); }
            if (code != 200 && code != 206) { r.body().close(); throw new IOException("HTTP " + code + " for " + uri); }
            boolean append = code == 206 && have > 0;
            try (InputStream in = r.body();
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                         append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) {
                    checkCancel(cancel);               // .part is kept so the download can resume later
                    out.write(buf, 0, n);
                    if (onBytes != null) onBytes.accept(n);
                }
            }
            return;
        }
        throw new IOException("Too many redirects");
    }

    private void requireHttps(URI u) throws IOException {
        if (httpsOnly && !"https".equalsIgnoreCase(u.getScheme()))
            throw new IOException("Refusing non-HTTPS URL: " + u);
    }
    private static void checkCancel(AtomicBoolean c) { if (c != null && c.get()) throw new CancellationException("cancelled"); }
}
