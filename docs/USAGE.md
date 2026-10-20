# Kotlin/WinRT usage reference

For installation and Gallery downloads, start with the [README](../README.md). To create a shared Native/JVM XAML application, follow the [WinUI quick start](QUICKSTART.md) or the source-tree [UWP guide](UWP.md). This reference covers projection selection, packaging and custom hosts.

## Targets

Current supported validation targets are:

- JVM: uses JDK 25 and the `java.lang.foreign` FFM API for the Win32/COM bridge.
- `mingwX64`: supports runtime calls, generated projections, native executables, and native authored WinRT component exports for the implemented surface.

WinUI validation runs through both the generated JVM application host and the `mingwX64` executable path.

The application model, XAML framework, package identity and Windows App SDK deployment
are separate settings:

| Application | `applicationModel` | `windows.xaml.framework` | Package and deployment |
| --- | --- | --- | --- |
| WinUI 3 | `Desktop` (default) | `WinUI` (default) | Packaged or unpackaged; Windows App SDK deployment selected independently |
| UWP system XAML, including WinUI 2 controls | `UWP` | `UWP` (inferred from the application model) | `Packaged` and Windows App SDK deployment `None`; bundled JVM when targeting JVM |

WinUI 2's `Microsoft.UI.Xaml` namespace does not select WinUI 3: its controls extend
`Windows.UI.Xaml`. XAML libraries must set their framework explicitly because they
have no application model. UWP builds currently require a matching local compiler
package or an explicitly configured UWP compiler release and checksum; see the
[UWP guide](UWP.md#supply-the-uwp-compiler). UWP packaged execution remains under
integration validation, separate from the existing WinUI validation.

The Windows toolkit supports Kotlin **2.4.0** and **2.4.20**. It selects compiler plugin artifacts for the project's exact Kotlin Gradle Plugin version, for both JVM and `mingwX64`; no version override is required. Other compiler versions fail with a supported-version diagnostic before loading the IR plugin. Runtime, metadata, and generated projection libraries share the same publication version across these compiler targets.

Gradle builds running on JDK 21 can declare the Windows toolkit with `apply false`. Applying the plugin and compiling WinRT/WinUI still require Gradle to run on JDK 25 or newer; the plugin checks this before loading its WinRT tools.

## Modules

- `winrt-runtime`: WinRT ABI, COM interop, activation, marshaling, object identity, WinUI bootstrap, and runtime helpers.
- `winrt-metadata`: WinMD loading and normalized metadata model construction.
- `winrt-generator`: Kotlin source generation for WinRT and WinUI projection bindings.
- `winrt-compiler-plugin`: compiler-visible projection and authoring support.
- `winrt-projections`: generated projection output and prebuilt Windows SDK / Windows.UI.Xaml / Windows App SDK projection artifacts.
- `winrt-authoring`: Kotlin-authored WinRT type, TypeDetails, host manifest, and native export support.
- `winrt-benchmarks`: projection-cost comparisons across Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT.
- `winrt-samples`: validation applications and sample surfaces.

## Dependency setup

`type` and `namespace` are optional projection filters. An explicit local WinMD input
without either filter keeps its complete projection surface, including when other libraries
already own dependency types. Dependency and authoring roots expand an explicit filter;
they never turn an unfiltered input into a dependency-only selection. Reuse published SDK
projections for routine consumers; generating an entire SDK locally still has an initial cost.
With explicit filters, authored base types, interfaces, overrides and activation/static factories
are additional roots for both reference and implementation declarations. Their metadata signature
closure is retained. Types reached only through dynamic names must be included explicitly (or
provided by the shared SDK owner); this is type selection, not method-level trimming.
Task-graph selection reads only the declared projection inputs; dependency type subtraction
and WinMD analysis run inside generation tasks. XAML compiler-discovered roots augment an
existing local projection selection; they do not create a projection owner in a consumer of
precompiled SDK projections. Such consumers obtain the required XAML types from that SDK;
declare local metadata or enable SDK projection generation when producing missing bindings.
If an explicit selection is entirely provided
by dependencies, the existing module support anchor keeps the selected Native KLIB valid
without duplicating projected types. Consumers declaring no local projection inputs skip that module.
Their business compilations still merge authoring registrar and dependency support, so defining
authored WinRT classes does not require re-enabling local SDK projection generation.
The component's activation factories, CCW support, and host exports are generated separately
under `authoring-support` and compiled with its business declarations. Native components
therefore retain `DllGetActivationFactory` and `DllCanUnloadNow` even when dependencies own
all imported SDK projections; IDE import uses reference declarations for the same support.

The `winui-kmp-base-library` → `winui-kmp-library` → `winui-kmp-app` sample uses the
shared `windows-sdk` and `windows-app-sdk` projection modules without consumer `type` or
`namespace` lists. Its SDK versions follow `kotlinWinRT.projections.windowsSdkVersion` and
`kotlinWinRT.projections.windowsAppSdkVersion`, so metadata tools and projection owners agree.
Building these owners from source incurs their first-build cost; published consumers reuse
their contract and implementation artifacts.

WinRT dependency identities record three independent compatibility versions:
`projectionShapeVersion` for generated declaration/mapping policy,
`projectionRuntimeAbiVersion` for generated-code runtime symbols and ownership semantics,
and `projectionCompilerProtocolVersion` for compiler support descriptors. SDK and package
versions are separate. The toolkit rejects unsupported explicit versions before using a
dependency's type ownership or merging its compiler support; it cannot safely regenerate
types already defined by an incompatible library.

Ownership lists are read from top-level JSON arrays and malformed lists are rejected.
Identities declare `projectionOwner` (group and archive name) and `projectionOwnerVersion`.
Custom publication coordinates must keep this owner stable or configure the identity tasks'
owner explicitly. Identical identities resolved at multiple paths share that owner; conflicting
versions or contents for one owner fail. Compiler-support merging checks projection and
source-addition names in one ownership namespace, including when the consumer emits no local
projection. Different owners claiming the same type remain an error. The identity-content
fingerprint is not a Kotlin binary ABI fingerprint and does not permit merging duplicate JARs
or KLIBs: dependency resolution must still provide one implementation artifact per owner.

Identities also record the resolved projection dependency closure in `dependencyOwners`
(owner, version and identity-content fingerprint) and deterministic generation-input hashes
in `generationInputFingerprints`. These contain no machine-local source paths. Dependency
records use the design-time projection identities, so producing them does not link authored
hosts or build dependency implementation binaries.
The resolved graph must match those recorded owners. A library built against a different SDK
identity must be rebuilt against the selected SDK; a dependency resolver's version upgrade alone
does not prove projection compatibility. This is intentionally stricter than binary API matching.

Generated registrations also enforce one Kotlin type identity per WinRT metadata name in
each shared runtime. Repeating the same registration is supported, and an older registration
may omit an IID that a later registration supplies. Different classes, kinds, base types or
conflicting IIDs are rejected. On JVM, equal class names loaded by different ClassLoaders
remain different identities; share the projection and runtime through a common loader.
Separate runtime/ClassLoader universes are not an object-sharing mechanism. Native consumers
must link one owner KLIB for each projected type. Custom runtime aliases retain their own
mapping rules; they are not independently generated projection owners.

Every published identity must include the complete schema, projection shape, runtime ABI
and compiler protocol version tuple. Unversioned or partially versioned identities require
rebuilding with the current toolkit; there is no 0.1.0 migration path. A projection
compilation also reads ABI constants from the resolved contracts and runtime implementation
libraries, rejecting missing or incompatible markers. Overriding the runtime dependency
therefore cannot bypass validation by keeping a newer toolkit installed.
Kotlin compiler/KLIB compatibility and shared publication ownership are separate checks.

Local JVM projection compilations produce a separate reference class directory using the
matching Kotlin compiler's `jvm-abi-gen` plugin. Business compilations use that reference
directory (including Kotlin metadata and inline bodies), while archives and execution use
the implementation classes. Changes to ordinary projection method bodies therefore do not
change the business compiler's projection input. Reference classes are compile-only and
must never replace implementation classes on the runtime classpath.

`generateWinRTReferenceProjections` derives declarations from the same KotlinPoet tree as
the implementation. `compileKotlinWinRTProjectionJvmReference` compiles them independently;
business compilation does not require the full projection compilation. Inline bodies,
default expressions, constants, delegation metadata and inbound compiler recipes are retained.
Support sources without a structured declaration tree currently retain their original bodies.
The CLI exposes the same generation mode as `--reference`.

Reference generation still plans the complete selected declaration surface. It is not FIR
declaration injection or method-level reachability compilation. A producer's first build may
compile both reference and implementation artifacts, so the split does not promise a faster
first source build. Reusing published SDK owners, avoiding implementation work during import,
and keeping implementation-only changes out of consumer contracts are the intended savings.

`verifyKotlinWinRTProjectionJvmReferenceAbi` compares the two compiler-produced ABI inventories,
including Kotlin metadata and inline code, excluding debug locations and declaration-free file
facades. Run it when changing generator rules. Other JVM target names use the corresponding
task suffix.

JVM API variants publish a `winrt-reference` JAR containing both the independent projection
and the main compilation's Kotlin overlays, metadata and inline code. The main compilation
remains incremental: unchanged reference files survive partial compilation and deleted types
are pruned against the implementation output. Java overlays are included conservatively with
their bodies. Runtime variants keep the implementation JAR. Gradle Module Metadata preserves
this selection and transitive dependencies; Maven/POM-only consumers use the implementation
JAR for both compilation and runtime. Never place the `winrt-reference` classifier on a runtime
classpath. Project consumers selecting the classes variant receive the same complete surface.

IDE import (`prepareKotlinIdeaImport`, `ideaModule`, or `idea.sync.active=true`) exposes
the reference declaration roots. Import preparation runs `generateWinRTReferenceProjections`
and `mergeWinRTReferenceCompilerSupport`, without compiling or generating the implementation
projection. A cold import needs no preceding build. The same cacheable tasks repair deleted
outputs and replace declarations when WinMD inputs change. Normal builds use implementation
roots for generated authoring code; do not combine IDE import mode with Native implementation
compilation in one invocation.
Dependency `prepareWinRTIdentity` tasks also consume reference metadata, including authored
type and host declarations. A project diamond therefore does not reintroduce implementation
generation through its identity producers. Packaging identities still use implementation outputs.
Authoring TypeDetails and registrar declarations use the same reference pass as projections,
preserving source-set ownership and compiler inbound recipes. Their registration and CCW
construction bodies remain in the implementation output used by normal compilation.
Authored types exposing `IMap<K, V>` (including the required map of `IPropertySet`)
must implement Kotlin `MutableMap<K, V>`. TypeDetails adds the runtime's closed dictionary
and iterable ABI definitions to the authored object's existing CCW. Implementing similarly
named `lookup`/`insert` methods alone is insufficient for the mapped managed contract.
Likewise, authored `IClosable` uses the runtime mapping to `AutoCloseable`.

`compileWinRTProjectionReferenceKotlinMingwX64` produces an independent Native reference KLIB
with the implementation's module identity. `verifyWinRTProjectionReferenceAbiMingwX64` checks
manifest compatibility, declaration ABI and inlinable IR using the selected Kotlin/Native
compiler. Linking must select the implementation KLIB for that identity; reference KLIBs
contain throwing bodies and are never runtime libraries. Toolkit consumers select the
`io.github.composefluent.winrt.native-projection-artifact=reference` variant for compilation
and select the `demand` variant for binary linking. The reference variant
includes the main business module's official header KLIB (`winrt-main-reference`), retaining
defaults and inline bodies. The demand variant carries business KLIBs, semantic usage manifests
(`winrt-native-uses`) and portable normalized metadata recipes (`winrt-native-recipe`).
Project dependencies support KGP's unpacked KLIB variant as well.
Published Gradle Module Metadata carries this selection; Native consumers must retain it.
Consumers without the toolkit use the implementation variant for both stages. Do not add
the `winrt-projection-reference` or `winrt-main-reference` classifier as an ordinary transitive dependency.

Native header KLIBs can retain source offsets even when their semantic contract fingerprint
is unchanged. Ordinary business-library body edits can therefore still invalidate downstream
Native compilation; projection implementation isolation does not promise identical incremental
behavior for every business-library edit. Final Native release linking also processes the
implementation graph and can require substantially more time and heap than contract compilation.
Native registration follows typed uses in each compiled body, including concrete generic
arguments, base/interface types and literal WinRT type names. A referenced type initializes its
own metadata; there is no module initializer that roots every SDK type. Unreachable bodies
and their registration dependencies can be removed together. Registration expressions are emitted
directly at each use, avoiding both exported helper roots and cross-file private KLIB symbols. Libraries retain their canonical
projection owner, and consumers do not need `type`/`namespace` filters for this behavior.
Names constructed dynamically are not discoverable by this pass; their projected types must
also have a typed use. Before linking, `<binary>WinRTDemandImplementation` merges usage from
the application and its transitive business libraries, closes metadata dependencies, and generates
and compiles an implementation KLIB for each original projection module identity. SDK projection
declarations are shape inputs, not discovery roots. The IDE/reference contract remains complete.
SDK COM overlays are generated separately, so ordinary Native linking does not require full SDK
implementation source generation or compilation. Explicit full implementation publication and
`verifyWinRTProjectionReferenceAbiMingwX64` remain available and still compile the full implementation.

Executable and shared-library trimming differ. Kotlin/Native 2.4.0's
`DevirtualizationAnalysis.computeRootSet` treats public functions and virtual-call targets
as roots when there is no program entry point. An authoring DLL can therefore retain SDK
types that the executable removes, even without a global projection registration table.
Demand generation bounds that retention to the selected type closure before the optimizer runs.
Within that type closure, Native implementation generation omits unreferenced runtime-class
static methods, properties and events. Semantic calls and callable/property references from
all business libraries retain the canonical owner's member family: overloads, getter/setter
pairs and event add/remove entry points stay together. A literal runtime type name retains
that owner's static surface conservatively. This happens before compiling implementation sources;
the complete IDE/JVM/reference declarations are unaffected. Old Native usage manifests must be
rebuilt with the current compiler plugin, rather than silently interpreted as empty member demand.
Native instance calls and callable/property references also contribute semantic member demand.
The local implementation drops unused instance member families together from interface declarations,
RCW proxies and runtime-class forwarding, including Kotlin interface dispatch targets. Overloads,
accessor pairs, event entry points and authoring bridge aliases stay together within each type.
Demand propagates between a type and its base/interface member surfaces in both directions:
retained interface members keep their implementations, and retained overrides keep their base
declarations. Unrelated types no longer retain each other's homonymous members. The closure is
shared across projection owners, including inherited declarations absent from the derived metadata.
Overloads within one type remain a family; this is not overload-signature-level trimming.
Event helper subclasses follow that same instance demand closure, so unused events do not
leave helper implementations behind. Static events and required generic/authoring surfaces
keep their existing conservative retention rules.
Private interface projection caches are omitted when the type/inheritance demand closure
keeps no instance member on that interface. This removes unused lazy initializers before
Native compilation; retained dispatch, CCW obligations and original ABI slots are unaffected.
Native CCW input discovery follows retained instance bindings and converges with the
type/inheritance demand closure. A required CCW retains its complete interface and discovers
further parameter dependencies before emission. Unused instance signatures no longer root
CCW factories and marshaling bodies. Static paths and unknown bindings remain conservative.
Native usage manifests also distinguish construction from type references. Activation and
composable factories, their constructors and marshaling call sites are emitted only for
constructed types, exposed inheritance and name-based uses, including the required base chain.
RCW wrapping, public reference contracts and original ABI slots remain unchanged. Producers
with older usage manifests must be rebuilt so missing construction data cannot cause unsafe pruning.
Business inheritance, ABI input interfaces, protected/overridable WinRT
callbacks, generic and mapped adapters retain their required complete surfaces automatically.
Factories, metadata and marshaling helpers remain conservative. Type signatures still participate
in the full metadata closure; original ABI slots are never compacted or renumbered. Full reference
contracts and published library identities remain unchanged: only the final local implementation
KLIB is sliced, after merging the usage manifests from every business dependency. These private
KLIBs must not be published as substitutes for the complete reference contracts/recipes.
Implementation KLIBs compile in owner dependency order against the corresponding sliced dependency
KLIBs, so cross-owner interface obligations agree at compilation and linking. Business compilations
continue to use complete references. Registration bookkeeping is injected after demand collection,
so generated type-name literals do not accidentally retain an owner's entire member surface.
Within one Gradle build, consumers with identical generated sources, support manifests,
dependency contents, compiler/plugin inputs and compiler options reuse the successful implementation
compilation. Concurrent consumers wait for that result; different demand inputs compile separately.
This reuse requires no configuration and adds no persistent cache across builds. Normal Gradle
up-to-date checks still apply to each consumer's outputs.
Changing the selected members regenerates the implementation before linking. Stale usage manifests
fail with a rebuild diagnostic; they are never interpreted as an empty instance demand set.
An exclusive interface used only as a static factory's ABI retains its declarations, IID and
original slots, but does not need an object proxy or wrapper factory. Native demand generation
omits that proxy only when no business type/member reference, ordinary signature, inheritance
edge or non-static factory role requires the projected interface. The original metadata model
remains intact for static call planning and cross-library contract identity.
DLLs can still retain more of this bounded graph than executables.
The KMP WinUI sample links with `-Xpartial-linkage-loglevel=error` and checks both required
and unused runtime type names. Missing KLIB symbols must fail validation instead of producing
runtime traps that make an invalid executable appear smaller.

This repository runs the Native compiler in a separate JVM with an 8 GiB G1 heap
(`kotlin.native.disableCompilerDaemon` and `kotlin.native.jvmArgs`). Kotlin/JVM compilation
also uses an 8 GiB G1 compiler daemon (`kotlin.compiler.execution.strategy` and
`kotlin.daemon.jvmargs`) for the full SDK reference contract. Gradle retains its 4 GiB G1
heap for the build/IDE model. Release uses the standard Kotlin/Native optimization pipeline. These heap
settings are local build configuration; the toolkit does not override a consuming project's
JVM memory settings.

Projection generation records `kotlin-winrt-support/projection-input-contract.tsv` beside
its support descriptors. Its deterministic fingerprint covers normalized metadata,
declaration policy, suppressed dependency declarations, support ownership and protocol
versions. Source locations, output directories, diagnostics and implementation bytecode
are excluded. The source cache combines that fingerprint with a separate implementation
fingerprint. This is a conservative generation-input contract, not a published Kotlin ABI
hash: metadata row/slot details remain significant, and the support owner is not yet a
canonical Maven publication owner. Reference and implementation generation have distinct cache keys.

Compiled contracts have separate fingerprints. JVM reference JARs include
`META-INF/kotlin-winrt/binary-contract.json`, derived from the official ABI output including
Kotlin metadata and retained inline code. Java/ASM method bodies and debug locations are
excluded from that fingerprint. Native publications attach a `winrt-contract` JSON classifier,
derived from official header KLIB IR for both the projection and business overlays. This retains
default expressions and inline/reified code while excluding ordinary bodies and serialized
source offsets. Packed and unpacked headers are supported. Compiler, runtime ABI, compiler
protocol and projection-shape versions participate in the contract; the producer release version
is recorded separately. WinMD versions and generator implementation fingerprints remain distinct.

Use the complete [Maven Central plugin resolution example](../README.md#create-your-application) in the root README. While Gradle Plugin Portal approval is pending, map the plugin ID directly to the Maven Central implementation module with `useModule`. For snapshots, add the Sonatype snapshot repository to both plugin and dependency repositories and select `0.1.0-SNAPSHOT`.

The plugin adds `winrt-runtime` automatically to JVM and KMP main configurations. Add runtime dependencies manually only when using the runtime without the plugin:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-runtime-jvm:0.1.0")
}
```

For KMP without the plugin:

```kotlin
kotlin {
    jvm()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.compose-fluent:winrt-runtime:0.1.0")
        }
    }
}
```

## Projecting WinRT APIs

For a `mingwX64` + JVM project:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.compose-fluent.windows-toolkit") version "0.1.0"
}

kotlin {
    jvm()
    mingwX64 {
        binaries {
            executable()
        }
    }
}

windows {
    packageReferences {
        windowsSdk(generateProjection = true)
        namespace("Windows.Data.Json")
    }
}
```

