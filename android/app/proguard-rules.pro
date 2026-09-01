# kotlinx.serialization 生成的 serializer 需要保留
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.copdtrend.demo.network.** {
    *** Companion;
}
-keepclasseswithmembers class com.copdtrend.demo.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
