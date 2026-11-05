plugins {
    // All plugins are declared here so they share one classloader;
    // the Kotlin Android plugin must see AGP's classes.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
