plugins {
    id("com.android.application")
}

android {
    namespace = "dev.rustcraft.game"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "dev.rustcraft.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")
}

val repositoryRoot = rootProject.projectDir.resolve("../..")
val nativeLibraryDirectory = projectDir.resolve("src/main/jniLibs")

val buildRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the Rust cdylib for Android ARM64 using cargo-ndk."
    workingDir = repositoryRoot

    commandLine(
        "cargo", "ndk",
        "--platform", "26",
        "-t", "arm64-v8a",
        "-o", nativeLibraryDirectory.absolutePath,
        "build", "--release", "--lib"
    )
}

tasks.named("preBuild").configure {
    dependsOn(buildRust)
}
