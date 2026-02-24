# ============================================
# SECURITY & OBFUSCATION RULES
# ============================================

# ✅ Keep BuildConfig (needed for API URLs)
-keep class com.example.smallbasket.BuildConfig { *; }

# ✅ Keep model classes (used with Gson)
-keep class com.example.smallbasket.models.** { *; }
-keepclassmembers class com.example.smallbasket.models.** { *; }

# ✅ Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ✅ Gson
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ✅ OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ✅ Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ✅ Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ✅ Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ✅ Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# ✅ Obfuscate everything except what's kept above
-repackageclasses
-allowaccessmodification
-optimizationpasses 5