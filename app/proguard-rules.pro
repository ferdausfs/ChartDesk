# ChartDesk release rules

# Keep the JS bridge: methods are called reflectively from JavaScript.
-keepclassmembers class com.chartdesk.app.chart.ChartBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.chartdesk.app.**$$serializer { *; }
-keepclassmembers class com.chartdesk.app.** { *** Companion; }
-keepclasseswithmembers class com.chartdesk.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
