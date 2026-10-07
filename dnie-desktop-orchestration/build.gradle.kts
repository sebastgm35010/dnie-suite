plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
kotlin { jvmToolchain(17) }
providers.gradleProperty("desktopBuildDir").orNull?.let { layout.buildDirectory.set(file(it).resolveSibling("desktop-orchestration")) }
dependencies { testImplementation("junit:junit:4.13.2") }
