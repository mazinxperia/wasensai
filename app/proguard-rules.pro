# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Zip4j
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.mazin.wasensai.**$$serializer { *; }
-keepclassmembers class com.mazin.wasensai.** {
    *** Companion;
}
-keepclasseswithmembers class com.mazin.wasensai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Data models - never strip
-keep class com.mazin.wasensai.data.model.** { *; }
-keep class com.mazin.wasensai.data.repository.** { *; }

# OkIO (used by Coil disk cache)
-keep class okio.** { *; }
-dontwarn okio.**
