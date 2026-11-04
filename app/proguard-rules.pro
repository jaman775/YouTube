# Drishyopot release rules
-keepattributes SourceFile,LineNumberTable

# NewPipe Extractor (uses Rhino for signature decoding and reflection for time-ago patterns)
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Engine: the interface is shared with downloadable updates, so its names must survive shrinking.
-keep class com.drishyopot.engine.api.** { *; }
-keep class com.drishyopot.engine.impl.EngineImpl { public <init>(); }

# Page bridges: methods are called by name from JavaScript.
-keepclassmembers class com.drishyopot.app.browser.WebSpeechBridge { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface
