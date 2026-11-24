# Applied to release builds (isMinifyEnabled = true).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Room entities / DAOs (reflection-free, but keep annotated members).
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Classes serialised with Gson by field name: backup file, updater release cache, sync wire models.
# Renaming their fields would silently break backups, restores and sync.
-keep class com.spendingsaver.data.** { *; }
-keep class com.spendingsaver.sync.** { *; }
-keep class com.spendingsaver.update.ReleaseInfo { *; }
-keep class com.spendingsaver.repository.DataBackupRepository$* { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp / Okio reference optional platform classes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# opencsv optional dependencies
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.beanutils.**

# JNI: the native library binds to these exact names.
-keep class com.spendingsaver.link.NativeLink { *; }
