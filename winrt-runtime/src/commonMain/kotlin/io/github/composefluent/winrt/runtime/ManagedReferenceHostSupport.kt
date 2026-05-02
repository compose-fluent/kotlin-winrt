package io.github.composefluent.winrt.runtime

internal object ManagedReferenceHostSupport {
    fun <T : AutoCloseable> createLease(
        createReference: () -> T,
        releaseManagedReference: () -> Unit,
        abiOf: (T) -> RawAddress,
    ): AbiReferenceLease<T> {
        val reference = createReference()
        return try {
            AbiReferenceLeaseSupport.create(
                abi = abiOf(reference),
                ownedReference = reference,
                cleanup = releaseManagedReference,
            )
        } catch (failure: Throwable) {
            try {
                reference.close()
            } finally {
                releaseManagedReference()
            }
            throw failure
        }
    }

    fun <T> detachReference(
        createReference: () -> T,
        releaseManagedReference: () -> Unit,
    ): T {
        val reference = createReference()
        releaseManagedReference()
        return reference
    }

    fun <T : AutoCloseable, R> wrapOwnedReference(
        createReference: () -> T,
        releaseManagedReference: () -> Unit,
        wrap: (T, () -> Unit) -> R,
    ): R {
        val reference = createReference()
        return try {
            wrap(reference, releaseManagedReference)
        } catch (failure: Throwable) {
            try {
                reference.close()
            } finally {
                releaseManagedReference()
            }
            throw failure
        }
    }
}
