import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import groovy.json.JsonOutput

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("io.github.compose-fluent.windows-toolkit")
}

description = "Cross-projection WinRT performance comparisons for Kotlin, CsWinRT, and C++/WinRT"

kotlin {
    jvmToolchain(25)
    jvm()
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.composefluent.winrt.benchmarks.main"
                linkerOpts("-lole32")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
        }
        named("winuiMain") {
            dependencies {
                implementation(projects.winrtProjections)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val benchmarkWarmupRounds = providers.gradleProperty("kotlinWinRT.benchmarks.warmupRounds")
    .orElse("5")
val benchmarkMeasurementRounds = providers.gradleProperty("kotlinWinRT.benchmarks.measurementRounds")
    .orElse("15")
val benchmarkIterations = providers.gradleProperty("kotlinWinRT.benchmarks.iterations")
    .orElse("1")
val benchmarkFilter = providers.gradleProperty("kotlinWinRT.benchmarks.filter")
val benchmarkWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.benchmarks.windowsSdkVersion")
    .orElse("10.0.26100.0")
val benchmarkTestWinRTSource = providers.gradleProperty("kotlinWinRT.benchmarks.testWinRTSource")
val referenceBenchmarkComponentDirectory = layout.buildDirectory.dir("reference-benchmark-component")
val referenceBenchmarkComponentDll = referenceBenchmarkComponentDirectory.map { it.file("BenchmarkComponent.dll") }
val referenceBenchmarkComponentWinmd = referenceBenchmarkComponentDirectory.map { it.file("BenchmarkComponent.winmd") }
val benchmarkResultsDirectory = layout.buildDirectory.dir("results/benchmarks")
val benchmarkReportsDirectory = layout.buildDirectory.dir("reports/benchmarks")
val benchmarkCatalogDirectory = layout.buildDirectory.dir("results/benchmark-catalogs")

val buildReferenceBenchmarkComponent by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Builds the exact TestWinRT BenchmarkComponent pinned by .cswinrt/src/get_testwinrt.cmd."
    val script = layout.projectDirectory.file("scripts/Build-ReferenceBenchmarkComponent.ps1")
    inputs.file(script)
    inputs.property("windowsSdkVersion", benchmarkWindowsSdkVersion)
    inputs.property("testWinRTCommit", "aa4edcd52542cfe036473d008d3f66c8a220d59d")
    inputs.property("testWinRTSource", benchmarkTestWinRTSource.orElse(""))
    outputs.file(referenceBenchmarkComponentDll)
    outputs.file(referenceBenchmarkComponentWinmd)
    outputs.file(referenceBenchmarkComponentDirectory.map { it.file("TestWinRT.commit") })
    outputs.dir(referenceBenchmarkComponentDirectory.map { it.dir("cppwinrt-include") })
    outputs.dir(referenceBenchmarkComponentDirectory.map { it.dir("cppwinrt-reference-include") })
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        script.asFile.absolutePath,
        "-OutputDirectory",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
        "-WindowsSdkVersion",
        benchmarkWindowsSdkVersion.get(),
    )
    benchmarkTestWinRTSource.orNull?.takeIf(String::isNotBlank)?.let { sourceDirectory ->
        args("-SourceDirectory", sourceDirectory)
    }
}

tasks.matching { task ->
    task.name == "generateWinRTMetadataIndex" ||
        task.name == "prepareWinRTProjectionMetadata" ||
        task.name == "generateWinRTProjections" ||
        task.name.startsWith("compileKotlin")
}.configureEach {
    dependsOn(buildReferenceBenchmarkComponent)
}

fun benchmarkArguments(outputFileName: String): List<String> =
    buildList {
        addAll(
            listOf(
                "--warmup-rounds",
                benchmarkWarmupRounds.get(),
                "--measurement-rounds",
                benchmarkMeasurementRounds.get(),
                "--iterations",
                benchmarkIterations.get(),
                "--output",
                benchmarkResultsDirectory.get().file(outputFileName).asFile.absolutePath,
            ),
        )
        benchmarkFilter.orNull?.takeIf(String::isNotBlank)?.let { filter ->
            add("--filter")
            add(filter)
        }
    }

// The published JVM artifact includes both business and separately compiled projection classes.
val benchmarkJvmArtifact = tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }

