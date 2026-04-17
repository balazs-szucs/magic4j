plugins {
  `java-library`
  `maven-publish`
  signing
  checkstyle
  pmd
  id("com.diffplug.spotless") version "8.4.0"
  id("com.github.spotbugs") version "6.4.8"
}

group = "org.grimmory"

version = "0.1.0"

repositories {
  mavenCentral()
}

configure<CheckstyleExtension> {
  toolVersion = "13.3.0"
  configFile = rootProject.file("config/checkstyle/checkstyle.xml")
  isShowViolations = true
}

configure<PmdExtension> {
  toolVersion = "7.22.0"
  isConsoleOutput = true
  rulesMinimumPriority.set(5)
  ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
  ruleSets = emptyList()
}

extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
  format("misc") {
    target("*.md", "*.kts", "*.gradle.kts", "**/*.yml", "**/*.yaml", "**/.gitignore")
    targetExclude("**/build/**", "**/.gradle/**")
    trimTrailingWhitespace()
    endWithNewline()
  }
  java {
    target("src/*/java/**/*.java")
    targetExclude("**/build/**")
    googleJavaFormat("1.35.0")
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
  excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
  reports.create("html") { required.set(true) }
  reports.create("xml") { required.set(false) }
}

tasks.withType<Checkstyle>().configureEach { exclude("**/internal/*Bindings.java") }

tasks.withType<Pmd>().configureEach { exclude("**/internal/*Bindings.java") }

java {
  toolchain { languageVersion = JavaLanguageVersion.of(25) }
  withJavadocJar()
  withSourcesJar()
}

tasks.withType<Test> {
  useJUnitPlatform()
  dependsOn("stageLocalNative")
  classpath += files(layout.buildDirectory.dir("test-natives"))
  jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Javadoc> {
  (options as StandardJavadocDocletOptions).apply {
    source = "25"
    addStringOption("Xdoclint:none", "-quiet")
  }
  isFailOnError = false
}

tasks.withType<JavaExec> { jvmArgs("--enable-native-access=ALL-UNNAMED") }

tasks.named("check") { dependsOn("spotlessCheck") }

// Exclude native libraries from the main JAR; they live in classifier JARs only.
// The magic.mgc database (at src/main/resources/magic.mgc) IS included in the main JAR.
tasks.processResources { exclude("natives/**") }

// -- Per-platform classifier JAR tasks --
// Binaries are pre-staged by CI into src/main/resources/natives/{platform}/
val magicPlatforms =
  listOf(
    "linux-x64",
    "linux-arm64",
    "linux-musl-x64",
    "linux-musl-arm64",
    "darwin-x64",
    "darwin-arm64",
    "windows-x64",
  )

val nativeJarTasks =
  magicPlatforms.map { platform ->
    val sanitized =
      platform.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }
    tasks.register<Jar>("nativesJar$sanitized") {
      group = "build"
      description = "Packages $platform libmagic native library"
      archiveClassifier.set("natives-$platform")
      from(layout.projectDirectory.dir("src/main/resources/natives/$platform")) {
        into("natives/$platform")
      }
    }
  }

tasks.assemble { dependsOn(nativeJarTasks) }

// Verify that all platform native directories are populated before publishing.
val verifyNativeClassifiers by
  tasks.registering {
    description = "Verifies that all platform native libraries and magic.mgc are staged"
    doLast {
      magicPlatforms.forEach { platform ->
        val libFileName =
          when {
            platform.startsWith("linux") -> "libmagic.so"
            platform.startsWith("darwin") -> "libmagic.dylib"
            platform.startsWith("windows") -> "magic1.dll"
            else -> error("Unknown platform: $platform")
          }
        val lib =
          layout.projectDirectory
            .dir("src/main/resources/natives/$platform")
            .file(libFileName)
            .asFile
        require(lib.exists()) { "Missing native library for $platform: ${lib.absolutePath}" }
      }
      val db = layout.projectDirectory.file("src/main/resources/magic.mgc").asFile
      require(db.exists()) { "Missing magic database: ${db.absolutePath}" }
      logger.lifecycle("All native classifiers and magic.mgc verified OK")
    }
  }