For a JVM-only project:

```kotlin
plugins {
    kotlin("jvm")
    id("io.github.compose-fluent.windows-toolkit") version "0.1.0"
}

windows {
    packageReferences {
        windowsSdk(generateProjection = true)
        namespace("Windows.Data.Json")
    }
}
```

Generated projection types can be used from ordinary Kotlin code. Non-XAML WinRT calls should run inside a runtime scope:

```kotlin
import io.github.composefluent.winrt.runtime.RuntimeScope
import windows.`data`.json.JsonArray
import windows.`data`.json.JsonObject

fun readProfile(json: String): String =
    RuntimeScope.initializeSingleThreaded().use {
        val profile = JsonObject.parse(json)
        val education = profile.getNamedArray("education", JsonArray())
        val firstSchool = education.getObjectAt(0u).getNamedObject("school")

        "${profile.getNamedString("name")} attended ${firstSchool.getNamedString("name")}"
    }
```

## Prebuilt Projections

For the default Windows SDK and one XAML family, prefer the prebuilt projection artifacts. Applications select the Windows SDK coordinate independently, then choose either the UWP `Windows.UI.Xaml` family or the Windows App SDK / WinUI 3 family:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-ui-xaml:10.0.26100.0")
}

windows {
    packageReferences {
        windowsSdk(includeExtensions = true)
    }
}
```

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-app-sdk:2.5.1")
}

windows {
    packageReferences {
        windowsSdk(includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1") {
            generateProjection = false
        }
    }
}
```

