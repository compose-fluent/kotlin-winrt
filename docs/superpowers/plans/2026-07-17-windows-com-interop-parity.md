# Windows COM Interop Helper Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate the complete 17-type public COM interop helper surface from CsWinRT for Kotlin/JVM and Kotlin/Native `mingwX64`, gated by the Universal API Contract major version of an explicitly declared `windowsSdk(...)` metadata source.

**Architecture:** `winrt-metadata` parses each declared Windows SDK `Platform.xml` into a normalized SDK/contract inventory carried by `WinRTMetadataCache` and `WinRTMetadataModel`. A single metadata-owned adapter catalog describes helper ownership, UAC gates, IIDs, slots, parameters, results, and prerequisites; projection inventory filters that catalog, while `winrt-generator` renders each selected descriptor through the existing ABI type bindings and compiler intrinsics. Gradle dependency identity continues to suppress already-owned helper FQNs after metadata selection.

**Tech Stack:** Kotlin 2.2 multiplatform, Gradle, KotlinPoet, JUnit 5, Windows SDK WinMD metadata, existing `WinRTProjectionIntrinsic` JVM/`mingwX64` lowering.

## Global Constraints

- `.cswinrt/src/cswinrt/WinRT.Interop.idl` and `.cswinrt/src/cswinrt/strings/ComInteropHelpers.cs` are the ABI and public API sources of truth.
- A `WinRTMetadataSource.WindowsSdk` declaration is mandatory for Windows COM interop additions; path, local-machine, and NuGet inputs never synthesize SDK/UAC identity.
- UAC gates are exactly 1, 2, 3, 4, and 15 from `ComInteropHelpers.cs`; do not reuse the different IDL preprocessor gates.
- `winrt.interop.WindowNative` and `winrt.interop.InitializeWithWindow` remain unversioned Windows-projection helpers; `Microsoft.UI.Win32Interop` remains unchanged.
- The metadata catalog is the only enumeration of the 15 UAC-gated adapters and their methods.
- Public helper APIs remain generated projection code; do not add helper-specific public APIs to `winrt-runtime`.
- Generated helper bodies must use existing typed `WinRTProjectionIntrinsic` paths and must not use `ComVtableInvoker` or a generic vararg fallback.
- Keep Kotlin/JVM and `mingwX64` behavior and generated common API semantically identical.
- Preserve the current uncommitted `InputPaneInterop` work, replacing its one-off metadata and renderer branches with the catalog implementation.
- Run Windows Gradle validation one module/test class at a time with `--max-workers=1`.

---

## File Structure

- `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataSources.kt`: resolve SDK version and parse `Platform.xml` into structured contract metadata.
- `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModel.kt`: retain normalized declared SDK selections on the loaded model.
- `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTComInteropAdapters.kt`: own the complete declarative adapter/method ABI catalog and UAC/type prerequisite selection.
- `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataTraversal.kt`: convert selected descriptors into namespace additions without duplicating helper names.
- `winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataLoaderTest.kt`: verify SDK contract parsing, normalization, propagation, and fail-closed errors.
- `winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModelTest.kt`: verify exact helper inventories at UAC boundaries and filters.
- `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinComInteropSourceRenderer.kt`: render catalog descriptors using existing ABI bindings and intrinsics.
- `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionSupportRenderer.kt`: delegate catalog additions to the common renderer while preserving the three existing non-catalog additions.
- `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGenerator.kt`: pass the selected descriptor/model context and remove implicit SDK declaration behavior.
- `winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt`: verify all public files, methods, ABI shapes, gates, and dependency suppression.
- `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTIdentityTask.kt`: consume exact emitted source-addition names without static helper reconstruction.
- `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`: keep generation and identity inputs aligned with explicit Windows SDK declarations.
- `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`: verify SDK requirement, boundary identity, and downstream ownership suppression.
- `PLAN.md`: track this `.cswinrt/src/cswinrt` to metadata/generator/projection parity slice and its final validation evidence.

### Task 1: Retain Declared Windows SDK Contract Inventory

