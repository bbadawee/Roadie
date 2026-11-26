-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization (models, Companion, $$serializer)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.roadie.innertube.** { *; }
-keep,includedescriptorclasses class io.roadie.auth.** { *; }

# Room database & entities
-keep class io.roadie.data.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# App entry points, ViewModel & reflection targets
-keep class io.roadie.app.** { *; }
-keep class io.roadie.carui.RoadieViewModel { *; }
-keep class io.roadie.carui.CarUxMonitor { *; }

# OkHttp / Okio / Media3 optional platform references
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.car.**
