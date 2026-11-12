# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.virusx.app.**$$serializer { *; }
-keepclassmembers class com.virusx.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.virusx.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Supabase & Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn org.slf4j.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**