// For local development: locate system-installed libmagic + magic.mgc and stage them so
// the NativeLoader classpath extraction path works during tests (same as the production flow).
val stageLocalNative by
  tasks.registering {
    description = "Stages system-installed libmagic and magic.mgc into build/test-natives for tests"
    val outputDir = layout.buildDirectory.dir("test-natives")
    outputs.dir(outputDir)
    doLast {
      val os = System.getProperty("os.name").lowercase()
      val arch = System.getProperty("os.arch").lowercase()
      val platform =
        when {
          os.contains("mac") && (arch == "aarch64" || arch == "arm64") -> "darwin-arm64"
          os.contains("mac") -> "darwin-x64"
          os.contains("nux") && (arch == "aarch64" || arch == "arm64") -> "linux-arm64"
          os.contains("nux") -> "linux-x64"
          else -> null
        }
      if (platform == null) {
        logger.lifecycle("stageLocalNative: unsupported OS for auto-staging; tests may fail")
        return@doLast
      }
      val (libName, libCandidates) =
        when {
          os.contains("mac") ->
            "libmagic.dylib" to
              listOf("/opt/homebrew/lib/libmagic.dylib", "/usr/local/lib/libmagic.dylib")
          else ->
            "libmagic.so" to
              listOf(
                "/usr/lib/x86_64-linux-gnu/libmagic.so.1",
                "/usr/lib/aarch64-linux-gnu/libmagic.so.1",
                "/usr/lib/libmagic.so.1",
              )
        }
      val dbCandidates =
        when {
          os.contains("mac") ->
            listOf(
              "/opt/homebrew/share/misc/magic.mgc",
              "/usr/local/share/misc/magic.mgc",
            )
          else -> listOf("/usr/share/misc/magic.mgc")
        }
      val libFile = libCandidates.map { File(it) }.firstOrNull { it.exists() }
      if (libFile != null) {
        val dest = outputDir.get().dir("natives/$platform").asFile
        dest.mkdirs()
        libFile.copyTo(dest.resolve(libName), overwrite = true)
        logger.lifecycle("stageLocalNative: staged ${libFile.name} for $platform")
      } else {
        logger.lifecycle("stageLocalNative: $libName not found; NativeLoader will fall back to System.loadLibrary")
      }
      val dbFile = dbCandidates.map { File(it) }.firstOrNull { it.exists() }
      if (dbFile != null) {
        dbFile.copyTo(outputDir.get().file("magic.mgc").asFile, overwrite = true)
        logger.lifecycle("stageLocalNative: staged magic.mgc from ${dbFile.parent}")
      } else {
        logger.lifecycle("stageLocalNative: magic.mgc not found; tests may fail if no magic.mgc on classpath")
      }
    }
  }

dependencies {
  compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
  testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// -- Maven Central publishing --

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])

      nativeJarTasks.forEach { jarTask -> artifact(jarTask) }

      pom {
        name = "magic4j"
        description =
          "Lightweight Java 25 FFM wrapper around libmagic for zero-dependency MIME type detection"
        url = "https://github.com/grimmory-tools/magic4j"
        inceptionYear = "2026"

        licenses {
          license {
            name = "Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0"
          }
        }

        developers {
          developer {
            id = "grimmory-tools"
            name = "Grimmory Tools"
          }
        }

        scm {
          connection = "scm:git:git://github.com/grimmory-tools/magic4j.git"
          developerConnection = "scm:git:ssh://github.com/grimmory-tools/magic4j.git"
          url = "https://github.com/grimmory-tools/magic4j"
        }
      }
    }
  }
}

signing {
  val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
  val signingPassword =
    findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")
  if (signingKey != null && signingPassword != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
  }
}
