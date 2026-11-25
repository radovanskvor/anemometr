plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing secrets live in the git-ignored .env in the project root (KEY=value lines).
val releaseSecrets: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 && !line.trimStart().startsWith("#") } }
    ?.associate { (key, value) -> key.trim() to value.trim() }
    .orEmpty()
val hasReleaseKey = releaseSecrets["ANEMOMETR_KEYSTORE"]?.let { file(it).exists() } == true

android {
    namespace = "cz.skvor.windmeter"
    compileSdk {
        // Compose 1.13 needs the Android 17 QPR1 SDK (API 37.1).
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "cz.skvor.windmeter"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.2"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseSecrets.getValue("ANEMOMETR_KEYSTORE"))
                storePassword = releaseSecrets.getValue("ANEMOMETR_KEYSTORE_PASSWORD")
                keyAlias = releaseSecrets.getValue("ANEMOMETR_KEY_ALIAS")
                keyPassword = releaseSecrets.getValue("ANEMOMETR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the private key (e.g. a fork) the release build is still installable, signed with the debug key.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
