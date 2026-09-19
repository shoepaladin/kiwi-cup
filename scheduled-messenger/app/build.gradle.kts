plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kiwicup.scheduledmessenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kiwicup.scheduledmessenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Sideload builds: a stable key from CI secrets when present, else the debug key so the
        // APK is still installable (updates then require an uninstall first).
        create("sideload") {
            val keystore = rootProject.file("sideload.keystore")
            if (keystore.exists() && System.getenv("SIDELOAD_KEYSTORE_PASSWORD") != null) {
                storeFile = keystore
                storePassword = System.getenv("SIDELOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIDELOAD_KEY_ALIAS") ?: "sideload"
                keyPassword = System.getenv("SIDELOAD_KEY_PASSWORD") ?: System.getenv("SIDELOAD_KEYSTORE_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            // Not minified yet: the MMS library and Room rely on reflection and a shrink pass
            // needs keep rules verified on a device first. APK stays small enough for sideloading.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    lint {
        // Lint findings are reported as artifacts; only real errors fail the build.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        disable += setOf("ObsoleteLintCustomCheck", "OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                test.testLogging {
                    // "started" matters as much as the rest: when a test hangs, the completion
                    // events never arrive and the log ends on the last test that finished, which
                    // names the one before the culprit. Logging starts makes the log name it.
                    events("started", "passed", "failed", "skipped")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
                // A hung test otherwise burns the job's whole 40-minute budget before CI says
                // anything. The suite runs in well under two minutes when healthy.
                test.timeout.set(java.time.Duration.ofMinutes(12))
            }
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mmslib)
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