val benchmarkKotlinJvm by tasks.registering(JavaExec::class) {
    group = "benchmark"
    description = "Runs the Kotlin/JVM WinRT projection benchmark."
    dependsOn(buildReferenceBenchmarkComponent)
    workingDir(layout.projectDirectory)
    mainClass.set("io.github.composefluent.winrt.benchmarks.BenchmarkMainKt")
    classpath(
        benchmarkJvmArtifact,
        configurations.named("jvmRuntimeClasspath"),
    )
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Xms512m",
        "-Xmx512m",
    )
    systemProperty(
        "kotlin.winrt.runtimeAssetsRoot",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
    )
    args(benchmarkArguments("kotlin-jvm.jsonl"))
    outputs.upToDateWhen { false }
}

tasks.named<Test>("jvmTest") {
    dependsOn(buildReferenceBenchmarkComponent)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "kotlin.winrt.runtimeAssetsRoot",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
    )
}

val benchmarkKotlinNative by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Runs the Kotlin/Native mingwX64 WinRT projection benchmark."
    dependsOn("linkReleaseExecutableMingwX64")
    dependsOn(buildReferenceBenchmarkComponent)
    workingDir(layout.projectDirectory)
    executable(
        layout.buildDirectory
            .file("bin/mingwX64/releaseExecutable/winrt-benchmarks.exe")
            .get()
            .asFile,
    )
    args(benchmarkArguments("kotlin-native.jsonl"))
    environment(
        "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
    )
    outputs.upToDateWhen { false }
}

val benchmarkCsWinRT by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Runs the .NET 8 CsWinRT projection benchmark."
    dependsOn(buildReferenceBenchmarkComponent)
    workingDir(layout.projectDirectory.dir("cswinrt"))
    commandLine(
        "dotnet",
        "run",
        "--project",
        "CsWinRTBenchmark.csproj",
        "--configuration",
        "Release",
        "--no-launch-profile",
        "-p:BenchmarkComponentRoot=${referenceBenchmarkComponentDirectory.get().asFile.absolutePath}",
        "--",
    )
    args(benchmarkArguments("cswinrt.jsonl"))
    environment("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
    environment("DOTNET_NOLOGO", "1")
    outputs.upToDateWhen { false }
}

val benchmarkCppWinRT by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Builds and runs the x64 Release C++/WinRT projection benchmark."
    dependsOn(buildReferenceBenchmarkComponent)
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        layout.projectDirectory.file("scripts/Build-And-Run-CppWinRT.ps1").asFile.absolutePath,
        "-ProjectPath",
        layout.projectDirectory.file("cppwinrt/CppWinRTBenchmark.vcxproj").asFile.absolutePath,
        "-OutputPath",
        benchmarkResultsDirectory.get().file("cppwinrt.jsonl").asFile.absolutePath,
        "-WarmupRounds",
        benchmarkWarmupRounds.get(),
        "-MeasurementRounds",
        benchmarkMeasurementRounds.get(),
        "-Iterations",
        benchmarkIterations.get(),
        "-WindowsSdkVersion",
        benchmarkWindowsSdkVersion.get(),
        "-BenchmarkComponentRoot",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
    )
    benchmarkFilter.orNull?.takeIf(String::isNotBlank)?.let { filter ->
        args("-Filter", filter)
    }
    outputs.upToDateWhen { false }
}

fun benchmarkCatalogArguments(outputFileName: String): List<String> =
    listOf(
        "--list-scenarios",
        "--output",
        benchmarkCatalogDirectory.get().file(outputFileName).asFile.absolutePath,
    )

val benchmarkKotlinJvmCatalog by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Exports the Kotlin/JVM executable benchmark catalog."
    dependsOn(buildReferenceBenchmarkComponent)
    workingDir(layout.projectDirectory)
    mainClass.set("io.github.composefluent.winrt.benchmarks.BenchmarkMainKt")
    classpath(
        benchmarkJvmArtifact,
        configurations.named("jvmRuntimeClasspath"),
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "kotlin.winrt.runtimeAssetsRoot",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
    )
    args(benchmarkCatalogArguments("kotlin-jvm.txt"))
    outputs.file(benchmarkCatalogDirectory.map { it.file("kotlin-jvm.txt") })
}

val benchmarkKotlinNativeCatalog by tasks.registering(Exec::class) {
    group = "verification"
    description = "Exports the Kotlin/Native executable benchmark catalog."
    dependsOn("linkReleaseExecutableMingwX64")
    dependsOn(buildReferenceBenchmarkComponent)
    workingDir(layout.projectDirectory)
    executable(
        layout.buildDirectory
            .file("bin/mingwX64/releaseExecutable/winrt-benchmarks.exe")
            .get()
            .asFile,
    )
    args(benchmarkCatalogArguments("kotlin-native.txt"))
    environment(
        "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
    )
    outputs.file(benchmarkCatalogDirectory.map { it.file("kotlin-native.txt") })
}

