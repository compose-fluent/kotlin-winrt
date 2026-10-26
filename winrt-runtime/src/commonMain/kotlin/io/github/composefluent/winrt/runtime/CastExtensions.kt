package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/**
 * Kotlin-side equivalent of `.cswinrt/src/WinRT.Runtime/CastExtensions.cs`.
 *
 * The public receiver intentionally stays broad enough for values typed as projected interfaces. The runtime
 * path remains fail-closed to projected WinRT wrappers through `ComWrappersSupport.tryUnwrapObject`, matching
 * CsWinRT's `As<TInterface>(this object value)` plus `TryUnwrapObject` split.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Any.asWinRT(): T {
    if (this is T) {
        return this
    }
    val typeHandle = requireRegisteredWinRTInterfaceTypeHandle(T::class)
    return winRTCast(this, typeHandle, T::class)
}

fun requireRegisteredWinRTInterfaceTypeHandle(type: KClass<*>): WinRTTypeHandle {
    val typeId = WinRTTypeRegistry.findByClass(type)
    val iid = typeId?.iid
    if (typeId?.isWindowsRuntimeType == true && iid != null) {
        return WinRTTypeHandle(typeId.projectedTypeName, iid)
    }
    throw IllegalArgumentException(
        "The target type '${type.qualifiedName}' is not a registered WinRT interface type.",
    )
}

fun <T : Any> winRTCast(
    value: Any,
    typeHandle: WinRTTypeHandle,
    targetType: KClass<T>,
): T {
    val objRef = ComWrappersSupport.tryUnwrapObject(value)
        ?: throw IllegalArgumentException(
            "The source object type ('${value::class.qualifiedName}') is not a projected WinRT type.",
        )

    objRef.use {
        // CsWinRT CastExtensions.As<T> uses ObjectReference.AsInterface<T> once
        // the managed type check fails. Kotlin has no IDynamicInterfaceCastable:
        // a cached wrapper supporting the IID is not necessarily a T. Create
        // the requested projection without replacing the canonical identity RCW.
        // Runtime-class factories can construct a wrapper without probing its IID.
        // Validate the cast before invoking one, matching Runtime2's
        // WindowsRuntimeObjectReference.As -> DerivedTryAsNative contract.
        val wrapper = it.queryInterface(typeHandle.interfaceId).getOrThrow().use { typed ->
            ComWrappersSupport.createRcwForComObject(
                typed.pointer.asRawAddress(), typeHandle, tryUseCache = false,
            )
        }
        if (targetType.isInstance(wrapper)) {
            @Suppress("UNCHECKED_CAST")
            return wrapper as T
        }
        throw IllegalArgumentException(
            "Unable to create a WinRT wrapper for '${typeHandle.projectedTypeName}'.",
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun Any.winrtAs(typeHandle: WinRTTypeHandle): Any {
    if (this is IWinRTObject && primaryTypeHandle == typeHandle) {
        return this
    }

    val objRef = ComWrappersSupport.tryUnwrapObject(this)
        ?: throw IllegalArgumentException(
            "The source object type ('${this::class.qualifiedName}') is not a projected WinRT type.",
        )

    objRef.use {
        return ComWrappersSupport.createRcwForComObject(it.pointer.asRawAddress(), typeHandle)
            ?: throw IllegalArgumentException(
                "Unable to create a WinRT wrapper for '${typeHandle.projectedTypeName}'.",
            )
    }
}
