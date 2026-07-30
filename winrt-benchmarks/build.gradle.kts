import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("io.github.compose-fluent.winrt")
}

description = "Cross-projection WinRT performance comparisons for Kotlin, CsWinRT, and C++/WinRT"

kotlin {
    jvmToolchain(25)
    jvm()
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.composefluent.winrt.benchmarks.main"
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
    .orElse("10000")
val benchmarkFilter = providers.gradleProperty("kotlinWinRT.benchmarks.filter")
val benchmarkWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.benchmarks.windowsSdkVersion")
    .orElse("10.0.26100.0")
val benchmarkResultsDirectory = layout.buildDirectory.dir("results/benchmarks")
val benchmarkReportsDirectory = layout.buildDirectory.dir("reports/benchmarks")

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

val benchmarkKotlinJvm by tasks.registering(JavaExec::class) {
    group = "benchmark"
    description = "Runs the Kotlin/JVM WinRT projection benchmark."
    dependsOn("compileKotlinJvm")
    mainClass.set("io.github.composefluent.winrt.benchmarks.BenchmarkMainKt")
    classpath(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        configurations.named("jvmRuntimeClasspath"),
    )
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Xms512m",
        "-Xmx512m",
    )
    args(benchmarkArguments("kotlin-jvm.jsonl"))
    outputs.upToDateWhen { false }
}

val benchmarkKotlinNative by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Runs the Kotlin/Native mingwX64 WinRT projection benchmark."
    dependsOn("linkReleaseExecutableMingwX64")
    executable(
        layout.buildDirectory
            .file("bin/mingwX64/releaseExecutable/winrt-benchmarks.exe")
            .get()
            .asFile,
    )
    args(benchmarkArguments("kotlin-native.jsonl"))
    outputs.upToDateWhen { false }
}

val benchmarkCsWinRT by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Runs the .NET 8 CsWinRT projection benchmark."
    workingDir(layout.projectDirectory.dir("cswinrt"))
    commandLine(
        "dotnet",
        "run",
        "--project",
        "CsWinRTBenchmark.csproj",
        "--configuration",
        "Release",
        "--no-launch-profile",
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
    )
    benchmarkFilter.orNull?.takeIf(String::isNotBlank)?.let { filter ->
        args("-Filter", filter)
    }
    outputs.upToDateWhen { false }
}

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
