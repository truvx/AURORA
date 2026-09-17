# AURORA release rules.
#
# R8 is enabled for release builds. Anything reached only by reflection, by a JavaScript
# bridge, or by a generated adapter must be kept explicitly, because R8 cannot see those
# references and will otherwise strip or rename them.

# --- Kotlin serialization ------------------------------------------------------------
# Serializers are generated and looked up reflectively. Losing them turns every AI gateway
# response into a runtime parse failure that does not reproduce in debug builds.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.aurora.player.**$$serializer { *; }
-keepclassmembers class dev.aurora.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ----------------------------------------------------------------------------
# Entities are bound by generated code that references fields by name.
-keep class dev.aurora.player.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- Media3 --------------------------------------------------------------------------
# Renderers and audio processors are instantiated reflectively by ExoPlayer.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class dev.aurora.player.data.player.*AudioProcessor { *; }

# --- YouTube IFrame bridge -----------------------------------------------------------
# @JavascriptInterface methods are called by name from the WebView. R8 has no way to see
# those calls, and renaming them silently breaks playback with no error.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Retrofit / OkHttp ---------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response

# --- Domain models -------------------------------------------------------------------
# Provider-neutral models cross serialization and persistence boundaries by name.
-keep class dev.aurora.player.domain.models.** { *; }
-keep class dev.aurora.player.domain.ai.** { *; }

# --- Diagnostics ---------------------------------------------------------------------
# Keep source file and line numbers so a release stack trace is readable, but rename the
# source file attribute so it does not leak original paths.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