**Files:**
- Modify: `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataSources.kt`
- Modify: `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModel.kt`
- Test: `winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataLoaderTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Produces: `WinRTWindowsSdkContract(name: String, version: String)` with validated `majorVersion: Int`.
- Produces: `WinRTWindowsSdkSelection(version: String, contracts: List<WinRTWindowsSdkContract>)` with `contractMajorVersion(name: String): Int?`.
- Produces: `WinRTMetadataCache.windowsSdkSelections: List<WinRTWindowsSdkSelection>`.
- Produces: `WinRTMetadataModel.windowsSdkSelections: List<WinRTWindowsSdkSelection>` and `universalApiContractMajorVersion: Int?`.

- [x] **Step 1: Write failing resolver and propagation tests**

  Add tests that create SDK fixtures with `Platform.xml` entries carrying explicit versions, then assert:

  ```kotlin
  assertEquals("10.0.22621.0", cache.windowsSdkSelections.single().version)
  assertEquals(
      15,
      cache.windowsSdkSelections.single()
          .contractMajorVersion("Windows.Foundation.UniversalApiContract"),
  )
  assertEquals(15, cache.load().universalApiContractMajorVersion)
  ```

  Add a two-SDK-source test where UAC 14 and 15 normalize deterministically and the model reports 15. Extend `writeApiContractXml` and `copyContractWinmd` to accept `Triple<String, String, Path>` so the WinMD lookup version matches each XML entry. Add malformed versions `""`, `"abc"`, and `"0.0.0.0"` and assert the resolver names the contract and `Platform.xml` in its `IllegalArgumentException`.

- [x] **Step 2: Run the metadata resolver tests and verify RED**

  Run:

  ```powershell
  .\gradlew.bat :winrt-metadata:test --tests "io.github.composefluent.winrt.metadata.WinRTMetadataLoaderTest" --max-workers=1
  ```

  Expected: compilation fails because SDK selection types and cache/model properties do not exist.

- [x] **Step 3: Implement structured SDK parsing and model propagation**

  Add the concrete contracts:

  ```kotlin
  data class WinRTWindowsSdkContract(
      val name: String,
      val version: String,
  ) {
      val majorVersion: Int
          get() = version.substringBefore('.').toInt()

      fun normalized(): WinRTWindowsSdkContract = copy(
          name = name.trim(),
          version = version.trim(),
      )
  }

  data class WinRTWindowsSdkSelection(
      val version: String,
      val contracts: List<WinRTWindowsSdkContract>,
  ) {
      fun contractMajorVersion(name: String): Int? = contracts
          .asSequence()
          .filter { it.name == name }
          .maxOfOrNull(WinRTWindowsSdkContract::majorVersion)

      fun normalized(): WinRTWindowsSdkSelection = copy(
          version = version.trim(),
          contracts = contracts.map(WinRTWindowsSdkContract::normalized)
              .distinct()
              .sortedWith(compareBy(WinRTWindowsSdkContract::name, WinRTWindowsSdkContract::version)),
      )
  }
  ```

  Refactor the SDK resolver to return a `ResolvedMetadataSource` containing both files and exactly one `WinRTWindowsSdkSelection`. Parse platform contracts into one data structure and reuse it for WinMD resolution; extension manifests may contribute files but must not change the platform UAC inventory. Normalize selections in `WinRTMetadataSourceResolver.resolve`, carry them into `WinRTMetadataCache`, and make `WinRTMetadataCache.load()` copy them into the loaded model. Preserve selections in `WinRTMetadataModel.normalized()` and calculate:

  ```kotlin
  val universalApiContractMajorVersion: Int?
      get() = windowsSdkSelections.maxOfOrNull { selection ->
          selection.contractMajorVersion("Windows.Foundation.UniversalApiContract") ?: 0
      }?.takeIf { it > 0 }
  ```

  Reject blank names, versions that are not four numeric components with a positive major component and non-negative remaining components, and SDK platform inventories without a valid Universal API Contract only when interop selection requests it in Task 2.

- [x] **Step 4: Run metadata tests and verify GREEN**

  Run the command from Step 2. Expected: all `WinRTMetadataLoaderTest` tests pass.

- [x] **Step 5: Update `PLAN.md` and commit Task 1**

  Mark the SDK inventory prerequisite complete inside the active COM interop item, then commit only Task 1 files:

  ```powershell
  git add PLAN.md winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataSources.kt winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModel.kt winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataLoaderTest.kt
  git commit -m "feat(metadata): retain Windows SDK contract inventory"
  ```

### Task 2: Centralize the UAC-Gated COM Interop Catalog

**Files:**
- Create: `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTComInteropAdapters.kt`
- Modify: `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataTraversal.kt`
- Test: `winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModelTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `WinRTMetadataModel.universalApiContractMajorVersion` and `WinRTMetadataProjectionContext.sources`.
- Produces: `WinRTComInteropAdapterDescriptor`, `WinRTComInteropMethodDescriptor`, `WinRTComInteropParameterDescriptor`, `WinRTComInteropResultDescriptor`, and `WinRTComInteropAdapters.forProjection(model, context)`.
- Produces: `WinRTNamespaceAddition.comInteropAdapters: List<WinRTComInteropAdapterDescriptor>`; `generatedTypeNames` is derived from those descriptors.