Do not consume `winrt-projections-windows-ui-xaml` and `winrt-projections-windows-app-sdk` together. UWP applications and their projection modules also set `windows.xaml.framework` to `XamlFramework.UWP`; selecting a projection dependency alone does not configure the application model or compiler backend. The App SDK artifact exposes WebView2 Core transitively; add `winrt-projections-windows-webview2` directly only when using the standalone WebView2 projection without the App SDK family.

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-webview2:1.0.3719.77")
}
```

Projection artifact versions follow their metadata baseline. Snapshot projection builds append the kotlin-winrt snapshot suffix, for example `10.0.26100.0-kotlin-winrt-0.1.0-SNAPSHOT`.

The WebView2 Core projection is a separate artifact, matching the `Microsoft.Web.WebView2` NuGet boundary. The Windows App SDK projection depends on it publicly and owns the WinUI `Microsoft.UI.Xaml.Controls.WebView2` control, so applications using the App SDK coordinate receive both surfaces without duplicate generated classes.

Prebuilt projections are never selected implicitly from a Windows SDK or NuGet version declaration. Add the prebuilt projection artifact to `dependencies` when you want to use it. A `nugetPackage(...)` declaration defaults to local projection generation; set `generateProjection = false` only when the projection surface is supplied by an explicit dependency and the NuGet package is needed for runtime assets.

When a project intentionally needs a local projection from a NuGet package, keep projection generation enabled:

```kotlin
windows {
    packageReferences {
        windowsSdk(includeExtensions = true, generateProjection = true)
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1") {
            generateProjection = true
        }

        type("Microsoft.UI.Xaml.Application")
        type("Microsoft.UI.Xaml.Window")
        type("Microsoft.UI.Xaml.Controls.Button")
    }
}
```

Reusable WinRT libraries may declare `packageReferences { nugetPackage(...) }` or `application { runtimeAsset(...) }`. Final application modules consume dependency WinRT identity metadata and stage the aggregated runtime assets, so downstream apps do not need to repeat every library declaration just to place payloads in the final layout.

Automatic native runtime staging omits PDB symbols and linker intermediates (`.lib`, `.exp`, `.obj`). Add a symbol file with `runtimeAsset(...)` when native debugging requires it; explicit NuGet copy-local payloads are also preserved.

Gradle modules keep the WinApp metadata inventory and select runtime payloads directly from the resolved NuGet cache. They discard WinApp's redundant copies of runtime architectures, C++ headers and import libraries. Custom CLI workflows can retain them with `tasks.named<RestoreWinAppDependenciesTask>("restoreWinAppDependencies") { includeRuntimeAssets.set(true); includeNativeBuildFiles.set(true) }`.

JVM development runs assemble their launcher, dependency JARs, shared JVM image and runtime assets directly into the development package. They do not first materialize the ordinary package and standalone host directories; those outputs are still available through their own build/package tasks.

New IDE project templates enable Gradle's build cache and configuration cache. Existing projects can enable them in `gradle.properties`:

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
```

