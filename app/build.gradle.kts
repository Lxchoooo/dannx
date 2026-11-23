import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
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
        versionName = configValue("VERSION_NAME").ifBlank { "1.8.0" }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", quoted(configValue("SUPABASE_URL")))
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            quoted(configValue("SUPABASE_ANON_KEY")),
        )
    }

    val releaseSigning = signingConfigs.findByName("release")

    buildTypes {
        // Solo para builds locales con el keystore configurado: firmar el APK
        // de debug con la clave de release permite instalarlo encima de la
        // versión publicada. CI no le pasa el keystore a los builds de PR, así
        // que ahí se usa la firma de depuración habitual.
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
        // Copia de release para Macrobenchmark: mismo R8 y mismos recursos,
        // firmada con la clave de depuración para instalarla en un emulador.
        // No se publica y `app/src/benchmark/AndroidManifest.xml` la marca
        // como profileable.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // Release sin R8 ni shrink, firmada con la clave de depuración. Sirve
        // para generar el Baseline Profile con nombres legibles: las reglas que
        // salen de aquí las remapea AGP contra el mapping de cada build
        // release, y así no quedan atadas a un mapping exacto. Tampoco se
        // publica y su manifiesto propio la marca como profileable.
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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

    // Screenshot tests con Roborazzi: corren como tests unitarios (Robolectric),
    // sin emulador. Los dorados se versionan en src/test/screenshots.
    roborazzi {
        outputDir.set(file("src/test/screenshots"))
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
                // Las capturas son las pruebas más lentas: se validan en los
                // tags o a mano (workflow de capturas), no en cada push.
                it.filter {
                    if (providers.gradleProperty("screenshots").isPresent) {
                        includeTestsMatching("com.dannx.walls.ui.screenshot.*")
                    } else {
                        excludeTestsMatching("com.dannx.walls.ui.screenshot.*")
                    }
                }
            }
        }
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
    implementation(libs.compose.material.icons)
    implementation(libs.material.color.utilities)

    implementation(libs.coil.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screenshot tests en JVM: Robolectric dibuja Compose con Native Graphics.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.junit)
    // Sin kotlinx-coroutines-test acá: la prueba usa runBlocking de la versión
    // que ya resuelve la app; una versión más nueva solo desalineaba el APK de
    // test contra el de la app en el emulador.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
