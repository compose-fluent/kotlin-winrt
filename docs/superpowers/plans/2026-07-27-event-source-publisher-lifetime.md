# Publisher-Owned Event Lifetime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a successful projected event subscription live exactly as long as its WinRT publisher unless explicitly removed, without letting the process-shutdown safeguard retain the publisher or Kotlin callback graph.

**Architecture:** `EventSource` transfers the delegate CCW's managed COM reference after native add succeeds and installs a weak state cleanup action on that CCW. `EventSourceShutdownRegistry` stores weak publisher/state/callback references plus a capture-free vtable removal strategy for generated projections; publisher teardown clears state without calling remove, while explicit unsubscribe and live-publisher process shutdown call remove at most once.

**Tech Stack:** Kotlin Multiplatform common code, JVM FFM-backed WinRT CCWs, Kotlin/Native `mingwX64`, Gradle, Kotlin Test, WinUI 3, WebView2.

## Global Constraints

- `.cswinrt/src/WinRT.Runtime/Interop/EventSource{TDelegate}.cs`, `EventSourceState{TDelegate}.cs`, and `EventSourceCache.cs` are the behavior and responsibility reference.
- Keep the change inside `winrt-runtime`; do not restore sample-local event tokens or mandatory `-=` cleanup.
- Preserve both protected `EventSource` constructor surfaces and the generated vtable-index path.
- A successful native add transfers managed delegate ownership; a failed add does not.
- Publisher teardown never calls the native remove method.
- Explicit removal of the last handler calls native remove exactly once.
- The global shutdown registry cannot strongly retain publisher, state, injected removal callback, or callback captures.
- Implement and validate common behavior on JVM first, then run the same common tests on `mingwX64`.
- Preserve unrelated dirty worktree files and the untracked `.gradle-review/` and `.worktrees/` directories.

---

### Task 1: Define Publisher-Lifetime Regressions

**Files:**
- Modify: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/EventRuntimeInfrastructureCommonTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `EventSource.subscribe`, `EventSourceShutdownRegistry.closeAllForTests`, `PlatformManagedWeakReference`, `PlatformFinalization.drain`, and the existing `TestIntEventSource` fake native registration.
- Produces: common regression tests named `publisher_delegate_release_ends_subscription_without_native_remove` and `shutdown_registry_does_not_retain_abandoned_subscription_graph`.

- [x] **Step 1: Add a deterministic publisher-teardown regression**

Add a test whose native add callback owns one `WinRTDelegateReference`, whose Kotlin handler captures a unique object, and whose remove callback increments a counter. After closing only the native delegate reference, drain finalization and then close the shutdown registry:

```kotlin
@Test
fun publisher_delegate_release_ends_subscription_without_native_remove() {
    EventSourceCache.clearForTests()
    EventSourceShutdownRegistry.clearForTests()

    val owner = WinRTInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
    var activeDelegate: WinRTDelegateReference? = null
    var removals = 0
    val source =
        TestIntEventSource(
            owner = owner,
            addHandler = { _, handler ->
                activeDelegate = WinRTDelegateReference.fromAbi(
                    handler.getRefPointer().asRawAddress(),
                    testIntEventDescriptor,
                )
                EventRegistrationToken(0x12345678_00000001)
            },
            removeHandler = { _, _ ->
                removals += 1
                activeDelegate?.close()
                activeDelegate = null
            },
        )

    val retainedReference = subscribeCapturedHandler(source)
    activeDelegate!!.close()
    activeDelegate = null

    drainUntilCleared(retainedReference)
    EventSourceShutdownRegistry.closeAllForTests()

    assertEquals(0, removals)
    owner.close()
    EventSourceCache.clearForTests()
    EventSourceShutdownRegistry.clearForTests()
}
```

Create the handler capture in a helper boundary so the test method never owns a strong local reference:

```kotlin
private fun subscribeCapturedHandler(
    source: TestIntEventSource,
): PlatformManagedWeakReference<HandlerCapture> {
    val retained = HandlerCapture()
    source.subscribe { _, value ->
        retained.lastValue = value
    }
    return PlatformManagedWeakReference(retained)
}

private data class HandlerCapture(var lastValue: Int = 0)
```

- [x] **Step 2: Add an abandoned-subscription graph regression**

Create an owned fake publisher reference for cleanup and a second borrowed wrapper passed into `TestIntEventSource`. Return only weak references to that borrowed wrapper and to an object captured exclusively by the injected remove callback, plus the native delegate reference that keeps event state alive:

```kotlin
private data class AbandonedSubscription(
    val publisher: PlatformManagedWeakReference<ComObjectReference>,
    val removalCapture: PlatformManagedWeakReference<RemovalCapture>,
    val nativeDelegate: WinRTDelegateReference,
    val removals: MutableList<EventRegistrationToken>,
    val received: MutableList<Int>,
)

private fun abandonSubscription(owner: ComObjectReference): AbandonedSubscription {
    val borrowedPublisher = ComObjectReference(
        pointer = owner.pointer,
        interfaceId = owner.interfaceId,
        preventReleaseOnDispose = true,
    )
    val publisherReference = PlatformManagedWeakReference(borrowedPublisher)
    val removalCapture = RemovalCapture()
    val removalCaptureReference = PlatformManagedWeakReference(removalCapture)
    val removals = mutableListOf<EventRegistrationToken>()
    val received = mutableListOf<Int>()
    var nativeDelegate: WinRTDelegateReference? = null

    TestIntEventSource(
        owner = borrowedPublisher,
        addHandler = { _, handler ->
            nativeDelegate = WinRTDelegateReference.fromAbi(
                handler.getRefPointer().asRawAddress(),
                testIntEventDescriptor,
            )
            EventRegistrationToken(0x22334455_00000001)
        },
        removeHandler = { _, token ->
            removalCapture.calls += 1
            removals += token
        },
    ).subscribe { _, value -> received += value }

    return AbandonedSubscription(
        publisher = publisherReference,
        removalCapture = removalCaptureReference,
        nativeDelegate = nativeDelegate!!,
        removals = removals,
        received = received,
    )
}

private data class RemovalCapture(var calls: Int = 0)
```

The test drains both weak references, calls `closeAllForTests()`, invokes the still-native delegate, and asserts that native remove was skipped and the closed state no longer dispatches:

```kotlin
@Test
fun shutdown_registry_does_not_retain_abandoned_subscription_graph() {
    EventSourceShutdownRegistry.clearForTests()
    val host = WinRTInspectableComObject.inspectableBox("owner", "test.Owner")
    val owner = host.createPrimaryReference()
    val abandoned = abandonSubscription(owner)

    try {
        drainUntilCleared(abandoned.publisher)
        drainUntilCleared(abandoned.removalCapture)

        EventSourceShutdownRegistry.closeAllForTests()
        abandoned.nativeDelegate.invoke(listOf("sender", 19))

        assertEquals(emptyList(), abandoned.removals)
        assertEquals(emptyList(), abandoned.received)
    } finally {
        abandoned.nativeDelegate.close()
        owner.close()
        host.close()
        EventSourceShutdownRegistry.clearForTests()
    }
}
```

Use one generic `drainUntilCleared(reference: PlatformManagedWeakReference<T>)` test helper with the same bounded `PlatformFinalization.drain()` plus allocation-pressure pattern already used by `PlatformCacheSeamsTest`.

- [x] **Step 3: Run the JVM tests and verify RED**

Run:

```powershell
.\gradlew.bat :winrt-runtime:jvmTest --tests io.github.composefluent.winrt.runtime.EventRuntimeInfrastructureCommonTest --rerun-tasks
```

Expected: the new publisher-release test fails because the registry/state still retains the handler and later invokes native remove; the abandoned-subscription test fails because the global closure retains the borrowed publisher and callback capture. Existing event tests remain passing before those assertions.

Observed: 12 JVM event tests ran and only the two new tests failed. The weak-reference assertion retained `HandlerCapture(lastValue=0)` in the publisher-release case and a borrowed `ComObjectReference` in the abandoned-subscription case.

- [x] **Step 4: Run the Native tests and verify the same RED contract**

Run:

```powershell
.\gradlew.bat :winrt-runtime:mingwX64Test --rerun-tasks
```

Expected: the same two common tests fail for the ownership/retention assertions, not from compilation, pointer access, or test setup errors.

Observed: 311 `mingwX64` runtime tests ran and only the same two new lifecycle tests failed; compilation, linking, and the native test process completed normally.

- [x] **Step 5: Record RED and commit the contract**

Keep `Runtime-Event-Publisher-Lifetime` marked `正在做` in `PLAN.md` and record the exact two expected failures for JVM and `mingwX64`.

```powershell
git add -- PLAN.md winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/EventRuntimeInfrastructureCommonTest.kt
git commit -m "test(runtime): define publisher-owned event lifetime"
```

### Task 2: Transfer Delegate Ownership And Weaken Shutdown Cleanup

