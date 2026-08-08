@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)
@file:Suppress("DEPRECATION")

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Resolves descriptor text and backend IR types to one closed, backend-specific identity. */
internal class WinRTProjectedTypeCanonicalizer(
    private val pluginContext: IrPluginContext,
) {
    fun canonicalize(type: IrType): String? = canonicalizeIrType(type, emptyMap())?.render()

    fun canonicalize(typeText: String): String? {
        val parsed = ProjectedTypeTextParser(typeText.canonicalProjectedTypeText()).parse() ?: return null
        return canonicalizeParsedType(parsed)?.render()
    }

    private fun canonicalizeParsedType(type: CanonicalProjectedType): CanonicalProjectedType? {
        val arguments = type.arguments.map { argument ->
            canonicalizeParsedType(argument) ?: return null
        }
        val symbol = referenceClassifier(type.classifier)
        val resolved = when (symbol) {
            is IrTypeAliasSymbol -> {
                val parameters = symbol.owner.typeParameters
                if (parameters.size != arguments.size) return null
                canonicalizeIrType(
                    symbol.owner.expandedType,
                    parameters.mapIndexed { index, parameter -> parameter.symbol to arguments[index] }.toMap(),
                ) ?: return null
            }
            is IrClassSymbol -> CanonicalProjectedType(
                classifier = canonicalClassName(
                    symbol.owner.fqNameWhenAvailable ?: FqName(type.classifier),
                ),
                arguments = arguments,
            )
            null -> CanonicalProjectedType(
                classifier = canonicalClassName(FqName(type.classifier)),
                arguments = arguments,
            )
            else -> return null
        }
        return resolved.makeNullableIf(type.nullable)
    }

    private fun canonicalizeIrType(
        type: IrType,
        substitutions: Map<IrTypeParameterSymbol, CanonicalProjectedType>,
    ): CanonicalProjectedType? {
        val simple = type as? IrSimpleType ?: return null
        val resolved = when (val classifier = simple.classifier) {
            is IrClassSymbol -> {
                val fqName = classifier.owner.fqNameWhenAvailable ?: return null
                val arguments = simple.arguments.map { argument ->
                    argument.typeOrNull?.let { canonicalizeIrType(it, substitutions) } ?: return null
                }
                CanonicalProjectedType(
                    classifier = canonicalClassName(fqName),
                    arguments = arguments,
                )
            }
            is IrTypeParameterSymbol -> {
                if (simple.arguments.isNotEmpty()) return null
                substitutions[classifier] ?: return null
            }
            else -> return null
        }
        return resolved.makeNullableIf(simple.isNullable())
    }

    private fun referenceClassifier(classifier: String): IrSymbol? {
        val fqName = runCatching { FqName(classifier) }.getOrNull() ?: return null
        val segments = fqName.pathSegments().map(Name::asString)
        val candidates = buildList {
            add(ClassId.topLevel(fqName))
            for (packageSize in segments.lastIndex downTo 0) {
                add(
                    ClassId(
                        packageFqName = FqName(segments.take(packageSize).joinToString(".")),
                        relativeClassName = FqName(segments.drop(packageSize).joinToString(".")),
                        isLocal = false,
                    ),
                )
            }
        }
        return candidates.distinct().firstNotNullOfOrNull { classId ->
            runCatching { pluginContext.referenceClassifier(classId) }.getOrNull()
        }
    }

    private fun canonicalClassName(fqName: FqName): String =
        JavaToKotlinClassMap.mapJavaToKotlinIncludingClassMapping(fqName)
            ?.asSingleFqName()
            ?.asString()
            ?: fqName.asString()
}

private data class CanonicalProjectedType(
    val classifier: String,
    val arguments: List<CanonicalProjectedType> = emptyList(),
    val nullable: Boolean = false,
) {
    fun makeNullableIf(nullable: Boolean): CanonicalProjectedType =
        if (nullable && !this.nullable) copy(nullable = true) else this

    fun render(): String = buildString {
        append(classifier)
        if (arguments.isNotEmpty()) arguments.joinTo(this, separator = ",", prefix = "<", postfix = ">") {
            it.render()
        }
        if (nullable) append('?')
    }
}

private class ProjectedTypeTextParser(
    private val text: String,
) {
    private var index: Int = 0

    fun parse(): CanonicalProjectedType? {
        val result = parseType() ?: return null
        return result.takeIf { index == text.length }
    }

    private fun parseType(): CanonicalProjectedType? {
        val classifierStart = index
        while (index < text.length && text[index].isProjectedClassifierCharacter()) index++
        if (classifierStart == index) return null
        val classifier = text.substring(classifierStart, index)
        val arguments = if (index < text.length && text[index] == '<') {
            index++
            buildList {
                while (true) {
                    add(parseType() ?: return null)
                    when (text.getOrNull(index)) {
                        ',' -> index++
                        '>' -> {
                            index++
                            break
                        }
                        else -> return null
                    }
                }
            }
        } else {
            emptyList()
        }
        val nullable = index < text.length && text[index] == '?'
        if (nullable) index++
        return CanonicalProjectedType(classifier, arguments, nullable)
    }
}

private fun Char.isProjectedClassifierCharacter(): Boolean =
    isLetterOrDigit() || this == '_' || this == '.' || this == '$'

internal fun String.canonicalProjectedTypeText(): String {
    val aliases = mapOf(
        "Any" to "kotlin.Any",
        "Array" to "kotlin.Array",
        "Boolean" to "kotlin.Boolean",
        "Byte" to "kotlin.Byte",
        "Char" to "kotlin.Char",
        "Double" to "kotlin.Double",
        "Float" to "kotlin.Float",
        "Int" to "kotlin.Int",
        "Long" to "kotlin.Long",
        "Short" to "kotlin.Short",
        "String" to "kotlin.String",
        "UByte" to "kotlin.UByte",
        "UInt" to "kotlin.UInt",
        "ULong" to "kotlin.ULong",
        "UShort" to "kotlin.UShort",
        "Iterable" to "kotlin.collections.Iterable",
        "Iterator" to "kotlin.collections.Iterator",
        "List" to "kotlin.collections.List",
        "Map" to "kotlin.collections.Map",
        "MutableList" to "kotlin.collections.MutableList",
        "MutableMap" to "kotlin.collections.MutableMap",
        "Set" to "kotlin.collections.Set",
    )
    return replace("`", "").replace(Regex("\\s+"), "")
        .replace(Regex("(?<![A-Za-z0-9_.])([A-Za-z][A-Za-z0-9_]*)")) { match ->
            aliases[match.value] ?: match.value
        }
}

internal fun String.closedTypeArgumentIdentities(): List<String>? =
    ProjectedTypeTextParser(replace(Regex("\\s+"), ""))
        .parse()
        ?.arguments
        ?.map(CanonicalProjectedType::render)
