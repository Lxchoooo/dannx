import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun configValue(key: String): String =
    System.getenv(key) ?: localProperties.getProperty(key) ?: ""

fun quoted(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releaseStorePath = configValue("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = configValue("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = configValue("ANDROID_KEY_ALIAS")
val releaseKeyPassword = configValue("ANDROID_KEY_PASSWORD")

android {
    namespace = "com.dannx.walls"
    compileSdk = 37

    signingConfigs {
        if (releaseStorePath.isNotBlank() && file(releaseStorePath).exists()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                storeType = "PKCS12"
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.dannx.walls"
        minSdk = 29
        targetSdk = 36
        versionCode = configValue("VERSION_CODE").toIntOrNull() ?: 2
        versionName = configValue("VERSION_NAME").ifBlank { "1.1.0" }

        buildConfigField("String", "SUPABASE_URL", quoted(configValue("SUPABASE_URL")))
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            quoted(configValue("SUPABASE_ANON_KEY")),
        )
    }

    val releaseSigning = signingConfigs.findByName("release")

    buildTypes {
        // En CI, el APK de debug se firma con la misma clave que el release
        // cuando el keystore está disponible. Así se puede instalar encima de
        // la versión publicada sin desinstalarla. Sin keystore (por ejemplo,
        // un PR desde un fork) queda la firma de depuración habitual.
        if (releaseSigning != null) {
            getByName("debug").signingConfig = releaseSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = releaseSigning
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose-reports")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