JVM SDK projection bytecode can be reused across project directories and root project names when the projection module's artifact name, SDK inputs, Kotlin compiler, JVM target, and compiler settings match. The first compilation of a new SDK/compiler combination still compiles the generated declarations; later projects can restore that task's output from the build cache. IDE import prepares XAML analysis and projection sources. The compiled SDK preview host is prepared when a preview is opened, and successful preview compilation removes its temporary class files.

XAML compilation retains verified native declarations and XBF per page, reusing unchanged pages when sources change. Reference metadata, compiler tools, and Kotlin semantic symbols are part of the cache context; missing or damaged entries are compiled again. Application resources and shared resource dictionaries retain full native batch compilation because the compiler protocol does not export their dependency graph. First IDE sync still prepares projection sources for completion without a manual build.

## WinApp CLI and NuGet Restore

The Gradle plugin translates project and dependency `packageReferences { nugetPackage(...) }` declarations into an internal `build/generated/kotlin-winrt/winapp/winapp.yaml`. Do not create or maintain that file manually. With the default `packageReferences { restoreNuGetPackages = true }`, `restoreWinAppDependencies` runs `winapp restore`, validates its schema-3 lockfile, and uses the resolved WinMD files for projection generation. The same lockfile's NuGet package roots drive DLL, PRI, asset, and manifest staging after compilation; explicit local `packageReferences { winmd(...) }` inputs remain part of the projection input set.

