# Add project specific ProGuard rules here.
# For more details, see: http://developer.android.com/guide/developing/tools/proguard.html

# PDFBox references an optional JP2 decoder that is not shipped on Android
-dontwarn com.gemalto.jp2.JP2Decoder

# Strip verbose and debug log calls from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
