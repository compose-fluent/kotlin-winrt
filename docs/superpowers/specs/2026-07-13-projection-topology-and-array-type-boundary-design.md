# Projection Topology And Array Type Boundary Design

## Goal

Remove the duplicate prebuilt-projection publication and validation wiring introduced during project-review remediation, restore the projection topology already represented by Kotlin/WinRT dependency identity metadata, and remove JVM reflection from reference-array component classification.

The resulting design must remain aligned with `.cswinrt`: Windows SDK projection ownership is independent of the selected Windows App SDK version, `Windows.UI.Xaml` and `Microsoft.UI.Xaml` are mutually exclusive XAML families, and platform-specific reflection must not define shared runtime ABI behavior.

## Projection Dependency Authority

The Kotlin/WinRT identity dependency graph is the only source of truth for generated projection ownership. A dependency identity describes projected types, projection-surface additions, compiler support, authored metadata, and runtime assets already supplied by that dependency.

Generation must use this graph to suppress dependency-owned output and fail closed when two simultaneously consumable dependency identities claim incompatible ownership. Prebuilt modules must not maintain a second ownership graph through manually declared audit peers.

Cross-artifact validation remains useful as a regression check, but it must be derived from valid dependency compositions. It must not define or override ownership.

## Projection Families

The supported projection topology has one base Windows family and two mutually exclusive XAML branches:

```text
Windows SDK base projection
|- Windows.UI.Xaml projection (legacy/UWP XAML family)
`- Windows App SDK projection (Microsoft.UI.Xaml / WinUI 3 family)
   `- WebView2 Core projection where required by the selected App SDK surface
```

`windows-ui-xaml` and `windows-app-sdk` are peers, not dependencies. They must not be cross-audited as if they are expected to coexist, and an application must not consume both families.

The App SDK projection may contain the small `Windows.UI.Xaml.*` compatibility surface referenced by WinUI metadata, matching `.cswinrt/src/Projections/WinAppSDK`. The standalone Windows.UI.Xaml projection independently owns the corresponding legacy XAML surface. Duplicate FQNs across these mutually exclusive families are therefore not an ownership error.

The Windows.UI.Xaml filters must return to the `.cswinrt/src/Projections/Windows.UI.Xaml` boundary. Types excluded only to manufacture global uniqueness with the App SDK artifact, including `Windows.UI.Xaml.Data.BindableAttribute` and `Windows.UI.Xaml.Markup.ContentPropertyAttribute`, must no longer be excluded for that reason.

## Windows SDK Version Independence

The Windows App SDK projection artifact must not publish a dependency on a particular Windows SDK projection version. Applications select the Windows SDK projection appropriate for their own target independently of the Windows App SDK artifact version.

When building prebuilt App SDK, WebView2, or XAML projections in this repository, a selected Windows SDK projection may be used only as a non-published build reference:

- its classes satisfy compilation of generated signatures;
- its identity tells the generator which Windows types and additions already exist;
- its artifact version is not copied into the dependent projection's public metadata;
- the dependent projection does not mutate the Windows SDK project's version;
- consumers remain responsible for declaring their selected Windows SDK projection.

The plugin must model this as an internal projection-reference/identity edge rather than an `api` dependency. The implementation should reuse existing identity configurations and variant selection instead of adding a parallel ownership DSL.

## Build Plugin Convergence

Prebuilt projection build scripts should contain only module-specific metadata inputs, filters, coordinates, and ordinary dependencies that are truly published.

The Kotlin/WinRT Gradle plugin will own the shared mechanics:

- discover dependency identities from the normal dependency graph;
- expose a non-published build reference for sibling prebuilt projection compilation and identity consumption;
- configure generated source and compiler-support wiring;
- normalize publication metadata for genuinely public API dependencies;
- register the standard generated-output validation task and attach it to `check`;
- derive valid-composition duplicate checks from dependency identities;
- reject an invalid composition containing both Windows.UI.Xaml and Microsoft.UI.Xaml projection families.

