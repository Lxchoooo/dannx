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
