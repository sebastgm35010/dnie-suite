plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":dnie-protocol"))
    implementation(project(":dnie-diagnostics-core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
