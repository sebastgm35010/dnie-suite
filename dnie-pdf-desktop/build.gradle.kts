plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
kotlin { jvmToolchain(17) }
providers.gradleProperty("desktopBuildDir").orNull?.let { layout.buildDirectory.set(file(it).resolveSibling("pdf-desktop")) }
dependencies {
    api(project(":dnie-signing-core"))
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    providers.gradleProperty("visibleEvidence").orNull?.let { systemProperty("dnie.pdf.visibleEvidence",it) }
    val temp = layout.buildDirectory.dir("test-tmp")
    doFirst { temp.get().asFile.mkdirs() }
    systemProperty("java.io.tmpdir", temp.get().asFile.absolutePath)
}
