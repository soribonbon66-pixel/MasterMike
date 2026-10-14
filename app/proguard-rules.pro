# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep audio processing classes
-keep class com.michel.masteringpro.audio.** { *; }
-keep class com.michel.masteringpro.dsp.** { *; }
