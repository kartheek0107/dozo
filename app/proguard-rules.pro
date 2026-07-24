# ============================================
# ATTRIBUTES (Critical for Retrofit/Gson)
# ============================================
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ============================================
# API MODELS & SERVICES
# ============================================
# Preserve all models to ensure Gson deserialization works
-keep class com.smallbasket.dozo.models.** { *; }
-keepclassmembers class com.smallbasket.dozo.models.** { *; }

# Preserve the ApiService interface for Retrofit reflection
-keep interface com.smallbasket.dozo.api.ApiService { *; }

# ✅ DISABLE R8 FULL MODE (Crucial if R8 is stripping generic info despite rules)
-dontoptimize

# ✅ Suppress Kotlin Metadata warnings
-dontwarn kotlin.Metadata
-dontwarn kotlin.jvm.internal.DefaultConstructorMarker

# ============================================
# RETROFIT
# ============================================
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface retrofit2.Call
-keep class retrofit2.Response
-keep class kotlin.coroutines.Continuation

# ============================================
# GSON
# ============================================
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * extends com.google.gson.TypeAdapter { *; }
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory { *; }

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
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ============================================
# KOTLIN & COROUTINES
# ============================================
-keep class kotlin.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.**

# ============================================
# MAPLIBRE
# ============================================
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# ============================================
# REMOVE LOGGING IN RELEASE
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
