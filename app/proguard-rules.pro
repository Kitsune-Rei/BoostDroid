# ProGuard rules for BoostDroid

# Kotlin reflection support
-keep class kotlin.reflect.jvm.internal.** { *; }

# Internal PowerProfile class access
-keep class com.android.internal.os.PowerProfile { *; }
-dontwarn com.android.internal.os.PowerProfile

# Strip all Log.d and Log.v calls in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ViewBinding keep rules
-keep class com.boostdroid.app.databinding.** { *; }

# ViewModel keep rules
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}