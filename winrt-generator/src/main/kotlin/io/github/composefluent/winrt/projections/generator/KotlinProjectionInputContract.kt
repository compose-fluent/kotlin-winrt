package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataModelCodec
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.runtime.WINRT_PROJECTION_RUNTIME_ABI_VERSION
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat

/**
 * Inputs shared by reference and implementation planning, following cswinrt/main.cpp and
 * code_writers.h's reference_projection boundary. This is deliberately a conservative input
 * identity, NOT a Kotlin binary ABI hash: the complete metadata model includes ABI slot order,
 * attributes and supplemental declarations. No generator/runtime implementation bytes enter it.
 */
class KotlinProjectionInputContract private constructor(val descriptor: String) {
    val fingerprint: String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(descriptor.toByteArray(Charsets.UTF_8)),
    )

    companion object {
        fun create(
            model: WinRTMetadataModel,
            sourceModel: WinRTMetadataModel,
            context: WinRTMetadataProjectionContext,
            suppressedTypes: Set<String>,
            suppressedAdditions: Set<String>,
            supportOwner: String,
            emitJvmHost: Boolean,
        ): KotlinProjectionInputContract {
            val fields = sortedMapOf<String, String>()
            fun field(name: String, value: Any) { fields[name] = value.toString() }
            fun names(name: String, values: Set<String>) {
                // Encode each entry independently; delimiters inside names cannot alias a list.
                field(name, values.sorted().joinToString(",") { encode(it) })
            }
            field("projectionShapeVersion", KOTLIN_PROJECTION_SHAPE_VERSION)
            field("runtimeAbiVersion", WINRT_PROJECTION_RUNTIME_ABI_VERSION)
            field("compilerProtocolVersion", WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION)
            field("model", WinRTMetadataModelCodec.fingerprint(model))
            field("sourceModel", WinRTMetadataModelCodec.fingerprint(sourceModel))
            // Source locations and output directories are transport details. Source presence
            // still changes source-addition behavior and must remain part of the contract.
            field("hasSources", context.sources.isNotEmpty())
            names("include", context.include)
            names("exclude", context.exclude)
            names("excludedTypes", context.excludedTypes)
            names("additionExclude", context.additionExclude)
            names("inaccessibleDependencyTypes", context.inaccessibleDependencyTypes)
            field("target", context.target.name)
            field("xamlFramework", context.xamlFramework.name)
            field("component", context.component)
            field("internal", context.internal)
            field("embedded", context.embedded)
            field("publicEnums", context.publicEnums)
            field("publicExclusiveTo", context.publicExclusiveTo)
            field("idicExclusiveTo", context.idicExclusiveTo)
            field("partialFactory", context.partialFactory)
            // verbose is diagnostic only, unlike the declaration policy above.
            names("suppressedTypes", suppressedTypes)
            names("suppressedAdditions", suppressedAdditions)
            field("supportOwner", supportOwner)
            field("emitJvmHost", emitJvmHost)
            return KotlinProjectionInputContract(buildString {
                appendLine("kotlin-winrt-projection-input-contract-v1")
                fields.forEach { (name, value) -> append(name).append('\t').append(encode(value)).append('\n') }
            })
        }

        private fun encode(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}
