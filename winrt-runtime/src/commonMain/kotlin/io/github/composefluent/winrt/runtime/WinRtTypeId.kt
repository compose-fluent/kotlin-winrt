package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

data class WinRtTypeId<T : Any>(
    val kClass: KClass<T>,
    val projectedTypeName: String = kClass.qualifiedName ?: kClass.simpleName ?: "<anonymous>",
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

object WinRtTypeRegistry {
    private val byClass = mutableMapOf<KClass<*>, WinRtTypeId<*>>()
    private val byName = mutableMapOf<String, WinRtTypeId<*>>()

    fun register(typeId: WinRtTypeId<*>) {
        byClass[typeId.kClass] = typeId
        index(typeId.projectedTypeName, typeId)
        index(typeId.runtimeClassName, typeId)
        index(typeId.boxedName, typeId)
        index(typeId.kClass.qualifiedName, typeId)
        typeId.aliases.forEach { alias ->
            index(alias, typeId)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findByClass(type: KClass<T>): WinRtTypeId<T>? = byClass[type] as? WinRtTypeId<T>

    fun findByName(name: String): WinRtTypeId<*>? = byName[name]

    fun findByProjectedName(projectedTypeName: String): WinRtTypeId<*>? = findByName(projectedTypeName)

    fun registerAlias(
        type: KClass<*>,
        alias: String,
    ) {
        val existing = byClass[type] ?: return
        @Suppress("UNCHECKED_CAST")
        register(
            (existing as WinRtTypeId<Any>).copy(
                aliases = existing.aliases + alias,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> update(
        type: KClass<T>,
        transform: (WinRtTypeId<T>?) -> WinRtTypeId<T>,
    ): WinRtTypeId<T> {
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
    ): WinRtTypeId<T> =
        WinRtTypeId(
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

    inline fun <reified T : Any> find(): WinRtTypeId<T>? = findByClass(T::class)

    internal fun clearForTests() {
        byClass.clear()
        byName.clear()
    }

    private fun index(
        name: String?,
        typeId: WinRtTypeId<*>,
    ) {
        if (!name.isNullOrBlank()) {
            byName[name] = typeId
        }
    }
}
