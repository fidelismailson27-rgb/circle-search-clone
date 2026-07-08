# T076: rules for the reflection-sensitive libraries on the BYOK network path
# (constitution IX). Retrofit, OkHttp, Hilt/Dagger, and Tink each already bundle
# their own consumer ProGuard rules inside their AARs (applied automatically by AGP),
# so most of what's below covers documented edge cases rather than duplicating them.

# --- Retrofit / OkHttp / Okio ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- kotlinx.serialization ---
# Standard pattern from kotlinx.serialization's own ProGuard guidance: keep every
# @Serializable class's generated $$serializer and its Companion.serializer() lookup,
# scoped to this app's own DTOs (data/network/dto) rather than the whole classpath.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.circulesearch.app.data.network.dto.**$$serializer { *; }
-keepclassmembers class com.circulesearch.app.data.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.circulesearch.app.data.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Tink (BYOK credential encryption, T014) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
