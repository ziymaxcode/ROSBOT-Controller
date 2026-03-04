// Top-level build file where you can add configuration options common to all sub-projects/modules.
// In build.gradle.kts (Project: AndroidRosApp)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // ADD THIS LINE
    alias(libs.plugins.kotlin.compose) apply false
}