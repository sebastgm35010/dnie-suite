plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.apache.santuario:xmlsec:2.3.5") {
        exclude(group = "com.fasterxml.woodstox")
        exclude(group = "org.codehaus.woodstox")
        exclude(group = "jakarta.xml.bind")
    }
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")
    testImplementation("junit:junit:4.13.2")
}
