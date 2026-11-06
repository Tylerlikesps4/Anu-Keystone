# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class keystone.engine.**$$serializer { *; }
-keepclassmembers class keystone.engine.** { *** Companion; }
-keepclasseswithmembers class keystone.engine.** { kotlinx.serialization.KSerializer serializer(...); }
