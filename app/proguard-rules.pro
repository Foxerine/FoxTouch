# ── Ktor ──
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── OkHttp & Okio (Ktor engine) ──
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── kotlinx-serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ai.foxtouch.**$$serializer { *; }
-keepclassmembers class ai.foxtouch.** { *** Companion; }
-keepclasseswithmembers class ai.foxtouch.** { kotlinx.serialization.KSerializer serializer(...); }

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class **Dao_Impl { *; }
-dontwarn androidx.room.paging.**

# ── Tink (crypto, heavy reflection) ──
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── OpenAI SDK ──
-keep class com.aallam.openai.** { *; }
-dontwarn com.aallam.openai.**

# ── Coil (image loading) ──
-dontwarn coil3.**
-dontwarn coil.**

# ── Markdown renderer ──
-dontwarn com.mikepenz.markdown.**

# ── Services declared in AndroidManifest (loaded by system) ──
-keep class ai.foxtouch.assistant.FoxTouchVoiceInteractionService
-keep class ai.foxtouch.assistant.FoxTouchSessionService
-keep class ai.foxtouch.assistant.FoxTouchAssistantSession
-keep class ai.foxtouch.agent.AgentForegroundService
-keep class ai.foxtouch.accessibility.ScreenCaptureService
-keep class ai.foxtouch.ui.overlay.FloatingBubbleService