WinApp CLI provisioning is automatic and does not use WinGet, modify `PATH`, or require an administrator install. The plugin first probes `winAppCliExecutable` (default: `winapp`) and uses it only when it reports the required version, currently `0.6.0`. Otherwise it downloads `Microsoft.Windows.SDK.BuildTools.WinApp` from NuGet, verifies the pinned SHA-512 checksum, and caches the extracted host tools below the Gradle user home. A custom system location can be selected without changing `PATH`:

```kotlin
windows {
    winAppCliExecutable = "C:/tools/winapp.exe"
}
```

The plugin keeps static projection sources available during IDE import, so projection completion does not require a preceding compilation. Prepared sources are shared in the Gradle user home; warm imports reuse unchanged content checks and preserve generated source timestamps. Missing or modified generated files are repaired during import.

The plugin's `winapp restore`, `winapp package`, and `winapp tool makeappx` paths do not invoke MSBuild and do not require an MSBuild project. JVM and `mingwX64` compilation still require their normal JDK, Kotlin/Native, C/C++, and Windows SDK prerequisites.

JVM native EXE and authoring DLL hosts automatically discover installed Visual Studio or Build Tools C++ toolchains through `vswhere`, including custom installation locations. The plugin initializes the MSVC environment for the variant's architecture and the configured Windows SDK in a child process; builds work from an ordinary terminal or IDE without a Developer Command Prompt or system `PATH` changes. An already configured matching C++ environment and `clang-cl` on `PATH` remain supported. Missing C++ components must be installed through Visual Studio Installer; the plugin does not install MSVC or invoke MSBuild to compile these hosts.

WinApp CLI `0.6.0` performs its normal C++/WinRT workspace setup during restore and may check or install Windows App SDK runtime packages. That release does not expose a switch that limits `restore` to NuGet download and lockfile generation, and WinApp itself has no `--offline` option. When Gradle runs with `--offline`, the plugin does not invoke `winapp restore`: it reuses the verified `.winapp` lock/cache and fails if the lock, package contents, or restore context is missing or stale. Without Gradle offline, a normal restore may use the configured NuGet sources and CLI behavior. Set `packageReferences { restoreNuGetPackages = false }` to retain the legacy NuGet cache/CLI resolution path.

The plugin creates one package task graph per matching Kotlin target variant. `application.packageType` and `application.windowsAppSdkDeployment` select the default used by `packageWinApp<TargetVariant>`; the unsuffixed `packageWinApp` aggregates those default tasks. Applications are packaged by default. Desktop applications can set `application { packageType = WindowsPackageType.None }` for a default loose unpackaged layout; UWP requires packaged identity. A `mingwX64` executable remains its variant's package entry payload; JVM variants package the generated host, runtime classpath, and the same staged WinRT resources. `.msix` outputs use `winapp package`, while an explicitly configured `.appx` output uses `winapp tool makeappx pack`. Each concrete `verifyWinAppPackage<TargetVariant>` task unpacks its result through `winapp tool makeappx` and validates its manifest, payload, and resource-resolution report. Existing builds can keep an explicit Windows SDK MakeAppx path as a legacy override.

Windows App SDK applications also expose four explicit packaging formats. Append the usual Kotlin target variant suffix, or a named application suffix, to any of these tasks:

| Task prefix | Output | Windows App SDK deployment |
| --- | --- | --- |
| `packageWinAppPackagedFrameworkDependent` | MSIX | Installed framework runtime |
| `packageWinAppPackagedSelfContained` | MSIX | Runtime included in the package |
| `packageWinAppUnpackagedFrameworkDependent` | Loose directory | Installed framework runtime and bootstrap |
| `packageWinAppUnpackagedSelfContained` | Loose directory | Runtime included beside the executable |

For example, repackage a release MinGW application without editing its build script:

```powershell
.\gradlew.bat :app:packageWinAppUnpackagedSelfContainedMingwX64MainReleaseExecutable
.\gradlew.bat :app:packageWinAppPackagedFrameworkDependentMingwX64MainReleaseExecutable
```

Each format has its own `stageWinAppPackage<Format><TargetVariant>` and runtime-assets stage. Packaged formats also have matching `verifyWinAppPackage`, `signWinAppPackage`, and `installWinAppPackage` tasks. Unsuffixed format tasks aggregate that format across the application's targets. Explicit format tasks create the requested artifact even when the default's `generatePackage` is false. They retain the application's manifest, resource, JVM, signing, and installation settings; explicit `packageOutputFile`, `signedPackageOutputFile`, and `installPackageFile` overrides apply only to the default tasks. Formats use separate automatic output paths.

