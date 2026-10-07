plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    testImplementation("junit:junit:4.13.2")
}
