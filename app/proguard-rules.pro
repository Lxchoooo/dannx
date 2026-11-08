-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

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
