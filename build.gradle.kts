plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}
repositories { mavenCentral() }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
javafx { version = "21.0.5"; modules = listOf("javafx.controls") }
application { mainClass = "app.nova.Main" }
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
