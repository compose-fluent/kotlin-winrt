package io.github.composefluent.winrt.runtime

internal interface ManagedReferenceHost {
    fun createReference(interfaceId: Guid): ComObjectReference

    fun acquireReference(interfaceId: Guid): RawAddress

    fun detachReference(interfaceId: Guid): RawAddress

    fun releaseManagedReference()
}
