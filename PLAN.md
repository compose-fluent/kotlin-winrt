# Plan

## Operating Rules

- Use `.cswinrt/` as the primary behavioral and architectural reference.
- Preserve dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- Keep runtime mechanics out of generated API-shape policy and keep sample code validation-only.
- Treat a CallSite as WinMD semantic metadata only. Generated declarations expose typed `TODO()` stubs and structured facts; the IR plugin plans marshaling and emits platform code from those facts and exact closed IR types.
- Do not serialize implementation plans, recursive recipes, ABI carriers, codec names, result factories, or transport choices into CallSite metadata, Base64 payloads, opaque sidecars, or projected-type enumerations.
- Keep runtime-owned and compiler-expanded intrinsic calls as peer implementations of the same contract. Do not add, remove, or substitute runtime-owned call sites during performance work.
- Use [PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md) for active performance priorities, ownership, and accepted work; keep only decision-relevant measurements, validity limits, and local evidence pointers in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md). Do not append per-run chronology.
- Converge semantically identical JVM and `mingwX64` call orchestration into common code one active slice at a time, then optimize that shared path once; do not create parallel target fast paths for the same projection operation.
- Put shared sequencing, HRESULT handling, ownership, identity, caching, and state machines in common code. Isolate only irreducible FFI invocation, raw-address/memory access, native symbol binding, callback trampolines, and toolchain mechanics in target source sets.
- Work on Windows with `KOTLIN_WINRT_WINDOWS_SDK_ROOT=D:\Windows Kits\10`; use `gradlew.bat` and targeted validation before aggregate gates.
- Preserve unrelated working-tree changes. Do not commit without explicit user authorization.

## Reference Mapping

- `.cswinrt/src/WinRT.Runtime` maps to `winrt-runtime`.
- `.cswinrt/src/cswinrt` maps to `winrt-metadata`, `winrt-generator`, and `winrt-compiler-plugin`.
- `.cswinrt/src/Projections` maps to `winrt-projections`.
- `.cswinrt/src/Authoring` maps to `winrt-authoring`.
- `.cswinrt/src/Samples` maps to `winrt-samples`; tests remain in their owning Kotlin modules.
- Active performance references are `.cswinrt/src/WinRT.Runtime`, `.cswinrt/src/cswinrt/code_writers.h`, and `.cswinrt/src/cswinrt/helpers.h`.

## Completed Baseline

- [x] Implement the runtime foundations: ABI primitives, initialization, COM lifetime and identity, activation, generic IIDs, delegates, events, collections, async, error propagation, weak/agile references, and WinUI hooks.
- [x] Implement WinMD loading and normalized metadata models for types, members, generics, interfaces, factories, parameter directions, layouts, and attributes.
- [x] Implement deterministic declaration/member generation, activation and composable surfaces, closed generics, authoring handoff, and JVM/Native compiler lowering.
- [x] Generate Windows SDK, Windows.UI.Xaml, Windows App SDK, and WebView2 projection artifacts without duplicate projected FQNs.
- [x] Implement authored metadata, TypeDetails, CCW/activation boundaries, native component/consumer fixtures, and HRESULT propagation.
- [x] Implement packaging and JVM/Native WinUI sample hosts, including the Controls Sample and WebView2 validation surface.

## Completed Performance Foundations

- [x] Mirror all 6 families and 97 methods from `.cswinrt/src/Benchmarks` across C++/WinRT, CsWinRT, Kotlin/Native, and Kotlin/JVM with one component, protocol, filter set, warmup policy, and checksum contract.
- [x] Share fixed-shape vtable dispatch, reusable ABI frames, direct Native calls, stable reference caches, and HSTRING access between runtime-owned and compiler-expanded paths.
- [x] Centralize RCW/CCW identity, ownership, call leases, escaped-reference promotion, and managed-projection state in common runtime contracts.
- [x] Restore common generated runtime-class RCW identity and weak-cache cleanup on JVM and `mingwX64`.
- [x] Use immutable update-time event snapshots and root native callback lifetime in event state.
- [x] Use the fixed-carrier JVM vtable invoker for `IUnknown::AddRef` and `IUnknown::Release`.
- [x] Capture the 2026-08-18 four-runner performance baseline at commit `19175936aae4b5d53b78cb9318e991f538f4eede`: all four catalogs contain 6 families and 97 scenarios, with Kotlin/Native at `21.33x` C++/WinRT and Kotlin/JVM at `0.88x` CsWinRT by geometric mean; record the prioritized program in [PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md).

## Current Focus

- [x] Implement P61 owned `System.Object` return decoding in the common RCW and
  call-site codec path. Follow `.cswinrt/src/WinRT.Runtime` ownership:
  consume ABI-owned `IInspectable*` returns on cache hits and transfer them on
  cold construction, while preserving borrowed delegate/event inputs. The
  measured Native path now probes the direct RCW cache before managed-CCW
  identity lookup; canonical-key resolution remains deferred to the existing
  miss path.
- [x] Validate the new focused JVM ownership/lifecycle regressions and
  `:winrt-runtime:compileKotlinMingwX64`; do not repeat already-passed
  generator/lowering checks in this pass.
- [x] Run the unchanged official P61 benchmark controls using the existing
  executable and fixed 97-scenario inputs. Six alternating fixed-workload Native
  pairs improved `0.89725x` candidate/baseline (`10.28%`, 95% interval
  `[0.84696, 0.95053]`); the JVM control was `0.97880x` with no material
  regression signal. The complete matrix retained 6 families and 97 scenarios
  across all four runners with zero checksum mismatches. A fresh adaptive
  rerun remained checksum-clean at Native/C++ `20.5517x` and JVM/CsWinRT
  `0.7067x`; treat that spread as diagnostic variance, not an acceptance gate.
  Keep event/delegate work frozen until new Native-only evidence appears.
- [x] Guard the P58 RCW hot-entry metadata snapshot and cache validation against
  constructor-lifecycle `WinRTObjectBase` instances that intentionally start with
  `super(null)`. The guard now treats an uninitialized base as a cache miss without
  touching its `lateinit nativeObject`, while retaining the initialized snapshot fast path.
- [x] Complete P27 scenario-isolated profile-first attribution. JVM JFR and
  Native profile/machine-code evidence locate the removable first authored-event
  admission cost in repeated closed-delegate `WinRTTypeSignature`,
  parameterized-IID, and SHA-1 work. Fresh reconstructed P22 artifacts improve
  the primary row in `12/12` balanced fixed-workload pairs on both targets.
- [x] Implement and retain P22 compiler-owned closed delegate IID scalar lowering
  from current source, aligned with
  `.cswinrt/src/WinRT.Runtime/GuidGenerator.cs` and
  `.cswinrt/src/cswinrt/code_writers.h`. Metadata/generator remains the sole
  closed-type/signature classifier; the compiler folds only proven constant
  `createFromSignature` calls; common runtime carries the two IID words for both
  JVM and `mingwX64`, while dynamic signatures keep the existing fallback.
  - [x] Preserve the generator validation boundary: metadata-owned
    `delegate({iid})` fragments are renderable as in `code_writers.h`, while a
    nested delegate without an IID remains rejected; authored reference writers
    now validate the common borrowed-reference acquisition contract.
  - [x] Pass all 420 generator tests, `:winrt-runtime:jvmTest`, JVM lowering,
    `mingwX64` main compilation, and Native lowering. Full `mingwX64Test` remains
    independently blocked by the existing common-test `@JvmInline` references.
  - [x] Confirm the fresh JVM candidate removes the profiled
    `ParameterizedInterfaceId`/SHA-1/temporary-reference chain and the Native
    candidate emits the two IID constants directly into the common scalar
    borrowed-reference call.
  - [x] Pass twelve balanced fixed-workload `AB/BA` pairs on both targets. The
    primary first-add scenario wins `12/12`, with candidate/baseline geometric
    mean ratios `0.5350` on JVM and `0.4797` on Native; construction-control
    intervals cross `1.0` on both targets.
  - [x] Complete the unchanged full benchmark gate: every runner retains 6
    families and 97 unique checksum-matched scenarios. The prioritization-only
    matrix reports JVM/CsWinRT `0.8386x` and Native/C++ `21.9251x` overall.
  - [x] Audit the long-memory gate without claiming a pass. The stock add-only
    scenario intentionally retains handlers through a COM cycle, while the
    add/remove loop accumulates transient wrappers before explicit GC; both hit
    watchdog limits and cannot distinguish a P22 leak. P22 adds no retained
    holder or cache, and ownership-sensitive runtime tests pass.
- [x] Reject P28 managed authored-CCW identity publication convergence. The
  post-entry Native stack proved that `WeakKeyStateMap.getOrPut` contains the
  timed construction path, but not that its identity lookup is an exclusive
  cost. A fixed-artifact six-pair Native screen gave construction `3/6`, ratio
  `0.9877` [`0.9262`, `1.0533`]; first-add and add-remove each won only `1/6` at
  approximately `1.02x`. Restore the P22 weak-cache path and skip JVM/final
  timing because the priority target already failed the large-effect gate.
