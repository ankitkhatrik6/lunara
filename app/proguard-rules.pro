# Proguard rules for Dhunya
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class com.dhunya.app.data.remote.models.** { *; }
-keepclassmembers enum * { *; }
