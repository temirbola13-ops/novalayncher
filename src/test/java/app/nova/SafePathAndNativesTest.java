package app.nova;
import app.nova.core.SafePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class SafePathAndNativesTest {
    @Test void blocksTraversal(@TempDir Path d) {
        assertThrows(SecurityException.class, () -> SafePath.resolve(d, "../evil"));
        assertThrows(SecurityException.class, () -> SafePath.resolve(d, "a/../../evil"));
        assertEquals(d.toAbsolutePath().normalize().resolve("a/b"), SafePath.resolve(d, "a/b"));
    }
    @Test void zipSlipRejected(@TempDir Path d) throws Exception {
        Path zip = d.resolve("n.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("../evil.dll")); z.write(1); z.closeEntry();
        }
        var m = Class.forName("app.nova.core.Installer").getDeclaredMethod("extractNatives", Path.class, Path.class, java.util.List.class);
        m.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, zip, d.resolve("out"), java.util.List.of()));
        assertInstanceOf(SecurityException.class, ex.getCause());
        assertFalse(Files.exists(d.resolve("evil.dll")));
    }
}
