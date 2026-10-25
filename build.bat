@echo off
rem One-click local build: needs JDK 21 (with jpackage) and Gradle on PATH, and WiX 3 for the .exe installer.
where jpackage >nul 2>nul || (echo JDK 21 not found. Install Temurin 21 first. & exit /b 1)
where gradle >nul 2>nul || (echo Gradle not found. Run: winget install Gradle.Gradle & exit /b 1)
call gradle test installDist --no-daemon || exit /b 1
jpackage --type exe --name "Nova Launcher" --app-version 0.2.0 --vendor "Nova Launcher" ^
 --input build\install\nova-launcher\lib --main-jar nova-launcher.jar --main-class app.nova.Main ^
 --add-modules java.base,java.desktop,java.logging,java.net.http,java.xml,jdk.unsupported,jdk.crypto.ec ^
 --win-menu --win-shortcut --win-dir-chooser --win-per-user-install ^
 --win-upgrade-uuid 7c1f1b0e-5a3d-4c36-9b6e-2f4a8d9e1a10 --dest dist || exit /b 1
echo Installer: dist\Nova Launcher-0.2.0.exe
