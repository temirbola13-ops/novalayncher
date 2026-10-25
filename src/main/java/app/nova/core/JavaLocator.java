package app.nova.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Finds an installed Java matching the version's requirement. (Auto-download of Mojang runtimes: not implemented yet.) */
public final class JavaLocator {
    public record Found(Path exe, int major) {}
    private JavaLocator() {}

    /** "1.8.0_392" -> 8, "21.0.2" -> 21, "17" -> 17; -1 if unparsable. */
    static int parseMajor(String v) {
        try {
            String[] p = v.replace("\"", "").trim().split("[._+-]");
            return p[0].equals("1") ? Integer.parseInt(p[1]) : Integer.parseInt(p[0]);
        } catch (RuntimeException e) { return -1; }
    }

    /** Java 8 is required exactly for old versions (newer JVMs break them); otherwise the smallest java >= required. */
    public static Optional<Found> find(int required, String userPath) {
        List<Found> all = new ArrayList<>();
        if (userPath != null && !userPath.isBlank()) probe(Path.of(userPath)).ifPresent(all::add);
        else {
            List<Path> homes = new ArrayList<>();
            if (System.getenv("JAVA_HOME") != null) homes.add(Path.of(System.getenv("JAVA_HOME")));
            homes.add(Path.of(System.getProperty("java.home")));
            for (String base : new String[]{"C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium", "C:\\Program Files\\Microsoft",
                    "C:\\Program Files\\Zulu", "C:\\Program Files\\BellSoft", "C:\\Program Files\\Amazon Corretto", "C:\\Program Files (x86)\\Java"}) {
                Path b = Path.of(base);
                if (Files.isDirectory(b)) try (Stream<Path> s = Files.list(b)) { s.forEach(homes::add); } catch (IOException ignored) {}
            }
            for (Path h : homes) probe(h.resolve("bin").resolve(exeName())).ifPresent(all::add);
        }
        return all.stream().filter(f -> required <= 8 ? f.major() == 8 : f.major() >= required)
                .min(Comparator.comparingInt(Found::major));
    }

    private static String exeName() { return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java"; }

    static Optional<Found> probe(Path exe) {
        try {
            if (!Files.isRegularFile(exe)) return Optional.empty();
            Path rel = exe.toAbsolutePath().getParent().getParent().resolve("release");
            if (!Files.isRegularFile(rel)) return Optional.empty();
            for (String line : Files.readAllLines(rel))
                if (line.startsWith("JAVA_VERSION=")) {
                    int m = parseMajor(line.substring(13));
                    return m > 0 ? Optional.of(new Found(exe, m)) : Optional.empty();
                }
        } catch (IOException ignored) {}
        return Optional.empty();
    }
}
