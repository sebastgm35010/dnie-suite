plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pe.gob.reniec.dnie.passkeys.android"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":dnie-passkeys-core"))
    implementation(project(":dnie-vault-core"))
    implementation(project(":dnie-protocol"))
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    api("androidx.credentials:credentials:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(project(":dnie-core"))
}