- [x] Complete P29 exclusive Native authored-CCW construction attribution,
  mapped to `.cswinrt/src/WinRT.Runtime/ComWrappersSupport.net5.cs` and
  `DefaultComWrappers.ComputeVtables`. On the frozen P22 artifact,
  `createCachedCcwHost` executes 7,603 instructions and its
  `WinRTInspectableComObject` construction executes 6,994; the post-host cache,
  marshaler, and QI tail is only 2,604 instructions at the same four-level trace
  depth versus 22,741 for host construction plus that tail. Vtable creation,
  weak lifecycle registration, and allocation are not dominant. The selected
  exclusive cost is 80 repeated tiny calls while initializing the ten physical
  interface objects: query-table words, table/vtable/binding/counter slots, and
  interface-object address calculation.
- [x] Reject P29 common-owned allocation-backed CCW initialization lowering.
  Inlining the existing `NativeMemoryView` calls and common interface-object
  address calculation reduced Native constructor instructions from 6,994 to
  6,070 and direct calls from 96 to 85, but the clean fixed-artifact six-pair
  Native screen gave construction `4/6`, ratio `1.0026` [`0.9343`, `1.0758`].
  First-add was `3/6`, `0.9986` [`0.9382`, `1.0629`], and add-remove was `4/6`,
  `0.9766` [`0.9338`, `1.0213`]; every interval crosses `1.0`. A narrower
  mingwX64 typed-pointer trial was structurally worse at 7,872 instructions and
  125 calls because cinterop pointer conversions remained calls. Restore P22
  and skip JVM/final timing because the Native priority gate failed.
- [x] Complete P30 Native wall-clock stack attribution on the frozen P22
  `NativeIntEventOverhead` executable. A 500 Hz primary-thread sample contains
  5,970 valid RIPs: `NtDelayExecution` and `NtWaitForMultipleObjects` account for
  `20.00%` and `14.74%`. Timed-thread conditional stacks map both waits to
  Kotlin/Native GC safepoints, reached respectively through fresh
  `WinRTProjectionMarshaler` construction and `ComPtr` Cleaner registration;
  weak CCW-cache sweep/read symbols account for another material sampled share.
  This explains why P29's local instruction reduction was wall-clock neutral.
- [x] Reject P31 common scalar owned-projection input transfer. The unchanged
  Native six-pair screen showed a real construction improvement: `6/6`, ratio
  `0.8997` [`0.8546`, `0.9473`]. The required JVM gate did not confirm the
  construction effect (`8/12`, `0.8726` [`0.7205`, `1.0568`]) and independently
  proved an add-remove regression (`2/12`, `1.2603` [`1.0711`, `1.4830`]).
  Restore P22 production code and skip the remaining Native final pairs because
  a shared candidate cannot be retained with a repeatable JVM control
  regression.
- [x] Reject P32 compiler-owned raw carrier with the unchanged P22 generic
  detach path. Native construction reached only `4/6`, ratio `0.9755`
  [`0.8889`, `1.0706`]; first-add was `4/6`, `0.9832`
  [`0.8739`, `1.1062`], and add-remove was `3/6`, `1.0433`
  [`0.9848`, `1.1053`]. Restore P22 and skip JVM because the priority row lost
  P31's large effect.
- [x] Reject P33 compiler-only direct-host raw acquisition. The specialized cold
  fallback kept generic detach and cached/borrowed paths unchanged and passed all
  64 JVM ownership/lowering tests plus the Native release link, but Native
  construction reached only `3/6`, ratio `0.9737` [`0.8971`, `1.0568`].
  First-add was `5/6`, `0.9865` [`0.9419`, `1.0332`], and add-remove was `6/6`,
  `0.9509` [`0.8813`, `1.0260`]. Restore P22 and skip JVM/final timing because
  P31's construction effect did not reproduce.
- [x] Reject P34 common lazy `WinRTEvent` registration storage. Fresh P22
  timed-thread stacks and exact-image disassembly show every `ManagedEvents()`
  eagerly constructs four empty handler/token maps through its two `WinRTEvent`
  fields before any subscription. The `.cswinrt` event-source first-use state
  ownership model was tested by allocating those maps on the first add only, while
  keeping token-table, duplicate/LIFO removal, ABI, lifetime, and JVM/`mingwX64`
  sequencing unchanged. The fixed P22 Native screen gave construction `5/6`,
  ratio `0.9608` [`0.8764`, `1.0534`]; first-add was `2/6`, `1.0477`
  [`0.9569`, `1.1472`], and add-remove was `4/6`, `0.9367`
  [`0.8497`, `1.0326`]. Restore P22 and skip JVM/final timing because the
  construction interval crosses `1.0` and first-add does not preserve direction.
- [x] Reject P35 physical-`IUnknown` removal after the fixed six-pair native
  screen. `NativeIntEventOverhead` won `4/6`, paired median `-1.01%`, geometric
  mean ratio `0.9901` with 95% CI `[0.9427, 1.0397]`; first-add won `4/6`,
  `-1.22%`, `0.9779` `[0.9193, 1.0403]`; add-remove won `2/6`, `+1.37%`,
  `1.0159` `[0.9624, 1.0723]`. All intervals cross `1.0`, and add-remove moves
  in the wrong direction, so restore the P22 production shape.
- [x] Reject P36 raw call-scoped standard-event delegate references after
  profile-first attribution and the fixed Native screen. A 500 Hz timed-thread
  comparison on the frozen P22 artifact found each standard first subscription
  constructing `WinRTDelegateReference` -> `ComPtr` -> Cleaner around the
  synchronous add, while `.cswinrt/src/WinRT.Runtime/Interop/EventSource{TDelegate}.cs`
  uses a stack `ObjectReferenceValue`. The common candidate reused the existing
  raw marshaling reference only for standard-vtable adds and kept custom
  callbacks, reference tracking, ABI calls, publication, and ownership order
  unchanged. With the same P22 executable/component input and six alternating
  `40/15/5000` pairs, single-add won `5/6`, paired median `-3.766%`, geometric
  mean ratio `0.96310` with 95% CI `[0.93017, 0.99718]`; the target multi-add
  row won `5/6`, `-1.949%`, `0.97546` `[0.93441, 1.01831]`; construction control
  won `4/6`, `-1.171%`, `0.99533` `[0.97586, 1.01518]`. Multi-add saved a
  median `988 ns`, no more than single-add's `1014 ns`, and its interval crosses
  `1.0`; restore P22 and skip JVM/final gates because the target large-effect
  hypothesis did not reproduce.
- [x] Reject P37 common CCW memory-template initialization. Exact Native
  profiling justified replacing repeated vtable/IID writes with one shape-owned
  bulk copy, and the fixed six-pair Native gate improved delegate marshaling
  `5/6`, ratio `0.8946` with 95% CI `[0.8152, 0.9818]`. The required JVM gate
  did not reproduce the effect: `3/6`, ratio `0.9018` `[0.6650, 1.2228]`, with
  only a `-1.36%` paired median. Restore P22 production code; keep the profile
  and raw A/B evidence local, and add no benchmark scenario, runner, or script.
- [x] Reject P38 direct standard-event operator subscriptions after profile-first
  attribution against `.cswinrt/src/WinRT.Runtime/Interop/EventSource*.cs`.
  Native put `63.8%` below the two adds and JVM put `48.3%` there, but removing
  the synthetic token maps did not reproduce a retainable dual-target effect.
  The fixed six-pair JVM main row won `4/6`, ratio `0.9528` with 95% CI
  `[0.8274, 1.0971]`; Native won `5/6`, ratio `0.9901` `[0.9702, 1.0105]`, while
  the Native single-add row regressed in all six pairs at ratio `1.0312`
  `[1.0206, 1.0420]`. Restore P22, skip the twelve-pair and lifetime gates, and
  add no benchmark scenario, runner, script, or target-specific path.
- [x] Reject P39 identical-key RCW publication suppression after profile-first
  attribution and the fixed 12-pair gate. Native FastABI first-call won `12/12`
  at ratio `0.8272` [`0.7989`, `0.8565`], but JVM won only `7/12` at `0.9719`
  [`0.8719`, `1.0835`], failing both the direction and confidence requirements.
  Restore P22, skip lifecycle/full gates, and add no scenario, script, or
  target-specific path.
- [x] P40 consumes caller-owned composable factory results once, mapping CsWinRT
  `ObjectReference.Attach`/raw inner release into common runtime ownership. The
  fixed `40/60/5000` 12-pair gate retained it: JVM FastABI first-call won
  `12/12` at ratio `0.8324` [`0.7996`, `0.8665`], and Native won `12/12` at
  `0.6314` [`0.6152`, `0.6480`]; all four steady/default-first controls kept CIs
  across `1.0`. The 21-million-call lifecycle gate kept checksum parity with JVM
  tail growth `+1.66 MiB` and Native peak/tail growth `35.58/+4.79 MiB`. Full
  four-runner validation passed `97/97` scenario/schema/checksum parity; the six
  FastABI first-call scenarios now span JVM `0.668x-1.212x` versus CsWinRT and
  Native `36.902x-75.158x` versus C++/WinRT. No target branch, benchmark
  scenario, runner, or script was added.
