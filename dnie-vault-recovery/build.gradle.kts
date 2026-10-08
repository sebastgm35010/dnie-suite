plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":dnie-vault-core"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    // Exact vault-core ABI; Windows JVM tests use the same format as Android.
    testImplementation("com.google.crypto.tink:tink-android:1.23.0")
    testImplementation("junit:junit:4.13.2")
}