The plugin must eliminate per-module `evaluationDependsOn`, manual `kotlinWinRTLibraryDependencyIdentity` additions, repeated `MavenPublication.pom.withXml` blocks, repeated validation task registration, and manual `check` wiring.

Published consumer fixtures must use only ordinary dependency declarations. They must not know the internal identity configuration name. App SDK consumer validation must declare both the App SDK coordinate and an independently selected Windows SDK coordinate.

## WebView2 Boundary

`Microsoft.Web.WebView2.Core` remains owned by the separate WebView2 projection artifact. The Kotlin App SDK projection continues to own `Microsoft.UI.Xaml.Controls.WebView2`, because Kotlin cannot consume the official managed `Microsoft.WinUI` implementation used by the standalone CsWinRT test projection.

The App SDK projection publishes the WebView2 projection as a public dependency. This matches the `Microsoft.WindowsAppSDK.WinUI` to `Microsoft.Web.WebView2` package edge and is required because `Microsoft.UI.Xaml.Controls.WebView2` exposes WebView2 Core types in its public surface. This relationship is independent of the Windows SDK non-binding rule.

## Reference Array Type Boundary

Shared runtime behavior must use only Kotlin Multiplatform `KClass` and explicit WinRT metadata. The JVM must not recover a reference-array component type through `KClass.java`, `Class.componentType`, or the runtime array object's Java class.

The `platformReferenceArrayComponentType` expect/actual boundary will be removed.

Reference-array classification follows these rules:

1. Generated ABI and authoring paths already know the declared element type and must pass the element `KClass`, type identity, or marshaling metadata explicitly.
2. Generic Kotlin APIs that retain a compile-time element type use `inline reified T` and pass `T::class` into the shared implementation.
3. Truly dynamic `Array<*>` values use conservative all-element inference over non-null values.
4. A dynamic empty array, an all-null array, or an array whose elements do not establish one compatible type falls back to inspectable/object-array metadata.
5. `Array<Any?>` must remain inspectable when the caller supplies its declared element type explicitly; JVM runtime component metadata must not alter that result.
6. JVM and `mingwX64` execute the same common runtime classification code.

No permanent platform stub or platform-specific classification table replaces the removed reflection path.

## Validation

Gradle plugin regression coverage must prove:

- ordinary dependencies automatically provide identity metadata without explicit identity configuration entries;
- prebuilt module scripts no longer need `evaluationDependsOn`, POM XML mutation, duplicated audit task registration, or duplicated `check` wiring;
- App SDK publication metadata contains no Windows SDK dependency;
- an isolated App SDK consumer compiles when it independently declares a Windows SDK projection;
- valid Windows SDK + Windows.UI.Xaml and Windows SDK + App SDK + WebView2 compositions have no duplicate classes within each composition;
- Windows.UI.Xaml and App SDK together are rejected as incompatible families;
- App SDK publication metadata exposes WebView2 on its public compile/API surface.

Runtime and generator coverage must prove:

- no reference-array implementation source imports or calls Java reflection;
- explicit/reified element types select the same metadata on JVM and `mingwX64`;
- dynamic heterogeneous, empty, all-null, and `Array<Any?>` values follow the conservative rules;
- generated and authored array paths pass explicit element metadata where required;
- existing JVM and `mingwX64` runtime, generator, publication, and representative sample gates remain green.

## Delivery Order

1. Add failing Gradle plugin tests for identity-derived prebuilt wiring, SDK-independent App SDK publication, family composition, and simplified consumers.
2. Move common publication and validation mechanics into the Gradle plugin and simplify the prebuilt build scripts.
3. Add failing common/JVM tests for reflection-free reference-array classification.
4. Replace reflection-based component discovery with explicit/reified common metadata flow and update generated/authoring call sites.
5. Run focused JVM and `mingwX64` tests, split publication consumers, valid-family composition checks, and the full project-review remediation gate.