- [x] Reject P41 after testing the allocation-free reference-tracker QI carrier.
  It removed the `NativePointerResult` allocation but expanded the Native
  tracker function from `0x830` to `0xB90` bytes and its stack frame from
  `0x168` to `0x208` bytes. In the fixed Native `40/60/5000` screen, the
  primary first-call FastABI scenario won only `3/6` pairs at `1.0622x` with
  95% CI `[0.9515, 1.1858]`; restore P40 and do not retry this carrier shape.
- [x] Reject P42 identical-key RCW publication suppression on top of P40. Exact
  machine code removed only the second `RcwIdentityCache.set` when direct and
  canonical keys matched, but the fixed Native six-pair screen did not reproduce
  P39's stable effect: FastABI first-call won `4/6` at `0.8988` with 95% CI
  `[0.7418, 1.0890]`; steady FastABI was neutral at `0.9938`
  `[0.9552, 1.0340]`, and ordinary construction was too variable to attribute.
  Restore P40 and do not extend the failed screen adaptively.
- [x] P44 caches each generated composable-factory reference at its
  owning projected type, matching CsWinRT's static `IObjectReference` factory
  ownership. Reuse the same common reference from ordinary and derived
  construction so Native removes one per-call `ComPtr`/Cleaner/tracker-QI/RCW
  publication chain without a target branch, runtime map, or benchmark change.
  The fixed `40/60/5000` gate retained it: Native won `12/12` at ratio `0.8857`
  with 95% CI `[0.8646, 0.9073]`; JVM measured `1.0116`
  `[0.9706, 1.0544]` with no repeatable regression. Full generator tests, JVM
  production execution, and the Native release link passed with matching
  inputs and checksums.
- [x] Complete P45 profile-first attribution on the retained P44 Native FastABI
  first-call path. Exact 4,096-call counts show one cached factory read, factory
  ABI call, result attach, `ComPtr`/Cleaner/tracker/register chain, and final
  getter per construction, but two RCW publications and therefore two
  `WeakValueCache.put`/`sweepOneEntry` calls. In a 20,000-sample exact-image
  profile, Native `HashMap.findKey`/`addKey` and weak-cache sweeping account for
  the largest remaining controllable share; the factory ABI call, result attach,
  tracker initialization, and final getter are individually small. Select the
  next candidate from the measured double-publication cache path, not source
  inspection, and keep the P44 executable as its fixed baseline.
- [x] Reject P46 after reusing each common weak-value entry directly as the RCW
  hot entry. The concrete common `expect`/platform `actual` entry removed both
  hot-wrapper types, reduced the Native executable by 2,560 bytes, shrank
  `RcwIdentityCache.set` from `0x210` to `0x190` bytes and its allocator calls
  from two to one, and kept the inlined `get` smaller than P44 (`0x2E0` versus
  `0x310`). The fixed Native `40/60/5000` 12-pair AB/BA gate nevertheless won
  only `3/12`: geometric-mean ratio `1.017423` with 95% CI
  `[0.982616, 1.053462]` and paired-median regression `+3.071%`. Restore P44;
  source/allocation and machine-code reductions alone do not justify retention,
  and do not retry weak-entry hot reuse in another target shape.
- [x] Complete P47 caller-and-key attribution for the two retained P44
  `RcwIdentityCache.set` calls. A CDB capture after the exact FastABI scenario
  entry shows both calls use the same pointer key and originate from adjacent
  calls in `registerObjectForInterface`: the direct-pointer and canonical-pointer
  alias publications, not separate result-attachment and final-wrapper owners.
  Removing the second publication would therefore retry the already rejected P42
  same-key suppression candidate; keep both common publications and do not revisit
  that shape under a Native target split.
- [x] Reject P48 before timing because Kotlin/Native lowers `linkedMapOf` and
  `hashMapOf` at this call site to the same `HashMap(Int)` construction. P44 and
  the rebuilt candidate have identical executable size and byte-identical
  `WeakValueCache` constructor, `put`, `reference`, and `sweepOneEntry` machine
  code, including identical symbol addresses and sizes. The different whole-file
  hash is only a relink artifact, not a hot-path change; restore the source and do
  not spend the fixed benchmark gate on a code-identical candidate.
- [x] Reject P49 after replacing Native `sweepOneEntry` map validation with an
  entry-current marker. Debugger counters over 8,192 completed P44 FastABI
  first-call sweeps found 2,485 stale (`30.33%`), 4,685 current-live (`57.19%`),
  and 1,022 current-cleared (`12.48%`) entries. The candidate removed
  `HashMap.findKey` from every nonempty sweep and shrank `sweepOneEntry` from
  `0x1E0` to `0x170`, but enlarged each entry allocation from four to five
  allocator units. The unchanged Native `40/60/5000` 12-pair gate won only
  `6/12`: geometric-mean ratio `0.982641`, 95% CI `[0.947186, 1.019424]`, paired
  median `-1.423%`, and matching checksums. Restore P44; lookup removal does not
  justify a larger per-publication entry.
- [x] Reject P50 after replacing duplicate Native weak-cache queue entries with
  one key token per current map entry. The entry allocation fell from four to
  three allocator units and replacement puts skipped `ArrayDeque.addLast`, but
  every sweep then reached the current entry and paid for its weak-reference read
  and live requeue instead of cheaply rejecting stale replacement entries. The
  fixed P44 `40/60/5000` gate lost its first 11 pairs (`0/11`), making the required
  `10/12` impossible: geometric-mean ratio `1.211217`, 95% CI
  `[1.178714, 1.244616]`, paired-median regression `+19.639%`, with matching
  checksums. Stop the last pair, restore P44, and do not retry key-token queue
  compaction or infer throughput from allocation reduction alone.
- [x] Reject P51 after making Native weak-cache cleanup follow map growth instead
  of every publication. The candidate reduced the exact batch from 8,192 sweeps
  to the 3,545 new-key puts, skipped replacement queue appends, and reduced each
  entry from four to three allocator units. Its fixed P44 `40/60/5000` gate still
  won only `3/12`: geometric-mean ratio `1.049220`, 95% CI
  `[0.985019, 1.117604]`, paired-median regression `+10.254%`, and matching
  checksums. Restore P44; key-token/current-entry cleanup remains slower and more
  variable even when all 4,647 replacement sweeps are removed, so retire this
  combined scheduling shape rather than layering another queue policy on it.
- [x] Complete P52 profile-driven `ComPtr` owner-allocation experiment and reject
  it on the fixed P44 Native FastABI gate. Exact P44 machine-code/debugger counts
  showed three `ComPtr.create` allocation sites per construction, so the trial
  made `ComPtr` an inline carrier and anchored finalization on the owning
  `ComObjectReference` without changing ABI or explicit-close sequencing. The
  immutable release image passed checksum smoke but won only 4/12 strict AB/BA
  pairs at 40 warmup rounds, 60 measurement rounds, and 5,000 iterations; its
  P52/P44 geometric-mean median ratio was `1.021868` with 95% CI
  `[0.980962, 1.064481]`. Restore P44: eliminating this visible allocation does
  not produce a reproducible end-to-end win and must not be retained on source
  inspection alone.
- [x] Complete P53 profile-driven managed-TLS scratch-pool experiment and reject
  it on the fixed P44 Native FastABI gate. The trial replaced the scalar/struct
  `@ThreadLocal` owners with one Windows FLS-owned per-fiber record while keeping
  the common scratch and ABI contracts unchanged. The release image passed
  checksum smoke and exited cleanly, but won only `8/12` strict AB/BA pairs at
  40 warmup rounds, 60 measurement rounds, and 5,000 iterations. Its P53/P44
  geometric-mean ratio was `0.984448` with 95% CI `[0.948158, 1.022126]`; the
  paired median improved `2.850%`, but neither the `10/12` direction gate nor the
  confidence gate passed. Restore P44: the profiled TLS cluster is real, but FLS
  plus `StableRef` ownership does not remove it with reproducible end-to-end gain.
- [x] Complete P54 profile-driven Native dictionary HSTRING call convergence and
  retain it on the fixed P44 Native gate. A 20,000-RIP profile of
  `ExistingDictionaryLookupCached` completed with zero failures and attributed
  `9.19%` of samples to scoped HSTRING acquire/release plus `21.08%` to adjacent
  pthread TLS/spin/emulated-TLS work. String-key `IMap`/`IMapView` lookup now uses
  the existing Native wide-scalar recipe thunk so `HSTRING_HEADER` and pointer
  result stay on its stack, matching `.cswinrt/src/WinRT.Runtime/Marshalers.cs`
  `MarshalString.Pinnable`; String classification, lookup/result policy, and
  non-String behavior remain common, while JVM keeps its FFM adapter. Immutable
  candidate `4EB06605E2D5CF09EA150F11555835486A7495B74649247189840C9269875ED5`
  passed checksum smoke and won `12/12` alternating P54/P44 pairs at 40 warmup
  rounds, 60 measurement rounds, and 5,000 iterations. Its geometric-mean ratio
  was `0.504990`, paired median `0.503454`, and bootstrap 95% CI
  `[0.501155, 0.510447]`, a reproducible `49.50%` reduction. JVM dictionary
  projection tests and Native dictionary/recipe-thunk focused suites passed; a
  missing common-test `JvmInline` import exposed by Native compilation was fixed
  without adding coverage or production scaffolding.
