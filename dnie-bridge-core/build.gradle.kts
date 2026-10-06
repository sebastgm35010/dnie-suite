plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
