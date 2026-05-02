package io.github.composefluent.winrt.runtime

internal interface ManagedReferenceHost {
    fun createReference(interfaceId: Guid): ComObjectReference

    fun releaseManagedReference()
}
