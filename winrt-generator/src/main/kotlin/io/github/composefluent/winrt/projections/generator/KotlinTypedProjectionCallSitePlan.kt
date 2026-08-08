package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteCatalogKey
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteMetadata
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultKind
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionParameterMetadata
import java.security.MessageDigest

/**
 * The complete static signature of one generated projection call site.
 *
 * This is deliberately not split into result families. WinMD type bindings and
 * their ordered marshaler slots compose this plan before occurrence counting.
 */
internal data class KotlinTypedProjectionCallSitePlan(
    val descriptor: WinRTProjectionCallSiteDescriptor,
    val returnType: TypeName,
    val parameters: List<KotlinTypedProjectionCallSiteParameter>,
    val resultKind: WinRTProjectionCallSiteResultKind = WinRTProjectionCallSiteResultKind.INFER,
    val returnAbiType: String = "",
) {
    init {
        require(descriptor.functionParameterCount == parameters.size) {
            "A typed WinRT call-site signature must expose every ordered marshaler input/support parameter."
        }
    }

    val metadata: WinRTProjectionCallSiteMetadata = WinRTProjectionCallSiteMetadata(
        hResultPolicy = descriptor.hResultPolicy,
        resultKind = resultKind,
        returnAbiType = returnAbiType.ifEmpty(returnType::jvmErasedUnsignedTypeName),
        parameters = parameters.map { parameter ->
            WinRTProjectionParameterMetadata(
                direction = parameter.direction,
                abiType = parameter.abiType.ifEmpty(parameter.type::jvmErasedUnsignedTypeName),
            )
        },
    )

    val functionName: String
        get() = "callSite_${stableSignatureHash()}"

    private fun stableSignatureHash(): String {
        val signature = buildString {
            append(metadata)
            append('|')
            append(returnType)
            parameters.forEach { parameter ->
                append('|')
                append(parameter.type)
                append('|')
                append(parameter.direction.name)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }


    fun runtimeOwnedCatalogKey(): WinRTProjectionCallSiteCatalogKey =
        WinRTProjectionCallSiteCatalogKey(
            metadata = metadata,
            jvmMethodDescriptor = buildString {
                append('(')
                append(COM_OBJECT_REFERENCE_JVM_DESCRIPTOR)
                append('I')
                parameters.forEach { parameter -> append(parameter.type.jvmDescriptor()) }
                append(')')
                append(returnType.jvmDescriptor(isReturnType = true))
            },
        )
}

internal data class KotlinTypedProjectionCallSiteParameter(
    val type: TypeName,
    val direction: WinRTProjectionCallSiteParameterDirection =
        WinRTProjectionCallSiteParameterDirection.IN,
    val abiType: String = "",
)

internal data class KotlinTypedProjectionCallSiteInvocation(
    val plan: KotlinTypedProjectionCallSitePlan,
    val arguments: List<CodeBlock>,
) {
    init {
        require(arguments.size == plan.parameters.size) {
            "A typed WinRT call-site invocation must supply one expression for every composed marshaler slot."
        }
    }
}

private fun TypeName.jvmDescriptor(isReturnType: Boolean = false): String {
    val type = if (this is WildcardTypeName) outTypes.singleOrNull() ?: inTypes.singleOrNull() ?: ANY else this
    if (type == UNIT && isReturnType) return "V"
    val raw = (type as? ParameterizedTypeName)?.rawType ?: type as? ClassName
        ?: error("Cannot form a JVM descriptor for generated WinRT call-site type '$type'.")
    return when (raw.canonicalName) {
        "kotlin.Boolean" -> "Z"
        "kotlin.Byte", "kotlin.UByte" -> "B"
        "kotlin.Short", "kotlin.UShort" -> "S"
        "kotlin.Char" -> "C"
        "kotlin.Int", "kotlin.UInt" -> "I"
        "kotlin.Long", "kotlin.ULong" -> "J"
        "kotlin.Float" -> "F"
        "kotlin.Double" -> "D"
        "kotlin.Unit" -> "Lkotlin/Unit;"
        "kotlin.Any" -> "Ljava/lang/Object;"
        "kotlin.String" -> "Ljava/lang/String;"
        "kotlin.Array" -> "[${(type as ParameterizedTypeName).typeArguments.single().jvmDescriptor()}"
        "kotlin.BooleanArray" -> "[Z"
        "kotlin.ByteArray", "kotlin.UByteArray" -> "[B"
        "kotlin.ShortArray", "kotlin.UShortArray" -> "[S"
        "kotlin.CharArray" -> "[C"
        "kotlin.IntArray", "kotlin.UIntArray" -> "[I"
        "kotlin.LongArray", "kotlin.ULongArray" -> "[J"
        "kotlin.FloatArray" -> "[F"
        "kotlin.DoubleArray" -> "[D"
        else -> "L${raw.canonicalName.replace('.', '/')};"
    }
}

private const val COM_OBJECT_REFERENCE_JVM_DESCRIPTOR =
    "Lio/github/composefluent/winrt/runtime/ComObjectReference;"

/** Kotlin unsigned inline types erase to the same JVM descriptor as their signed storage type. */
private fun TypeName.jvmErasedUnsignedTypeName(): String {
    val raw = (this as? ParameterizedTypeName)?.rawType ?: this as? ClassName ?: return ""
    return raw.canonicalName.takeIf { name -> name in JVM_UNSIGNED_INLINE_TYPE_NAMES }.orEmpty()
}

private val JVM_UNSIGNED_INLINE_TYPE_NAMES =
    setOf("kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong")