**Files:**
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/EventSource.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/EventSourceState.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/EventSourceShutdownRegistry.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ManagedComHostState.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WinRTInspectableComObject.kt`
- Modify: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/EventRuntimeInfrastructureCommonTest.kt`
- Modify: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/ManagedComHostStateTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `WinRTDelegateHandle.releaseManagedReferenceForNativeOwnership()`, `WinRTDelegateHandle.addCleanupAction()`, `WeakReferenceReference.resolve()`, `ComObjectReference.tryGetWeakReference()`, `ManagedComHostState.tryAddReference()`, and `StandardDelegates.removeEventHandler()`.
- Produces: `EventSourceShutdownRegistry.registerCallback(...)`, `EventSourceShutdownRegistry.registerVtable(...)`, weak structured registrations, and race-safe `.cswinrt`-aligned nonzero COM reference detection for runtime-owned delegate CCWs.

- [x] **Step 1: Replace the arbitrary shutdown closure with structured weak state**

Change `EventSourceShutdownRegistry` so registration entries contain:

```kotlin
private val state: WeakReference<Any>
private val publisher = PublisherReference(objectReference)
private val removal: Removal
private val token: EventRegistrationToken
```

Provide two registration entry points:

```kotlin
fun registerCallback(
    objectReference: ComObjectReference,
    state: WeakReference<Any>,
    token: EventRegistrationToken,
    removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
): AutoCloseable

fun registerVtable(
    objectReference: ComObjectReference,
    state: WeakReference<Any>,
    token: EventRegistrationToken,
    removeHandlerSlot: Int,
): AutoCloseable
```

`CallbackRemoval` stores `PlatformManagedWeakReference(removeHandler)`. `VtableRemoval` stores only the remove slot and invokes `StandardDelegates.removeEventHandler`. `PublisherReference` stores a `PlatformManagedWeakReference<ComObjectReference>`, the original IID, and a best-effort native weak reference created with `runCatching { objectReference.tryGetWeakReference() }.getOrNull()`.

The publisher resolver must borrow a still-live original wrapper without closing it, or `use` an owned reference returned by native weak resolution. If neither path resolves, skip remove. `Registration.closeForShutdown()` claims an atomic closed flag, attempts removal only when state, publisher, and removal strategy resolve, always closes the state, and always closes the native weak-reference helper. `unregister()` claims the same flag and releases only weak helper resources. `clearForTests()` must unregister its snapshot instead of dropping native weak helpers.

- [x] **Step 2: Preserve constructor behavior while selecting shutdown removal strategy**

Give `EventSource` a private primary constructor with an optional `shutdownRemoveHandlerSlot`. Keep both existing protected constructors:

```kotlin
abstract class EventSource<T : Any> private constructor(
    private val objectReference: ComObjectReference,
    private val addHandler: (ComObjectReference, ComObjectReference) -> EventRegistrationToken,
    private val removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
    private val index: Int,
    private val shutdownRemoveHandlerSlot: Int?,
) {
    protected constructor(
        objectReference: ComObjectReference,
        addHandler: (ComObjectReference, ComObjectReference) -> EventRegistrationToken,
        removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
        index: Int = 0,
    ) : this(objectReference, addHandler, removeHandler, index, null)

    protected constructor(
        objectReference: ComObjectReference,
        vtableIndexForAddHandler: Int,
    ) : this(
        objectReference,
        { reference, handler -> StandardDelegates.addEventHandler(reference, vtableIndexForAddHandler, handler) },
        { reference, token -> StandardDelegates.removeEventHandler(reference, vtableIndexForAddHandler + 1, token) },
        vtableIndexForAddHandler,
        vtableIndexForAddHandler + 1,
    )
}
```

Generated projections therefore use `registerVtable`; callback-constructed runtime/test sources use `registerCallback` without the registry strongly retaining the callback.

- [x] **Step 3: Transfer ownership only after successful native add**

In the new-registration branch of `EventSource.subscribe()`:

1. Obtain `stateReference` before registration.
2. Store the handle in `state.eventInvokeHandle`.
3. Attach `eventInvokeHandle.addCleanupAction { (stateReference.tryGetTarget() as? EventSourceState<*>)?.close() }` before native add.
4. Call add through a temporary delegate reference.
5. Install the callback- or vtable-based shutdown registration with the completed token.
6. Publish the weak local/cache state.
7. Call `eventInvokeHandle.releaseManagedReferenceForNativeOwnership()` as the last success-path ownership step.

Keep the catch path closing the handle and state and clearing the local weak state. Do not transfer ownership when add throws.

