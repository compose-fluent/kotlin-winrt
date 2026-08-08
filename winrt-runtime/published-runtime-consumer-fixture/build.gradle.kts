import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform") version "2.4.0"
}

val runtimeCoordinate = providers.gradleProperty("kotlinWinRT.test.coordinate").get()
val publicationRepository = file(providers.gradleProperty("kotlinWinRT.test.repository").get()).canonicalFile

configurations.configureEach {
    resolutionStrategy.useGlobalDependencySubstitutionRules = false
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

kotlin {
    jvm()
    mingwX64 {
        binaries.executable {
            entryPoint = "io.github.composefluent.winrt.runtime.consumer.main"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(runtimeCoordinate)
        }
    }
}

fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
    if (sequence.isEmpty()) return true
    if (size < sequence.size) return false
    for (start in 0..size - sequence.size) {
        var matches = true
        for (offset in sequence.indices) {
            if (this[start + offset] != sequence[offset]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return false
}

fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(readBytes())
    .joinToString("") { byte -> "%02x".format(byte) }

fun File.binaryEntries(): Sequence<Pair<String, ByteArray>> = sequence {
    if (isDirectory) {
        walkTopDown()
            .filter(File::isFile)
            .sortedBy { file -> file.relativeTo(this@binaryEntries).invariantSeparatorsPath }
            .forEach { file ->
                yield(file.relativeTo(this@binaryEntries).invariantSeparatorsPath to file.readBytes())
            }
    } else if (extension == "jar" || extension == "klib") {
        ZipFile(this@binaryEntries).use { archive ->
            val entries = buildList {
                val enumeration = archive.entries()
                while (enumeration.hasMoreElements()) {
                    add(enumeration.nextElement())
                }
            }.sortedBy { entry -> entry.name }
            for (entry in entries) {
                if (!entry.isDirectory) {
                    yield(entry.name to archive.getInputStream(entry).use { it.readBytes() })
                }
            }
        }
    } else {
        yield(name to readBytes())
    }
}

fun File.assertMarkersAbsent(description: String, markers: Set<String>, entryFilter: (String) -> Boolean = { true }) {
    val markerBytes = markers.associateWith(String::encodeToByteArray)
    val match = binaryEntries()
        .filter { (entryName, _) -> entryFilter(entryName) }
        .firstNotNullOfOrNull { (entryName, bytes) ->
            markerBytes.entries.firstOrNull { (_, marker) -> bytes.containsSequence(marker) }
                ?.let { (marker, _) -> entryName to marker }
        }
    check(match == null) {
        "Forbidden marker '${match!!.second}' escaped into $description at ${absolutePath}!/${match.first}."
    }
}

private val winrtPluginMarkers = setOf(
    "io.github.composefluent.winrt.compiler",
    "io.github.composefluent.winrt.compiler.callsites",
    "KotlinWinRTCompilerPlugin",
    "WinRTProjectionCallSiteCompilerPlugin",
    "winrt-compiler-plugin",
    "callsite-lowering",
)

private fun String.containsWinRTPluginMarker(): Boolean =
    winrtPluginMarkers.any { marker -> contains(marker, ignoreCase = true) }

fun Configuration.artifactEvidence(): List<String> = incoming.artifacts.artifacts
    .map { artifact -> "${artifact.id.componentIdentifier}|${artifact.file.name}" }
    .sorted()

fun Configuration.assertNoWinRTPluginArtifacts(): List<String> = artifactEvidence().also { evidence ->
    check(evidence.none(String::containsWinRTPluginMarker)) {
        "A WinRT compiler/Gradle plugin artifact escaped into $name: $evidence"
    }
}

fun Configuration.runtimeArtifact(extension: String): Pair<ModuleComponentIdentifier, File> {
    val candidates = incoming.artifacts.artifacts.mapNotNull { artifact ->
        val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
        if (
            component.group == "io.github.compose-fluent" &&
            component.module.startsWith("winrt-runtime") &&
            artifact.file.extension.equals(extension, ignoreCase = true)
        ) {
            component to artifact.file
        } else {
            null
        }
    }.filterNot { (_, file) -> file.name.contains("-cinterop-") }
    check(candidates.size == 1) {
        "Expected one published primary $extension runtime artifact in $name, found ${candidates.map { it.second.name }}."
    }
    return candidates.single()
}

fun Configuration.runtimeCInteropArtifact(cinteropName: String): Pair<ModuleComponentIdentifier, File> {
    val candidates = incoming.artifacts.artifacts.mapNotNull { artifact ->
        val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
        if (
            component.group == "io.github.compose-fluent" &&
            component.module.startsWith("winrt-runtime") &&
            artifact.file.extension.equals("klib", ignoreCase = true) &&
            artifact.file.name.contains("-cinterop-$cinteropName")
        ) {
            component to artifact.file
        } else {
            null
        }
    }
    check(candidates.size == 1) {
        "Expected one published $cinteropName cinterop KLIB in $name, found ${candidates.map { it.second.name }}."
    }
    return candidates.single()
}

fun Configuration.assertPublishedRuntimeGraph() {
    val components = incoming.resolutionResult.allComponents.map { it.id }
    val projectComponents = components.filterIsInstance<ProjectComponentIdentifier>()
    check(projectComponents.all { component ->
        component.projectPath == project.path && component.projectName == project.name
    }) {
        "Project dependency or composite substitution escaped into $name: $projectComponents"
    }
    val modules = components.filterIsInstance<ModuleComponentIdentifier>()
    check(modules.any { it.group == "io.github.compose-fluent" && it.module.startsWith("winrt-runtime") }) {
        "Published runtime module is absent from $name: $modules"
    }
    check(modules.none { component ->
        component.group == "io.github.compose-fluent" &&
            (component.module.contains("compiler-plugin") || component.module.contains("gradle-plugin") || component.module.contains("callsite-lowering"))
    }) {
        "A WinRT plugin dependency escaped into $name: $modules"
    }
}

fun File.assertMatchesPublicationRepository(description: String): File {
    val hash = sha256()
    val publicationMatch = publicationRepository.walkTopDown()
        .filter(File::isFile)
        .firstOrNull { candidate -> candidate.extension == extension && candidate.sha256() == hash }
    check(publicationMatch != null) {
        "$description ${absolutePath} does not match any artifact in isolated repository ${publicationRepository.absolutePath}."
    }
    return publicationMatch
}

fun runProcess(vararg command: String): String {
    val output = ByteArrayOutputStream()
    val errors = ByteArrayOutputStream()
    val process = ProcessBuilder(*command).redirectErrorStream(false).start()
    process.inputStream.copyTo(output)
    process.errorStream.copyTo(errors)
    check(process.waitFor() == 0) {
        "Command failed (${command.joinToString(" ")}): ${errors.toString(Charsets.UTF_8)}"
    }
    return output.toString(Charsets.UTF_8)
}

fun javap(classpath: String, vararg arguments: String): String {
    val executable = File(System.getProperty("java.home"), "bin/javap.exe")
    check(executable.isFile) { "Missing javap at ${executable.absolutePath}." }
    return runProcess(executable.absolutePath, "-classpath", classpath, *arguments)
}

fun dumpKlibIr(klib: File): String {
    val konanDataDirectory = System.getenv("KONAN_DATA_DIR")
        ?.let(::File)
        ?: File(System.getProperty("user.home"), ".konan")
    val executable = konanDataDirectory.resolve(
        "kotlin-native-prebuilt-windows-x86_64-2.4.0/bin/klib.bat",
    )
    check(executable.isFile) { "Missing Kotlin/Native klib tool at ${executable.absolutePath}." }
    return runProcess("cmd", "/c", executable.absolutePath, "dump-ir", klib.absolutePath)
}

val verifyPublishedRuntimeBoundary by tasks.registering {
    group = "verification"
    description = "Inspects the isolated published runtime and plugin-free JVM/mingwX64 consumer boundary."
    dependsOn("compileKotlinJvm", "linkDebugExecutableMingwX64")

    doLast {
        check(!pluginManager.hasPlugin("io.github.compose-fluent.winrt"))
        check(!pluginManager.hasPlugin("io.github.composefluent.winrt"))

        val compilerArguments = buildList {
            tasks.withType<KotlinJvmCompile>().forEach { task -> addAll(task.compilerOptions.freeCompilerArgs.get()) }
            tasks.withType<KotlinNativeCompile>().forEach { task -> addAll(task.compilerOptions.freeCompilerArgs.get()) }
        }
        check(compilerArguments.none(String::containsWinRTPluginMarker)) {
            "WinRT compiler-plugin arguments escaped into the consumer: $compilerArguments"
        }

        val jvmConfiguration = configurations.getByName("jvmCompileClasspath")
        val jvmRuntimeConfiguration = configurations.getByName("jvmRuntimeClasspath")
        val nativeConfiguration = configurations.getByName("mingwX64CompileKlibraries")
        jvmConfiguration.assertPublishedRuntimeGraph()
        jvmRuntimeConfiguration.assertPublishedRuntimeGraph()
        nativeConfiguration.assertPublishedRuntimeGraph()

        val compilerPluginConfigurations = configurations
            .filter { configuration ->
                configuration.isCanBeResolved &&
                    configuration.name.contains("compilerPluginClasspath", ignoreCase = true)
            }
        check(compilerPluginConfigurations.isNotEmpty()) {
            "No Kotlin compiler-plugin classpath was available for inspection."
        }
        val compilerPluginClasspathEvidence = compilerPluginConfigurations.associate { configuration ->
            configuration.name to configuration.assertNoWinRTPluginArtifacts()
        }
        val buildscriptClasspathEvidence = buildscript.configurations
            .getByName("classpath")
            .assertNoWinRTPluginArtifacts()

        val (jvmComponent, runtimeJar) = jvmConfiguration.runtimeArtifact("jar")
        val (nativeComponent, runtimeKlib) = nativeConfiguration.runtimeArtifact("klib")
        val (nativeStringInteropComponent, nativeStringInteropKlib) =
            nativeConfiguration.runtimeCInteropArtifact("winrtString")
        val publishedJar = runtimeJar.assertMatchesPublicationRepository("Resolved JVM runtime JAR")
        val publishedKlib = runtimeKlib.assertMatchesPublicationRepository("Resolved Native runtime KLIB")
        val publishedNativeStringInteropKlib = nativeStringInteropKlib.assertMatchesPublicationRepository(
            "Resolved Native WinRT string cinterop KLIB",
        )
        val nativeStringInteropManifest = nativeStringInteropKlib.binaryEntries()
            .single { (entryName, _) -> entryName == "default/manifest" }
            .second
            .decodeToString()
        check(nativeStringInteropManifest.lineSequence().any { it == "linkerOpts=-lruntimeobject" }) {
            "Published Native WinRT string cinterop does not propagate -lruntimeobject."
        }

        val runtimePlaceholderMarkers = setOf(
            "Lowered while building winrt-runtime",
            "TODO()",
        )
        val ownerPlaceholderMarkers = runtimePlaceholderMarkers + "NotImplementedError"
        runtimeJar.assertMarkersAbsent(
            "published JVM runtime call-site owner",
            ownerPlaceholderMarkers,
        ) { entry -> entry.endsWith("/WinRTProjectionIntrinsic.class") }
        runtimeKlib.assertMarkersAbsent("published Native runtime KLIB", runtimePlaceholderMarkers)

        val consumerClassDirectory = layout.buildDirectory.dir("classes/kotlin/jvm/main").get().asFile
        val consumerClass = consumerClassDirectory.resolve(
            "io/github/composefluent/winrt/runtime/consumer/PublishedRuntimeConsumerKt.class",
        )
        check(consumerClass.isFile) { "Missing JVM consumer bytecode at ${consumerClass.absolutePath}." }
        consumerClass.assertMarkersAbsent("JVM consumer bytecode", ownerPlaceholderMarkers + setOf(
            "KotlinWinRTCompilerPlugin",
            "WinRTProjectionCallSiteCompilerPlugin",
        ))

        val consumerBytecode = javap(
            "${consumerClassDirectory.absolutePath}${File.pathSeparator}${runtimeJar.absolutePath}",
            "-c",
            "-p",
            "io.github.composefluent.winrt.runtime.consumer.PublishedRuntimeConsumerKt",
        )
        listOf(
            "WinRTProjectionIntrinsic.setString:(Lio/github/composefluent/winrt/runtime/ComObjectReference;ILjava/lang/String;)V",
            "WinRTProjectionIntrinsic.getInt32:(Lio/github/composefluent/winrt/runtime/ComObjectReference;I)I",
            "WinRTProjectionIntrinsic.getBoolean:(Lio/github/composefluent/winrt/runtime/ComObjectReference;I)Z",
            "WinRTProjectionIntrinsic.getString:(Lio/github/composefluent/winrt/runtime/ComObjectReference;I)Ljava/lang/String;",
        ).forEach { expectedCall ->
            check(consumerBytecode.contains(expectedCall)) {
                "JVM consumer bytecode does not contain the ordinary published-runtime call $expectedCall."
            }
        }
        val localLoweringMarkers = setOf(
            "WinRTJvmFfmDowncallHandles",
            "MethodHandle.invokeExact:",
            "acquireNativeScalarScratchFrame",
            "NativeScalarScratchFrame",
            "NativeHStringReferenceFrame",
            "acquireInitializedNativeHStringReferenceFrame",
            "ComVtableInvoker",
            "confinedScope",
            "allocateBytes",
        )
        check(localLoweringMarkers.none(consumerBytecode::contains)) {
            "The JVM consumer locally contains lowered runtime implementation instead of ordinary calls."
        }

        val runtimeOwnerBytecode = javap(
            runtimeJar.absolutePath,
            "-v",
            "-p",
            "io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic",
        )
        // JVM omits binary annotation instances whose arguments are all defaults after IR lowering.
        // The compiled owner still has exactly one public method per fixed runtime-owned route.
        val catalogCount = runtimeOwnerBytecode.lineSequence().count { line ->
            line.startsWith("  public final ") && line.endsWith(';')
        }
        check(catalogCount == 14) { "Published runtime-owned catalog count is $catalogCount, expected 14." }
        check("descriptor=" !in runtimeOwnerBytecode && "v4|" !in runtimeOwnerBytecode) {
            "Published runtime-owned CallSites still contain the obsolete serialized descriptor payload."
        }

        val consumerKlib = layout.buildDirectory.dir(
            "classes/kotlin/mingwX64/main/klib/${project.name}",
        ).get().asFile
        check(consumerKlib.isDirectory) { "Missing compiled Native consumer KLIB at ${consumerKlib.absolutePath}." }
        consumerKlib.assertMarkersAbsent(
            "compiled Native consumer KLIB",
            ownerPlaceholderMarkers + winrtPluginMarkers + localLoweringMarkers,
        )
        val nativeConsumerIr = dumpKlibIr(consumerKlib)
        listOf(
            "CALL 'io.github.composefluent.winrt.runtime/WinRTProjectionIntrinsic.setString|setString(io.github.composefluent.winrt.runtime.ComObjectReference;kotlin.Int;kotlin.String){}[0]'",
            "CALL 'io.github.composefluent.winrt.runtime/WinRTProjectionIntrinsic.getInt32|getInt32(io.github.composefluent.winrt.runtime.ComObjectReference;kotlin.Int){}[0]'",
            "CALL 'io.github.composefluent.winrt.runtime/WinRTProjectionIntrinsic.getBoolean|getBoolean(io.github.composefluent.winrt.runtime.ComObjectReference;kotlin.Int){}[0]'",
            "CALL 'io.github.composefluent.winrt.runtime/WinRTProjectionIntrinsic.getString|getString(io.github.composefluent.winrt.runtime.ComObjectReference;kotlin.Int){}[0]'",
            "CALL 'io.github.composefluent.winrt.runtime.consumer/consumeRuntimeOwnedWrappers|consumeRuntimeOwnedWrappers(io.github.composefluent.winrt.runtime.ComObjectReference;kotlin.Int){}[0]'",
        ).forEach { expectedCall ->
            check(nativeConsumerIr.contains(expectedCall)) {
                "Native consumer IR does not contain the ordinary published-runtime call $expectedCall."
            }
        }
        check(localLoweringMarkers.none(nativeConsumerIr::contains)) {
            "The Native consumer KLIB locally contains lowered runtime implementation instead of ordinary calls."
        }

        val nativeExecutable = layout.buildDirectory.dir("bin/mingwX64/debugExecutable").get().asFile
            .walkTopDown()
            .filter(File::isFile)
            .singleOrNull { it.extension.equals("exe", ignoreCase = true) }
        check(nativeExecutable != null) { "Missing linked mingwX64 consumer executable." }
        nativeExecutable.assertMarkersAbsent(
            "linked mingwX64 consumer executable",
            ownerPlaceholderMarkers + setOf(
                "KotlinWinRTCompilerPlugin",
                "WinRTProjectionCallSiteCompilerPlugin",
                "winrt-compiler-plugin",
            ),
        )
        val nativeBytes = nativeExecutable.readBytes()
        check(nativeBytes.size >= 2 && nativeBytes[0] == 'M'.code.toByte() && nativeBytes[1] == 'Z'.code.toByte()) {
            "Native final artifact is not a PE executable."
        }
        check(nativeBytes.containsSequence("published-runtime-only-consumer".encodeToByteArray())) {
            "Native final artifact does not retain the statically referenced consumer boundary."
        }
        check(nativeBytes.containsSequence("WindowsDeleteString".encodeToByteArray())) {
            "Native final artifact does not retain the direct WindowsDeleteString import."
        }

        val reportDirectory = layout.buildDirectory.dir("reports/optimization-19.8").get().asFile
        reportDirectory.mkdirs()
        reportDirectory.resolve("jvm-bytecode.txt").writeText(consumerBytecode)
        reportDirectory.resolve("native-consumer-ir.txt").writeText(nativeConsumerIr)
        reportDirectory.resolve("runtime-owner-bytecode.txt").writeText(runtimeOwnerBytecode)
        reportDirectory.resolve("artifact-evidence.txt").writeText(
            buildString {
                appendLine("coordinate=$runtimeCoordinate")
                appendLine("repository=${publicationRepository.absolutePath}")
                appendLine("jvmComponent=$jvmComponent")
                appendLine("jvmResolved=${runtimeJar.absolutePath}")
                appendLine("jvmPublished=${publishedJar.absolutePath}")
                appendLine("jvmSha256=${runtimeJar.sha256()}")
                appendLine("nativeComponent=$nativeComponent")
                appendLine("nativeResolved=${runtimeKlib.absolutePath}")
                appendLine("nativePublished=${publishedKlib.absolutePath}")
                appendLine("nativeSha256=${runtimeKlib.sha256()}")
                appendLine("nativeStringInteropComponent=$nativeStringInteropComponent")
                appendLine("nativeStringInteropResolved=${nativeStringInteropKlib.absolutePath}")
                appendLine("nativeStringInteropPublished=${publishedNativeStringInteropKlib.absolutePath}")
                appendLine("nativeStringInteropSha256=${nativeStringInteropKlib.sha256()}")
                appendLine("nativeExecutable=${nativeExecutable.absolutePath}")
                appendLine("nativeExecutableSha256=${nativeExecutable.sha256()}")
                appendLine("catalogCount=$catalogCount")
                appendLine("compilerArguments=$compilerArguments")
                appendLine("compilerPluginClasspaths=$compilerPluginClasspathEvidence")
                appendLine("buildscriptClasspath=$buildscriptClasspathEvidence")
                appendLine("jvmRuntimeGraph=${jvmRuntimeConfiguration.artifactEvidence()}")
                appendLine("runtimePlaceholderMarkers=$runtimePlaceholderMarkers")
                appendLine("ownerAndConsumerPlaceholderMarkers=$ownerPlaceholderMarkers")
            },
        )
    }
}
