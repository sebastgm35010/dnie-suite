plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    // Android relocates Protobuf types: compile and test against the APK's exact ABI.
    compileOnly("com.google.crypto.tink:tink-android:1.23.0")
    testImplementation("com.google.crypto.tink:tink-android:1.23.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
