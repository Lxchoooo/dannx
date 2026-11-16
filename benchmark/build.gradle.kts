import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dannx.walls.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Mismo nombre que el buildType de :app para que AGP empareje la variante
    // que se mide. El APK de test se firma con la clave de depuración.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // Variante espejo de `nonMinifiedRelease` de la app: se mide la copia
        // sin R8 para que el perfil generado conserve nombres legibles.
        create("nonMinifiedRelease") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("nonMinifiedRelease")
        }
    }

    // El módulo de test compila y se instala solo; no comparte código con :app.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    // Solo las variantes `benchmark` y `nonMinifiedRelease` tienen sentido:
    // las demás instalarían la app en modo debug, que Macrobenchmark rechaza.
    beforeVariants {
        it.enable = it.buildType == "benchmark" || it.buildType == "nonMinifiedRelease"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
