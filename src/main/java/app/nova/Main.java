package app.nova;
/** Separate entry point so the fat jar / jpackage run without module-path issues. */
public final class Main { public static void main(String[] a) { app.nova.ui.App.main(a); } }
