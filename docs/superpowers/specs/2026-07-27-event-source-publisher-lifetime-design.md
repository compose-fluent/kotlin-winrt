# Publisher-Owned Event Lifetime Design

## Goal

Align Kotlin event subscriptions with `.cswinrt`: after a successful native add, the WinRT publisher owns the delegate COM reference, and releasing that publisher ends the registration without requiring Kotlin callers to retain an `EventRegistrationToken` or execute `-=`. Explicit `-=` remains supported when a caller wants to stop observing an event before the publisher is released.

The JVM process-shutdown safeguard remains, but it must not extend the lifetime of the publisher, event state, handler, delegate, projected control, window, or sample application.

## Reference And Confirmed Divergence

The reference path is:

- `.cswinrt/src/WinRT.Runtime/Interop/EventSource{TDelegate}.cs`
- `.cswinrt/src/WinRT.Runtime/Interop/EventSourceState{TDelegate}.cs`
- `.cswinrt/src/WinRT.Runtime/Interop/EventSourceCache.cs`

After `AddHandler` returns, `.cswinrt` disposes the managed marshaller reference in `finally`. A successful publisher therefore owns the remaining delegate COM reference. `EventSourceState.HasComReferences()` treats any remaining COM reference as native ownership, and both the event state and cache entries are weakly held outside that native delegate graph.

The Kotlin runtime currently diverges in three connected ways:

1. `EventSource.subscribe()` never calls `WinRTDelegateHandle.releaseManagedReferenceForNativeOwnership()` after a successful add.
2. `EventSourceState.hasComReferences()` consequently subtracts one permanent managed reference instead of using the `.cswinrt` nonzero-COM-reference rule.
3. `EventSourceShutdownRegistry` stores a closure that strongly captures `objectReference` and `EventSourceState`. The state retains handlers and the delegate handle, so the global registry roots the complete publisher/callback graph until process shutdown.

Removing tokens from a sample is correct only after these runtime ownership rules are repaired. Adding sample-local `-=` cleanup would hide the runtime defect and violate the repository's runtime-first ownership boundary.

## Chosen Architecture

### Native Delegate Ownership

`EventSource.subscribe()` will prepare all Kotlin-side cleanup before transferring ownership:

1. Create the `EventSourceState`, its stable weak cache reference, and the delegate handle.
2. Attach a delegate-CCW cleanup action that resolves the state weakly and closes it when the delegate COM reference count reaches zero.
3. Call the native add method while the temporary ABI reference and managed delegate reference are both still valid.
4. After a successful add, install the weak shutdown registration and publish the weak state in `EventSourceCache`.
5. Release the delegate's managed COM reference with `releaseManagedReferenceForNativeOwnership()`.

If the add method fails, the managed delegate reference is not transferred. The existing failure path closes the handle and state, removes any partial cache/registry state, and rethrows the original failure.

Once ownership is transferred, the delegate handle retained by `EventSourceState` is only an idempotent lifecycle/control object; it no longer contributes a COM reference. `EventSourceState.hasComReferences()` will therefore match `.cswinrt` and report true for any nonzero ordinary COM reference or reference-tracker reference.

### Publisher Teardown

The publisher releases its native delegate reference as part of its own teardown. When that release takes the delegate CCW to zero, the delegate cleanup action closes the weakly resolved `EventSourceState`. Closing the state:

- clears all Kotlin handlers;
- removes the matching weak cache entry;
- unregisters the process-shutdown fallback;
- clears reference-tracking pointers; and
- closes the lifecycle handle idempotently.

This path must not call the publisher's remove method. The publisher is already tearing down its registration, and calling `RemoveHandler` from delegate destruction can re-enter a dead or partially destroyed native object.

### Explicit Unsubscription

Removing the last matching Kotlin handler still calls the native remove method exactly once. Native removal may synchronously release the delegate and run the delegate cleanup action before `unsubscribe()` returns, so `EventSourceState.close()`, registry unregistration, and handle closure must remain idempotent and safe under that reentrancy.

