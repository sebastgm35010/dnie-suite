plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin { jvmToolchain(17) }
application {
    mainClass.set("pe.gob.reniec.dnie.desktop.cli.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

providers.gradleProperty("desktopBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }

dependencies {
    implementation(project(":dnie-signing-core"))
    implementation(project(":dnie-pdf-desktop"))
    implementation(project(":dnie-signing-link"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test {
    providers.gradleProperty("pdfFixture").orNull?.let { systemProperty("dnie.pdf.fixture", it) }
    val temporary = layout.buildDirectory.dir("test-tmp")
    doFirst { temporary.get().asFile.mkdirs() }
    systemProperty("java.io.tmpdir", temporary.get().asFile.absolutePath)
}


// Optional pairing UI helper shipped with the user-mode distribution; no service installer.
distributions { main { contents { from("tools/make-pairing-qr.ps1") { into("tools") } } } }

// Preserve upstream notices in the user-mode distribution.
distributions { main { contents {
    from("../dnie-pdf-desktop/third-party") { into("licenses/pdf") }
    from("tools/verify-job.ps1") { into("tools") }
} } }
