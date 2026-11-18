# Smart Index release rules (LLD — App shell §6.4). Hilt, Room, WorkManager, Media3 and Coil ship consumer rules.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Release logs are WARN+ only: strip verbose/debug/info calls.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
