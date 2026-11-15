// Los plugins viven acá con `apply false` para que Gradle resuelva cada
// versión una sola vez: los módulos los piden sin repetirla y AGP, que trae
// `com.android.application` y `com.android.test` en el mismo artefacto, no
// choca con "already on the classpath with an unknown version".
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
