package io.github.kitectlab.winrt.runtime

class AgileReferenceTyped<T : Any>(
    private val typeHandle: WinRtTypeHandle,
    instance: ComObjectReference?,
) : AutoCloseable {
    private val reference = AgileReference(instance)

    @Suppress("UNCHECKED_CAST")
    fun get(): T? =
        reference.getReference(typeHandle)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer.asRawAddress(), typeHandle) as? T
        }

    override fun close() {
        reference.close()
    }
}
