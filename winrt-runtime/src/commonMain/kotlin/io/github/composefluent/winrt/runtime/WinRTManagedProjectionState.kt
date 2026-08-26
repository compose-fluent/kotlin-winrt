package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Object-owned access to the shared CCW identity selected by [ComWrappersSupport].
 *
 * The compiler plugin injects one instance into source classes that implement a
 * [WinRTProjectedInterface]. The state is only a fast binding to the global
 * weak-key cache; that cache remains responsible for host lifetime and cleanup.
 */
@OptIn(ExperimentalAtomicApi::class)
class WinRTManagedProjectionState {
    @PublishedApi
    internal val binding = AtomicReference<WinRTManagedProjectionStateBinding?>(null)

    @PublishedApi
    internal inline fun tryBorrowAbi(interfaceId: Guid): RawAddress {
        val snapshot = binding.load() ?: return RawAddress.Null
        if (
            snapshot.borrowedInterfaceIdLowBits == interfaceId.abiLowBits &&
            snapshot.borrowedInterfaceIdHighBits == interfaceId.abiHighBits
        ) {
            return snapshot.borrowedAbi
        }

        val abi = snapshot.cache.tryBorrowAbi(interfaceId)
        if (!PlatformAbi.isNull(abi)) {
            binding.compareAndSet(
                snapshot,
                WinRTManagedProjectionStateBinding(
                    generation = snapshot.generation,
                    cache = snapshot.cache,
                    borrowedInterfaceIdLowBits = interfaceId.abiLowBits,
                    borrowedInterfaceIdHighBits = interfaceId.abiHighBits,
                    borrowedAbi = abi,
                ),
            )
        }
        return abi
    }

    @PublishedApi
    internal inline fun tryAcquireCallLease(
        knownManagedValue: Any,
        interfaceId: Guid,
    ): WinRTProjectionMarshaler? {
        val snapshot = binding.load() ?: return null
        if (
            snapshot.borrowedInterfaceIdLowBits == interfaceId.abiLowBits &&
            snapshot.borrowedInterfaceIdHighBits == interfaceId.abiHighBits
        ) {
            snapshot.borrowedCallLease?.let { return it }
        }

        val lease = snapshot.cache.tryAcquireCallLease(knownManagedValue, interfaceId) ?: return null
        binding.compareAndSet(
            snapshot,
            WinRTManagedProjectionStateBinding(
                generation = snapshot.generation,
                cache = snapshot.cache,
                borrowedInterfaceIdLowBits = interfaceId.abiLowBits,
                borrowedInterfaceIdHighBits = interfaceId.abiHighBits,
                borrowedAbi = lease.abi,
                borrowedCallLease = lease,
            ),
        )
        return lease
    }

    internal fun invalidateBorrowedAbi(source: WinRTManagedProjectionAbiSource) {
        while (true) {
            val snapshot = binding.load() ?: return
            if (
                snapshot.cache !== source ||
                (PlatformAbi.isNull(snapshot.borrowedAbi) && snapshot.borrowedCallLease == null)
            ) {
                return
            }
            val cleared = WinRTManagedProjectionStateBinding(
                generation = snapshot.generation,
                cache = snapshot.cache,
            )
            if (binding.compareAndSet(snapshot, cleared)) {
                return
            }
        }
    }
}

/**
 * Implementation hook for object-owned managed CCW state.
 *
 * The compiler injects [WinRTManagedProjectionStateOwner] into managed implementations of
 * projected interfaces. Keeping this hook out of every generated interface avoids a shared
 * default method across the complete projection hierarchy; specialized manual implementations can
 * still opt into the null default when direct state access is useful.
 */
interface WinRTManagedProjectionStateAccess {
    fun winRTManagedProjectionState(): WinRTManagedProjectionState? = null
}

interface WinRTManagedProjectionStateOwner : WinRTManagedProjectionStateAccess

@PublishedApi
internal class WinRTManagedProjectionStateBinding(
    val generation: Int,
    val cache: WinRTManagedProjectionAbiSource,
    val borrowedInterfaceIdLowBits: Long = 0L,
    val borrowedInterfaceIdHighBits: Long = 0L,
    val borrowedAbi: RawAddress = RawAddress.Null,
    val borrowedCallLease: WinRTProjectionMarshaler? = null,
)

@PublishedApi
internal interface WinRTManagedProjectionAbiSource {
    fun tryBorrowAbi(interfaceId: Guid): RawAddress

    fun tryAcquireCallLease(
        knownManagedValue: Any,
        interfaceId: Guid,
    ): WinRTProjectionMarshaler?
}
