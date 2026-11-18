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

# Blu-ray ISO bridge (libetkn_bluray.so): JNI binds these names and signatures.
-keep class xyz.etkn.mobile.player.bluray.BlurayNative { native <methods>; }
-keep class xyz.etkn.mobile.player.bluray.BlurayNative$Track { <init>(...); }
-keepclassmembers class xyz.etkn.mobile.player.bluray.BlurayInput { byte[] read(long, int); }
# Native code calls ExtractorInput.read(byte[], int, int) on Media3's concrete input class.
-keepclassmembers class * implements androidx.media3.extractor.ExtractorInput { int read(byte[], int, int); }

# libmpv (mpv-android): native code calls back into MPVLib and its observers by name.
-keep class is.xyz.mpv.** { *; }
-keep class * implements is.xyz.mpv.MPVLib$EventObserver { *; }
