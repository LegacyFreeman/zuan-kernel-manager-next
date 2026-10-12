# Project-specific R8 rules. Keep these narrow because release builds should remain shrinkable.

# LibSU: shell/root service classes are accessed through the library runtime.
-keep class com.topjohnwu.superuser.** { *; }
-keep class com.topjohnwu.superuser.ipc.** { *; }
-keep class com.topjohnwu.superuser.nio.** { *; }

# Shizuku public API/provider classes are loaded through the Android/Shizuku runtime.
-keep class rikka.shizuku.** { *; }

# AIDL interfaces and generated Stub/Proxy implementations.
-keep interface com.zuan.kernelmanager.next.I*Service { *; }
-keep class com.zuan.kernelmanager.next.I*Service$* { *; }
-keep class com.zuan.kernelmanager.next.service.ZkmRootService { *; }

# WebView JavaScript bridge methods are invoked by name.
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Embedded Termux terminal implementation is referenced by runtime package/classes.
-keep class com.termux.** { *; }
