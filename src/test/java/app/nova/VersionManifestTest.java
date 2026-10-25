package app.nova;
import app.nova.core.VersionManifest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VersionManifestTest {
    private static String v(String id, String type, String t) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"url\":\"https://x/" + id + ".json\",\"sha1\":\"aa\",\"releaseTime\":\"" + t + "\"}";
    }
    @Test void filtersOlderThan1122() {
        String json = "{\"versions\":[" + v("1.21", "release", "2024-06-13T10:00:00+00:00") + ","
                + v("1.12.2", "release", "2017-09-18T08:39:46+00:00") + ","
                + v("1.12.1", "release", "2017-08-03T08:00:00+00:00") + ","
                + v("1.7.10", "release", "2014-06-26T00:00:00+00:00") + "]}";
        var ids = VersionManifest.parse(json).stream().map(VersionManifest.Entry::id).toList();
        assertEquals(java.util.List.of("1.21", "1.12.2"), ids);
    }
}