- [x] **Step 4: Match `.cswinrt` COM-reference detection without stale-pointer probes**

Keep the Kotlin-owned managed COM reference explicit until native add succeeds. `EventSourceState` starts with one managed reference, exposes `transferDelegateToNativeOwnership()` to release the handle and move that count to zero, and lets `EventSource.subscribe()` call that state method as its final success-path step.

Do not copy `eventInvokePtr` or `referenceTrackerTargetPtr` out of the state lock and then call their vtables. A concurrent final native `Release` can unregister and free the Kotlin CCW between the snapshot and `AddRef`, causing a use-after-free. Resolve the pointer through `WinRTInspectableComObject`'s managed-host registry instead, and atomically pin only a live `ManagedComHostState` with `tryAddReference()` before reading and releasing the reference count. A zero-count host must return `null` and must never be resurrected.

In `EventSourceState.hasComReferences()`, compare the safely probed count against the current managed-reference count:

```kotlin
if (countAfterRelease > currentManagedReferenceCount) {
```

Before transfer this preserves the existing standalone-state behavior by excluding Kotlin's one retained reference. After transfer the count is zero, so the test is equivalent to `.cswinrt`'s `countAfterRelease != 0u`. Kotlin's `ManagedComHostState.addTrackerReference()` also increments the normal COM reference count, so the same pinned probe covers reference-tracker ownership and the stale tracker-target pointer/probe is removed. The tracker path must call `tryAddReference()` before publishing its tracker-count CAS; if the CAS loses, release that temporary pin and retry. This ordering prevents a concurrent final `Release` from cleaning the host between tracker publication and ordinary reference retention, and prevents any subsequent `0 -> 1` resurrection. An unmanaged pointer that is absent from the Kotlin registry is treated as having no managed event-state references and is never dereferenced.

Add `event_source_state_does_not_probe_an_unmanaged_delegate_pointer` with a three-slot unmanaged IUnknown fake. Its `QueryInterface` returns `E_NOINTERFACE`, while `AddRef` and `Release` count calls. Verify RED against the old implementation (`expected 0 but was 1` for `AddRef`), then verify both counters remain zero after the managed-host probe. Add focused `ManagedComHostStateTest` coverage proving a zero-count host cannot be resurrected by either probe or tracker add, a live host is pinned only for the duration of the probe, and a valid tracker reference still retains the host until tracker release. The tracker regression is RED against the old publish-first implementation because `addTrackerReference()` returns 1 after cleanup instead of 0.

- [x] **Step 5: Run focused JVM GREEN**

Run:

```powershell
.\gradlew.bat :winrt-runtime:jvmTest --tests io.github.composefluent.winrt.runtime.EventRuntimeInfrastructureCommonTest --rerun-tasks
```

Expected: all event infrastructure tests pass. The publisher-release regression observes zero remove calls, the abandoned graph becomes weakly collectible, explicit unsubscribe still removes exactly once, and live-publisher shutdown still removes once.

Observed: all 13 `EventRuntimeInfrastructureCommonTest` JVM tests passed with zero failures and zero errors after introducing pre/post-transfer managed-reference accounting and replacing raw CCW/reference-tracker vtable probes with a pinned managed-host lookup.

- [x] **Step 6: Run complete JVM and Native runtime GREEN**

Run:

```powershell
.\gradlew.bat :winrt-runtime:jvmTest :winrt-runtime:mingwX64Test --rerun-tasks
```

Expected: both complete runtime test targets pass with zero failures. Existing Gradle/Kotlin warnings may remain, but no new warning or native access violation is accepted.

Observed: the complete rerun passed all 326 JVM runtime tests and all 315 `mingwX64` runtime tests with zero failures, errors, or skips. The Native publisher-release regression initially remained red only because its preflight `retainedReference.get()` kept the resolved target live for the caller frame; removing that test-owned strong resolution made the same lifecycle contract green without changing runtime cleanup or increasing GC retries. A later final-review regression first failed with one unexpected raw `AddRef` call, then passed on both targets after `ManagedComHostState.tryAddReference()` and `WinRTInspectableComObject.tryProbeReferenceCount()` replaced stale-pointer probing. The follow-up tracker regression first observed an invalid `0 -> 1` host resurrection, then passed after tracker add changed from publish-first to pin-first ordering with CAS rollback.

- [x] **Step 7: Close the runtime slice and commit**

Mark `Runtime-Event-Publisher-Lifetime` complete in `PLAN.md` with exact JVM/Native test evidence. Keep `WebView2-Sample（正在做）` until its downstream gate is rerun.

