package app.nova;
import app.nova.core.Hashing;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class HashingTest {
    @Test void knownVector(@TempDir Path d) throws Exception {
        Path f = d.resolve("a.txt"); Files.writeString(f, "abc");
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Hashing.sha1(f));
    }
}