- [x] Complete P55 profile-driven owned-RCW hot/cold path experiment and reject
  it before timing. An exact-image
  20,000-RIP profile of the retained P54 `ExistingDictionaryLookupCached` path
  completed with zero sampling failures: `67.61%` of samples were in the Native
  executable, and `ComWrappersSupport.createRcwForOwnedComObject` was the largest
  Kotlin-owned exclusive region at `18.00%`. Address-level control-flow mapping
  shows the benchmark repeatedly hits the direct-pointer weak identity entry and
  does not enter `rcwCacheKey` or RCW creation, yet each hit still carries the
  generic slow path's `0x288` stack frame and lock/exception state. The common
  cold-helper trial preserved the `.cswinrt/src/WinRT.Runtime/ComWrappersSupport.net5.cs`
  identity contract and passed JVM/Native compilation plus release linking, but
  exact candidate machine-code inspection showed LLVM inlined the helper back
  into the caller: the function grew from about `0x13A0` to `0x14C0`, with no
  independent cold symbol. Restore P54 without running a benchmark gate; this
  source-only split does not change the measured hot path and must not be timed
  as if it were an optimization.
- [x] Complete P56 profile-driven Native owned-RCW release adaptation and reject
  it on the fixed P54 Native gate. The exact P54 dictionary profile attributed
  `711/20,000` RIP samples (`3.56%`) to the inlined
  `WinRTPlatformApi.releaseRaw` path after a validated weak-cache hit, so the
  trial reused the existing zero-input Win64 recipe thunk without changing the
  common ownership call or JVM actual. Exact-image inspection confirmed that
  `createRcwForOwnedComObject` shrank from `0x13A0` to `0xD70` bytes and its
  stack frame from `0x288` to `0x198`, while the initialized hot path called the
  cached thunk directly. The candidate passed checksum smoke but won only
  `8/12` strict AB/BA pairs at 40 warmup rounds, 60 measurement rounds, and
  5,000 iterations. Its P56/P54 geometric-mean ratio was `0.992945`, paired
  median `0.989493`, and bootstrap 95% CI `[0.984789, 1.001269]`. Restore P54:
  removing this visible Native pointer adaptation does not produce a
  reproducible end-to-end win, and its `3.56%` profile share is too small to be
  the next major Native/C++ gap-closing theme.
- [x] Complete P57: move the common owned runtime-class RCW hot-cache probe to the
  adapter boundary so cache hits consume the ABI-owned reference without entering
  the generic creation state machine; retain the generic second probe for races,
  and merge the adjacent collection disposed check with pointer acquisition. The
  exact Native machine code keeps the generic creator and its `0x288` slow-state
  frame off the hit path; the fixed P54 gate wins `12/12` at ratio `0.80413` with
  95% CI `[0.79845, 0.80988]`. The complete JVM/Native runtime gate passed, and
  all four existing dictionary controls won `4/4` paired screens with reductions
  between `14.41%` and `26.84%`.
- [x] Complete P58: move exact typed-RCW hot-hit lifetime and primary-type
  validation into the common `RcwIdentityCache` entry that already owns the weak
  identity probe. Generated `WinRTObjectBase` wrappers should no longer return a
  managed `nativeObject` on every hit merely to read the disposed flag; non-base
  implementations and type mismatches retain the existing generic path. Exact
  Native machine code removes the second getter/TLS chain; the complete
  JVM/Native runtime gate passes, and the frozen P57 gate wins `12/12` at ratio
  `0.882077`, a `11.79%` reduction, with 95% CI `[0.861359, 0.904233]`.
- [x] Complete P59: remove the next profile-confirmed Native root-frame boundary
  without changing common cache semantics. One target inline read seam keeps
  the JVM interface read while `mingwX64` directly reads the sole entry's native
  weak reference. Exact machine code eliminates the indirect
  `WeakValueCacheEntry.get` call, the complete JVM/Native runtime gate passes,
  and the frozen P58 gate wins `11/12` at ratio `0.885928`, an `11.41%`
  reduction, with paired bootstrap 95% CI `[0.861958, 0.918723]`; no benchmark
  scenario, runner, script, strong reference, or target-only cache was added.
- [x] Complete P60: align closed `IVector<T>` descriptor ownership with
  `.cswinrt/src/WinRT.Runtime/Projections/IList.net5.cs` and `GuidGenerator.cs`.
  `winrt-runtime` exposes one common descriptor contract while the generated
  closed `IVector<T>` module owner retains its signature, PIID, Kotlin type
  handle, element adapter, and owned-result factory. Generated List
  `createMarshaler`/`fromManaged`/`fromAbi` paths must reuse that descriptor on
  JVM and Native without adding state to every reference adapter. Each closed
  descriptor is initialized behind its own generated holder so touching an
  unrelated module codec does not initialize all vector descriptors.
  The JVM/generator/Native runtime gates and Native release link pass. The
  independent-holder candidate wins the fixed `40/60/5000` Native gate `12/12`
  at geometric-mean ratio `0.468638`, a `53.14%` reduction, with 95% CI
  `[0.460393, 0.477030]`. Unchanged `CreateListGuid` and `GetExistingUri`
  controls have ratios `0.987222` and `1.001604`, respectively, with both 95%
  intervals crossing `1`; no benchmark scenario, runner, script, target-specific
  IID map, or repository result artifact was added.
- [x] Complete P43 profile-first attribution on the retained P40 Native FastABI
  first-call path. A 20,000-sample exact-image profile, conditional stacks, and
  4,096-call counts agree that every construction creates two `ComPtr`/Cleaner
  and RCW-publication chains; one entire chain belongs to the transient
  composable factory reference before the component ABI call. The final getter
  is negligible, so P44 targets the upstream factory owner rather than another
  source-selected micro-path.
- [x] Complete P26 identical-artifact calibration and retire the adaptive
  `5/15/1`, five-pair acceptance gate. P19-P25 remain reverted; their old
  timing effects are inconclusive. P22 is the only candidate promoted and
  retained by the replacement profile-first protocol.

Detailed ownership and next steps are in
[PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md); compact
evidence and validity limits are in
[BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md).

## Next Queue

- [x] Reuse one immutable common `IWeakReference` CCW definition for managed weak-reference hosts, matching `.cswinrt/src/WinRT.Runtime/WeakReference.netstandard2.0.cs` and type-owned `ComWrappers` shape while keeping per-instance target state, resolve semantics, ownership, and JVM/`mingwX64` orchestration unchanged.
  - [x] Accept fixed-artifact 10-pair alternating A/B with matching checksums. JVM managed weak create/create+resolve improved by paired medians of `47.08%`/`32.56%`, both with 10/10 wins; Native improved by `61.91%`/`43.78%`, also with 10/10 wins. The existing native-object weak-reference control did not regress on either target.
  - [x] Pass dual-target `InteropRuntimeHooksTest`, `InteropRuntimeTest`, and `WeakReferenceInteropTest`, plus a 21-million-operation managed weak-reference lifetime gate on each target. Native private memory remained `20.06 MiB` early and late; JVM private memory fell from `657.71 MiB` to `649.10 MiB`. All four benchmark catalogs remain 6 families and 97 scenarios.
- [x] Close the remaining weak-reference bookkeeping review without another implementation: Kotlin already matches `.cswinrt/src/WinRT.Runtime/WeakReference.netstandard2.0.cs` for managed fallback, native-only-on-unwrapped-target state, synchronized retarget/resolve, and managed-target republish; a split would create semantic divergence rather than remove duplicate target orchestration.
- [x] Close the completed async-await fast-path experiment without retaining code: both common candidates improved `mingwX64` but regressed the unchanged JVM controls, so preserve the existing cancellation-first, race-safe await sequence rather than introducing separate target implementations. Detailed fixed-input results are recorded in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md).
- [x] Hoist closed async interface and handler IIDs into generated module metadata, matching CsWinRT's type-static `PIID` ownership so JVM and `mingwX64` stop rendering and hashing the same signatures for every returned async reference. The unchanged four-scenario A/B accepted the common path: immediate `Return` improved by `51.0%-64.2%` on JVM and `79.5%` on Native; scheduler-dominated JVM yield/control variation did not reproduce consistently. Detailed samples are in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md).
- [x] Close yielded async completion attribution without retaining another candidate: removing the post-registration status read matched CsWinRT's handler-setter assumption and slightly improved Native, but regressed both JVM yielded scenarios by about `20%`; restore the defensive common race check and do not split target behavior. Detailed samples are in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md).
- [x] Complete the nullable/object/type-input marshaling slice using only the existing official scenarios; keep boxing, type lookup, scratch, and ownership sequencing common across JVM and `mingwX64`.
  - [x] Remove short-lived `ComPtr`/context/Cleaner wrappers after a boxed runtime class has selected its closed `IReference` or `IReferenceArray` plan: one common raw QI/read/release sequence now matches CsWinRT's synchronous nullable factory while preserving canonical RCW identity and fallback ordering; the unchanged nullable and object controls passed the dual-target acceptance gate.
  - [x] Reuse the common classifier result for `SetPrimitiveType`, then close nullable-array and `SetUri` from the unchanged official controls: the array path retains only required materialization work, and each `SetUri` operation must still construct and activate a new WinRT `Uri`. No scenario, script, cache, or target-specific projection path was added.
