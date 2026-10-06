plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pe.gob.reniec.dnie.bridge.android"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":dnie-bridge-core"))
    api(project(":dnie-protocol"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.property("crossLayerFixture", providers.environmentVariable("DNIE_CROSS_LAYER_FIXTURE").orElse("disabled"))
}
