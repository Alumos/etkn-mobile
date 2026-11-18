# kotlinx.serialization: keep generated serializers for @Serializable models.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class xyz.etkn.mobile.**$$serializer { *; }
-keepclassmembers class xyz.etkn.mobile.** { *** Companion; }
-keepclasseswithmembers class xyz.etkn.mobile.** { kotlinx.serialization.KSerializer serializer(...); }

# NextLib FFmpeg extension is loaded reflectively by Media3 / JNI.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# OkHttp optional platform classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
