// Top-level build file for Gearan Wear OS app (Galaxy Watch4 Classic target).
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    // NOTE: org.jetbrains.kotlin.plugin.compose exists only for Kotlin 2.0+.
    // On 1.9.x Compose uses composeOptions.kotlinCompilerExtensionVersion (app module).
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25" apply false
}
