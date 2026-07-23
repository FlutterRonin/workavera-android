# Keep kotlinx.serialization generated serializers for our models.
-keepclassmembers class com.workavera.android.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.workavera.android.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
