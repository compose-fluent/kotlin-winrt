# WinRT Projection Benchmarks

`winrt-benchmarks` compares the cost of the same WinRT ABI operations through four projections:

- Kotlin/JVM through `kotlin-winrt`
- Kotlin/Native `mingwX64` through `kotlin-winrt`
- .NET 8 through CsWinRT 2.2.0
- Native C++ through C++/WinRT 3.0.260818.1

The module belongs to the validation layer. It does not define runtime contracts or generator policy.

The C++ runner's component and Windows SDK projection headers are generated together with the pinned C++/WinRT version. The shared `BenchmarkComponent.dll` retains the original TestWinRT producer, keeping its ABI workload identical across runners. Snapshots record the consumer version in `projectionVersions.cppwinrt`. Earlier 2.x snapshots remain historical baselines and must not be labeled as 3.0 results.

## Reference Mapping

The workload source of truth is the pinned CsWinRT `src/Benchmarks` copy in [`reference`](reference/README.md). The module mirrors all 97 benchmark methods from its six benchmark families without substituting another WinRT workload:

| Reference family | Scenarios |
| --- | ---: |
| `QueryInterfacePerf` | 27 |
| `EventPerf` | 14 |
| `ReflectionPerf` | 39 |
| `GuidPerf` | 11 |
| `AsyncPerf` | 4 |
| `NonAgileObjectPerf` | 2 |

All four runners use the `BenchmarkComponent` built from TestWinRT commit `aa4edcd52542cfe036473d008d3f66c8a220d59d`. Scenario names, setup and cleanup, timed operations, async completion, and checksums follow the corresponding C# benchmark methods. A shared serialized harness gives Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT the same warmup and measurement protocol. JSONL is only the result transport format.

Kotlin's synchronous scenario factories inline the operation and checksum into each batch loop, matching the C++ runner's template specialization. This avoids measuring generic lambda dispatch and primitive boxing on every iteration. Comparisons with older Kotlin runners that used generic loops include a harness change and must not be reported as runtime improvements. Async scenarios retain their existing coroutine execution model.

Every scenario validates a deterministic single-operation checksum before warmup. Each runner then calibrates that scenario toward an approximately 5 ms batch, capped at 100,000 operations. Warmup treats `warmupRounds` as a shared minimum and continues until it has covered at least 100,000 operations and 500 ms, or one second for a slow scenario, followed by five settling batches. This reduces startup effects but does not guarantee JIT stabilization: cold calibration can select a tiny batch and hit the time cap before enough operations execute. Results record the actual warmup rounds and operations; check these when assessing regressions. The report generator refuses to compare runners when their scenario sets, shared protocol parameters, or normalized checksums differ.

The `benchmarkCatalogParity` gate derives the authoritative catalog directly from the `[MemoryDiagnoser]` classes and `[Benchmark]` methods in `reference`, then compares it with the catalogs exported by all four built runners. It also checks the explicitly listed supplemental scenarios. A renamed, missing, unexpected, or duplicated scenario fails the gate.

Gradle finishes the Kotlin build prerequisites before timing and serializes the four runner processes. This keeps project-parallel builds or another benchmark runner from competing with the active measurement.

## Prerequisites

- Windows x64
- JDK 25
- .NET 8 SDK or newer with the .NET 8 targeting pack
- Visual Studio or Build Tools with `Desktop development with C++` and an x64 MSVC toolset
- Windows SDK 10.0.26100.0 with C++/WinRT headers

The Gradle wrapper downloads the Kotlin and Kotlin/Native dependencies. The C++ runner discovers Windows Kits from the installed-kits registry first, then `KOTLIN_WINRT_WINDOWS_SDK_ROOT`, and finally the default location. It reports the exact missing Visual Studio or Windows SDK component instead of silently skipping it.

TestWinRT declares `BenchmarkComponent` as a Windows Store project even though it targets the desktop API family and CRT. The build overrides that application type and produces a desktop DLL with the same compiler options, so the Universal Windows Platform build tools are not required.

## Run

Run the complete matrix and generate Markdown plus JSON reports:

```powershell
.\gradlew.bat :winrt-benchmarks:benchmarkAll
```

Focused runners are also available:

```powershell
.\gradlew.bat :winrt-benchmarks:benchmarkKotlinJvm
.\gradlew.bat :winrt-benchmarks:benchmarkKotlinNative
.\gradlew.bat :winrt-benchmarks:benchmarkCsWinRT
.\gradlew.bat :winrt-benchmarks:benchmarkCppWinRT
.\gradlew.bat :winrt-benchmarks:benchmarkCatalogParity
```

