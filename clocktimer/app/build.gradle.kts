plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.clocktimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.clocktimer"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.material)

    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.compose.foundation:foundation:1.7.6")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    testImplementation("junit:junit:4.13.2")

}

// Workaround for Windows file locking issues
tasks.withType<Delete> {
    setFollowSymlinks(false)
}

// Prevent package tasks from being blocked by file locks
tasks.whenTaskAdded {
    if (name.contains("package", ignoreCase = true) ||
        name.contains("merge", ignoreCase = true)) {
        outputs.upToDateWhen { false }
    }
}