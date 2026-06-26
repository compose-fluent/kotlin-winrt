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
    val typeHandle = requireRegisteredWinRTInterfaceTypeHandle(T::class)
    if (this is T) {
        return this
    }
    if (this is IWinRTObject && primaryTypeHandle == typeHandle) {
        return this as T
    }
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
        val wrapper = ComWrappersSupport.createRcwForComObject(it.getRefPointer().asRawAddress(), typeHandle)
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
        return ComWrappersSupport.createRcwForComObject(it.getRefPointer().asRawAddress(), typeHandle)
            ?: throw IllegalArgumentException(
                "Unable to create a WinRT wrapper for '${typeHandle.projectedTypeName}'.",
            )
    }
}
