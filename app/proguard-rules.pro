# Keep Room entities
-keep class com.novelreader.data.local.db.entity.** { *; }

# Keep Jsoup
-keep class org.jsoup.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Coil
-keep class coil.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# AndroidX Navigation
-keep class androidx.navigation.** { *; }

# Keep source file and line number for crash reporting
-keepattributes SourceFile,LineNumberTable

# Google RE2J (optional dependency of Jsoup 1.21+)
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
