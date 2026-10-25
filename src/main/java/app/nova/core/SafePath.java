package app.nova.core;

import java.nio.file.Path;

public final class SafePath {
    private SafePath() {}
    /** Resolves rel under base; throws if the result would escape base (zip-slip, "..", absolute paths). */
    public static Path resolve(Path base, String rel) {
        Path b = base.toAbsolutePath().normalize();
        Path r = b.resolve(rel).normalize();
        if (!r.startsWith(b) || r.equals(b)) throw new SecurityException("Path escapes base directory: " + rel);
        return r;
    }
}
