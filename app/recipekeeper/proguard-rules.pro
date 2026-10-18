# Add project specific ProGuard rules here.

# Keep MediaPipe LLM Inference classes (used reflectively in some builds).
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# Keep our app's classes (Koin uses reflection for parameter resolution).
-keep class com.llmlocal.** { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