- [ ] **Step 1: Write failing boundary and filter tests**

  Build a compact metadata model containing all required runtime classes/interfaces/enums and vary `windowsSdkSelections` across UAC 1, 2, 3, 4, 14, and 15. Assert exact generated helper FQNs:

  ```kotlin
  val expectedByUac = mapOf(
      1 to setOf(
          "windows.applicationmodel.datatransfer.DataTransferManagerInterop",
          "windows.applicationmodel.datatransfer.dragdrop.core.DragDropManagerInterop",
          "windows.graphics.printing.PrintManagerInterop",
          "windows.media.SystemMediaTransportControlsInterop",
          "windows.media.playto.PlayToManagerInterop",
          "windows.security.authentication.web.core.WebAuthenticationCoreManagerInterop",
          "windows.security.credentials.ui.UserConsentVerifierInterop",
          "windows.ui.applicationsettings.AccountsSettingsPaneInterop",
          "windows.ui.viewmanagement.InputPaneInterop",
          "windows.ui.viewmanagement.UIViewSettingsInterop",
      ),
      2 to setOf("windows.ui.input.spatial.SpatialInteractionManagerInterop"),
      3 to setOf(
          "windows.ui.input.RadialControllerConfigurationInterop",
          "windows.ui.input.RadialControllerInterop",
      ),
      4 to setOf("windows.ui.input.core.RadialControllerIndependentInputSourceInterop"),
      15 to setOf("windows.graphics.display.DisplayInformationInterop"),
  )
  ```

  Each boundary includes all lower-gated helpers plus `winrt.interop.WindowNative` and `winrt.interop.InitializeWithWindow`. Also assert: no SDK selection emits neither UAC-gated helpers nor a guessed UAC; a path source containing UAC metadata still emits none; `additionExclude` removes owning namespaces; missing required types reports a deterministic prerequisite diagnostic rather than emitting a broken helper.

- [ ] **Step 2: Run model tests and verify RED**

  Run:

  ```powershell
  .\gradlew.bat :winrt-metadata:test --tests "io.github.composefluent.winrt.metadata.WinRTMetadataModelTest" --max-workers=1
  ```

  Expected: compilation fails because the catalog and SDK-aware namespace additions do not exist.

