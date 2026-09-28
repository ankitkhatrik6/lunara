# Proguard rules for Lunara
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class com.lunara.app.data.remote.models.** { *; }
-keepclassmembers enum * { *; }