- [x] Complete common managed-CCW construction parity with CsWinRT's once-per-type exposed-interface table and module-once TypeDetails registration: keep immutable definition/shape work type-owned while measuring and reducing only shared per-instance host lifecycle cost.
  - [x] Compose one immutable generated `WinRTCcwDefinition` for each projected-interface implementation shape and register it once per projection support owner; `ManagedEvents : IEvents` therefore performs no per-instance definition/interface-list construction.
  - [x] Convert generated authored runtime classes and manual `WinRTAuthoring` schemas from value-capturing factories to immutable static definitions. Method dispatch recovers the current managed value from the common host, holder names remain support-owner scoped, and cross-instance JVM/`mingwX64` tests preserve per-instance values, identity, storage, and reference counts.
  - [x] Quantify and retain authored static-definition fresh CCW construction. Keep the authoritative catalog at 97 scenarios; the opt-in `AuthoringDiagnostic.*` pair uses the same definition shape, no-op QI fallback, host/cache/QI/release sequence, and an ABI dispatch precheck on both targets before fixture and lifetime acceptance gates.
    - [x] Run the fixed-input JVM/Native diagnostic pair with 15 measured samples. This is diagnostic evidence only, not an accepted optimization or cross-runner comparison; complete samples, checksums, and the second rerun are recorded in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md).
    - [x] Complete the required five-run noise floor and combine it with the existing construction/allocation profiles: static definitions won all five same-run comparisons on JVM and `mingwX64`; detailed samples and analysis remain in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md).
  - [x] Close the generated Native authoring fixture compile gate for static definitions: keep the fixes in shared metadata/generator/Gradle ownership and pass `:winrt-authoring:native-component-fixture:compileKotlinMingwX64` before lifetime and catalog acceptance.
    - [x] The authored DLL compiled and its `DllGetActivationFactory`/`DllCanUnloadNow` exports were confirmed with the existing Kotlin/Native `objdump.exe` via an absolute path; the Gradle export-validation task still reports a PATH-only tool-discovery failure and must be rerun in an environment that exposes the tool without persisting a PATH change.
    - [x] Route inherited authored override bridges through the metadata `[ExclusiveTo]` base owner in the common support renderer; regenerated support contains only base-owner casts and no invalid direct derived-type bridge calls.
    - [x] Restore projection-dependent authored fixture source ownership: move Windows-only fixture implementations from upstream `commonMain` into `winuiMain` and escape the Kotlin-keyword `windows.`data`` namespace rather than adding target-specific dependencies or source paths. The Native compile now resolves every downstream Windows projection and has narrowed to four authored code-generation contract failures.
    - [x] Close the four residual authored code-generation failures: shared metadata preserves a runtime class's default async-interface projection while retaining its WinMD ABI identity, TypeDetails and support-renderer direct inbound stubs consume that shape, generic delegate `fromAbi` receives the closed IID, and static factory `WinRTOut<T>` parameters retain ABI write-back.
      - [x] Render closed generic delegate IIDs through the same metadata-owned `pinterface({generic-guid};args...)` signature path as generic interfaces, matching `.cswinrt/src/WinRT.Runtime/GuidGenerator.cs` and keeping `Metadata.fromAbi` target-neutral.
      - [x] Extend the static-factory out-parameter regression so the shared activation inventory retains both ordinary and `WinRTOut<T>`-bearing static methods before ABI write-back is validated.
    - [x] Preserve nullable projected types in generated direct-inbound getter and setter stubs: keep the non-null WinMD identity used for shape classification and ABI annotations, then reapply the shared ABI binding's Kotlin nullability to both return and parameter types; the focused generator regression, full `:winrt-generator:test`, and `:winrt-authoring:native-component-fixture:compileKotlinMingwX64` pass.
      - [x] Add a metadata-complete XAML regression fixture with a projected-interface input and ABI slot rows; the focused nullable generator test and the full `:winrt-generator:test` suite now pass.
  - [x] Use cached `HeapAlloc`/`HeapFree` entry points through the pure Kotlin/JVM native-storage adapter.
  - [x] Reject and revert replacing each host's bound cleanup function with a direct cleanup-owner slot: fixed-artifact 20-pair A/B regressed by a paired median `+0.76%` on JVM and `+5.30%` on `mingwX64`, so one source-level allocation removal did not meet the dual-target acceptance gate.
  - [x] Profile the initial identity/host path: JVM allocation and CPU samples put FFM storage writes, `createCachedCcwHost`, host/state construction, and weak-key publication ahead of any remaining type-shape work; Native A/B confirms source-level cleanup dispatch is not the next useful lever.
  - [x] Reject and revert making `CachedCcwHosts` the initial object-owned projection-state binding: the candidate preserved the global weak-key cache, generation invalidation, and managed-instance collection semantics, but fixed-artifact 20-pair A/B regressed by a paired median `+8.35%` on JVM and `+1.62%` on `mingwX64`.
  - [x] Pass the focused JVM lifecycle suite plus Native compilation, call-site lowering, and full `mingwX64Test` before rejection; skip the long-run memory gate because both target performance gates failed and the candidate was fully reverted.
  - [x] Reprofile the remaining common first-publication path: the standalone `ManagedComInboundBindingHandle` accounts for `2.18%` of sampled JVM allocation pressure, while both targets duplicate the same create/attach/detach/dispose sequence around different final registry/`StableRef` tokens.
  - [x] Move inbound-binding token lifecycle into common `ManagedComInboundBinding`: common code now creates one token per host, attaches it to every interface object, clears every slot during cleanup, and disposes it once; JVM retains only canonical-address registry mechanics and Native retains only `StableRef` mechanics.
  - [x] Accept the common inbound-token candidate after fixed-artifact 20-pair A/B. JVM baseline/candidate measured `1037.00 ns`/`1067.09 ns`, with a noise-level `+0.25%` paired median and 10/20 candidate wins; Native measured `3203.95 ns`/`2967.05 ns`, improving the paired median by `7.71%` with 12/20 wins. JVM JFR removed all sampled `ManagedComInboundBindingHandle` allocations and the class itself, while a 21-million-operation Native gate showed no call-count-correlated memory growth.
  - [x] Pass inbound-slot, registry, secondary-interface, final-release, collection, compiler-lowering, full JVM/`mingwX64` runtime, and four-runner benchmark gates. All four catalogs retain 6 families and 97 checksum-matched scenarios; the complete matrix measured Native/C++ `21.69x` and JVM/CsWinRT `0.84x` by geometric mean, with the directly affected construction scenario at Native/JVM `2852.88 ns`/`970.56 ns`.
  - [x] Reprofile the post-token common host-construction path: JVM allocation samples now attribute `22.01%` to `WinRTInspectableComObject`, `17.67%` to the one-per-host `CachedCcwHost` wrapper, `16.19%` combined to FFM native segments/Cleaner registration, and `6.79%` to the weak inbound root; CPU remains concentrated in weak-key/registry publication and cleanup.
  - [x] Remove the redundant common `CachedCcwHost` wrapper: store `WinRTInspectableComObject` directly in `CachedCcwHosts`, derive default-interface and retained-root behavior from the existing host, and preserve cache invalidation, specialized array hosts, composable forwarding, reference counts, and unchanged target adapters.
  - [x] Accept the direct-host cache candidate after fixed-artifact 20-pair A/B. JVM improved from `1088.75 ns` to `1014.18 ns`, with a paired median `-5.84%` and 13/20 wins; Native was stable at a paired median `+0.76%` and 10/20 wins while its aggregate median improved from `2963.01 ns` to `2862.67 ns`.
  - [x] Confirm the allocation and lifetime mechanism: JVM JFR removed all 217 sampled `CachedCcwHost` allocations and reduced total allocation-sample events from 1038 to 723; the candidate JAR contains no wrapper class and is 1375 bytes smaller. A 21-million-operation Native construction/release gate ended below its peak memory with checksum 1 and no call-count-correlated growth.
  - [x] Pass focused and full JVM/`mingwX64` construction/lifetime tests, compiler lowering, projection compilation, and the complete four-runner matrix. All catalogs retain 6 families and 97 checksum-matched scenarios; geometric-mean ratios were Native/C++ `21.36x` and JVM/CsWinRT `0.82x`, with the directly affected construction scenario at Native/JVM `2895.47 ns`/`775.84 ns`.
