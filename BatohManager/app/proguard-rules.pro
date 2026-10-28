# R8 rules for GifPack release builds (minify + resource shrinking).
# Libraries ship consumer rules (Hilt/Dagger, Room, WorkManager, Coil, OkHttp, Compose, media3,
# Retrofit 2.9, Moshi); only the app-specific gaps are listed here.

# --- Moshi reflection (KotlinJsonAdapterFactory in NetworkModule) ---------------------------------
# The app uses reflective Moshi, not codegen (no @JsonClass, no moshi-kotlin-codegen). The adapter
# reads constructor parameters, property names and nullability through kotlin-reflect, so the DTOs
# must keep their names, members and Kotlin metadata. Scoped to the DTO package only.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-keep class com.batoh.core.data.remote.model.** { *; }

# --- Retrofit service interfaces ------------------------------------------------------------------
# Retrofit 2.9 consumer rules keep annotated interface methods but not the generic return types that
# R8 full mode (default in AGP 8) strips; suspend funs return Response<T> via Continuation.
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.batoh.core.data.remote.*Api { *; }

# --- Not needed (verified by reading the code) ----------------------------------------------------
# Room: entities/DAOs/database are covered by Room's generated code and consumer rules.
# Hilt/WorkManager: @HiltWorker generated factories are referenced from generated code.
# JNI: no System.loadLibrary or app-owned .so files.
# Enums persisted by name (UiThemeMode, PanelFamily via valueOf): R8 keeps enum values()/valueOf()
# via the default android-optimize rules, and constant names are not obfuscated.
# BLE protocol uses numeric opcodes and byte arrays, not names or reflection.

# Missing optional annotation classes referenced by libraries (R8 full mode would otherwise fail).
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