Use Gradle properties to tune a run without changing the shared protocol. `warmupRounds` and `iterations` are minimums, not fixed counts:

```powershell
.\gradlew.bat `
  '--project-prop=kotlinWinRT.benchmarks.warmupRounds=8' `
  '--project-prop=kotlinWinRT.benchmarks.measurementRounds=25' `
  '--project-prop=kotlinWinRT.benchmarks.iterations=1' `
  '--project-prop=kotlinWinRT.benchmarks.filter=QueryInterfacePerf.QueryDefaultInterface,ReflectionPerf.ExecuteMarshalingForString' `
  :winrt-benchmarks:benchmarkAll
```

Results are written under `winrt-benchmarks/build/results/benchmarks`; the comparison report is written under `winrt-benchmarks/build/reports/benchmarks`.

For stable numbers, close unrelated workloads, use a fixed power mode, and compare repeated full runs. The benchmark reports ratios but intentionally does not enforce a pass/fail threshold until a repository baseline and accepted noise budget are established.

## Saved binaries, independent forks, and memory

Build a new, immutable snapshot before changing runtime code (use a different directory after the change):

```powershell
.\gradlew.bat :winrt-benchmarks:benchmarkSnapshot '-PkotlinWinRT.benchmarks.snapshot=E:/bench-results/before'
pwsh -File winrt-benchmarks/scripts/Measure-BenchmarkSnapshot.ps1 -Snapshot E:/bench-results/before -OutputDirectory E:/bench-results/before-timing
pwsh -File winrt-benchmarks/scripts/Measure-BenchmarkSnapshot.ps1 -Snapshot E:/bench-results/before -OutputDirectory E:/bench-results/before-memory -Memory
```

The script runs all 97 reference scenarios and the supplemental scenarios on all four runners, with three fresh process forks per scenario by default. Builds finish before measurement. Each fork uses eight minimum warmup rounds and 25 timing samples; runner order reverses on alternate forks. A timeout fails the run instead of silently omitting a scenario. `-Runners`, `-Scenarios`, and `-Forks` narrow diagnostic runs; use the unfiltered matrix for validation. Snapshots record revision, tracked working-tree changes, launch arguments, binaries, reference assets, and catalogs. Keep the snapshot at its original absolute path, and retain the configured JVM/.NET installations.

Compare completed runs with `Compare-BenchmarkSnapshots.ps1 -Before <before-results> -After <after-results> -OutputPath <comparison.json>`. It rejects incomplete matrices, duplicate forks, protocol/checksum mismatches, and mixing timing with memory mode. It reports medians across forks plus the individual fork values; overlapping ranges require more runs before claiming a gain or regression.

For very short operations, use more samples, for example `-MeasurementRounds 1000` (up to 10,000). The runners cap calibrated batches at 100,000 operations, including an `iterations` request above that limit; check the recorded `iterations` rather than assuming a larger request lengthened the measurement. Use identical sample counts on both snapshots, and keep extended diagnostic runs separate from the standard matrix.

For JVM regression checks affected by cold calibration, specify both `-WarmupRounds 200 -MinimumIterations 5000`. This guarantees at least one million warmup operations before timing, while allowing calibration to increase the batch. Apply the same parameters to both snapshots and inspect the sample distribution across fresh forks. `-MinimumIterations` overrides the snapshot's iteration argument only when supplied; otherwise its launch arguments remain in effect. The run manifest records the requested warmup rounds, measurement rounds, and iteration override.

`measurements.jsonl` contains timings and 50 ms samples of process working set and private bytes. These include process startup, runtime, native COM allocations, and allocator reserve; they are not live object size. In `-Memory` mode a separate, checksum-validated batch after timing records:

* JVM and CsWinRT: current-thread managed allocated bytes and operation count. Divide to obtain B/op; worker-thread and native allocations are excluded.
* JVM, Native and CsWinRT: managed heap occupancy before and after that batch, with two collection requests and the result sink cleared. Scenario setup stays alive. Deltas may be negative and are not proof of a leak or a per-operation allocation count.
* Native: cumulative allocated bytes are explicitly `null`/`unavailable`; the public `GC.lastGCInfo` API provides occupancy, not a cumulative allocation counter. C++ has no managed heap metrics. Both still have process memory samples.

Run memory diagnostics separately from timing comparisons because forced collections change subsequent execution. Per-scenario process isolation prevents previous scenarios from retaining objects or filling caches. Native collections are synchronous; JVM collection requests rely on the standard JVM configuration (do not disable explicit GC). Native memory excludes COM/native heaps; use the process measurements alongside it.

The comparison includes managed-memory medians and individual allocation samples where available. Allocation batches run immediately after collection: weak-reference cleanup and cache repopulation can contribute to their allocation counts, so these are post-collection diagnostics, not guaranteed steady-state B/op. Large differences between forks require investigation rather than averaging them away.

The 97 scenarios match reference *operations*, not necessarily equal projection work. For example, `ExecuteMarshalingForExistingArray` and `ExecuteMarshalingForExistingNullable` return `IInspectable` in C++, whereas CsWinRT/Kotlin project boxed values into language values. These ratios include unboxing/projection costs and must not be described as pure ABI-call overhead. Keep the original catalog unchanged for continuity.

`ProjectionStressPerf.SetMixedNullableValues` supplements the reference catalog. One measured operation writes `Int32(4)`, `BlittableStruct(2)`, and `TimeSpan(100 ticks)` in that order on the same component instance. This alternates three boxed types with warm per-type caches and exposes allocation costs hidden by single-type hot caches. All four runners execute those same three setters; reported time and allocation are per three-write operation. It is not part of the original 97-scenario aggregate.

Dictionary lookup comparisons also include projection semantics: the C++ scenario uses `Lookup`, while CsWinRT/Kotlin's `TryGetValue` path checks `HasKey` before `Lookup`. The extra check follows CsWinRT's handling of dictionaries with nonstandard missing-key errors. Do not remove it just to equalize ABI call counts. Exact Native allocation counts and supplemental microbenchmarks with equal ABI work remain coverage gaps.

## Compilation performance

Measure compilation independently, without runtime benchmarks competing for CPU:

```powershell
pwsh -File winrt-benchmarks/scripts/Measure-Compilation.ps1 `
  -BuildTask :winrt-runtime:compileKotlinMingwX64 -CleanTask :winrt-runtime:clean `
  -ProbeFile E:/repo/kotlin-winrt/winrt-runtime/src/commonMain/kotlin/CompilationBenchmarkProbe.kt `
  -OutputDirectory E:/bench-results/native-compile
```

