plugins { id("org.jetbrains.kotlin.jvm"); application }
kotlin { jvmToolchain(17) }
application { mainClass.set("pe.gob.reniec.dnie.desktop.ui.MainKt"); applicationDefaultJvmArgs=listOf("-Dfile.encoding=UTF-8") }
providers.gradleProperty("desktopBuildDir").orNull?.let { layout.buildDirectory.set(file(it).resolveSibling("signing-ui")) }
dependencies {
    implementation("io.nayuki:qrcodegen:1.8.0")
    implementation(project(":dnie-desktop-orchestration"))
    implementation(project(":dnie-signing-desktop")); implementation(project(":dnie-pdf-desktop"))
    implementation(project(":dnie-signing-core")); implementation(project(":dnie-signing-link"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.zxing:core:3.5.3")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}
distributions { main { contents {
    from("tools/create-shortcut.ps1") { into("tools") }
    from("../dnie-signing-desktop/tools/verify-job.ps1") { into("tools") }
    from("../dnie-pdf-desktop/third-party") { into("licenses/pdf") }
    from("../THIRD_PARTY_VALIDATION.md") { into("licenses/validation") }
    from("third-party") { into("licenses/qr") }
} } }

providers.gradleProperty("bridgeIntegrationDir").orNull?.let { adapter ->
    distributions { main { contents { from(adapter) { into("tools/bridge-integration") } } } }
}

tasks.test {
    val temporary=layout.buildDirectory.dir("test-tmp")
    doFirst { temporary.get().asFile.mkdirs() }
    systemProperty("java.io.tmpdir",temporary.get().asFile.absolutePath)
}
