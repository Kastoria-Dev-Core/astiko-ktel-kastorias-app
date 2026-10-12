# kotlinx.serialization: keep generated serializers for the data models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.michadasis.astikaktel.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.michadasis.astikaktel.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation type-safe routes are @Serializable
-keepclassmembers class com.michadasis.astikaktel.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.michadasis.astikaktel.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}
