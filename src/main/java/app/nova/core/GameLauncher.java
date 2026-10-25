package app.nova.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class GameLauncher {
    private GameLauncher() {}
    /** Starts the game as a separate OS process; stdout/stderr go to <gameDir>/logs/nova-game.log. */
    public static Process start(List<String> cmd, Path gameDir) throws IOException {
        Files.createDirectories(gameDir.resolve("logs"));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(gameDir.toFile()).redirectErrorStream(true)
                .redirectOutput(gameDir.resolve("logs").resolve("nova-game.log").toFile());
        return pb.start();
    }
}
