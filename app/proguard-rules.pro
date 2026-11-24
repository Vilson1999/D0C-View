# R8 / ProGuard size compression rules for D0C View
-allowaccessmodification
-repackageclasses 'o'

# Strip verbose debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
