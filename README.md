# Nova Launcher

Custom Minecraft: Java Edition launcher (Java 21, JavaFX, Gradle). **Work in progress — see status.**

## Status (honest)
**Written, NOT yet compiled or run** (the authoring environment had no javac/Gradle and no access to
Maven Central or Mojang servers). Run `gradle test` first and fix whatever the compiler reports.

| Stage | State |
|---|---|
| 1. Project, sidebar UI, dark/light themes, RU/EN, scale, compact mode, settings persisted to JSON | code written, unverified |
| 2. Mojang version manifest (>= 1.12.2), resumable SHA-1-verified HTTPS downloader | code + unit tests written, unverified |
| 2. Vanilla install (version json, client, libraries, assets, natives; parallel, SHA-1, resume, cancel, speed/progress) | code + unit tests written, unverified |
| 2. Command-line builder (new + legacy 1.12.2 formats), Java 8/17/21 detection among installed JDKs, process start | code + unit tests written, unverified |
| 2. Mojang Java runtime auto-download (jre-legacy / gamma / delta ...), no system Java needed; JDK search is only a fallback | code + unit tests written, unverified |
| 2. Launch is **demo mode only** (`--demo`) until stage 3 auth; Mojang runtime auto-download not implemented, JDK must be installed | partial |
| 3. Microsoft/Xbox/Minecraft auth | not implemented |
| 4. Fabric/Forge/NeoForge/Quilt, profile manager | not implemented |
| 5-6. Modrinth/CurseForge, resource packs, shaders, .mrpack import/export | not implemented |
| 7. Logging, diagnostics, jpackage installer, updater | not implemented |
| SQLite storage | not yet; settings are JSON (`%APPDATA%\NovaLauncher\settings.json`) |

## Run / test
    gradle run
    gradle test
Requires JDK 21 to build. The game's Java is downloaded automatically from Mojang on first launch (a custom Java path can be set in settings.json: `javaPath`).
Versions page: pick a version, "Install" downloads and verifies everything; "Verify and launch (demo)" re-verifies then starts the game; game log: `<root>\instances\default\logs\nova-game.log`. Planned installer: `jpackage --type msi --win-menu --win-shortcut --win-dir-chooser` (needs WiX 3 on Windows; not yet wired in).

## Notes for later stages
- CurseForge API needs an API key (apply at Overwolf/CurseForge); Modrinth requires a descriptive User-Agent.
- Microsoft login needs an Azure app registration approved for Minecraft services.
- Forge/NeoForge install via their official installers run as a separate process, never inside the launcher JVM.

## Getting an installer without a local setup
Push the project to a GitHub repository. The workflow `.github/workflows/build.yml` runs the tests on Windows,
builds the app with a bundled Java runtime and publishes `NovaLauncher-installer` (.exe, desktop shortcut,
choice of install directory, uninstall entry, upgrade-safe) as a downloadable artifact in the Actions tab.
Locally: `build.bat` does the same. Not yet verified by an actual run; the first run may reveal compile errors.
Code signing and in-app update checks are not implemented.

## Publishing a download link for other people
After the first successful build, create a release tag (GitHub: Releases -> Draft a new release -> tag `v0.2.0`).
The workflow then attaches the installer .exe to that release; share the release page link.
End users need no Java and no Gradle. Windows SmartScreen will warn about an unsigned app until a code-signing certificate is added.
