# El catálogo se consume por REST con OkHttp; no hay SDK extra que conservar.
# OkHttp y Coil incluyen sus propias reglas de consumer, así que no se
# duplican acá.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Serializadores generados por kotlinx.serialization.
-keep,includedescriptorclasses class com.dannx.walls.**$$serializer { *; }

-keepclassmembers class **$$serializer {
    *** descriptor;
}

-keepclassmembers class com.dannx.walls.** {
    *** Companion;
}

-keepclasseswithmembers class com.dannx.walls.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# No dejar logs de depuración en el APK de release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
