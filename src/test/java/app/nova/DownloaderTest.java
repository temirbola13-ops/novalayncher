package app.nova;
import app.nova.core.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class DownloaderTest {
    static final byte[] DATA = "hello nova launcher".getBytes(StandardCharsets.UTF_8);
    HttpServer srv; AtomicInteger hits = new AtomicInteger(); int failFirst = 0;

    @BeforeEach void up() throws Exception {
        srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/f", ex -> {
            if (hits.incrementAndGet() <= failFirst) { ex.sendResponseHeaders(503, -1); ex.close(); return; }
            ex.sendResponseHeaders(200, DATA.length); ex.getResponseBody().write(DATA); ex.close();
        });
        srv.createContext("/r", ex -> { ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + srv.getAddress().getPort() + "/f"); ex.sendResponseHeaders(302, -1); ex.close(); });
        srv.start();
    }
    @AfterEach void down() { srv.stop(0); }
    URI u(String p) { return URI.create("http://127.0.0.1:" + srv.getAddress().getPort() + p); }
    String sha() throws Exception { var f = Files.createTempFile("h", ""); Files.write(f, DATA); try { return Hashing.sha1(f); } finally { Files.delete(f); } }

    @Test void downloadsAndVerifies(@TempDir Path d) throws Exception {
        AtomicLong n = new AtomicLong();
        new Downloader(false, 2, 1).download(u("/f"), d.resolve("a/b.bin"), sha(), null, n::addAndGet);
        assertArrayEquals(DATA, Files.readAllBytes(d.resolve("a/b.bin")));
        assertEquals(DATA.length, n.get());
    }
    @Test void retriesOnServerError(@TempDir Path d) throws Exception {
        failFirst = 2;
        new Downloader(false, 4, 1).download(u("/f"), d.resolve("x"), sha(), null, null);
        assertEquals(3, hits.get());
    }
    @Test void rejectsWrongHash(@TempDir Path d) {
        assertThrows(HashMismatchException.class, () ->
            new Downloader(false, 2, 1).download(u("/f"), d.resolve("x"), "0".repeat(40), null, null));
        assertFalse(Files.exists(d.resolve("x")));
    }
    @Test void followsRedirect(@TempDir Path d) throws Exception {
        new Downloader(false, 1, 1).download(u("/r"), d.resolve("x"), null, null, null);
        assertTrue(Files.exists(d.resolve("x")));
    }
    @Test void refusesHttpByDefault(@TempDir Path d) {
        assertThrows(java.io.IOException.class, () -> new Downloader(true, 1, 1).download(u("/f"), d.resolve("x"), null, null, null));
    }
    @Test void cancelStopsDownload(@TempDir Path d) {
        AtomicBoolean c = new AtomicBoolean(true);
        assertThrows(CancellationException.class, () -> new Downloader(false, 1, 1).download(u("/f"), d.resolve("x"), null, c, null));
    }
}