- [x] Complete value-reference host construction for `IReference<T>`, `IReferenceArray<T>`, and `IPropertyValue`: remove shared per-instance shape work only after proving retained definitions remain value-free and immutable on both targets.
  - [x] Share immutable metadata-composed closed-value shapes and pass temporary borrowed interface pointers directly to synchronous setters.
  - [x] Cache exact inbound decode plans while preserving compatibility for unknown external metadata.
  - [x] Retain synthetic scalar/array definitions by immutable closed value shape and remove the obsolete weak-shape map. Handlers read each current host's managed value, so cached definitions remain value-free and scalar/array hosts cannot leak values across instances.
  - [x] Accept the common-only synthetic-definition cache after fixed-artifact 20-pair A/B. Native improved from `44606.00 ns` to `12033.32 ns`, with a paired median `-73.76%` and 20/20 wins; JVM improved from `5118.79 ns` to `1310.95 ns`, with a paired median `-74.55%` and 20/20 wins.
  - [x] Validate definition reuse, augmentation reuse, weak managed values, scalar/array/enum/struct/delegate/nullable value isolation, and lifetime on both targets. A 21-million-operation mixed scalar/array gate ended at Native/JVM private-memory medians of `19.38 MiB`/`277.65 MiB`, with no call-count-correlated growth.
  - [x] Pass focused and full JVM/`mingwX64` runtime tests, compiler lowering, projection compilation, and the complete four-runner matrix in `11m7s`. Every runner retained 6 families and 97 checksum-matched scenarios; geometric-mean ratios were Native/C++ `21.3613x` and JVM/CsWinRT `0.8328x`.
- [x] Complete generated delegate RCW performance while preserving owned-return identity, call-scoped authoring inputs, metadata-composed generic IIDs, and one common JVM/`mingwX64` ownership sequence.
  - [x] Use one weak hot RCW lookup, publish one shared weak-reference handle, cache delegate `IReference<T>` IIDs, and reuse output scratch frames.
  - [x] Profile remaining Native nullable/new delegate allocation and cleanup costs with existing-delegate controls. `GetNewIntDelegate`, `GetExistingIntDelegate`, and `GetNullableIntDelegate` produced 767/632/633 successful instruction samples; the cold path is concentrated in Native TLS/allocation, weak-cache sweeping and hash lookup, dual alias publication, `ComPtr`/Cleaner construction, canonical identity QI, and cache publication.
  - [x] Reject carrying a canonical `IUnknown` pointer into `ComPtr`: `rcwCacheKey` performs the only identity QI, while `IUnknownReference`/`ComPtr.create` perform threading (`IAgileObject`/`IMarshal`) checks rather than a duplicate identity query. Retaining the queried pointer would add ownership complexity without removing measured duplicate work.
  - [x] Reject and revert publishing direct/canonical aliases through one weak-value record. The prior `weak-alias-hot-fixed` fixed-artifact evidence already exercised the same direct-hot-key design: new-delegate cost was noise-level (`-0.36%`, 3/5 wins), while existing-delegate cost regressed by a paired median `+2.23%` over 10 pairs. Do not repeat the weak-alias, array-cursor, or worklist cache candidates.
  - [x] Remove redundant common `ComPtr` storage: raw pointer, interface ID, borrowed/owned release policy, aggregation state, context, tracker state, and disposal state now have one authoritative home in `RawComObjectReferenceSupport`; `ComPtr` retains only `support` and `finalizationRegistration` on JVM while preserving the shared Cleaner and close sequence on both targets.
  - [x] Pass the `ComPtr` identity, focused/full JVM and `mingwX64` runtime, compiler-lowering, published runtime-only consumer, fixed-artifact delegate A/B, checksum, and long-run memory gates. The complete 97-scenario matrix remained checksum-clean and measured Native/C++ `20.6817x` and JVM/CsWinRT `0.8439x` by geometric mean.
  - [x] Reuse a call-scoped owned raw reference for managed delegate ABI inputs only when the synchronous call contract cannot escape it. `ProjectedDelegateCcwCache` now acquires the reference directly from the common CCW host and `WinRTDelegateArgumentMarshaler` releases it directly, so neither target constructs a per-call `ComPtr` or Cleaner registration; native-backed delegate unwrap/QI and every return, event, cache, or other escaping path retain owned semantics. This follows `.cswinrt`'s `ObjectReferenceValue`/`MarshalInterface.CreateMarshaler2` responsibility split without adding JVM/`mingwX64` orchestration branches.
    - [x] Accept fixed-artifact 10-pair A/B with matching checksums. JVM `ExecuteMarshalingForDelegate` improved from `469.02 ns` to `467.30 ns` (`-0.31%`, 8/10 wins); Native improved from `8417.47 ns` to `6156.47 ns` (`-26.39%`, 10/10 wins). Existing/new delegate controls remained neutral, and the JVM allocation estimate fell from `23,204.79 MiB` to `19,909.64 MiB` while removing sampled `ComPtr`, cleanup-state, raw-reference-support, and Cleaner phantom-reference allocations from the candidate.
    - [x] Pass both 21-million-call memory gates, focused and full JVM/`mingwX64` runtime tests, compiler/generator/projection lowering, Native thunk accessors, benchmark catalog parity, and the plugin-free published-runtime consumer. The complete four-runner matrix finished in `7m35s` with four 6-family/97-scenario catalogs and zero checksum mismatches; Native/C++ measured geometric mean `21.6274x`, median `28.2206x`, and 5/97 Kotlin wins, while JVM/CsWinRT measured `0.8039x`, `0.8302x`, and 59/97 wins.
