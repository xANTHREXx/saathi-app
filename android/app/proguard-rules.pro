# R8 rules for the release build. Shrinking and resource shrinking stay on; only obfuscation is off.

# Crash reports must stay readable (we collect stack traces) and the code is open source anyway.
# Shrinking still removes unused code.
-dontobfuscate

# Keep line numbers for readable stack traces.
-keepattributes SourceFile,LineNumberTable

# --- kotlinx.serialization (standard rules from the library README) ---
# Settings, Contact, SavedPlace, Evidence, TripRecord, RouteCache.Entry, Fix, Recipients and the demo-route
# classes are @Serializable and go through Json.encodeToString / decodeFromString.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Serializer lookups for kotlinx.serialization.json.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Companion.serializer() of @Serializable classes nested in other classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# INSTANCE / serializer() of @Serializable objects and the generated $$serializer.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Glance widget ---
# actionRunCallback<Slide>() builds the callback by class name at click time; nothing references it directly.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