val benchmarkCsWinRTCatalog by tasks.registering(Exec::class) {
    group = "verification"
    description = "Exports the CsWinRT executable benchmark catalog."
    dependsOn(buildReferenceBenchmarkComponent)
    workingDir(layout.projectDirectory.dir("cswinrt"))
    commandLine(
        "dotnet",
        "run",
        "--project",
        "CsWinRTBenchmark.csproj",
        "--configuration",
        "Release",
        "--no-launch-profile",
        "-p:BenchmarkComponentRoot=${referenceBenchmarkComponentDirectory.get().asFile.absolutePath}",
        "--",
    )
    args(benchmarkCatalogArguments("cswinrt.txt"))
    environment("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
    environment("DOTNET_NOLOGO", "1")
    outputs.file(benchmarkCatalogDirectory.map { it.file("cswinrt.txt") })
}

val benchmarkCppWinRTCatalog by tasks.registering(Exec::class) {
    group = "verification"
    description = "Exports the C++/WinRT executable benchmark catalog."
    dependsOn(buildReferenceBenchmarkComponent)
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        layout.projectDirectory.file("scripts/Build-And-Run-CppWinRT.ps1").asFile.absolutePath,
        "-ProjectPath",
        layout.projectDirectory.file("cppwinrt/CppWinRTBenchmark.vcxproj").asFile.absolutePath,
        "-OutputPath",
        benchmarkCatalogDirectory.get().file("cppwinrt.txt").asFile.absolutePath,
        "-WarmupRounds",
        "0",
        "-MeasurementRounds",
        "1",
        "-Iterations",
        "1",
        "-WindowsSdkVersion",
        benchmarkWindowsSdkVersion.get(),
        "-BenchmarkComponentRoot",
        referenceBenchmarkComponentDirectory.get().asFile.absolutePath,
        "-ListScenarios",
    )
    outputs.file(benchmarkCatalogDirectory.map { it.file("cppwinrt.txt") })
}

val benchmarkCatalogParity by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies all four executable catalogs against the pinned CsWinRT benchmark sources."
    dependsOn(
        benchmarkKotlinJvmCatalog,
        benchmarkKotlinNativeCatalog,
        benchmarkCsWinRTCatalog,
        benchmarkCppWinRTCatalog,
    )
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        layout.projectDirectory.file("scripts/Test-BenchmarkCatalogParity.ps1").asFile.absolutePath,
        "-ReferenceDirectory",
        layout.projectDirectory.dir("reference").asFile.absolutePath,
        "-KotlinJvmCatalog",
        benchmarkCatalogDirectory.get().file("kotlin-jvm.txt").asFile.absolutePath,
        "-KotlinNativeCatalog",
        benchmarkCatalogDirectory.get().file("kotlin-native.txt").asFile.absolutePath,
        "-CsWinRTCatalog",
        benchmarkCatalogDirectory.get().file("cswinrt.txt").asFile.absolutePath,
        "-CppWinRTCatalog",
        benchmarkCatalogDirectory.get().file("cppwinrt.txt").asFile.absolutePath,
    )
}

// Catalog tasks also build reference executables; their external source inputs are not Gradle inputs.
benchmarkCsWinRTCatalog { outputs.upToDateWhen { false } }
benchmarkCppWinRTCatalog { outputs.upToDateWhen { false } }

benchmarkCppWinRT {
    mustRunAfter("compileKotlinJvm", "linkReleaseExecutableMingwX64")
}
benchmarkKotlinNative {
    mustRunAfter(benchmarkCppWinRT)
}
benchmarkCsWinRT {
    mustRunAfter(benchmarkKotlinNative)
}
benchmarkKotlinJvm {
    mustRunAfter(benchmarkCsWinRT)
}

val benchmarkReport by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Runs every projection benchmark and writes JSON and Markdown comparison reports."
    dependsOn(benchmarkKotlinJvm, benchmarkKotlinNative, benchmarkCsWinRT, benchmarkCppWinRT)
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        layout.projectDirectory.file("scripts/New-BenchmarkReport.ps1").asFile.absolutePath,
        "-ResultsDirectory",
        benchmarkResultsDirectory.get().asFile.absolutePath,
        "-MarkdownOutput",
        benchmarkReportsDirectory.get().file("benchmark-report.md").asFile.absolutePath,
        "-JsonOutput",
        benchmarkReportsDirectory.get().file("benchmark-report.json").asFile.absolutePath,
    )
    outputs.upToDateWhen { false }
}