- [ ] **Step 3: Implement catalog descriptor types and the exact 15 adapters**

  Define sealed result IID sources (`DefaultInterface`, `ParameterizedInterface`, `IAsyncAction`, `Constant`) and ABI value kinds (`RawAddress`, `String`, `ProjectedObject`). Populate exactly this reference table, all with IInspectable base slot 6 except DataTransfer's IUnknown slots 3/4:

  | Helper | UAC | Query IID | Activation type | Methods and slots |
  | --- | ---: | --- | --- | --- |
  | `DragDropManagerInterop` | 1 | `5AD8CBA7-4C01-4DAC-9074-827894292D63` | `CoreDragDropManager` | `getForWindow(hwnd): CoreDragDropManager` slot 6 |
  | `PrintManagerInterop` | 1 | `C5435A42-8D43-4E7B-A68A-EF311E392087` | `PrintManager` | `getForWindow(hwnd): PrintManager` slot 6; `showPrintUIForWindowAsync(hwnd): IAsyncOperation<Boolean>` slot 7 |
  | `SystemMediaTransportControlsInterop` | 1 | `DDB0472D-C911-4A1F-86D9-DC3D71A95F5A` | `SystemMediaTransportControls` | `getForWindow(hwnd)` slot 6 |
  | `PlayToManagerInterop` | 1 | `24394699-1F2C-4EB3-8CD7-0EC1DA42A540` | `PlayToManager` | `getForWindow(hwnd)` slot 6; `showPlayToUIForWindow(hwnd)` slot 7 |
  | `UserConsentVerifierInterop` | 1 | `39E050C3-4E74-441A-8DC0-B81104DF949C` | `UserConsentVerifier` | `requestVerificationForWindowAsync(hwnd, message)` slot 6 |
  | `WebAuthenticationCoreManagerInterop` | 1 | `F4B8E804-811E-4436-B69C-44CB67B72084` | `WebAuthenticationCoreManager` | `requestTokenForWindowAsync(hwnd, request)` slot 6; `requestTokenWithWebAccountForWindowAsync(hwnd, request, webAccount)` slot 7 |
  | `AccountsSettingsPaneInterop` | 1 | `D3EE12AD-3865-4362-9746-B75A682DF0E6` | `AccountsSettingsPane` | `getForWindow(hwnd)` slot 6; `showManageAccountsForWindowAsync(hwnd)` slot 7; `showAddAccountForWindowAsync(hwnd)` slot 8 |
  | `InputPaneInterop` | 1 | `75CF2C57-9195-4931-8332-F0B409E916AF` | `InputPane` | `getForWindow(hwnd)` slot 6 |
  | `UIViewSettingsInterop` | 1 | `3694DBF9-8F68-44BE-8FF5-195C98EDE8A6` | `UIViewSettings` | `getForWindow(hwnd)` slot 6 |
  | `DataTransferManagerInterop` | 1 | `3A3DCD6C-3EAB-43DC-BCDE-45671CE800C8` | `DataTransferManager` | `getForWindow(hwnd)` IUnknown slot 3, constant result IID `A5CAEE9B-8708-49D1-8D36-67D25A8DA00C`; `showShareUIForWindow(hwnd)` slot 4 |
  | `SpatialInteractionManagerInterop` | 2 | `5C4EE536-6A98-4B86-A170-587013D6FD4B` | `SpatialInteractionManager` | `getForWindow(hwnd)` slot 6 |
  | `RadialControllerConfigurationInterop` | 3 | `787CDAAC-3186-476D-87E4-B9374A7B9970` | `RadialControllerConfiguration` | `getForWindow(hwnd)` slot 6 |
  | `RadialControllerInterop` | 3 | `1B0535C9-57AD-45C1-9D79-AD5C34360513` | `RadialController` | `createForWindow(hwnd)` slot 6 |
  | `RadialControllerIndependentInputSourceInterop` | 4 | `3D577EFF-4CEE-11E6-B535-001BDC06AB3B` | `RadialControllerIndependentInputSource` | `createForWindow(hwnd)` slot 6 |
  | `DisplayInformationInterop` | 15 | `7449121C-382B-4705-8DA7-A795BA482013` | `DisplayInformation` | `getForWindow(hwnd)` slot 6; `getForMonitor(monitor)` slot 7 |

  Record each method's fully qualified parameter/result type prerequisites. `forProjection` must require an explicit `WindowsSdk` source and a non-null UAC inventory, apply namespace/addition filters, enforce the UAC floor, and validate required types against the normalized model. Replace the static empty COM additions in `WinRTNamespaceAdditions.all` with catalog-derived additions; retain the unversioned `WinRT.Interop` addition and unrelated source additions unchanged.

- [ ] **Step 4: Run model tests and verify GREEN**

  Run the command from Step 2. Expected: all `WinRTMetadataModelTest` tests pass, including the absorbed `InputPaneInterop` test rewritten to declare UAC 1.

