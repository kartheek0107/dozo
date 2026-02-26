# ============================================
# SECURITY & OBFUSCATION RULES
# ============================================

# ✅ Keep BuildConfig (needed for API URLs)
-keep class com.example.smallbasket.BuildConfig { *; }

# ✅ Keep model classes (used with Gson)
-keep class com.example.smallbasket.models.** { *; }
-keepclassmembers class com.example.smallbasket.models.** { *; }

# ============================================
# ATTRIBUTES (Must be at top level)
# ============================================
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ============================================
# RETROFIT
# ============================================
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ============================================
# GSON
# ============================================
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Prevent R8 from removing anonymous TypeToken subclasses
-keepclassmembers class * extends com.google.gson.TypeAdapter { *; }
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory { *; }
-keepclassmembers class * implements com.google.gson.JsonSerializer { *; }
-keepclassmembers class * implements com.google.gson.JsonDeserializer { *; }

# ============================================
# OKHTTP
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================
# FIREBASE
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ============================================
# KOTLIN
# ============================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ============================================
# COROUTINES
# ============================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================
# REMOVE LOGGING IN RELEASE
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# ============================================
# OBFUSCATION & OPTIMIZATION
# ============================================
-repackageclasses
-allowaccessmodification
-optimizationpasses 5