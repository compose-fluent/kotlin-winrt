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

    private val stableFunctionName: String = "callSite_${stableSignatureHash()}"

    /**
     * The physical call identity is deliberately separate from the typed wrapper identity.
     * Public Kotlin types, IID selection, nullability and ownership stay in [descriptor] and the
     * generated wrapper; only the ordered ABI carriers identify shared physical call support.
     * Typed wrapper placement uses its own identity so pointer-heavy shapes do not form hot shards.
     */
    val platformShape: KotlinProjectionPlatformCallShape =
        KotlinProjectionPlatformCallShape.from(descriptor)

    private val runtimeOwnedKey: WinRTProjectionCallSiteCatalogKey =
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

    val functionName: String
        get() = stableFunctionName

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


    fun runtimeOwnedCatalogKey(): WinRTProjectionCallSiteCatalogKey = runtimeOwnedKey
}

/**
 * Canonical physical shape of a WinRT vtable call within one generated artifact and target.
 *
 * This is a generator-side placement key, not a marshaling recipe.  The vtable slot remains a
 * runtime argument, so it is intentionally absent.  Likewise, public type names, IIDs and
 * ownership are absent: those decisions belong to each typed wrapper.  HSTRING is kept as a
 * distinct carrier because the Native recipe transport expands it to address plus length words,
 * while an ordinary address is passed as one word.
 */
internal data class KotlinProjectionPlatformCallShape(
    val targetAbi: String,
    val arguments: List<KotlinProjectionPlatformCallArgument>,
    val returnCarrier: KotlinProjectionPlatformCallCarrier = KotlinProjectionPlatformCallCarrier.INT32,
) {
    init {
        require(targetAbi.isNotBlank()) { "A platform call shape requires a target ABI." }
        require(arguments.isNotEmpty()) {
            "A platform call shape must include the COM instance carrier."
        }
        require(arguments.first() == KotlinProjectionPlatformCallArgument.INSTANCE) {
            "A platform call shape must start with the COM instance carrier."
        }
    }

    /** Stable, human-readable descriptor used for sharding and generated names. */
    val canonicalDescriptor: String = buildString {
        append(targetAbi)
        append('|')
        arguments.joinTo(this, separator = ",") { argument -> argument.canonicalDescriptor }
        append('|')
        append(returnCarrier.name)
    }

    val stableHash: String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalDescriptor.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    companion object {
        private const val WINDOWS_X64_WINRT_ABI = "windows-x64-winrt-com-vtable"

        fun from(descriptor: WinRTProjectionCallSiteDescriptor): KotlinProjectionPlatformCallShape {
            val arguments = buildList {
                add(KotlinProjectionPlatformCallArgument.INSTANCE)
                descriptor.slots.forEach { slot ->
                    when (slot.direction) {
                        WinRTProjectionCallSiteSlotDirection.IN,
                        WinRTProjectionCallSiteSlotDirection.PASS_ARRAY,
                        WinRTProjectionCallSiteSlotDirection.FILL_ARRAY,
                        -> slot.recipe.platformInputArguments().forEach(::add)

                        WinRTProjectionCallSiteSlotDirection.REF,
                        WinRTProjectionCallSiteSlotDirection.OUT,
                        WinRTProjectionCallSiteSlotDirection.CALLER_OUT,
                        -> repeat(slot.functionParameterCount) {
                            add(KotlinProjectionPlatformCallArgument.pointer())
                        }

                        WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY,
                        WinRTProjectionCallSiteSlotDirection.RETURN,
                        -> slot.abiCarriers.forEach { add(KotlinProjectionPlatformCallArgument.pointer()) }
                    }
                }
            }
            return KotlinProjectionPlatformCallShape(
                targetAbi = WINDOWS_X64_WINRT_ABI,
                arguments = arguments,
            )
        }
    }
}

internal data class KotlinProjectionPlatformCallArgument(
    val carrier: KotlinProjectionPlatformCallCarrier,
    val wordCount: Int = 1,
) {
    init {
        require(wordCount > 0) { "A platform call argument must occupy at least one word." }
        require(carrier != KotlinProjectionPlatformCallCarrier.HSTRING || wordCount == 2) {
            "An HSTRING platform argument must retain its two-word Native transport shape."
        }
    }

    val canonicalDescriptor: String
        get() = "${carrier.name}:$wordCount"

    companion object {
        val INSTANCE = KotlinProjectionPlatformCallArgument(
            carrier = KotlinProjectionPlatformCallCarrier.ADDRESS,
        )

        fun pointer(): KotlinProjectionPlatformCallArgument = INSTANCE

        fun hstring(): KotlinProjectionPlatformCallArgument = KotlinProjectionPlatformCallArgument(
            carrier = KotlinProjectionPlatformCallCarrier.HSTRING,
            wordCount = 2,
        )
    }
}

internal enum class KotlinProjectionPlatformCallCarrier {
    ADDRESS,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
    HSTRING,
}

private fun WinRTProjectionCallSiteRecipe.platformInputArguments(): List<KotlinProjectionPlatformCallArgument> =
    when (kind) {
        WinRTProjectionCallSiteRecipeKind.HSTRING -> listOf(KotlinProjectionPlatformCallArgument.hstring())
        else -> abiCarriers.map { carrier ->
            KotlinProjectionPlatformCallArgument(carrier.platformCarrier())
        }
    }

private fun WinRTProjectionCallSiteAbiCarrier.platformCarrier(): KotlinProjectionPlatformCallCarrier =
    when (this) {
        WinRTProjectionCallSiteAbiCarrier.ADDRESS -> KotlinProjectionPlatformCallCarrier.ADDRESS
        WinRTProjectionCallSiteAbiCarrier.INT8 -> KotlinProjectionPlatformCallCarrier.INT8
        WinRTProjectionCallSiteAbiCarrier.INT16 -> KotlinProjectionPlatformCallCarrier.INT16
        WinRTProjectionCallSiteAbiCarrier.INT32 -> KotlinProjectionPlatformCallCarrier.INT32
        WinRTProjectionCallSiteAbiCarrier.INT64 -> KotlinProjectionPlatformCallCarrier.INT64
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> KotlinProjectionPlatformCallCarrier.FLOAT32
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> KotlinProjectionPlatformCallCarrier.FLOAT64
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
