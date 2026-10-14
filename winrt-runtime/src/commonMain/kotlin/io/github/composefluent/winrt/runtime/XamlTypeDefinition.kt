package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

// The generated metadata contract is shared by the Windows SDK and WinUI XAML compilers.
// Keep the existing classes as the implementation for binary and source compatibility.
typealias XamlTypeDefinition = WinUIXamlTypeDefinition
typealias XamlMemberDefinition = WinUIXamlMemberDefinition
typealias XamlValueTypeDefinition = WinUIXamlValueTypeDefinition
typealias XamlCollectionDefinition = WinUIXamlCollectionDefinition
typealias XamlDictionaryDefinition = WinUIXamlDictionaryDefinition

fun registerXamlTypeDefinition(definition: XamlTypeDefinition) = registerWinUIXamlTypeDefinition(definition)

fun registerXamlProjectedTypeDefinition(definition: XamlTypeDefinition) = registerWinUIXamlProjectedTypeDefinition(definition)

fun <T : Enum<T>> registerXamlEnumType(type: KClass<T>, name: String, entries: Array<T>) =
    registerWinUIXamlEnumType(type, name, entries)
