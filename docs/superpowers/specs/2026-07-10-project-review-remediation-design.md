# Project Review Remediation Design

## Goal

Repair the correctness issues found by the repository-wide review without changing the public projection model or introducing Kotlin-only WinRT behavior. Each fix must follow the corresponding `.cswinrt` ownership boundary, work on JVM and `mingwX64` where applicable, and fail closed when ABI identity or generated ownership is ambiguous.

## Delivery Order

The work is split into six independently testable and independently committed slices:

1. Runtime ABI and lifetime foundations.
2. Metadata identity validation.
3. Generator and authoring ABI ownership.
4. Prebuilt projection publication topology.
5. Gradle projection/authoring task gating.
6. Sample reachability and final validation.

This order follows the repository runtime-first rule. Later slices may consume contracts added by earlier slices, but samples and publication tests must not define upstream behavior.

## Runtime ABI And Lifetime

`NativeAbiLayout.GUID` and `NativeStructScalarKind.GUID` will use the Windows ABI alignment of four bytes. Tests will verify GUID offsets and final structure size when GUID fields follow byte- and word-sized fields.

Arrays whose declared Kotlin component type is `Any` will always use WinRT `InspectableArray` metadata. Runtime element sampling must not change the declared ABI shape. Tests will cover heterogeneous arrays, null-first arrays, and homogeneous arrays declared as `Array<Any?>`.

The public WinRT weak-reference wrapper will own cleanup for its native `IWeakReference` handle. Replacing a target must still release the old handle immediately; abandoned wrappers must release the current handle through the existing cross-platform finalization abstraction. Cleanup must be idempotent and must not retain the target strongly.

## Metadata Identity

Merging duplicate qualified type definitions is allowed only when their identity-bearing metadata agrees. Two different non-null IIDs for the same interface or delegate are an input error and must fail before projection planning. The diagnostic must include the qualified type name and both IIDs. Existing one-sided IID enrichment remains supported.

## Generator And Authoring ABI

Authored receive-array return storage transferred to a native caller will be allocated with `CoTaskMemAlloc`. Generated code will write a null pointer for empty arrays or otherwise write a caller-owned CoTaskMem pointer; local `Arena` or `nativeHeap` ownership must not escape across the ABI boundary.

Projection generation will reject two rendered files that resolve to the same normalized output path. The diagnostic must identify the colliding relative path before either file is overwritten, including case-folding collisions produced by Kotlin package normalization.

`DllGetActivationFactory` will clear a non-null output slot before decoding the class ID or looking up a factory. Every failure path therefore returns a null output pointer, matching the CsWinRT authoring host shim.

## Projection Publication

Each generated FQN must have exactly one prebuilt artifact owner. Windows App SDK and Windows.UI.Xaml generation may consume Windows SDK metadata for reference closure, but dependency-owned declarations and support additions must not be emitted again. A cross-artifact audit will compare compiled/package paths and fail on duplicates.

Public projection artifacts whose signatures expose Windows SDK types will publish the Windows SDK projection as an API dependency. Validation will publish to a temporary repository and compile isolated JVM and `mingwX64` consumers using only the intended top-level coordinate.

WebView2 keeps the package boundary used by Windows App SDK: `Microsoft.Web.WebView2.Core` is published from a dedicated Kotlin projection artifact, while the WinUI `Microsoft.UI.Xaml.Controls.WebView2` control remains in the Windows App SDK projection. The Windows App SDK artifact exposes the WebView2 Core artifact as an API dependency so its public control interfaces remain consumable without duplicate Core declarations.

## Gradle Projection And Authoring Gating

Local projection generation and authoring compiler discovery are separate decisions. Dependency-owned or empty projection inventories may skip projection source generation, but Kotlin compilations capable of containing `@WinRTAuthoredRuntimeClass` declarations must still run the compiler plugin and emit authored candidates, WinMD, host manifests, and TypeDetails inputs.

Lifecycle validation will remain enabled whenever authoring output can be produced. Runtime-only projects with no authored declarations may produce empty authoring artifacts, but must not silently disable discovery based solely on projection inventory. Existing dependency identity cycle protections and dependency-owned projection suppression remain intact.

## Samples And Validation

The non-WinUI sample source set will be selected through an explicit property rather than an unreachable null check behind a default Windows App SDK version. Default behavior remains the current WinUI sample unless the property explicitly disables it.

Every slice follows red-green-refactor testing and ends with its owning module gate. Final validation includes runtime, metadata, generator, authoring, targeted Gradle plugin tests, split projection duplicate/publication consumer checks, and representative WinUI/non-WinUI sample tasks. Full native or packaging tasks that require unavailable external SDK state must be reported explicitly rather than treated as passing.

## Commit Boundaries

Each of the six slices is committed separately after its tests pass. `PLAN.md` is updated in the same slice, changing the active item from `正在做` to complete and marking the next item `正在做`. Existing unrelated working-tree changes are preserved and are not staged into these commits.