Use `:winrt-runtime:compileKotlinJvm` for JVM, or specify a sample's build/link and clean tasks to measure the full projection/application pipeline. The probe must be a new `.kt` file in a compiled source set. The script preserves existing files, changes the probe's function body, and removes it in `finally`. Three rounds measure selected-module clean builds, unchanged builds, and single-file body edits; cleaning is outside the timed build. Build cache and configuration cache are disabled, dependency caches are retained, and Gradle daemon startup can affect the first round. Each result includes task outcomes and a Gradle profile for inspecting compile/link costs. Compare identical tasks, toolchain, arguments, and machine conditions; this is not a cold dependency-download benchmark. `-GradleArguments` passes machine-specific settings or init scripts without changing repository configuration.

### Snapshot retention

Keep only the baseline and candidate needed by the active comparison. Once a comparison is complete, retain its measurements and version metadata, then remove superseded executables and dependencies. Snapshots contain runtime assets only; reference source checkouts, headers, and compiler tools are not copied.

For experiments saved under the repository's root `build` directory, preview and apply cleanup with the exact directory names to retain:

```powershell
& ./winrt-benchmarks/scripts/Remove-BenchmarkArtifacts.ps1 -KeepSnapshotNames @('perf-before', 'perf-after')
& ./winrt-benchmarks/scripts/Remove-BenchmarkArtifacts.ps1 -KeepSnapshotNames @('perf-before', 'perf-after') -Apply
```

Call the script directly from PowerShell when passing an array: `& ./winrt-benchmarks/scripts/Remove-BenchmarkArtifacts.ps1 -KeepSnapshotNames @('perf-before', 'perf-after') -Apply`. Do not clean a snapshot while its runners are executing. Cleanup preserves measurement files, catalogs and snapshot manifests; removed snapshots can still be compared through their saved results but must be rebuilt to run again. It also removes old standalone test binaries and profiling dumps in performance experiment directories. Source trees, module build outputs and dependency caches are outside its scope. The command defaults to a preview and rejects paths outside the repository build tree or paths containing directory links.
