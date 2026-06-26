package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

data class WinRTTypeId<T : Any>(
    val kClass: KClass<T>,
    val projectedTypeName: String,
    val guid: Guid? = null,
    val iid: Guid? = null,
    val signature: String? = null,
    val enumAbiValue: ((T) -> Int)? = null,
    /** All enum constants for this type, populated at registration time.  Non-null iff [enumAbiValue] is non-null. */
    val enumEntries: Array<T>? = null,
    /** Whether this type is a Kotlin exception / Error subtype, registered explicitly at registration time. */
    val isExceptionType: Boolean = false,
    val helperType: KClass<*>? = null,
    val defaultInterface: KClass<*>? = null,
    val boxedName: String? = null,
    val runtimeClassName: String? = null,
    val vftblType: KClass<*>? = null,
    val isDelegate: Boolean = false,
    val isRuntimeClass: Boolean = false,
    val isWindowsRuntimeType: Boolean = false,
    val aliases: Set<String> = emptySet(),
)

object WinRTTypeRegistry {
    private val byClass = mutableMapOf<KClass<*>, WinRTTypeId<*>>()
    private val byName = mutableMapOf<String, WinRTTypeId<*>>()

    fun register(typeId: WinRTTypeId<*>) {
        byClass[typeId.kClass] = typeId
        index(typeId.projectedTypeName, typeId)
        index(typeId.runtimeClassName, typeId)
        index(typeId.boxedName, typeId)
        typeId.aliases.forEach { alias ->
            index(alias, typeId)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findByClass(type: KClass<T>): WinRTTypeId<T>? = byClass[type] as? WinRTTypeId<T>

    fun findByName(name: String): WinRTTypeId<*>? = byName[name]

    fun findByProjectedName(projectedTypeName: String): WinRTTypeId<*>? = findByName(projectedTypeName)

    fun registerAlias(
        type: KClass<*>,
        alias: String,
    ) {
        val existing = byClass[type] ?: return
        @Suppress("UNCHECKED_CAST")
        register(
            (existing as WinRTTypeId<Any>).copy(
                aliases = existing.aliases + alias,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> update(
        type: KClass<T>,
        transform: (WinRTTypeId<T>?) -> WinRTTypeId<T>,
    ): WinRTTypeId<T> {
        val updated = transform(findByClass(type))
        register(updated)
        return updated
    }

    inline fun <reified T : Any> register(
        projectedTypeName: String,
        guid: Guid? = null,
        iid: Guid? = null,
        signature: String? = null,
        noinline enumAbiValue: ((T) -> Int)? = null,
        enumEntries: Array<T>? = null,
        isExceptionType: Boolean = false,
        helperType: KClass<*>? = null,
        defaultInterface: KClass<*>? = null,
        boxedName: String? = null,
        runtimeClassName: String? = null,
        vftblType: KClass<*>? = null,
        isDelegate: Boolean = false,
        isRuntimeClass: Boolean = false,
        isWindowsRuntimeType: Boolean = false,
        aliases: Set<String> = emptySet(),
    ): WinRTTypeId<T> =
        WinRTTypeId(
            kClass = T::class,
            projectedTypeName = projectedTypeName,
            guid = guid,
            iid = iid,
            signature = signature,
            enumAbiValue = enumAbiValue,
            enumEntries = enumEntries,
            isExceptionType = isExceptionType,
            helperType = helperType,
            defaultInterface = defaultInterface,
            boxedName = boxedName,
            runtimeClassName = runtimeClassName,
            vftblType = vftblType,
            isDelegate = isDelegate,
            isRuntimeClass = isRuntimeClass,
            isWindowsRuntimeType = isWindowsRuntimeType,
            aliases = aliases,
        ).also(::register)

    inline fun <reified T : Any> find(): WinRTTypeId<T>? = findByClass(T::class)

    internal fun clearForTests() {
        byClass.clear()
        byName.clear()
    }

    private fun index(
        name: String?,
        typeId: WinRTTypeId<*>,
    ) {
        if (!name.isNullOrBlank()) {
            byName[name] = typeId
        }
    }
}
