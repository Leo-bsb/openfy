buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    // AGP 9 usa Kotlin embutido; a InnerTubeX exige metadata 2.4.x.
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
  }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
