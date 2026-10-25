package app.nova.ui;

import app.nova.core.*;
import app.nova.core.VersionJson;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class App extends Application {
    private final Path settingsPath = Settings.defaultPath();
    private Settings st;
    private Stage stage;
    private ResourceBundle b;
    private String currentPage = "home";

    @Override public void start(Stage s) {
        stage = s; st = Settings.load(settingsPath);
        s.setTitle("Nova Launcher"); s.setMinWidth(900); s.setMinHeight(560);
        build(); s.show();
    }

    private void build() {
        b = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(st.language));
        BorderPane root = new BorderPane();
        root.getStyleClass().add("nova-root");
        root.setStyle("-fx-font-size: " + Math.round((st.compact ? 12 : 14) * st.scale) + "px;");
        StackPane content = new StackPane();
        content.getStyleClass().add("content");
        Map<String, Supplier<Node>> pages = new LinkedHashMap<>();
        pages.put("home", this::homePage); pages.put("versions", this::versionsPage); pages.put("settings", this::settingsPage);

        VBox side = new VBox(st.compact ? 4 : 8);
        side.getStyleClass().add("sidebar"); side.setPadding(new Insets(16)); side.setPrefWidth(st.compact ? 170 : 210);
        Label brand = new Label("✦ NOVA"); brand.getStyleClass().add("brand");
        side.getChildren().add(brand);
        ToggleGroup g = new ToggleGroup();
        for (var e : pages.entrySet()) {
            ToggleButton tb = new ToggleButton(b.getString("nav." + e.getKey()));
            tb.setToggleGroup(g); tb.setMaxWidth(Double.MAX_VALUE); tb.getStyleClass().add("nav-btn");
            tb.setOnAction(ev -> { currentPage = e.getKey(); show(content, e.getValue().get()); });
            tb.setSelected(e.getKey().equals(currentPage));
            g.selectedToggleProperty().addListener((o, p, n) -> { if (n == null) p.setSelected(true); });
            side.getChildren().add(tb);
        }
        root.setLeft(side); root.setCenter(content);
        show(content, pages.get(currentPage).get());

        Scene sc = new Scene(root, 1040, 660);
        sc.getStylesheets().add(getClass().getResource("/css/nova-" + st.theme + ".css").toExternalForm());
        stage.setScene(sc);
    }

    private void show(StackPane host, Node page) {
        host.getChildren().setAll(page);
        if (st.animations) { FadeTransition ft = new FadeTransition(Duration.millis(160), page); ft.setFromValue(0); ft.setToValue(1); ft.play(); }
    }

    private Node homePage() {
        Label h = new Label(b.getString("home.title")); h.getStyleClass().add("h1");
        Button play = new Button(b.getString("home.play")); play.getStyleClass().add("play");
        play.setDisable(true); // enabled in Stage 2 when install+launch is implemented
        Label note = new Label(b.getString("home.notReady")); note.getStyleClass().add("muted");
        VBox v = new VBox(20, h, play, note); v.setAlignment(Pos.CENTER); return v;
    }

    private Node versionsPage() {
        Label h = new Label(b.getString("versions.title")); h.getStyleClass().add("h1");
        CheckBox rel = new CheckBox(b.getString("versions.release")), snap = new CheckBox(b.getString("versions.snapshot"));
        rel.setSelected(true);
        ListView<VersionManifest.Entry> list = new ListView<>();
        Label status = new Label(b.getString("versions.loading")); status.getStyleClass().add("muted");
        List<VersionManifest.Entry> all = new ArrayList<>();
        Runnable filter = () -> list.getItems().setAll(all.stream()
                .filter(e -> (rel.isSelected() && e.type().equals("release")) || (snap.isSelected() && e.type().equals("snapshot"))).toList());
        rel.setOnAction(e -> filter.run()); snap.setOnAction(e -> filter.run());
        Task<List<VersionManifest.Entry>> t = new Task<>() { @Override protected List<VersionManifest.Entry> call() throws Exception { return VersionManifest.fetch(); } };
        t.setOnSucceeded(e -> { all.addAll(t.getValue()); filter.run(); status.setText(String.format(b.getString("versions.count"), all.size())); });
        t.setOnFailed(e -> status.setText(b.getString("versions.error") + ": " + t.getException().getMessage()));
        Thread th = new Thread(t, "manifest-fetch"); th.setDaemon(true); th.start();

        Button install = new Button(b.getString("versions.install")), demo = new Button(b.getString("versions.demo")),
               cancel = new Button(b.getString("versions.cancel"));
        ProgressBar pb = new ProgressBar(0); pb.setMaxWidth(Double.MAX_VALUE);
        Label op = new Label(); op.getStyleClass().add("muted");
        cancel.setDisable(true);
        BooleanBinding none = list.getSelectionModel().selectedItemProperty().isNull();
        install.disableProperty().bind(none); demo.disableProperty().bind(none);
        AtomicBoolean flag = new AtomicBoolean();
        Runnable[] busy = new Runnable[1];
        java.util.function.Consumer<Boolean> run = launch -> {
            VersionManifest.Entry sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            flag.set(false);
            Path root = st.rootDir();
            Task<Void> job = new Task<>() {
                String stage = ""; long lastT = System.nanoTime(), lastB = 0;
                @Override protected Void call() throws Exception {
                    Installer.Listener ls = new Installer.Listener() {
                        public void stage(String s) { stage = b.getString("stage." + s); updateMessage(stage); }
                        public synchronized void bytes(long d, long total) {
                            updateProgress(d, total);
                            long now = System.nanoTime();
                            if (now - lastT > 500_000_000L) {
                                double mbs = (d - lastB) / 1048576.0 / ((now - lastT) / 1e9);
                                lastT = now; lastB = d;
                                updateMessage(String.format("%s: %d/%d MB, %.1f MB/s", stage, d >> 20, total >> 20, mbs));
                            }
                        }
                    };
                    VersionJson v = Installer.install(sel, root, st.maxParallelDownloads, flag, ls);
                    if (launch) {
                        String javaExe;
                        if (!st.javaPath.isBlank()) javaExe = st.javaPath;
                        else {
                            try { javaExe = JavaRuntime.ensure(v.javaComponent, root, st.maxParallelDownloads, flag, ls).toString(); }
                            catch (java.io.IOException ioe) { // fall back to an installed JDK if the Mojang runtime is unavailable
                                javaExe = JavaLocator.find(v.javaMajor, "").orElseThrow(() ->
                                        new IllegalStateException(String.format(b.getString("error.java"), v.javaMajor))).exe().toString();
                            }
                        }
                        Path gd = root.resolve("instances").resolve("default");
                        var cfg = new LaunchBuilder.Config(root, gd, javaExe, st.minMemoryMb, st.maxMemoryMb, 0, 0, false,
                                List.of(), true, "Player", "00000000-0000-0000-0000-000000000000", "0", "legacy");
                        GameLauncher.start(LaunchBuilder.build(v, cfg), gd);
                    }
                    return null;
                }
            };
            pb.progressProperty().bind(job.progressProperty()); op.textProperty().bind(job.messageProperty());
            install.setDisable(true); demo.setDisable(true); cancel.setDisable(false);
            Runnable done = () -> { pb.progressProperty().unbind(); op.textProperty().unbind(); cancel.setDisable(true);
                install.disableProperty().bind(none); demo.disableProperty().bind(none); };
            job.setOnSucceeded(e -> { done.run(); op.setText(b.getString(launch ? "versions.started" : "versions.installed")); });
            job.setOnFailed(e -> { done.run(); Throwable x = job.getException();
                op.setText(x instanceof CancellationException ? b.getString("versions.cancelled") : b.getString("versions.error") + ": " + x.getMessage()); });
            job.setOnCancelled(e -> { done.run(); op.setText(b.getString("versions.cancelled")); });
            Thread jt = new Thread(job, "installer"); jt.setDaemon(true); jt.start();
        };
        install.setOnAction(e -> run.accept(false)); demo.setOnAction(e -> run.accept(true));
        cancel.setOnAction(e -> flag.set(true));
        VBox v = new VBox(12, h, new HBox(16, rel, snap, status), list, new HBox(12, install, demo, cancel), pb, op);
        v.setPadding(new Insets(24)); VBox.setVgrow(list, Priority.ALWAYS); return v;
    }

    private Node settingsPage() {
        Label h = new Label(b.getString("settings.title")); h.getStyleClass().add("h1");
        ComboBox<String> lang = new ComboBox<>(); lang.getItems().addAll("ru", "en"); lang.setValue(st.language);
        ComboBox<String> theme = new ComboBox<>(); theme.getItems().addAll("dark", "light"); theme.setValue(st.theme);
        Slider scale = new Slider(0.8, 1.5, st.scale); scale.setMajorTickUnit(0.1); scale.setShowTickMarks(true);
        CheckBox compact = new CheckBox(b.getString("settings.compact")); compact.setSelected(st.compact);
        CheckBox anim = new CheckBox(b.getString("settings.animations")); anim.setSelected(st.animations);
        Button apply = new Button(b.getString("settings.apply"));
        Label msg = new Label(); msg.getStyleClass().add("muted");
        apply.setOnAction(e -> {
            st.language = lang.getValue(); st.theme = theme.getValue(); st.scale = Math.round(scale.getValue() * 10) / 10.0;
            st.compact = compact.isSelected(); st.animations = anim.isSelected();
            try { st.save(settingsPath); } catch (Exception ex) { msg.setText(ex.getMessage()); return; }
            Platform.runLater(this::build);
        });
        GridPane gp = new GridPane(); gp.setHgap(16); gp.setVgap(14);
        gp.addRow(0, new Label(b.getString("settings.language")), lang);
        gp.addRow(1, new Label(b.getString("settings.theme")), theme);
        gp.addRow(2, new Label(b.getString("settings.scale")), scale);
        gp.add(compact, 1, 3); gp.add(anim, 1, 4);
        VBox v = new VBox(18, h, gp, apply, msg); v.setPadding(new Insets(24)); return v;
    }
}