- [x] Complete event callback and authored event-accessor performance without splitting JVM and `mingwX64` orchestration.
  - [x] Retain the existing-managed-object read-only lease probe after full fixed-artifact A/B and ownership review. JVM `InvokeNativeIntEvent` improved from `44.71 ns` to `43.00 ns` (`-5.59%` paired median, 7/10 wins); Native was neutral at `99.56/101.66 ns` (`-0.05%`, 5/10 wins). The historical AddRef/Release baseline is not a valid escaping-construction control because it leaves a native-retained managed interface on the unpinned borrow-ready state; current lifetime/concurrency tests pass on both targets, and 21-million-call gates retained checksum 1 without tail private-memory growth.
  - [x] Align authored event raises with `.cswinrt/src/Benchmarks/EventPerf.cs` by using the common update-time `EventRegistrationTokenTable` snapshot; the single-handler state mirrors the C# event delegate field and multi-handler state is composed only during add/remove.
  - [x] Validate single-handler and immutable multi-handler snapshots for ordering, duplicate registration, independent removal, mutation during invoke, and post-removal lifetime on JVM and `mingwX64`.
  - [x] Accept the update-time snapshot after fixed-bundle 10-pair A/B against the semantically equivalent lock-and-copy-per-raise baseline. JVM `InvokeNativeIntEvent` improved from `46.08 ns` to `41.78 ns` (`-8.75%`, 8/10 wins), while Native improved from `391.18 ns` to `89.82 ns` (`-77.10%`, 10/10 wins); the unaffected `InvokeIntEvent` control remained neutral on both targets. Existing 21-million-call Native/JVM and 105-million-call JVM gates exercise the exact candidate artifacts and show no call-count-correlated memory growth.
  - [x] Reject and fully revert carrying typed managed values through compiler-lowered callbacks. The bounded generic candidate improved the 50,000-iteration JVM event paths by `-3.45%`/`-7.31%`, but Native `InvokeNativeIntEvent` regressed by `+2.99%` with only 3/10 wins while `InvokeIntEvent` improved by `-2.22%`; the unbounded variant restored baseline JVM bytecode but regressed the Native authored-event smoke by `+3.01%` with 0/3 wins. Retain the accepted untyped common recovery contract and event snapshot rather than splitting target policy.
  - [x] Attribute common inbound binding lookup, sender decoding, and dispatch cost separately. Fixed-bundle JVM JFR attributes `29.27%` of `InvokeIntEvent` execution samples to the inbound `ConcurrentHashMap.get`, and `Long.valueOf(thisWord)` accounts for `94.82%`/`94.81%` of sampled allocation pressure in `InvokeIntEvent`/`InvokeNativeIntEvent`. Native instruction profiles completed with 765/766 samples and zero failures; the full callback exposes sender weak-RCW recovery while the binding-only authored callback isolates `pthread_getspecific`, spin-lock, emulated-TLS, and generated inbound recovery costs.
  - [x] Add one common exact-`thisWord` hot inbound binding entry. Both targets execute the same common hot lookup, binding read, decode, and dispatch sequence; platform code remains only the miss resolver. Close serializes against cache publication, invalidates by binding identity before registry/`StableRef` disposal, preserves secondary-interface and pointer-reuse correctness, and does not retain weak managed values.
    - [x] Reject the per-binding `AtomicInt` publication counter after the exact JVM `createCCWForObject`/close probe regressed by a `+9.47%` paired median across 20 alternating pairs with only 3/20 candidate wins, despite both event hot paths improving materially on JVM and Native.
    - [x] Reject one global `PlatformLock` around miss publication and binding close: it removed the per-instance atomic allocation but the exact JVM construction probe still regressed by a `+5.89%` paired median across 20 pairs with only 2/20 wins because every close entered the lock.
    - [x] Reject encoding only active publishers in the existing host cleanup atom: although focused JVM and Native lifecycle tests passed, fixed runtime `6EC7AE06AC22E68632B1724219ECF767EBD8F29A9EA61DD35A53F59AB7082B30` regressed the exact JVM construction probe by a `+4.95%` paired median across 20 pairs with only 4/20 wins. Every never-published host paid an added cleanup-state load before CAS and an unconditional hot-entry read, so the candidate does not proceed to event A/B.
    - [x] Reject storing a plain `hotEntryWasPublished` flag on each binding even after restoring the original single-CAS cleanup path for never-published hosts. Focused lifecycle tests passed on both targets, but fixed JVM runtime `FB7DD2F975D49BB3D052DFF0DFC88751855AF88AD268F11405C9368136CBB796` still regressed construction by a `+2.48%` paired median across 20 pairs with only 6/20 wins; event timing was skipped.
    - [x] Reject host-owned conditional invalidation with the published cleanup loop left inside `cleanupOnce()`. The binding layout and ordinary single-CAS state transition stayed unchanged, but fixed JVM runtime `39E66E16ECCEB976E3C47990611C360E67DD35FCEDF1D90FFC7D7E36879B7E80` still regressed construction from `919.06 ns` to `975.40 ns`: a `+5.10%` paired median across 20 pairs with only 5/20 wins. Frozen `javap -p -c` output rules out field-layout growth and shows `cleanupOnce()` expanding from bytecode offset 43 to 130 because the published drain, invalidation, and second cleanup exception region share the ordinary cleanup method. Evidence is retained under `.agent_tmp/event-inbound-hot-cache-host-owned-clear-ab-20260822`.
    - [x] Reject isolating published cleanup behind a common cold helper as a sufficient fix. Focused JVM/Native lifecycle tests passed and JVM `cleanupOnce()` shrank from bytecode offset 130 to 48, leaving the original cleanup body at offsets 0..43, but fixed runtime `BE390F437CDC603DA1DE3D97D055DF8B00568A7F0DFE39D2AE53E14C1A0FF245` still regressed construction from `994.06 ns` to `1024.03 ns`: a `+2.88%` paired median across 20 pairs with 7/20 wins. Evidence is retained under `.agent_tmp/event-inbound-hot-cache-cold-helper-ab-20260822`.
    - [x] Keep canonical managed-CCW release out of callback-cache publication. `tryReleaseManagedCcwReference()` now resolves its already-validated local identity directly through the existing platform binding resolver on both targets, while generated inbound callbacks retain the common exact-pointer cache. Focused JVM/Native lifecycle tests passed; JVM release bytecode shrank from offset 172 to 109 and contains no hot lookup, publication CAS, or hot-entry allocation. Fixed runtime `719AA84522E183C7397A0747A2BED9DE7E84E1DCD4E42E43F3F764D998816983` passed the 20-pair construction gate at baseline/candidate `976.64/963.30 ns`, a `-3.34%` paired median with 14/20 wins. Evidence is retained under `.agent_tmp/event-inbound-hot-cache-release-bypass-ab-20260822`.
    - [x] Pass the fixed complete-bundle hot-path gate with matching checksums. JVM `InvokeNativeIntEvent`/`InvokeIntEvent` improved by `-19.44%` (9/10 wins) and `-13.87%` (10/10 wins); Native improved by `-12.25%` (10/10 wins) and `-9.52%` (9/10 wins). Evidence is retained under `.agent_tmp/event-inbound-hot-cache-release-bypass-full-ab-20260822`.
    - [x] Isolate the initial JVM control regression from the runtime implementation. The complete bundle measured `+10.87%` for `AddAndInvokeMultipleIntEventsToSameEventSource` and `+6.94%` for `NativeIntEventOverhead`, both with 3/10 wins. With both sides fixed to the baseline projection, authoring, and benchmark classes and only runtime JAR `EE5263A218FE4B8B377EBB4FFF65B8F1F376265B6A4C382FA49C1D56446D7D71`/`719AA84522E183C7397A0747A2BED9DE7E84E1DCD4E42E43F3F764D998816983` exchanged, the same controls measured `-4.94%` and `-0.99%`, each with 6/10 wins. Frozen class comparison limits candidate generated changes to inbound functions that inline the common cache. Evidence is retained under `.agent_tmp/event-inbound-hot-cache-release-bypass-runtime-only-baseline-bundle-ab-20260822`.
    - [x] Evaluate separating IUnknown/IInspectable lifecycle lookup from member-callback admission, then retain the accepted single-entry member policy. CsWinRT supplies QueryInterface/AddRef/Release through `ComWrappers.GetIUnknownImpl`, while generated member callbacks use `ComInterfaceDispatch.GetInstance<T>`; every tested second-entry, direct-resolver, or shared-entry admission policy failed construction or dual-target hot/control gates, so none remains in production.
      - [x] Reject an uncached direct platform lookup as the final lifecycle path. Fixed runtime `839EFD1503CA7DCE2C0FB352E51542B8BA2002BBA28EA88E13A15C28756751BB` passed focused JVM/Native tests and the rebuilt full JVM controls/hot gates: controls measured `-13.91%`/`+1.33%`, while `InvokeNativeIntEvent`/`InvokeIntEvent` measured `-14.41%`/`-6.03%`. Rebuilt Native controls failed at `+1.72%` and `+4.97%`; an original-candidate control comparison was neutral at `+0.33%`/`-1.26%`, while the isolated lifecycle delta reproduced `-0.85%`/`+5.59%`, localizing the regression to repeated uncached `StableRef` resolution. Evidence is retained under `.agent_tmp/event-inbound-hot-cache-lifecycle-bypass-full-ab-20260822`, `.agent_tmp/event-inbound-hot-cache-release-bypass-native-controls-ab-20260822`, and `.agent_tmp/event-inbound-hot-cache-lifecycle-delta-native-controls-ab-20260822`.
      - [x] Reject unconditional publication into a distinct common lifecycle exact-pointer entry. Focused JVM/Native tests passed, but the full JVM controls regressed by `+15.12%`/`+1.17%`; a fixed projection/authoring/classes runtime-only delta reproduced `+9.80%`/`-0.59%`. Valid 200,000-operation JFR recordings found 495 candidate `ManagedComInboundHotEntry` samples versus one baseline sample; 493 candidate samples and `6.84 MiB` of estimated sampled weight came from `Cleaner-0` final-release publication. The interrupted zero-byte two-million-operation recording is excluded. Evidence is retained under `.agent_tmp/event-inbound-dual-hot-cache-full-ab-20260822`, `.agent_tmp/event-inbound-dual-hot-cache-jvm-delta-ab-20260822`, and `.agent_tmp/event-inbound-dual-hot-cache-jvm-profile-small-20260822`.
      - [x] Reject promoting a distinct lifecycle entry only after a repeated exact-pointer miss. The pointer-only delta from direct lifecycle to candidate was neutral at `+0.44%` with 9/20 wins, but the full candidate failed JVM construction by `+2.06%` with 8/20 wins and regressed `+1.62%` with only 7/20 wins against accepted member-cache runtime `719AA84522E183C7397A0747A2BED9DE7E84E1DCD4E42E43F3F764D998816983`; controls/hot/Native timing were skipped. Evidence is retained under `.agent_tmp/event-inbound-lifecycle-promotion-full-ab-20260822`, `.agent_tmp/event-inbound-lifecycle-promotion-jvm-construction-delta-20260822`, `.agent_tmp/event-inbound-direct-lifecycle-jvm-construction-delta-20260822`, and `.agent_tmp/event-inbound-lifecycle-promotion-vs-member-cache-jvm-construction-20260822`. The rejected source was removed, and a fresh JVM runtime rebuild exactly restored SHA256 `719AA84522E183C7397A0747A2BED9DE7E84E1DCD4E42E43F3F764D998816983`.
      - [x] Reject one physical exact-pointer entry with asymmetric common admission. The candidate retained one `ManagedComInboundHotEntry`, added only a pointer-word probe, let member callbacks publish on their first resolved miss, and let lifecycle callbacks replace the same entry only after a repeated miss; focused JVM tests and the complete `mingwX64Test` passed, and the 20-pair JVM construction gate was neutral at `+0.11%` with 10/20 wins.
        - [x] Run the valid low-load fixed-bundle JVM gates with adaptive minimum iteration 1. Independent hot results were `+3.18%` with 4/10 wins for `InvokeNativeIntEvent` and `-0.20%` with 5/10 wins for `InvokeIntEvent`; independent controls were `+1.27%` with 3/10 wins and `-0.93%` with 7/10 wins. The combined run had a strong B/C process-order effect and is retained without deleting samples. The earlier wrong-iteration/high-load pair remains diagnostic-only under `jvm-all-invalid-minimum-iterations-and-load-results`.
        - [x] Reject on the Native hot-path gate. The combined Native run already measured `InvokeIntEvent` at `+2.78%` with only 1/10 wins; an independent hot-only run reproduced `+4.74%` with 0/10 wins, while `InvokeNativeIntEvent` measured `+1.66%` with only 1/10 wins. The candidate cannot trade member dispatch for lifecycle admission, so allocation and long-memory gates were intentionally skipped.
        - [x] Remove the probe, lifecycle admission helper, lifecycle `findHost` switch, and candidate-only test. A fresh JVM runtime build restored the accepted 1,966,349-byte JAR exactly at SHA256 `719AA84522E183C7397A0747A2BED9DE7E84E1DCD4E42E43F3F764D998816983`; the build and every valid A/B wrapper reported `USER_PATH_UNCHANGED=True`. Evidence is retained under `.agent_tmp/event-inbound-single-entry-lifecycle-admission-full-ab-20260822`.
      - [x] Attribute the rejected candidate's Native member-dispatch regression before selecting another lifecycle policy. The candidate emitted a standalone `findHost` symbol of about `0x470` bytes, while the accepted baseline emitted no such symbol; `objdump` found seven lifecycle callback calls into that helper. The added admission body crossed Kotlin/Native's inlining threshold, so even shared-entry hits paid an extra function call on delegate QI/AddRef/Release paths.
      - [x] Reject keeping lifecycle exact-pointer hits inline while moving admission misses to one common cold helper. Focused JVM tests passed, Native executable `FA2E15398053E143401549A501C86DBBB22822C1F02B097D5898FA031CA25D9A` was only 1,024 bytes larger than baseline, `findHost` disappeared, and seven callback sites called the one cold helper only after a miss. The fixed JVM runtime `E5CB0B1D9C6C588105E0FACDAABF61B4FA0E4279DAB8E7BE117A8A1DFB111C67` nevertheless regressed construction by a `+2.93%` paired median with only 5/20 wins, so hot/control timing was skipped and the candidate was removed. Evidence is retained under `.agent_tmp/event-inbound-lifecycle-cold-admission-full-ab-20260822`.
  - [x] Complete the remaining event add/remove and multi-handler invoke decomposition on the same common JVM/`mingwX64` operation sequence. Retain the accepted immutable snapshot and inbound member cache; no additional common candidate passed the evidence threshold.
    - [x] Complete fixed-artifact JVM JFR and corrected Native main-thread profiles for 11 construction/add/remove/multi-handler scenarios. Both targets independently attribute managed-source event add work to repeated closed generic delegate IID signature rendering and SHA-1 calculation; Native profiles contain 698-749 valid samples per scenario with zero failures, and the construction control has no corresponding top-80 entry.
    - [x] Evaluate generator-owned closed delegate IIDs in authored CCW event-add handlers, then retain the existing runtime fallback after every tested storage shape failed a dual-target control gate. The metadata-owned event-helper descriptor remains the single semantic owner for closure and IID analysis, but generated JVM/`mingwX64` event-add handlers must not embed the rejected literal, captured, eager-field, lazy-owner, or ABI-word forms.
      - [x] Reject the per-add literal and handler-captured IID shapes. The literal removed parameterized-IID/SHA-1/signature rendering but retained `50.76 MiB` of sampled `Guid` parsing; direct literal-versus-capture add+invoke regressed `+51.98%` with only 1/10 capture wins.
      - [x] Reject the holder-wide eager immutable field despite its non-capturing handler. In fixed 10-pair A/B it improved all four JVM Native-source add families by `24.13-36.64%`, but JVM managed add+invoke regressed `+10.03%`; on Native, Native-source add+invoke regressed `+18.07%` and managed add/remove regressed up to `+4.72%`. The mixed result does not pass the control gate, so JFR was intentionally skipped. Evidence is retained under `.agent_tmp/event-delegate-iid-static-field-full-ab-20260822`.
      - [x] Reject one nested lazy owner per closed event IID. Source and JVM bytecode proved independent owner initialization, no holder-wide IID fields, non-capturing handlers, and no add-path `Guid(String)`/parameterized-IID/SHA-1/signature work, but fixed 10-pair A/B failed the control gates: JVM construction and managed multi-handler invoke regressed `+15.03%/+9.38%`; Native managed single/multi add and invoke controls were neutral-to-slower, while Native-source add+invoke regressed `+26.85%`. JFR was intentionally skipped. Evidence is retained under `.agent_tmp/event-delegate-iid-lazy-owner-full-ab-20260822`.
      - [x] Reject closed event IIDs emitted as two generator-computed ABI words. Generated common source contained 20 fresh `Guid.fromAbiWords(low, high)` constructions, no holder/field or target branch, and no parameterized-IID, SHA-1, signature-rendering, or delegate `Guid(String)` path; JVM bytecode confirmed non-capturing `invokedynamic () -> Function2` handlers whose add lambda performed only two `ldc2_w` loads plus `fromAbiWords(JJ)`. Fixed 10-pair A/B improved JVM Native-source add/multi-add/add+invoke/add+remove by `-27.43%/-37.40%/-26.52%/-27.87%`, but regressed construction, managed multi-handler invoke, managed add/remove, and `NativeIntEventOverhead` by `+4.41%/+6.47%/+5.90%/+12.33%`; Native improved the corresponding Native-source families by `-33.17%/-43.96%/-16.66%/-14.19%`, but regressed construction, `NativeIntEventOverhead`, managed add/remove, and managed invoke by `+1.09%/+6.46%/+1.87%/+2.06%`. The mixed control failures reject the candidate, so JFR was intentionally skipped. Evidence is retained under `.agent_tmp/event-delegate-iid-abi-words-full-ab-20260822`.
    - [x] Run the 11 opt-in common event probes once more on both targets, record the result in [BENCHMARK_RESULTS_2026-08-23.md](BENCHMARK_RESULTS_2026-08-23.md), and remove all temporary diagnostic scenarios, selection protocol, runner registration, and diagnostic-only tests. The cross-run single-to-multiple mutation direction was inconsistent, so no special-case container was added; the authoritative catalog remains 6 families and 97 scenarios.

