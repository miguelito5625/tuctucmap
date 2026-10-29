# ProGuard / R8 rules para TucTuc Map
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.tuctucmap.**$$serializer { *; }
-keepclassmembers class com.example.tuctucmap.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.tuctucmap.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Mantener modelos de datos y DTOs internos para Firebase Realtime Database y Supabase
-keep class com.example.tuctucmap.data.model.** { *; }
-keep class com.example.tuctucmap.data.repository.SupabaseRepository$* { *; }

# Ktor & Supabase
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn slf4j.**
-dontwarn org.slf4j.**