All formats share the Kotlin compilation, Native executable link, JVM launcher, authored host DLLs, and JVM runtime image. Generated JVM and Native hosts read `kotlin-winrt-host.properties` from their staged directory, so switching formats changes deployment inputs without changing generated application source. Unchanged compilation and link tasks stay up-to-date. Updating from a plugin version that embedded the packaging mode requires one rebuild of the host/Native entry; subsequent format switches reuse that executable. Custom Native entry points remain responsible for their own bootstrap configuration.

Format layouts live at `build/kotlin-winrt/application-layout/<variant>/<format>/package`, and MSIX files use `build/kotlin-winrt/packages/<project>-<variant>-<format>.msix`. Keep the complete loose directory when distributing an unpackaged application. Framework-dependent formats require the appropriate Windows App SDK runtime on the target machine.

Application package files live under the owning Kotlin source set at `src/<targetSourceSet>/appxResources/` (for example `src/winuiMain/appxResources/` or `src/main/appxResources/`). The plugin copies every file below that directory into the staged AppX root using its path relative to `appxResources/`; `AppxManifest.xml` is used automatically when no `application { appxManifest(...) }` is configured and is not copied as a second payload file. Source-set dependencies are merged from the least-specific source set to the selected target, so a target resource can override a shared resource. Explicit `appxManifest(...)` manifests take precedence. Explicit `packagePayload(...)` entries have the highest normal resource priority; same-level conflicts fail instead of being chosen by directory traversal order.

Windows version settings belong to Gradle, not a second copy in the source manifest:

```kotlin
windows {
    packageReferences {
        windowsSdk("10.0.26100.0")
    }
    application {
        minWindowsVersion = "10.0.19041.0"
        // maxVersionTested defaults to the selected SDK; override it only for a different tested OS.
    }
}
```

Keep `<TargetDeviceFamily Name="Windows.Desktop" />` in the source manifest without version attributes. Staging writes `MinVersion` and `MaxVersionTested` into the copied manifest; conflicting source attributes fail. When no target family is declared, staging adds `Windows.Desktop`. This applies to normal and `.dev` layouts, including manifests in unpackaged layouts, and never edits the source file. `minWindowsVersion` is required whenever an AppX manifest is staged and must not exceed `maxVersionTested`. Named applications inherit both settings and may override them independently.

`packageReferences { windowsSdk(version) }` selects the Windows API baseline and installed native SDK. When omitted, the plugin uses one shared, configuration-cache-tracked installed SDK selection for metadata, native tools, and the default `maxVersionTested`; pin the version for reproducible builds. `MaxVersionTested` is not an upper installation limit, and using a newer SDK does not make new APIs available on older Windows versions. Guard those calls when supporting an older `minWindowsVersion`.

The NuGet toolchain revision is separate: `windowsSdkToolsVersion = "10.0.26100.4654"` selects the exact `Microsoft.Windows.SDK.CPP` and `Microsoft.Windows.SDK.BuildTools` packages used by WinApp. Its default remains `10.0.26100.1742`. Changing this revision does not change the API baseline or manifest OS versions; the plugin does not invent a NuGet package version from an OS version. Explicit declarations of those tool packages must match the configured toolchain revision.

When a source set contains AppX resources, the plugin generates a KotlinPoet `AppxRes` accessor in that module's configured resource package. For example, `AppxRes.Assets.Square44x44LogoPng.path` is the package-root-relative path and `.uri` lazily creates the corresponding `ms-appx:///` `Windows.Foundation.Uri`; the accessor follows the shared Windows source-set visibility of the module.

Kotlin Multiplatform applications expose one task graph for each JVM main compilation and every declared MinGW executable build variant. For example, a `winuiJvm` main compilation plus the default MinGW executables creates `packageWinAppWinuiJvmMain`, `packageWinAppMingwX64MainDebugExecutable`, and `packageWinAppMingwX64MainReleaseExecutable`. The same suffix is used by staging, run, verification, signing, and installation tasks, and every variant has isolated layouts, package files, reports, and verification directories. The application DSL has no global target or build-type selectors; choose the artifact by invoking its concrete task.

JVM distribution and Windows App SDK deployment are independent settings. The default `bundledJvmRuntime()` creates or copies a runtime image beside the host; `externalJvmRuntime("C:/path/to/jdk")` requires that JVM on the target machine. `windowsAppSdkDeployment` is a `WindowsAppSdkDeployment` enum and defaults to `Auto`. Auto scans the application and its WinRT identity dependencies for a `Microsoft.WindowsAppSDK*` NuGet package, chooses `FrameworkDependent` when WinApp restore can provide its Bootstrap DLL, falls back to `SelfContained` when that condition is unavailable, and chooses `None` when no Windows App SDK package is present. Set `windowsAppSdkDeployment` explicitly when the deployment model is known; these settings do not change `packageType`.

NuGet source configuration follows NuGet's normal directory hierarchy. `windows { packageReferences { nugetConfig("path/to/NuGet.Config") } }` selects an explicit config, while `packageReferences.nugetConfigDirectory` can select the restore base directory. The plugin still generates `winapp.yaml`; users do not maintain that file or a second global cache. In offline mode, restore only reuses a verified lock/cache and fails clearly when a package or lock entry is missing.

NuGet archive signatures, extraction bookkeeping, and empty package directories do not invalidate restore or projection generation. WinMDs and package descriptors remain projection inputs; native payload changes invalidate restore and their owning runtime stages.

```kotlin
windows {
    application {
        packageType = WindowsPackageType.Packaged
        minWindowsVersion = "10.0.19041.0"
        makeAppxExecutable = "C:/Program Files (x86)/Windows Kits/10/bin/10.0.26100.0/x64/makeappx.exe"
    }
}
```

## WinUI Applications

