# WinRT Projection Benchmarks

`winrt-benchmarks` compares the cost of the same WinRT ABI operations through four projections:

- Kotlin/JVM through `kotlin-winrt`
- Kotlin/Native `mingwX64` through `kotlin-winrt`
- .NET 8 through CsWinRT 2.2.0
- Native C++ through C++/WinRT 2.0.201113.7

The module belongs to the validation layer. It does not define runtime contracts or generator policy.

## Reference Mapping

The source of truth is `.cswinrt/src/Benchmarks`. The module mirrors all 97 benchmark methods from its six benchmark families without substituting another WinRT workload:

| Reference family | Scenarios |
| --- | ---: |
| `QueryInterfacePerf` | 27 |
| `EventPerf` | 14 |
| `ReflectionPerf` | 39 |
| `GuidPerf` | 11 |
| `AsyncPerf` | 4 |
| `NonAgileObjectPerf` | 2 |

All four runners use the `BenchmarkComponent` built from TestWinRT commit `aa4edcd52542cfe036473d008d3f66c8a220d59d`. Scenario names, setup and cleanup, timed operations, async completion, and checksums follow the corresponding C# benchmark methods. A shared serialized harness gives Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT the same warmup and measurement protocol. JSONL is only the result transport format.

Every scenario validates a deterministic single-operation checksum before warmup. Each runner then calibrates that scenario toward an approximately 5 ms batch, capped at 100,000 operations. Warmup treats `warmupRounds` as a shared minimum and continues until it has covered at least 100,000 operations and 500 ms, or one second for a slow scenario, followed by five settling batches. This keeps JIT tier transitions and process ramp-up out of the measurement window without forcing slow scenarios through 100,000 calls. Results record the actual warmup rounds and operations. The report generator refuses to compare runners when their scenario sets, shared protocol parameters, or normalized checksums differ.

The `benchmarkCatalogParity` gate derives the authoritative catalog directly from the `[MemoryDiagnoser]` classes and `[Benchmark]` methods in `.cswinrt/src/Benchmarks`, then compares it with the catalogs exported by all four built runners. A renamed, missing, extra, or duplicated scenario fails the gate.

Gradle finishes the Kotlin build prerequisites before timing and serializes the four runner processes. This keeps project-parallel builds or another benchmark runner from competing with the active measurement.

## Prerequisites

- Windows x64
- JDK 25
- .NET 8 SDK or newer with the .NET 8 targeting pack
- Visual Studio or Build Tools with `Desktop development with C++` and an x64 MSVC toolset
- Windows SDK 10.0.26100.0 with C++/WinRT headers

The Gradle wrapper downloads the Kotlin and Kotlin/Native dependencies. The C++ runner discovers Windows Kits from `KOTLIN_WINRT_WINDOWS_SDK_ROOT`, the installed-kits registry, or the default location, and reports the exact missing Visual Studio or Windows SDK component instead of silently skipping it.

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
