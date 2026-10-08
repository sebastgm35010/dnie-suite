plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pe.gob.reniec.dnie"
    compileSdk = 34

    defaultConfig {
        applicationId = "pe.gob.reniec.dnie.evolution"
        minSdk = 24
        targetSdk = 34
        versionCode = 67
        versionName = "2.0.0-rc19-whole-vault"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Separate development identity for this checkout; keep this keystore for future updates.
    signingConfigs {
        create("evolution") {
            storeFile = rootProject.file("signing/evolution-development.jks")
            storePassword = "android"
            keyAlias = "evolution"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("evolution")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("evolution")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets.getByName("test").resources.srcDir(rootProject.file("test-resources/pdfbox"))

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

tasks.register<Copy>("exportBranchApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("app-debug.apk")
        rename("app-debug.apk", "dnie-evolution-debug.apk")
    }
    into(layout.buildDirectory.dir("outputs/apk/evolution"))
}

dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation(project(":dnie-core"))
    implementation(project(":dnie-protocol"))
    implementation(project(":dnie-signing-link"))
    implementation(project(":dnie-signing-core"))
    implementation(project(":dnie-nfc"))
    implementation(project(":dnie-bridge-android"))
    implementation(project(":dnie-pdf"))
    implementation(project(":dnie-icao"))
    implementation(project(":dnie-vault-core"))
    implementation(project(":dnie-vault-recovery"))
    implementation(project(":dnie-passkeys-android"))
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("com.github.Tgo1014:JP2ForAndroid:1.0.4")
    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Jetpack Compose BOM & M3
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Debugging & Preview
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
