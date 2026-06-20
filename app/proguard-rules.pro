# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Coil 3
-keep class coil3.** { *; }

# Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Gson
-keep class com.google.gson.** { *; }
-keep class cp.player.model.** { *; }
-keep class cp.player.provider.** { *; }

# Media3 (ExoPlayer)
-keep class androidx.media3.** { *; }

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose FontVariation inner classes (used via reflection for custom ROND axis)
-keep class androidx.compose.ui.text.font.FontVariation$** { *; }