- [ ] **Step 5: Update `PLAN.md` and commit Task 2**

  ```powershell
  git add PLAN.md winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTComInteropAdapters.kt winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataTraversal.kt winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModelTest.kt
  git commit -m "feat(metadata): catalog Windows COM interop adapters"
  ```

### Task 3: Render Runtime-Class and Unit COM Helpers Generically

**Files:**
- Create: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinComInteropSourceRenderer.kt`
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionSupportRenderer.kt`
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGenerator.kt`
- Test: `winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: selected `WinRTComInteropAdapterDescriptor` values from `WinRTMetadataProjectionInventory` plus `KotlinProjectionRenderer.renderAbiTypeBinding(...)`.
- Produces: `KotlinComInteropSourceRenderer.render(descriptor, model): KotlinProjectionFile`.

- [ ] **Step 1: Write failing common-renderer tests for runtime-class and unit shapes**

  Generate a UAC 15 model and assert all simple helper files exist with camel-cased public methods, exact slots/IIDs, projected return types, and `RawAddress` parameters. Assert `PlayToManagerInterop.showPlayToUIForWindow` and `DataTransferManagerInterop.showShareUIForWindow` use `WinRTProjectionIntrinsic.callUnit`. Assert every helper source lacks `ComVtableInvoker` and generic fallback text. Remove the existing InputPane-only assertions and replace them with table-driven assertions over all runtime-class/unit methods.

- [ ] **Step 2: Run generator tests and verify RED**

  Run:

  ```powershell
  .\gradlew.bat :winrt-generator:test --tests "io.github.composefluent.winrt.projections.generator.KotlinProjectionGeneratorTest" --max-workers=1
  ```

  Expected: tests fail because catalog FQNs are not rendered and the InputPane hard-coded branch cannot render the table.

- [ ] **Step 3: Implement descriptor-driven runtime-class/unit rendering**

  Construct KotlinPoet files from descriptors. For runtime-class results allocate/write the result IID in a confined scope and call:

  ```kotlin
  WinRTProjectionIntrinsic.callProjectedRuntimeClass(
      interop,
      method.slot,
      abiSignature,
      ResultType.Metadata::wrap,
      publicArguments,
      resultIidAddress,
  )
  ```

  For unit results call `WinRTProjectionIntrinsic.callUnit` without a result pointer. Use `ActivationFactory.get(descriptor.activationTypeName, Guid(descriptor.queryIid))` for both IInspectable and DataTransfer IUnknown descriptors. Derive package/path/object/method names from descriptors, and derive ABI signatures through existing type bindings rather than a new primitive branch table. Replace `renderSourceAdditionFile(typeName)` with a catalog lookup/delegation; keep only `WindowNative`, `InitializeWithWindow`, and `Win32Interop` as explicit non-catalog cases. Delete `windowsUiViewManagementInputPaneInteropSource()`.

  Remove `withWindowsSdkSourceForWindowsProjectionRoots()`: type completion may use only declared sources, and helper emission must never manufacture an SDK selection.

- [ ] **Step 4: Run generator tests and verify GREEN**

  Run the command from Step 2. Expected: all `KotlinProjectionGeneratorTest` tests pass for runtime-class/unit helper shapes.

- [ ] **Step 5: Update `PLAN.md` and commit Task 3**

  ```powershell
  git add PLAN.md winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinComInteropSourceRenderer.kt winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionSupportRenderer.kt winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGenerator.kt winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt
  git commit -m "feat(generator): render Windows COM interop helpers"
  ```

### Task 4: Render Async, HSTRING, and Projected-Object ABI Shapes

**Files:**
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinComInteropSourceRenderer.kt`
- Test: `winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: existing `descriptorIntrinsicArgument`, projected-object marshalers, `WinRTAsyncOperationReference.interfaceId(signature)`, and `WinRTAsyncInterfaceIds.IAsyncAction`.
- Produces: complete bodies for `PrintManagerInterop`, `UserConsentVerifierInterop`, `WebAuthenticationCoreManagerInterop`, and `AccountsSettingsPaneInterop` async methods.

