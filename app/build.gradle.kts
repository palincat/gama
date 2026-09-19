import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ── Release signing credentials ──────────────────────────────────────────────
// Credentials are NEVER stored in this file (it is committed to git).
// Resolution order:
//   1. keystore.properties at the repo root (gitignored):
//        storeFile=<absolute or project-relative path>
//        storePassword=...
//        keyAlias=...
//        keyPassword=...
//   2. Environment variables: GAMA_STORE_FILE, GAMA_STORE_PASSWORD,
//      GAMA_KEY_ALIAS, GAMA_KEY_PASSWORD
//   3. Neither present → debug, verification, and unsigned release tasks work;
//      configured credentials sign the release artifact when available.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingProp(name: String, envVar: String): String? {
    val fromFile = keystoreProps.getProperty(name)
    if (!fromFile.isNullOrBlank()) return fromFile
    val fromEnv = System.getenv(envVar)
    return if (fromEnv.isNullOrBlank()) null else fromEnv
}

val hasSigningCredentials = signingProp("storeFile", "GAMA_STORE_FILE") != null &&
    signingProp("storePassword", "GAMA_STORE_PASSWORD") != null &&
    signingProp("keyAlias", "GAMA_KEY_ALIAS") != null &&
    signingProp("keyPassword", "GAMA_KEY_PASSWORD") != null

android {
    namespace = "com.popovicialinc.gama"
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (hasSigningCredentials) {
            create("release") {
                storeFile = file(signingProp("storeFile", "GAMA_STORE_FILE")!!)
                storePassword = signingProp("storePassword", "GAMA_STORE_PASSWORD")
                keyAlias = signingProp("keyAlias", "GAMA_KEY_ALIAS")
                keyPassword = signingProp("keyPassword", "GAMA_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.popovicialinc.gama"
        minSdk = 29
        targetSdk = 36
        versionCode = 13
        versionName = "1.4"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            isDebuggable = false
            isJniDebuggable = false

            ndk {
                debugSymbolLevel = "NONE"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
        resValues = false
        shaders = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/license.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/notice.txt",
                "/META-INF/*.kotlin_module",
                "/kotlin/**",
                "/*.txt",
                "/*.bin"
            )
        }

        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf()
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.palette.ktx)

    // Other
    implementation("androidx.core:core-splashscreen:1.1.0-rc01")
    implementation("com.google.android.material:material:1.11.0")


    // WorkManager (boot renderer retry)
    implementation(libs.androidx.work.runtime.ktx)

    // Debug only
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    // Real org.json implementation — android.jar only ships method stubs that
    // throw "not mocked" when BackupHelper's JSON code runs under JUnit.
    testImplementation("org.json:json:20240303")

    // Instrumentation smoke tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
