import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.coerceAtMost(999999) ?: 1
val keyPath = System.getenv("DEBUG_KEYSTORE_PATH")

android {
    namespace = "com.kosong.misettings"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        applicationId = "com.kosong.misettings.screentime"
        minSdk = 26
        targetSdk = 35
        versionCode = 10000 + runNumber
        versionName = "0.1.$runNumber-debug"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (keyPath != null) {
        signingConfigs {
            create("persistentDebug") {
                storeFile = file(keyPath)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DEBUG_KEY_ALIAS")
                keyPassword = System.getenv("DEBUG_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            if (keyPath != null) signingConfig = signingConfigs.getByName("persistentDebug")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.work:work-runtime:2.10.5")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