- [ ] **Step 1: Write failing async/marshalling body tests**

  Assert exact public signatures:

  ```kotlin
  public fun showPrintUIForWindowAsync(appWindow: RawAddress): WinRTAsyncOperationReference<Boolean>
  public fun requestVerificationForWindowAsync(appWindow: RawAddress, message: String): WinRTAsyncOperationReference<UserConsentVerificationResult>
  public fun requestTokenForWindowAsync(appWindow: RawAddress, request: WebTokenRequest): WinRTAsyncOperationReference<WebTokenRequestResult>
  public fun requestTokenWithWebAccountForWindowAsync(appWindow: RawAddress, request: WebTokenRequest, webAccount: WebAccount): WinRTAsyncOperationReference<WebTokenRequestResult>
  public fun showManageAccountsForWindowAsync(appWindow: RawAddress): WinRTAsyncActionReference
  public fun showAddAccountForWindowAsync(appWindow: RawAddress): WinRTAsyncActionReference
  ```

  Assert the generated body scopes/disposes HSTRING and projected-object marshalers, supplies the hidden result-IID address last, uses parameterized async IIDs, and calls the typed projected-interface/async intrinsic path. Reject raw projected object arguments, leaked HSTRING ownership, `ComVtableInvoker`, and vararg fallback.

- [ ] **Step 2: Run generator tests and verify RED**

  Use the Task 3 test command. Expected: async/HSTRING/projected-object body assertions fail.

- [ ] **Step 3: Implement async IID and parameter marshalling through existing abstractions**

  Resolve every public and ABI type through `renderAbiTypeBinding`. Reuse `descriptorIntrinsicArgument(...)` to obtain parameter ABI shapes; use existing scoped marshaller code generation for `String`, `WebTokenRequest`, and `WebAccount`. Allocate/write the async result IID:

  ```kotlin
  val resultIid = when (result.iidSource) {
      WinRTComInteropResultIidSource.IAsyncAction -> WinRTAsyncInterfaceIds.IAsyncAction
      is WinRTComInteropResultIidSource.ParameterizedInterface ->
          WinRTAsyncOperationReference.interfaceId(abiTypeSignature(resultBinding))
      else -> resultIidFromDescriptor(result, resultBinding)
  }
  ```

  Use the existing projected-interface/async intrinsic rendering helpers so returned pointers acquire exactly one managed owner. The inspected `WinRTProjectionIntrinsic` surface already represents these descriptors, so this task makes no runtime or compiler-plugin change. If implementation evidence contradicts that inspection, stop before editing those modules and amend the approved design and this plan with the exact missing common intrinsic plus both JVM/native lowerings.

- [ ] **Step 4: Run generator and runtime/compiler tests and verify GREEN**

  Run:

  ```powershell
  .\gradlew.bat :winrt-generator:test --tests "io.github.composefluent.winrt.projections.generator.KotlinProjectionGeneratorTest" --max-workers=1
  ```

  If runtime/compiler code changed, also run each affected module's test task separately with `--max-workers=1`. Expected: all affected tests pass.

- [ ] **Step 5: Update `PLAN.md` and commit Task 4**

  Stage only files actually needed for async/marshalling completion and commit:

  ```powershell
  git commit -m "feat(generator): marshal COM interop async calls"
  ```

### Task 5: Preserve Exact Gradle Identity and Dependency Suppression

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTIdentityTask.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Test: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: exact catalog-filtered `source-additions.tsv` emitted by the generator.
- Produces: identity `generatedTypeNames` containing only helpers emitted for the declared SDK/UAC and filters; downstream `suppressedSourceAdditionTypeNames` removes those exact FQNs.

- [ ] **Step 1: Write failing Gradle boundary and dependency tests**

  Replace the current static `sourceAdditionTypeNames(namespaces, excludes)` InputPane assertion with fixture-backed SDK/UAC cases. Assert UAC 1, 2, 3, 4, 14, and 15 publish the same cumulative sets as Task 2. Add a fixture with Windows WinMD path input but no `windowsSdk(...)` and assert no UAC-gated additions. Add a producer/consumer fixture where the producer owns UAC-15 helper FQNs and assert the consumer emits none of those helper files or identity names.

