import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// Single source of truth for the version: the Android app's versionName.
val appVersion = Regex("versionName = \"([^\"]+)\"")
    .find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]

/** sherpa-onnx native library jar for the machine running the build, e.g. "osx-aarch64". */
val sherpaNativeClassifier: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> if (arm) "osx-aarch64" else "osx-x64"
        os.contains("win") -> if (arm) "win-arm64" else "win-x64"
        else -> if (arm) "linux-aarch64" else "linux-x64"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutine.swing)
    runtimeOnly("com.k2fsa.sherpa.onnx:sherpa-onnx-native-lib-$sherpaNativeClassifier:${libs.versions.sherpaOnnxJvm.get()}")
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("audibook.debug", "true")
    // `./gradlew :desktop:run -Paudibook.dataDir=/tmp/x` runs against a separate library, e.g. for testing.
    providers.gradleProperty("audibook.dataDir").orNull?.let { systemProperty("audibook.dataDir", it) }
}

compose.desktop {
    application {
        mainClass = "com.paulcoding.audibook.desktop.MainKt"
        jvmArgs += listOf("-Daudibook.version=$appVersion")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AudiBook"
            packageVersion = appVersion
            description = "Reads EPUB books aloud"
            modules("java.net.http", "java.sql", "jdk.unsupported")
            fileAssociation(mimeType = "application/epub+zip", extension = "epub", description = "EPUB book")
        }
    }
}
