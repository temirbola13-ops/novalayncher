package app.nova.core;

import app.nova.core.VersionJson.*;
import com.google.gson.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/** Builds the java command line from a version JSON. Never executes anything. */
public final class LaunchBuilder {
    public record Config(Path root, Path gameDir, String javaExe, int minMb, int maxMb, int width, int height,
                         boolean fullscreen, List<String> extraJvm, boolean demo,
                         String playerName, String uuid, String accessToken, String userType) {}

    private LaunchBuilder() {}
    private static final Pattern VAR = Pattern.compile("\\$\\{(\\w+)}");

    public static List<String> build(VersionJson v, Config c) {
        Set<String> feats = new HashSet<>();
        if (c.demo()) feats.add("is_demo_user");
        boolean res = c.width() > 0 && c.height() > 0;
        if (res) feats.add("has_custom_resolution");

        Path vdir = Installer.versionDir(c.root(), v.id);
        LinkedHashSet<String> cp = new LinkedHashSet<>();
        for (Library l : v.libraries) if (l.classpath() != null) cp.add(Installer.libPath(c.root(), l.classpath()).toString());
        cp.add(vdir.resolve(v.id + ".jar").toString());

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", c.playerName()); vars.put("version_name", v.id);
        vars.put("game_directory", c.gameDir().toString()); vars.put("assets_root", c.root().resolve("assets").toString());
        vars.put("assets_index_name", v.assetsId); vars.put("auth_uuid", c.uuid());
        vars.put("auth_access_token", c.accessToken()); vars.put("user_type", c.userType());
        vars.put("version_type", v.type); vars.put("natives_directory", vdir.resolve("natives").toString());
        vars.put("launcher_name", "NovaLauncher"); vars.put("launcher_version", "0.2");
        vars.put("classpath", String.join(File.pathSeparator, cp));
        vars.put("resolution_width", String.valueOf(c.width())); vars.put("resolution_height", String.valueOf(c.height()));
        vars.put("clientid", ""); vars.put("auth_xuid", ""); vars.put("user_properties", "{}");

        List<String> jvm = new ArrayList<>(), game = new ArrayList<>();
        JsonObject args = v.raw.has("arguments") ? v.raw.getAsJsonObject("arguments") : null;
        if (args != null) {
            collect(args.getAsJsonArray("jvm"), feats, v, jvm);
            collect(args.getAsJsonArray("game"), feats, v, game);
        } else { // legacy format (e.g. 1.12.2)
            jvm.addAll(List.of("-Djava.library.path=${natives_directory}", "-cp", "${classpath}"));
            game.addAll(Arrays.asList(v.raw.get("minecraftArguments").getAsString().trim().split("\\s+")));
            if (res) game.addAll(List.of("--width", "${resolution_width}", "--height", "${resolution_height}"));
            if (c.demo()) game.add("--demo");
        }
        if (c.fullscreen()) game.add("--fullscreen");

        List<String> cmd = new ArrayList<>();
        cmd.add(c.javaExe());
        cmd.add("-Xms" + c.minMb() + "M"); cmd.add("-Xmx" + c.maxMb() + "M");
        cmd.addAll(c.extraJvm());
        for (String s : jvm) cmd.add(subst(s, vars));
        cmd.add(v.mainClass);
        for (String s : game) cmd.add(subst(s, vars));
        return cmd;
    }

    private static void collect(JsonArray arr, Set<String> feats, VersionJson v, List<String> out) {
        if (arr == null) return;
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) { out.add(e.getAsString()); continue; }
            JsonObject o = e.getAsJsonObject();
            if (!Rules.allowed(o.getAsJsonArray("rules"), feats, v.os, v.arch)) continue;
            JsonElement val = o.get("value");
            if (val.isJsonArray()) val.getAsJsonArray().forEach(x -> out.add(x.getAsString())); else out.add(val.getAsString());
        }
    }

    static String subst(String s, Map<String, String> vars) {
        Matcher m = VAR.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    /** Copy of the command that is safe to log: the access token value is hidden. */
    public static List<String> masked(List<String> cmd) {
        List<String> r = new ArrayList<>(cmd);
        for (int i = 0; i < r.size() - 1; i++) if (r.get(i).equals("--accessToken")) r.set(i + 1, "***");
        return r;
    }
}
