# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:/Users/arthu/AppData/Local/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Firebase ProGuard rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.messaging.** { *; }

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Jetpack Compose classes
-keep class androidx.compose.** { *; }

# Keep Room classes
-keep class androidx.room.** { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# Keep Coroutines classes
-keep class kotlinx.coroutines.** { *; }

# Keep Accompanist classes
-keep class com.google.accompanist.** { *; }

# Keep Material Design classes
-keep class com.google.android.material.** { *; }

# Keep AndroidX classes
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.navigation.** { *; }

# Keep sensor-related classes
-keep class android.hardware.SensorManager { *; }
-keep class android.hardware.Sensor { *; }
-keep class android.hardware.SensorEvent { *; }
-keep class android.hardware.SensorEventListener { *; }

# Keep service-related classes
-keep class android.app.Service { *; }
-keep class android.content.BroadcastReceiver { *; }

# Keep notification classes
-keep class android.app.Notification { *; }
-keep class android.app.NotificationManager { *; }

# Keep reflection classes
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep data classes
-keepclassmembers class * {
    public synthetic <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}