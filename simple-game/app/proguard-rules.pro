# Keep only the JavaScript bridge methods that WebView calls by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Billing uses annotations and reflection in parts of its implementation.
-keepattributes *Annotation*
