# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class me.weishu.reflection.** {*;}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static *** throwUninitializedProperty(...);
    public static *** throwUninitializedPropertyAccessException(...);
}

# --- Xposed ---
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keepattributes RuntimeVisibleAnnotations

-keep class com.highcapable.yukihookapi.hook.* { *; }
-keep class com.highcapable.yukihookapi.annotation.* { *; }
-keep class com.highcapable.kavaref.* { *; }
-dontwarn java.lang.reflect.AnnotatedType

# --- Tool ---
-keep class hk.uwu.reareye.hook.** { *; }
-keep class com.hchen.superlyricapi.* {*;}
-dontwarn android.os.ServiceManager