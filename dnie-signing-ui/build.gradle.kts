plugins { id("org.jetbrains.kotlin.jvm"); application }
kotlin { jvmToolchain(17) }
application { mainClass.set("pe.gob.reniec.dnie.desktop.ui.MainKt"); applicationDefaultJvmArgs=listOf("-Dfile.encoding=UTF-8") }
providers.gradleProperty("desktopBuildDir").orNull?.let { layout.buildDirectory.set(file(it).resolveSibling("signing-ui")) }
dependencies {
    implementation(project(":dnie-desktop-orchestration"))
    implementation(project(":dnie-signing-desktop")); implementation(project(":dnie-pdf-desktop"))
    implementation(project(":dnie-signing-core")); implementation(project(":dnie-signing-link"))
    testImplementation("junit:junit:4.13.2")
}
distributions { main { contents {
    from("tools/create-shortcut.ps1") { into("tools") }
    from("../dnie-signing-desktop/tools/make-pairing-qr.ps1") { into("tools") }
    from("../dnie-signing-desktop/tools/verify-job.ps1") { into("tools") }
    from("../dnie-pdf-desktop/third-party") { into("licenses/pdf") }
    from("../THIRD_PARTY_VALIDATION.md") { into("licenses/validation") }
} } }

providers.gradleProperty("bridgeIntegrationDir").orNull?.let { adapter ->
    distributions { main { contents { from(adapter) { into("tools/bridge-integration") } } } }
}
