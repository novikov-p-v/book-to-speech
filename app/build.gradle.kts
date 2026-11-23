import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// sherpa-onnx is distributed as an AAR on GitHub releases (not on Maven Central).
// It is ~47 MB, so it is not committed; download it once if missing.
val sherpaVersion = "1.13.8" // 2.x dropped the built-in espeak-ng frontend that Piper voices need
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
if (!sherpaAar.exists()) {
    sherpaAar.parentFile.mkdirs()
    logger.lifecycle("Downloading sherpa-onnx $sherpaVersion AAR...")
    URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar")
        .toURL().openStream().use { input -> sherpaAar.outputStream().use { input.copyTo(it) } }
}

// Release signing comes from keystore.properties (git-ignored) or BOOKTTS_* environment variables.
// Without them, release builds fall back to the debug key: fine for your own phone, rejected by Play.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStore = signingValue("storeFile", "BOOKTTS_STORE_FILE")

android {
    namespace = "app.booktts"
    compileSdk = 36

    defaultConfig {
        // Permanent Play Store identity; the Kotlin package (namespace) can stay app.booktts.
        applicationId = "io.github.novikovpv.booktts"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.1"
    }

    // One APK per processor type for sideloading (app-arm64-v8a-release.apk ≈ 40 MB).
    // App Bundles for Google Play ignore this; Play delivers the right libraries per device anyway.
    splits {
        abi {
            // AGP can't build bundles with splits on (issuetracker 402800800), so only for APK builds.
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingValue("storePassword", "BOOKTTS_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "BOOKTTS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "BOOKTTS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStore != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("keystore.properties not found: release is signed with the DEBUG key (not accepted by Google Play)")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
        // Phones (arm64, older 32-bit ARM) and the x86_64 emulator; 32-bit x86 devices are practically gone.
        jniLibs.excludes += "lib/x86/**"
        resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(files(sherpaAar))

    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.media3:media3-session:1.6.0")
    implementation("androidx.media3:media3-common:1.6.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Book parsing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.github.albfernandez:juniversalchardet:2.5.2")
    implementation("org.apache.commons:commons-compress:1.28.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