Removing a handler while other handlers remain changes only the state snapshot. It does not remove and re-add the native delegate.

### Weak Shutdown Safeguard

`EventSourceShutdownRegistry` will store structured registration data instead of an arbitrary closure that captures the object graph. Each entry owns only:

- the event token;
- either a capture-free vtable removal strategy represented by its slot index, or a weak reference to an injected removal callback;
- a managed weak reference to the publisher reference;
- an optional native `IWeakReference` plus the publisher interface IID, so a still-live weak-reference-capable publisher can be resolved even if its original Kotlin wrapper was collected; and
- a managed weak reference to `EventSourceState`.

Generated event sources use the vtable strategy, which stores only the remove slot and calls `StandardDelegates.removeEventHandler` against a publisher resolved at shutdown. The protected callback-based constructor remains available for specialized runtime/test sources, but its callback is weak in the global registry so an injected closure cannot indirectly root an application graph. Normal registry entries must not strongly retain the publisher, state, callback, or anything captured by that callback.

Closing the registration handle during explicit unsubscribe or delegate teardown only unregisters the entry and releases its native weak-reference helper; it does not invoke the native remove method.

At process shutdown, each still-registered entry is claimed at most once. If the state is already gone, no work is required. If the state remains and both the publisher and removal strategy can be resolved, the registry invokes native remove once. Whether strategy/publisher resolution or removal succeeds or fails, it closes the state and releases the weak-reference helper. As today, shutdown cleanup is best effort and does not let one registration failure prevent the remaining entries from being processed.

For publishers that do not implement `IWeakReferenceSource`, shutdown removal is possible only while the original publisher reference is still managed-live. The registry will not keep such a publisher alive merely to make shutdown removal possible. This is the required trade-off between FFM shutdown protection and publisher-owned lifetime.

## Concurrency And Failure Rules

- Existing `EventSource` and `EventSourceState` locks continue to serialize handler and state transitions.
- Registry claiming and state closure are idempotent, so explicit unsubscribe, publisher teardown, and process shutdown may race without a duplicate remove or double release.
- A native remove failure during shutdown still clears handlers and Kotlin-side state.
- A native remove failure during explicit `-=` retains the existing observable failure contract; it is not silently converted into publisher teardown.
- No new exception may cross a projected delegate boundary. Delegate invocation behavior is unchanged.

## Validation

Common runtime tests will prove the behavior on both JVM and `mingwX64`:

1. A successful add transfers the managed delegate reference to the publisher.
2. Releasing the publisher's delegate closes state and handlers without invoking native remove.
3. The shutdown registry does not strongly retain publisher, state, an injected removal callback, or objects captured by that callback.
4. Explicit removal of the last handler invokes native remove exactly once, including synchronous delegate-release reentrancy.
5. Shutdown with a live publisher invokes native remove exactly once and closes state.
6. Shutdown after the publisher reference is gone skips native remove but still clears a remaining state.
7. Add failure releases the managed delegate and leaves no cache or shutdown registration.
8. Existing multi-handler dispatch, cached-state reuse, reference-tracker, and re-registration behavior remains green.

The focused gate is `:winrt-runtime:jvmTest :winrt-runtime:mingwX64Test`. After the runtime commit, the WebView2 slice must be revalidated with its 10 focused tests, JVM and `mingwX64` compilation, both automatic-exit smoke hosts, the Native-to-JVM user-data regression, and a responsive native window that closes with exit code 0.

## Scope Boundaries

This slice changes only `winrt-runtime`, its tests, `PLAN.md`, and the WebView2 validation status/documents needed to record the upstream dependency. It does not change generated event syntax, the public `WinRTEvent` API, WebView2 projection output, sample-local event cleanup, activation, marshaling policy outside delegates, or application packaging.