```powershell
git add -- PLAN.md docs/superpowers/plans/2026-07-27-event-source-publisher-lifetime.md winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/EventSource.kt winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/EventSourceState.kt winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/EventSourceShutdownRegistry.kt winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ManagedComHostState.kt winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WinRTInspectableComObject.kt winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/EventRuntimeInfrastructureCommonTest.kt winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/ManagedComHostStateTest.kt
git commit -m "fix(runtime): restore publisher-owned event lifetime"
```

### Task 3: Revalidate And Commit The WebView2 Sample

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-27-webview2-sample.md`
- Modify: `docs/superpowers/specs/2026-07-27-webview2-sample-design.md`
- Modify: `winrt-samples/build.gradle.kts`
- Modify: `winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt`
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WinUiSampleEntry.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: corrected runtime publisher ownership, the existing 10 `WebView2SampleTest` cases, `runWinRTApplicationHost`, `runReleaseExecutableMingwX64`, and target-specific `WEBVIEW2_USER_DATA_FOLDER` configuration.
- Produces: a committed sample using inline `+=` subscriptions without token retention or explicit event removal, with fresh dual-target test, smoke, user-data, and window evidence.

- [ ] **Step 1: Run static event-ownership and diff checks**

Run:

```powershell
rg -n "EventRegistrationToken|remove[A-Z][A-Za-z]*Handler|remove[A-Z][A-Za-z]*|-=|eventTokens|subscriptions" winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt
git diff --check
```

Expected: the forbidden event-cleanup scan has no matches and `git diff --check` reports no whitespace errors.

- [ ] **Step 2: Run focused sample tests and both compilations**

Run:

```powershell
.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest :winrt-samples:compileKotlinWinuiJvm :winrt-samples:compileKotlinMingwX64 --rerun-tasks
```

Expected: all 10 focused tests pass and both compilation targets complete successfully.

- [ ] **Step 3: Run fresh JVM and Native automatic-exit smoke**

Run:

```powershell
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runWinRTApplicationHost
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runReleaseExecutableMingwX64
```

Expected from each host: `core initialized`, `embedded page requested`, `navigation completed success=true`, successful cleanup, and exit code 0 without explicit event unsubscription.

- [ ] **Step 4: Repeat Native-to-JVM and verify user-data ownership**

Run the Native automatic-exit command immediately followed by the JVM command. Then evaluate:

```powershell
$tracked = Get-ChildItem -LiteralPath 'winrt-samples/build/kotlin-winrt/application-layout' -Recurse -Directory -Filter '*.WebView2' -ErrorAction SilentlyContinue
[pscustomobject]@{
    TrackedUserDataExists = [bool]$tracked
    NativeUserDataExists = Test-Path -LiteralPath 'winrt-samples/build/kotlin-winrt/webview2-user-data/mingwX64'
    JvmUserDataExists = Test-Path -LiteralPath 'winrt-samples/build/kotlin-winrt/webview2-user-data/jvm'
}
```

Expected: `TrackedUserDataExists=False`, `NativeUserDataExists=True`, and `JvmUserDataExists=True`.

- [ ] **Step 5: Verify a real responsive Native window and normal close**

Set `kotlin.winrt.samples.runWebView2Sample=true` in the process environment, start `winrt-samples/build/kotlin-winrt/application-layout/mingwX64/release/winrt-samples.exe` from its release directory, and poll until `MainWindowHandle` is nonzero and `MainWindowTitle` is `Kotlin WinRT WebView2`. Assert the process has not exited and `Responding` is true. Call `CloseMainWindow()`, wait up to 30 seconds, and assert exit code 0. Clear the temporary environment variable in `finally`.

- [ ] **Step 6: Update completion evidence and commit the sample**

Update the WebView2 design, implementation plan, and `PLAN.md` with only the fresh post-runtime evidence. Mark `WebView2-Sample` complete.

```powershell
git add -- PLAN.md README.md docs/superpowers/plans/2026-07-27-webview2-sample.md docs/superpowers/specs/2026-07-27-webview2-sample-design.md winrt-samples/build.gradle.kts winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WinUiSampleEntry.kt
git commit -m "feat(samples): add WebView2 browser sample"
```

- [ ] **Step 7: Run final review and leave the verified app visible**

Review the committed runtime and sample diffs against both design documents, rerun `git diff --check`, verify no tracked/untracked file outside the declared scope was added, and launch one final interactive Native WebView2 instance. Confirm its title, nonzero handle, and responsive state, then leave that final instance running for user inspection.
