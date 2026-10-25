package app.nova.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

public final class Settings {
    public String language = "ru";   // ru | en
    public String theme = "dark";    // dark | light
    public double scale = 1.0;       // 0.8 .. 1.5
    public boolean compact = false;
    public boolean animations = true;
    public int maxParallelDownloads = 4;
    public int minMemoryMb = 512;
    public int maxMemoryMb = 2048;   // never auto-sized to all RAM
    public String javaPath = "";     // empty = auto-detect
    public String gameDir = "";      // empty = default under %APPDATA%\NovaLauncher\game

    public Path rootDir() { return gameDir.isBlank() ? defaultPath().getParent().resolve("game") : Path.of(gameDir); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path defaultPath() {
        String base = System.getenv("APPDATA");
        Path dir = base != null ? Path.of(base, "NovaLauncher") : Path.of(System.getProperty("user.home"), ".nova-launcher");
        return dir.resolve("settings.json");
    }
    public static Settings load(Path p) {
        try { if (Files.isRegularFile(p)) { Settings s = GSON.fromJson(Files.readString(p), Settings.class); if (s != null) return s; } }
        catch (IOException | JsonParseException ignored) { /* fall back to defaults */ }
        return new Settings();
    }
    public void save(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(this));
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
