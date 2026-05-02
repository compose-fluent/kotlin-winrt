package io.github.composefluent.winrt.runtime

internal class AbiReferenceLease<T : AutoCloseable> internal constructor(
    val abi: RawAddress,
    private val ownedReference: T? = null,
    private val cleanup: (() -> Unit)? = null,
) : AutoCloseable {
    override fun close() {
        try {
            ownedReference?.close()
        } finally {
            cleanup?.invoke()
        }
    }
}

internal object AbiReferenceLeaseSupport {
    fun <T : AutoCloseable> create(
        abi: RawAddress,
        ownedReference: T? = null,
        cleanup: (() -> Unit)? = null,
    ): AbiReferenceLease<T> =
        AbiReferenceLease(
            abi = abi,
            ownedReference = ownedReference,
            cleanup = cleanup,
        )

    fun <T : AutoCloseable> borrowed(
        reference: T,
        cloneReference: (T) -> T,
        abiOf: (T) -> RawAddress,
    ): AbiReferenceLease<T> {
        val owned = cloneReference(reference)
        return AbiReferenceLease(
            abi = abiOf(owned),
            ownedReference = owned,
        )
    }
}