tasks.register("benchmarkAll") {
    group = "benchmark"
    description = "Runs Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT and generates a comparison report."
    dependsOn(benchmarkReport)
}

// Build once, then measure saved binaries without Gradle/compiler activity in the timed run.
tasks.register("benchmarkSnapshot") {
    group = "benchmark"
    description = "Saves all four runners and their catalogs for isolated, repeatable performance comparisons."
    dependsOn(benchmarkCatalogParity)
    notCompatibleWithConfigurationCache("Copies resolved runner launch configuration into a standalone snapshot")
    doLast {
        val destination = file(providers.gradleProperty("kotlinWinRT.benchmarks.snapshot")
            .getOrElse(layout.buildDirectory.dir("benchmark-snapshot").get().asFile.absolutePath))
        check(!destination.exists()) { "Use a new snapshot directory: $destination" }
        destination.mkdirs()
        fun copyTree(source: File, name: String): File = destination.resolve(name).also { target ->
            check(source.exists()) { "Missing snapshot input: $source" }
            source.copyRecursively(target, overwrite = false)
        }
        val assets = copyTree(referenceBenchmarkComponentDirectory.get().asFile, "assets")
        val jvm = benchmarkKotlinJvm.get()
        val jars = jvm.classpath.files.mapIndexed { index, source ->
            copyTree(source, "jvm/${index.toString().padStart(3, '0')}-${source.name}").absolutePath
        }
        val native = copyTree(layout.buildDirectory.file("bin/mingwX64/releaseExecutable/winrt-benchmarks.exe").get().asFile,
            "native/winrt-benchmarks.exe")
        val cpp = copyTree(file("cppwinrt/bin/x64/Release"), "cppwinrt")
        val cs = copyTree(file("cswinrt/bin/Release/net8.0-windows10.0.19041.0/win-x64"), "cswinrt")
        copyTree(benchmarkCatalogDirectory.get().asFile, "catalogs")
        val properties = jvm.systemProperties + ("kotlin.winrt.runtimeAssetsRoot" to assets.absolutePath)
        val runners = listOf(
            mapOf("name" to "kotlin-native", "executable" to native.absolutePath, "arguments" to emptyList<String>(),
                "environment" to mapOf("KOTLIN_WINRT_RUNTIME_ASSETS_ROOT" to assets.absolutePath), "managedMemory" to true),
            mapOf("name" to "cppwinrt", "executable" to cpp.resolve("cppwinrt-benchmark.exe").absolutePath,
                "arguments" to emptyList<String>(), "environment" to emptyMap<String, String>(), "managedMemory" to false),
            mapOf("name" to "cswinrt", "executable" to cs.resolve("CsWinRTBenchmark.exe").absolutePath,
                "arguments" to emptyList<String>(), "environment" to emptyMap<String, String>(), "managedMemory" to true),
            mapOf("name" to "kotlin-jvm", "executable" to jvm.javaLauncher.get().executablePath.asFile.absolutePath,
                "arguments" to (jvm.jvmArgs + properties.map { (key, value) -> "-D$key=$value" } +
                    listOf("-cp", jars.joinToString(File.pathSeparator), jvm.mainClass.get())),
                "environment" to emptyMap<String, String>(), "managedMemory" to true),
        )
        val revision = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
        val status = providers.exec { commandLine("git", "status", "--porcelain", "--untracked-files=no") }
            .standardOutput.asText.get().trim()
        destination.resolve("snapshot.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(
            mapOf(
                "schemaVersion" to 1, "revision" to revision, "trackedChanges" to status, "runners" to runners,
                "scenarioVersions" to mapOf(
                    "NonAgileObjectPerf.ConstructNonAgileObject" to 2,
                    "NonAgileObjectPerf.ConstructAndQueryNonAgileObject" to 2,
                ),
            ))))
        logger.lifecycle("Benchmark snapshot: ${destination.absolutePath}")
    }
}

windows {
    packageReferences {
        windowsSdk(benchmarkWindowsSdkVersion.get(), includeExtensions = false, generateProjection = false)
        namespace("BenchmarkComponent")
        type("Windows.ApplicationModel.Chat.ChatMessage")
        type("Windows.Storage.FileAttributes")
        type("Windows.System.Power.PowerManager")
        type("Windows.UI.Popups.PopupMenu")
        winmd(referenceBenchmarkComponentWinmd.get().asFile.absolutePath)
    }
    application {
        runtimeAsset(referenceBenchmarkComponentDll.get().asFile.absolutePath)
    }
}
