# Keep data classes for serialization
-keepclassmembers class com.tavern.lite.data.model.** { *; }
-keepclassmembers class com.tavern.lite.data.store.** { *; }
-keepclassmembers class com.tavern.lite.data.db.entity.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclassmembers class kotlinx.serialization.** {
    *** INSTANCE;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit — only keep HTTP method interfaces, not the entire library
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Markwon — keep plugin classes loaded via reflection
-keep class io.noties.markwon.core.** { *; }
-keep class io.noties.markwon.html.** { *; }
-keep class io.noties.markwon.ext.latex.** { *; }
-keep class io.noties.markwon.ext.strikethrough.** { *; }

# Coil — keep fetcher/decoder factories
-keep class coil.fetch.** { *; }
-keep class coil.decode.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Handlebars template engine (javax.script not available on Android)
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.openjdk.nashorn.**
-dontwarn jakarta.servlet.**
-keep class com.github.jknack.handlebars.** { *; }
-keep class org.openjdk.nashorn.** { *; }
