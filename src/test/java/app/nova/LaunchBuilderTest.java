package app.nova;
import app.nova.core.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LaunchBuilderTest {
    static final String COMMON = "\"id\":\"t\",\"type\":\"release\",\"mainClass\":\"net.Main\","
        + "\"assetIndex\":{\"id\":\"12\",\"url\":\"https://x\",\"sha1\":\"a\",\"size\":1},"
        + "\"downloads\":{\"client\":{\"url\":\"https://x/c.jar\",\"sha1\":\"b\",\"size\":2}},"
        + "\"libraries\":[{\"name\":\"a:b:1\",\"downloads\":{\"artifact\":{\"path\":\"a/b/1/b-1.jar\",\"url\":\"https://x/b.jar\",\"sha1\":\"c\",\"size\":3}}},"
        + "{\"name\":\"n:n:1\",\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}],\"downloads\":{\"artifact\":{\"path\":\"n/n/1/n-1.jar\",\"url\":\"https://x/n.jar\",\"sha1\":\"d\",\"size\":4}}}]";
    static final String NEW = "{" + COMMON + ",\"arguments\":{\"game\":[\"--username\",\"${auth_player_name}\",\"--accessToken\",\"${auth_access_token}\","
        + "{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"--demo\"}],"
        + "\"jvm\":[\"-Djava.library.path=${natives_directory}\",\"-cp\",\"${classpath}\"]}}";
    static final String OLD = "{" + COMMON + ",\"minecraftArguments\":\"--username ${auth_player_name} --version ${version_name} --accessToken ${auth_access_token}\"}";

    LaunchBuilder.Config cfg(boolean demo) {
        return new LaunchBuilder.Config(Path.of("root"), Path.of("game"), "java", 512, 2048, 0, 0, false, List.of(), demo, "Steve", "u", "SECRET", "legacy");
    }
    @Test void newFormat() {
        var cmd = LaunchBuilder.build(VersionJson.parse(NEW, "windows", "x86_64"), cfg(true));
        assertTrue(cmd.contains("--demo")); assertTrue(cmd.contains("Steve")); assertTrue(cmd.contains("-Xmx2048M"));
        assertTrue(cmd.indexOf("net.Main") > cmd.indexOf("-cp"));
        String cp = cmd.get(cmd.indexOf("-cp") + 1);
        assertTrue(cp.contains("b-1.jar")); assertFalse(cp.contains("n-1.jar")); assertTrue(cp.contains("t.jar"));
        assertFalse(LaunchBuilder.build(VersionJson.parse(NEW, "windows", "x86_64"), cfg(false)).contains("--demo"));
    }
    @Test void legacyFormat() {
        var cmd = LaunchBuilder.build(VersionJson.parse(OLD, "windows", "x86_64"), cfg(true));
        assertTrue(cmd.containsAll(List.of("-cp", "--version", "t", "--demo")));
    }
    @Test void tokenIsMaskedForLogs() {
        var cmd = LaunchBuilder.build(VersionJson.parse(NEW, "windows", "x86_64"), cfg(false));
        assertTrue(cmd.contains("SECRET"));
        assertFalse(LaunchBuilder.masked(cmd).contains("SECRET"));
    }
}
