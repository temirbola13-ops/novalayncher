package app.nova.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class JavaRuntimeTest {
    static final String ALL = "{\"windows-x64\":{\"jre-legacy\":[{\"manifest\":{\"url\":\"https://x/m.json\",\"sha1\":\"a\",\"size\":1}}],\"java-runtime-gamma\":[]}}";
    @Test void findsManifest() throws Exception {
        assertEquals("https://x/m.json", JavaRuntime.findManifest(ALL, "windows-x64", "jre-legacy").get("url").getAsString());
        assertThrows(java.io.IOException.class, () -> JavaRuntime.findManifest(ALL, "windows-x64", "java-runtime-gamma"));
        assertThrows(java.io.IOException.class, () -> JavaRuntime.findManifest(ALL, "windows-arm64", "jre-legacy"));
    }
    @Test void jobsAndPathSafety(@TempDir Path d) throws Exception {
        String ok = "{\"files\":{\"bin\":{\"type\":\"directory\"},\"bin/java.exe\":{\"type\":\"file\",\"downloads\":{\"raw\":{\"url\":\"https://x/j\",\"sha1\":\"b\",\"size\":5}}}}}";
        var jobs = JavaRuntime.jobs(ok, d);
        assertEquals(1, jobs.size()); assertTrue(Files.isDirectory(d.resolve("bin")));
        String evil = "{\"files\":{\"../x.dll\":{\"type\":\"file\",\"downloads\":{\"raw\":{\"url\":\"https://x/j\",\"sha1\":\"b\",\"size\":5}}}}}";
        assertThrows(SecurityException.class, () -> JavaRuntime.jobs(evil, d));
    }
}
