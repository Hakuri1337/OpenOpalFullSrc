# This template is expanded into build/<edition>/proguard/ before ProGuard runs.
# It processes only Loom's final remapped Fabric JAR; nested dependency JARs and
# all non-class resources are retained as archive resources.

-injars '%INPUT_JAR%'
-outjars '%OUTPUT_JAR%'

# Generated from the Java 21 toolchain and the Fabric client runtime classpath.
# Accurate hierarchy data is required when ProGuard recomputes stack-map frames.
%LIBRARY_JARS%

-dontshrink
-dontoptimize
-dontnote
-dontwarn
-useuniqueclassmembernames

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,MethodParameters,Record
-keepdirectories META-INF/**,assets/**
-adaptresourcefilecontents META-INF/services/**

# Fabric resolves this class by the literal value in fabric.mod.json.
-keep class wtf.oraculus.OraculusFabric { *; }

# Mixin configuration uses package-relative class names. Keep both class and
# member names so it remains valid without rewriting its JSON payload.
-keep class wtf.oraculus.mixin.** { *; }

# Script-facing surface is dynamically accessed by user scripts.
-keep class wtf.oraculus.scripting.** { *; }

# Minecraft and Fabric call these objects through runtime interfaces or
# overridable methods whose names come from the remapped game JAR. Keep the
# UI-facing surface conservative: changing an implementation name here causes
# AbstractMethodError rather than a recoverable missing-method failure.
-keep class wtf.oraculus.client.renderer.** { *; }
-keep class wtf.oraculus.client.screen.** { *; }
-keep class wtf.oraculus.utility.render.** { *; }
-keep class wtf.oraculus.client.music.** { *; }
-keep class * extends net.minecraft.** { *; }
-keep class * implements net.minecraft.** { *; }
-keep class * extends net.fabricmc.** { *; }
-keep class * implements net.fabricmc.** { *; }

# Only the login screen itself is retained by the Minecraft-superclass rule
# above. All non-UI authentication code is statically linked, uses explicit
# JSON keys rather than reflective DTOs, and can therefore be fully renamed.
# This intentionally covers AuthApiClient, AuthBootstrap, AuthRuntimeGate,
# AuthService, AuthSessionStore, AuthSnapshot, AuthState and the fingerprint
# provider without destabilising the Fabric or Mixin bootstrap surfaces.

# The catalog builds modules and their persisted settings. Class names can be
# safely shortened, but field names must remain stable for existing configs and
# Gson-backed local data.
-keepclassmembers class wtf.oraculus.** {
    <fields>;
}

# Preserve all explicit serialization contracts even when a future model adds
# a non-standard Gson adapter.
-keepclassmembers,allowoptimization class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Class.getEnumConstants() resolves these two Java enum ABI methods by their
# literal names. Renaming values() makes every reflective enum lookup return
# null, which breaks mode-property construction during client startup.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-printmapping '%MAPPING_FILE%'
