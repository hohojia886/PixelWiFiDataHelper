# LibXposed API 102 basic keep rules
-keep class io.github.hohojia886.pixelwifidatahelper.MainHook { *; }
-keep class io.github.hohojia886.pixelwifidatahelper.hooks.** { *; }

# Keep LibXposed API classes
-keep class io.github.libxposed.api.** { *; }

# General AndroidX/Material keep rules are usually handled by AARs,
# but we can add more if needed for reflection.
