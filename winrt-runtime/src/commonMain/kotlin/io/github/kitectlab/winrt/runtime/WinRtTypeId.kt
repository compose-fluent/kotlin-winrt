package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

data class WinRtTypeId<T : Any>(
    val kClass: KClass<T>,
    val projectedTypeName: String,
    val interfaceId: Guid? = null,
    val signature: WinRtTypeSignature? = null,
    val helperType: KClass<*>? = null,
    val defaultInterface: KClass<*>? = null,
    val boxedName: String? = null,
    val runtimeClassName: String? = null,
    val isDelegate: Boolean = false,
)

object WinRtTypeRegistry {
    private val byClass = mutableMapOf<KClass<*>, WinRtTypeId<*>>()
    private val byProjectedName = mutableMapOf<String, WinRtTypeId<*>>()

    fun register(typeId: WinRtTypeId<*>) {
        byClass[typeId.kClass] = typeId
        byProjectedName[typeId.projectedTypeName] = typeId
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findByClass(type: KClass<T>): WinRtTypeId<T>? = byClass[type] as? WinRtTypeId<T>

    fun findByProjectedName(projectedTypeName: String): WinRtTypeId<*>? = byProjectedName[projectedTypeName]

    inline fun <reified T : Any> register(
        projectedTypeName: String,
        interfaceId: Guid? = null,
        signature: WinRtTypeSignature? = null,
        helperType: KClass<*>? = null,
        defaultInterface: KClass<*>? = null,
        boxedName: String? = null,
        runtimeClassName: String? = null,
        isDelegate: Boolean = false,
    ): WinRtTypeId<T> =
        WinRtTypeId(
            kClass = T::class,
            projectedTypeName = projectedTypeName,
            interfaceId = interfaceId,
            signature = signature,
            helperType = helperType,
            defaultInterface = defaultInterface,
            boxedName = boxedName,
            runtimeClassName = runtimeClassName,
            isDelegate = isDelegate,
        ).also(::register)

    inline fun <reified T : Any> find(): WinRtTypeId<T>? = findByClass(T::class)

    internal fun clearForTests() {
        byClass.clear()
        byProjectedName.clear()
    }
}
