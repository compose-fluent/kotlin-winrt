package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION
import io.github.composefluent.winrt.runtime.WINRT_PROJECTION_RUNTIME_ABI_VERSION
import kotlinx.serialization.json.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.security.MessageDigest

/** A compiled contract is distinct from WinMD input and generator implementation fingerprints. */
@CacheableTask
abstract class GenerateWinRTJvmContractTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceClasses: DirectoryProperty
    @get:Input abstract val kotlinCompilerVersion: Property<String>
    @get:Input abstract val owner: Property<String>
    @get:Input abstract val producerVersion: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction fun generate() = writeProjectionBinaryContract(
        outputFile.get().asFile, "jvm", kotlinCompilerVersion.get(), owner.get(), producerVersion.get(),
        projectionAbiInventory(referenceClasses.get().asFile),
    )
}

internal fun writeProjectionBinaryContract(
    output: File, platform: String, compiler: String, owner: String, producerVersion: String,
    declarations: Map<String, String>,
) {
    val contract = buildJsonObject {
        put("schemaVersion", 1)
        put("platform", platform)
        put("kotlinCompilerVersion", compiler)
        put("projectionShapeVersion", CURRENT_PROJECTION_SHAPE_VERSION)
        put("projectionRuntimeAbiVersion", WINRT_PROJECTION_RUNTIME_ABI_VERSION)
        put("projectionCompilerProtocolVersion", WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION)
        put("declarations", JsonObject(declarations.toSortedMap().mapValues { JsonPrimitive(it.value) }))
    }
    val document = buildJsonObject {
        put("projectionOwner", owner)
        put("producerVersion", producerVersion)
        put("fingerprint", projectionContractDigest(contract.toString()))
        put("contract", contract)
    }
    GradleFileOperations.writeStringIfChanged(output.toPath(),
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), document) + "\n")
}

internal fun projectionContractDigest(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
