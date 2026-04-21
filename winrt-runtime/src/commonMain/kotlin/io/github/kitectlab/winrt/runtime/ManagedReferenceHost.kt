package io.github.kitectlab.winrt.runtime

internal interface ManagedReferenceHost {
    fun createReference(interfaceId: Guid): ComObjectReference

    fun releaseManagedReference()
}
