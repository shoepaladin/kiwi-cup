plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    // Test-only on purpose. This module's phone-number logic talks to PhoneNumberRecognizer, not
    // to a library: Google's plain Java build loads its metadata through Class#getResourceAsStream,
    // which its own README says Android apps should not rely on, so the app supplies the Android
    // port instead (see PossibleNumbers). Off-device the plain build is the real thing, and using
    // it here keeps these tests honest about what libphonenumber actually accepts.
    testImplementation(libs.libphonenumber)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
