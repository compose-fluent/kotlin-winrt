package io.github.composefluent.winrt.runtime

@Suppress("UNCHECKED_CAST")
internal actual fun largeStringTestMethod() = WinRTInspectableMethodDefinition(
    ComMethodSignature.of(*Array(11) { ComAbiValueKind.Pointer }),
    managedHandler = { managed, args -> (managed as (List<Any?>) -> Int)(args) },
)
