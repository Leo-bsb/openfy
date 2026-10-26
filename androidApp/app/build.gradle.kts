import java.util.Properties

// As chaves ficam no local.properties da raiz do projeto Openfy (fora do Git).
val localProps = Properties().apply {
    val file = rootProject.file("../local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localValue(key: String): String {
    val raw = localProps.getProperty(key)
        ?: System.getenv(key.replace('.', '_').uppercase())
        ?: ""
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.openfy.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openfy.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localValue("openfy.supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localValue("openfy.supabase.anonKey")}\"")
        buildConfigField("String", "SITE_URL", "\"${localValue("openfy.site.url")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}