This section describes desktop WinUI 3, including WinApp CLI development launches.
UWP uses package activation and a separate development runner; follow
[UWP build and activation](UWP.md#build-and-activate-each-target) for its manifest,
framework dependencies and launch options. UWP designer, Hot Reload and
`--debug-output` are not implemented.

Start with a `mingwX64` + JVM application and use the packaged AppX development runs (`runWinAppPackage...`) by default, as shown in the [README](../README.md#get-started). Direct host and executable commands below describe advanced launch options.

Enable the application model only in the final executable app module:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.compose-fluent.windows-toolkit") version "0.1.0"
}

kotlin {
    jvm("winuiJvm")
    mingwX64 {
        binaries {
            executable()
        }
    }
}

windows {
    application {
        mainClass = "sample.MainKt"
        minWindowsVersion = "10.0.19041.0"
        // console = true enables a console window for diagnostics.
        launcherIcon = layout.projectDirectory.file("src/winuiMain/appxResources/Assets/App.ico")
    }

    packageReferences {
        windowsSdk(includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1")
    }
}
```

`application.launcherIcon` accepts a Win32 `.ico` file, matching C# `ApplicationIcon` and C++ `ICON` resources. The plugin compiles it with the selected Windows SDK's `rc.exe` and embeds it in both the JVM launcher and the `mingwX64` executable. Multi-size ICOs, including PNG-compressed images inside the ICO, are preserved; standalone PNG and SVG files must first be converted to ICO. Named applications inherit this property and may override it. To also use the icon with `AppWindow.setIcon`, include the ICO in `appxResources` and set the window icon separately; AppX logo settings remain independent.

Set `application { executableBaseName = "animeko-desktop" }` to choose the launcher's filename without `.exe`. By default, a build with one application module uses the root project name (`animeko.exe`). When multiple modules enable `windows.application`, each appends its module name (`animeko-app2.exe` for module `app2`). Libraries and additional target or named application variants in the same module do not increase the module count. Named applications inherit the setting and may override it. The name applies to JVM launchers and staged `mingwX64` executables, their run tasks, and their application manifests. The first AppX `Application` is the primary launcher; references to its original executable, including extension registrations, follow the configured name. Kotlin/Native's linked binary retains its own `baseName`.

With Gradle's configure-on-demand enabled, application naming evaluates the remaining modules to determine the full application module count.

The dual-target project exposes separate JVM and Native task graphs automatically. Run the JVM host with:

```text
./gradlew runWinAppHostWinuiJvmMain
```

Run a MinGW build through its Kotlin/Native executable task, for example:

```text
./gradlew runReleaseExecutableMingwX64
```

Use a concrete packaging task when building one artifact, or the unsuffixed aggregate to build all artifacts. For example, `packageWinAppMingwX64MainReleaseExecutable` builds only the release Native package, while `packageWinApp` builds the JVM, debug Native, and release Native packages. Targets and executable build types are declared once in the Kotlin DSL; packaging follows that model without separate selection properties.

To build several applications in one invocation, declare named variants in the first `application` block. Settings outside `variants` are shared defaults; each application can override them:

```kotlin
windows {
    application {
        mainClass = "sample.MainKt"
        packageType = WindowsPackageType.Packaged
        variants {
            create("desktop") {
                variantName = "winuiJvm:main"
                runTask("runDesktopDiagnostics") {
                    jvmArgs.add("-Xmx256m")
                }
            }
            create("native") {
                variantName = "mingwX64:main:releaseExecutable"
            }
        }
    }
}
```

This registers `packageWinAppDesktop` and `packageWinAppNative`, with matching suffixed staging, verification, signing, and installation tasks. Each named application requires a full `variantName` (`target:compilation` for JVM, `target:compilation:executable` for Native); this binding is only available inside named variants. The unsuffixed tasks aggregate all named applications. Layouts, identity files, PRI work directories, generated Native entries, packages, and verification reports are isolated per application. Native applications must bind different executable binaries, even when they share a compilation. Explicit package output paths must also be distinct.

For multiple JVM applications, use the same JVM target with different main classes or explicitly bind a custom compilation (`variantName = "winuiJvm:preview"`). Use one JVM target and separate compilations for this configuration.

Use `runWinAppHostDesktop` for a JVM variant's direct host launch and the selected Kotlin/Native executable's run task for Native. These direct launches do not activate a packaged application's identity; use the packaged development run tasks below or install and activate an MSIX for packaged startup verification. Named applications do not inject all their layouts into the global Java `processResources`, `JavaExec`, or distribution tasks. Register additional JVM runs inside the corresponding variant with `runTask`.

For packaged development, use the default `packageType = WindowsPackageType.Packaged` and invoke the concrete package run task:

```powershell
.\gradlew.bat runWinAppPackageWinuiJvmMain
.\gradlew.bat runWinAppPackageMingwX64MainDebugExecutable
```

These tasks build their own variant, stage a development manifest with `.dev` appended to `Identity.Name`, regenerate the application PRI with that development identity, and pass the layout to `winapp run` in folder mode. The source manifest and normal package outputs remain unchanged. WinApp creates a separate development AppX layout, registers it, and launches through package identity, so `ms-appx:///` resources use the registered layout. This does not require creating or signing an MSIX, setting `installPackage`, or invoking MSBuild. Windows Developer Mode must be enabled. WinApp preserves development application data across redeployments by default. Each variant has an isolated deployment directory; variants with the same source manifest identity replace the same `.dev` registration, so packaged run tasks have no unsuffixed aggregate.

The `.dev` identity coexists with the installed production application and has separate application data. The source identity must leave four characters available within Windows' 50-character identity-name limit. If the input already has `resources.pri`, development staging requires `generateProjectPri` and its resource inputs to rebuild it; the plugin will not reuse a PRI indexed under the production identity. WinApp still refuses to overwrite any non-development package already occupying the `.dev` identity. The plugin never removes installed production packages automatically.

Packaged development runs require the `FrameworkDependent` deployment mode because WinApp CLI 0.6 has no self-contained folder-run option. Auto selects that mode when the restored Windows App SDK Bootstrap DLL is available; an explicit `SelfContained` value is rejected rather than silently changing the deployment model. Install and activate the generated MSIX to verify self-contained deployment.

The task waits for the application to exit and streams WinApp output to Gradle. Use `--detach` to return after launch, `--args='--flag value'` to pass an application command line, `--debug-output` to capture native debug output and exceptions, or `--no-launch` to register without launching. Native debug capture occupies the process's debugger connection; leave it off when using another native debugger. Named applications expose the same task with their name, for example `runWinAppPackageDesktop`.

Run the JVM application through the generated host:

```powershell
.\gradlew.bat runWinAppHostWinuiJvmMain
```

Run the native executable path:

```powershell
.\gradlew.bat runReleaseExecutableMingwX64
```

`runWinAppHostWinuiJvmMain` and `runReleaseExecutableMingwX64` depend on their variant-specific staging tasks automatically. They stage WinRT runtime assets, authored host DLLs, Windows App SDK payloads, and application layout files before launch. The unsuffixed WinApp tasks are aggregate entry points; use a suffixed task when operating on one target variant. Do not wire staging or host tasks manually for the normal run path.

A minimal WinUI entry point starts XAML directly:

```kotlin
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.XamlControlsResources

fun main() {
    Application.start {
        DemoApp()
    }
}

class DemoApp : Application() {
    private var window: Window? = null

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        Application.current.resources.mergedDictionaries.add(XamlControlsResources())

        val root = StackPanel().apply {
            padding = Thickness(24.0, 24.0, 24.0, 24.0)
            spacing = 12.0
            children.add(TextBlock().apply {
                text = "Hello from Kotlin WinRT"
                fontSize = 24.0
            })
            children.add(Button().apply {
                content = "OK"
            })
        }

        window = Window().apply {
            title = "Kotlin WinRT"
            content = root
            activate()
        }
    }
}
```

`RuntimeScope` is the explicit apartment scope for ordinary WinRT calls. It owns one successful
`CoInitializeEx` on the creating thread, and its `close()` must run on that same thread:

```kotlin
RuntimeScope.initializeMultithreaded().use {
    // Ordinary WinRT calls.
}
```

The generated `runWinAppHost<Target><Compilation>` tasks already create the application
host and its UI apartment before entering your main function. Keep `Application.start` as the
normal XAML entry point and do not create another `RuntimeScope` around it.

If you use a custom launcher or a Gradle `JavaExec` task instead, create the same host explicitly
with package identity and Windows App SDK deployment as separate inputs:

```kotlin
import io.github.composefluent.winrt.runtime.WindowsAppSdkBootstrap
import io.github.composefluent.winrt.runtime.WinAppHostConfiguration
import io.github.composefluent.winrt.runtime.WinAppPackageIdentity
import io.github.composefluent.winrt.runtime.WindowsAppSdkDeploymentMode
import microsoft.ui.xaml.Application

fun main() {
    WindowsAppSdkBootstrap.initializeApplicationHost(
        WinAppHostConfiguration.fromStagedRuntimeAssets(
            packageIdentity = WinAppPackageIdentity.Unpackaged,
            windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.FrameworkDependent,
        ),
    ).use {
        Application.start {
            DemoApp()
        }
    }
}
```

Use `Packaged` for a packaged identity, `SelfContained` when the staged payload owns the Windows
App SDK runtime, `FrameworkDependent` when the installed framework supplies it, `None` for a
WinApp without Windows App SDK, or `ExternallyInitialized` when another host already prepared the SDK.
An application host is unique per process, and both the host and deployment owner must be closed
on their creating thread. Closing the host performs application cleanup before releasing its
apartment and deployment owner; ordinary `RuntimeScope.close()` does not perform that process
cleanup.

When `windows { application {} }` is enabled, the plugin wires unpackaged `JavaExec` tasks to the staged payload and passes `-Dkotlin.winrt.runtimeAssetsRoot=...`. Custom native launchers or external packaging tools still need to place the staged `windows-package-runtime-assets` directory beside the launcher or pass `-Dkotlin.winrt.runtimeAssetsRoot=<path>`.

## Validation

Use Windows for full build and runtime validation:

```powershell
.\gradlew.bat test
.\gradlew.bat validateWinRTMingwParity
.\gradlew.bat validateWinRTFullWindowsSdkProjectionGate
.\gradlew.bat validateWinRTSampleSmoke
```

The sample modules' normal `check` tasks stay focused on compilation and unit tests. `validateWinRTSampleSmoke` runs the machine-dependent JVM/native launches and generated-output audits.

Run the projection benchmark matrix on an x64 Windows machine with .NET 8 and the Visual Studio C++ build tools:

```powershell
.\gradlew.bat :winrt-benchmarks:benchmarkAll
```

The generated comparison report is written to `winrt-benchmarks/build/reports/benchmarks/benchmark-report.md`. See `winrt-benchmarks/README.md` for workload semantics, runner prerequisites, and tuning parameters.

Useful focused sample runs:

```powershell
.\gradlew.bat :winrt-samples:runWinAppHostWinuiJvmMain
.\gradlew.bat :winrt-samples:runReleaseExecutableMingwX64
.\gradlew.bat :winrt-samples:winui-kmp-app:runWinAppHostWinuiJvmMain
.\gradlew.bat :winrt-samples:winui-kmp-app:runReleaseExecutableMingwX64
```

Run the WebView2 sample with an installed Evergreen WebView2 Runtime:

```powershell
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUI=false" :winrt-samples:runWinAppHostWinuiJvmMain
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUI=false" :winrt-samples:runReleaseExecutableMingwX64
```

The sample starts with embedded offline HTML and accepts HTTP or HTTPS addresses from its navigation bar. Both launch paths stage `WebView2Loader.dll` and the Windows App SDK payload through the normal application layout tasks, and keep WebView2 user data under `winrt-samples/build/kotlin-winrt/webview2-user-data` instead of the tracked application layout.

Build and validate this repository on Windows.

## Projection References

`kotlin-winrt` does not define WinRT projection behavior from scratch. Runtime classes, default interfaces, implemented interfaces, delegates, generic interfaces, parameterized IIDs, activation, authoring, and WinUI behavior are aligned with the Microsoft projection model:

- [C++/WinRT](https://github.com/microsoft/cppwinrt)
- [CsWinRT](https://github.com/microsoft/CsWinRT)

When local behavior disagrees with those projections, the intent is to change `kotlin-winrt` to match the reference projection model instead of inventing project-specific rules.
