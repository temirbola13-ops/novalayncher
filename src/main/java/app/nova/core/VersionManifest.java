package app.nova.core;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Official Mojang version manifest (v2). Only versions from 1.12.2 onwards are exposed. */
public final class VersionManifest {
    public static final URI URL = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    public static final String OLDEST = "1.12.2";

    public record Entry(String id, String type, String url, String sha1, String releaseTime) {
        @Override public String toString() { return id; }
    }

    private VersionManifest() {}

    public static List<Entry> fetch() throws IOException, InterruptedException {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<String> r = c.send(HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        return parse(r.body());
    }

    public static List<Entry> parse(String json) {
        JsonArray arr = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("versions");
        List<Entry> all = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            all.add(new Entry(o.get("id").getAsString(), o.get("type").getAsString(), o.get("url").getAsString(),
                    o.has("sha1") ? o.get("sha1").getAsString() : null, o.get("releaseTime").getAsString()));
        }
        // ISO-8601 timestamps with the same offset compare correctly as strings.
        String cutoff = all.stream().filter(v -> v.id().equals(OLDEST)).map(Entry::releaseTime).findFirst().orElse(null);
        if (cutoff == null) return all;
        return all.stream().filter(v -> v.releaseTime().compareTo(cutoff) >= 0).toList();
    }
}
