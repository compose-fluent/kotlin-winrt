# WinRT Projection Benchmarks

`winrt-benchmarks` compares the cost of the same WinRT ABI operations through four projections:

- Kotlin/JVM through `kotlin-winrt`
- Kotlin/Native `mingwX64` through `kotlin-winrt`
- .NET 8 through CsWinRT 2.2.0
- Native C++ through the C++/WinRT headers in Windows SDK 10.0.26100.0

The module belongs to the validation layer. It does not define runtime contracts or generator policy.

## Reference Mapping

The harness follows `.cswinrt/src/Benchmarks/Program.cs` for x64 Release execution and repeated warmup/measurement jobs, and `.cswinrt/src/Benchmarks/QueryInterface.cs` for activation, default-interface, non-default-interface, scalar, string, and object-return coverage. A shared harness replaces BenchmarkDotNet so C++/WinRT executes the identical sampling protocol.

All runners use `Windows.Data.Json`, which is implemented by Windows itself. This avoids registration and deployment differences from a custom component and keeps the comparison focused on projection, interface lookup, marshaling, and wrapper costs.

| Scenario | Timed operation |
| --- | --- |
| `activate_json_object` | Activate `JsonObject` and read `IJsonValue.ValueType` |
| `get_value_type` | Read a non-default interface scalar property on an existing object |
| `get_array_number_at` | Read a Double through an expanded scalar-result call on an existing array |
| `get_named_boolean` | Marshal an HSTRING input and return a Boolean |
| `get_named_string` | Marshal an HSTRING input and return an HSTRING |
| `stringify` | Invoke `IJsonValue.Stringify` and return an HSTRING |
| `parse_get_named_number` | Invoke a static factory, wrap the returned object, and read a Double |

Every scenario validates a deterministic checksum before warmup. The report generator refuses to compare runners when their scenario sets, iteration parameters, or checksums differ.

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
```

Use Gradle properties to tune a run without changing the shared protocol:

```powershell
.\gradlew.bat `
  '--project-prop=kotlinWinRT.benchmarks.warmupRounds=8' `
  '--project-prop=kotlinWinRT.benchmarks.measurementRounds=25' `
  '--project-prop=kotlinWinRT.benchmarks.iterations=20000' `
  '--project-prop=kotlinWinRT.benchmarks.filter=get_value_type,get_named_string' `
  :winrt-benchmarks:benchmarkAll
```

Results are written under `winrt-benchmarks/build/results/benchmarks`; the comparison report is written under `winrt-benchmarks/build/reports/benchmarks`.

For stable numbers, close unrelated workloads, use a fixed power mode, and compare repeated full runs. The benchmark reports ratios but intentionally does not enforce a pass/fail threshold until a repository baseline and accepted noise budget are established.
