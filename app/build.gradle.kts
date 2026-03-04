// In build.gradle.kts (Module :app)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ADD THIS LINE
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.androidrosapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.androidrosapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    // ADD THIS BLOCK
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Use a version compatible with your Kotlin plugin
    }
    // It's also good practice to add this
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // UPDATED: No specific version needed here because of the BOM
    implementation("androidx.compose.foundation:foundation")

    // For reading .xlsx Excel files
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.remote.creation.core) // Note: Using a slightly older, stable version
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // For ViewModel with Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // Note: Using a slightly older, stable version

    // For Kotlinx Serialization (JSON handling)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Note: Using a slightly older, stable version

    // For WebSockets and HTTP communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Note: Using a slightly older, stable version
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}