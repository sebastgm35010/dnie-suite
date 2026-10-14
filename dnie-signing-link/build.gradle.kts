plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":dnie-protocol"))
    api(project(":dnie-network-core"))
    testImplementation("junit:junit:4.13.2")
}
