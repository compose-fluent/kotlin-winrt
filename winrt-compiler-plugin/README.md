# Kotlin compiler compatibility

Compiler API compatibility is exact, including patch versions. The supported matrix is
defined in [compiler-versions.properties](compiler-versions.properties).

| Kotlin | Compiler plugin artifact | Bootstrap lowering artifact |
| --- | --- | --- |
| 2.4.0 | `winrt-compiler-plugin` | `callsite-lowering` |
| 2.4.20 | `winrt-compiler-plugin-kotlin-2.4.20` | `callsite-lowering-kotlin-2.4.20` |

The Windows toolkit Gradle plugin selects both artifacts using the consuming project's
Kotlin Gradle Plugin version. All artifacts use the same kotlin-winrt release version;
`callsite-contract`, runtime, metadata, and authoring libraries are shared.

This follows `.cswinrt/src/Authoring/WinRT.SourceGenerator/WinRT.SourceGenerator.props`:
each compiler target compiles the same source and test roots against its compiler API.
Version-specific API mechanics live under `src/compiler-compat/<version>/kotlin`.
The `IrFileImpl` constructor adapter is the current difference between 2.4.0 and 2.4.20.

Validate the matrix on Windows:

```powershell
.\gradlew.bat -p windows-toolkit-gradle-plugin :winrt-compiler-plugin:test :winrt-compiler-plugin:compiler-kotlin-2-4-20:test :winrt-compiler-plugin:callsite-lowering:test :winrt-compiler-plugin:callsite-lowering:lowering-kotlin-2-4-20:test --no-configuration-cache
.\gradlew.bat -p windows-toolkit-gradle-plugin :compilerCompatibilityTest --no-configuration-cache
```

The integration task compiles JVM and `mingwX64` consumers using their requested KGP
versions, checks both compiler classpaths, runs the Native binaries, and verifies the
unsupported-version diagnostic. Native libraries are published only to a test repository
under the toolkit's build directory.

To add a Kotlin version, register it in the matrix, add corresponding compiler/lowering
subprojects in the root and toolkit settings, and add any necessary compiler API adapter.
Include both publications and their tests in CI and the snapshot workflow. Advertise a
version only after the shared compiler tests and both consumer targets pass.
