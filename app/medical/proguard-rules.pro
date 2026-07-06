# Add project specific ProGuard rules here.

# Keep LiteRT-LM classes used via JNI / reflection.
-keep class com.google.ai.edge.litertlm.** { *; }

# Keep our app's classes (Koin uses reflection for parameter resolution).
-keep class com.llmlocal.** { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*