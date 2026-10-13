package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

// The generated metadata contract is shared by the Windows SDK and WinUI XAML compilers.
// Keep the existing classes as the implementation for binary and source compatibility.
typealias XamlTypeDefinition = WinUiXamlTypeDefinition
typealias XamlMemberDefinition = WinUiXamlMemberDefinition
typealias XamlValueTypeDefinition = WinUiXamlValueTypeDefinition
typealias XamlCollectionDefinition = WinUiXamlCollectionDefinition
typealias XamlDictionaryDefinition = WinUiXamlDictionaryDefinition

fun registerXamlTypeDefinition(definition: XamlTypeDefinition) = registerWinUiXamlTypeDefinition(definition)

fun registerXamlProjectedTypeDefinition(definition: XamlTypeDefinition) = registerWinUiXamlProjectedTypeDefinition(definition)

fun <T : Enum<T>> registerXamlEnumType(type: KClass<T>, name: String, entries: Array<T>) =
    registerWinUiXamlEnumType(type, name, entries)
