plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pe.gob.reniec.dnie.pdf"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets.getByName("test").resources.srcDir(rootProject.file("test-resources/pdfbox"))
}

dependencies {
    api(project(":dnie-core"))
    api(project(":dnie-signing-core"))
    implementation(project(":dnie-diagnostics-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("com.tom-roush:pdfbox-android:2.0.27.0") { exclude(group = "org.bouncycastle") }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
