// Top-level build file where you can add configuration options common to all sub-projects/modules.
// DO NOT CHANGE VERSIONS! UPDATED AND LOCKED!
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    alias(libs.plugins.kotlin.compose) apply false}