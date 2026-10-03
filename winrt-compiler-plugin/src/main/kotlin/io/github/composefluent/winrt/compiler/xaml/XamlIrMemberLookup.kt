package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.winRTCollectionAbiNameForKotlinType
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*

/** CsWinRT WinRTTypeWriter.GetMember walks application bases before the SDK boundary.
 * Collection Count follows the existing metadata collection mapping to Kotlin size.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlIrProperty(klass: IrClass, name: String, visited: MutableSet<IrClass> = mutableSetOf()): IrProperty? {
    if (!visited.add(klass)) return null
    val projected = if (name == "Count" && winRTCollectionAbiNameForKotlinType(klass.fqNameWhenAvailable?.asString().orEmpty()) != null)
        "size" else name.replaceFirstChar(Char::lowercase)
    return klass.properties.firstOrNull { it.name.asString() == name } ?:
        klass.properties.firstOrNull { it.name.asString() == projected } ?:
        klass.superTypes.mapNotNull { it.classOrNull?.owner }.firstNotNullOfOrNull { xamlIrProperty(it, name, visited) }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlIrFunctions(klass: IrClass, name: String, visited: MutableSet<IrClass> = mutableSetOf()): List<IrSimpleFunction> {
    if (!visited.add(klass)) return emptyList()
    val functions = klass.functions.filter { it.name.asString() == name }.toList().ifEmpty {
        klass.functions.filter { it.name.asString() == name.replaceFirstChar(Char::lowercase) }.toList()
    }
    return functions.ifEmpty {
        klass.superTypes.mapNotNull { it.classOrNull?.owner }.firstNotNullOfOrNull {
            xamlIrFunctions(it, name, visited).takeIf(List<*>::isNotEmpty)
        }.orEmpty()
    }
}

/** XamlCompiler closes generic path steps against their actual declaring type. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlIrMemberType(receiverType: IrType, owner: IrClass, memberType: IrType): IrType =
    (receiverType as? IrSimpleType)?.let {
        AbstractIrTypeSubstitutor.forSuperClass(owner.symbol, it)?.substitute(memberType)
    } ?: memberType