## Deferred

- [x] Reduce generated projection size centrally without changing coordinates, public API, hot paths, Native output, or multi-module ownership: prebuilt projection JVM compilation omits duplicate `SourceDebugExtension` SMAP annotations, reducing the full Windows SDK output from 176,673,075 to 169,585,203 bytes (`-4.01%`); the complete JVM/`mingwX64` audit passes with a tightened 172,000,000-byte SDK budget.
- [x] Complete the weak-reference and cross-target convergence workstreams in [PERFORMANCE_OPTIMIZATION_PLAN.md](PERFORMANCE_OPTIMIZATION_PLAN.md), including common CCW, value-reference, delegate, event, async IID, nullable/object, and TypeName ownership.
- [x] Run the existing WinUI Controls Sample entry points on JVM and `mingwX64`; both reach `MenuFlyout.ShowAt` and exit successfully without sample-owned runtime workarounds.
  - [x] Align reference-tracker source ownership with `.cswinrt/src/WinRT.Runtime/ObjectReference.cs`: the common manager owns host registration, tracker connect/disconnect, graph callbacks, and deferred releases; last-wrapper removal queues final release before disconnect, only the real host `ReleaseDisconnectedReferenceSources` callback drains it, and runtime shutdown does not impersonate that callback. Focused tracker/composable coverage and the full JVM `460/460` and `mingwX64` `437/437` runtime suites pass.
- Keep broad feature, packaging, and sample expansion frozen until the active Controls Sample validation is closed; this is a scheduling constraint, not a completion item.

## Acceptance Gates

Every active performance slice must pass all applicable gates before completion:

- Targeted and full JVM/`mingwX64` runtime tests.
- CallSite, compiler-plugin, generator, projection compilation, and direct-lowering checks.
- Plugin-free published-runtime consumers whenever a public inline or runtime-owned implementation changes.
- One common operation-sequence audit for JVM and `mingwX64`; any remaining target divergence must be limited to and documented as an irreducible platform adapter.
- Direct JVM allocation/CPU and Native profile or machine-code attribution before implementation; source inspection alone is not acceptance evidence.
- Checksum-matched, fixed-operation A/B in even balanced `AB/BA` order. Six pairs only screen large effects; retention requires at least twelve pairs, `10/12` primary direction, a log-ratio interval excluding zero, and independently interpreted controls.
- Same-session C++/WinRT, CsWinRT, Kotlin/Native, and Kotlin/JVM catalog parity after the focused candidate passes.
- A long-run memory gate for ownership-sensitive paths.
- `git diff --check` and exact commit-scope inspection.