- [ ] **Step 2: Run plugin tests and verify RED**

  Run:

  ```powershell
  .\gradlew.bat :winrt-gradle-plugin:test --tests "io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest" --max-workers=1
  ```

  Expected: boundary/producer-consumer assertions fail while identity still reconstructs static namespace additions.

- [ ] **Step 3: Make Gradle consume exact emitted catalog ownership**

  Keep the existing source-addition manifest as the sole helper-FQN boundary. Pass declared Windows SDK source information through every metadata/generator context used by generation and identity. Remove any direct `WinRTNamespaceAdditions.forNamespaces(...)` reconstruction at Gradle call sites when it can diverge from SDK/UAC selection; identity must read or calculate the same `WinRTMetadataProjectionInventory` used by generation. Apply existing dependency identity suppression after catalog selection, never inside metadata.

- [ ] **Step 4: Run plugin tests and verify GREEN**

  Run the command from Step 2. Expected: all `KotlinWinRTPluginTest` tests pass.

- [ ] **Step 5: Update `PLAN.md` and commit Task 5**

  ```powershell
  git add PLAN.md winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTIdentityTask.kt winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt
  git commit -m "fix(gradle): publish UAC-gated interop identity"
  ```

### Task 6: Compile Real Windows SDK Projections and Close the Slice

**Files:**
- Modify generated/checked-in outputs only through their owning generation task under `winrt-projections/windows-sdk`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: complete metadata catalog, generator renderer, Gradle identity, and installed Windows SDK `Platform.xml` inventories.
- Produces: compiling JVM and `mingwX64` Windows SDK projections with all helpers supported by the selected SDK.

- [ ] **Step 1: Run targeted metadata, generator, and Gradle test classes separately**

  Run the three commands from Tasks 1/2, 3/4, and 5 as separate Gradle invocations. Expected: all pass.

- [ ] **Step 2: Validate installed SDK boundary inventories where available**

  For each installed `10.0.19041.0`, `10.0.22000.0`, `10.0.22621.0`, and `10.0.26100.0`, invoke the Windows SDK generation/identity test fixture with that explicit version. Expected: helper set equals its `Platform.xml` UAC major, including `DisplayInformationInterop` only at UAC 15 or newer. Record unavailable local SDKs as environment gaps in `PLAN.md`; do not guess their UAC versions.

- [ ] **Step 3: Compile the Windows SDK projection for JVM**

  Run:

  ```powershell
  .\gradlew.bat :winrt-projections:windows-sdk:compileKotlinJvm --max-workers=1
  ```

  Expected: BUILD SUCCESSFUL and all generated helper sources compile.

- [ ] **Step 4: Compile the Windows SDK projection for `mingwX64`**

  Run:

  ```powershell
  .\gradlew.bat :winrt-projections:windows-sdk:compileKotlinMingwX64 --max-workers=1
  ```

  Expected: BUILD SUCCESSFUL with the same public helper API and native intrinsic lowering.

- [ ] **Step 5: Audit generated sources and identity**

  Verify the selected UAC-15-or-newer SDK produces exactly 17 helper FQNs: the 15 catalog helpers plus `winrt.interop.WindowNative` and `winrt.interop.InitializeWithWindow`. Verify `source-additions.tsv` contains those same sorted FQNs, no internal `WinRT.Interop.I*Interop` public declarations, no duplicate helper owners, no `ComVtableInvoker`, and no change to `microsoft.ui.Win32Interop`.

- [ ] **Step 6: Complete `PLAN.md`, run final diff/status review, and commit validation**

  Mark the active COM interop item complete with exact command results and any resource/environment limitations. Review `git diff --check`, `git status --short`, and the commits from Tasks 1-5. Commit only final projection/plan evidence:

  ```powershell
  git add PLAN.md winrt-projections/windows-sdk
  git commit -m "test(projections): validate Windows COM interop parity"
  ```

  If generation leaves no checked-in projection change, commit `PLAN.md` alone with the same message.
