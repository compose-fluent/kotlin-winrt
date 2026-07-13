# Projection Topology And Array Type Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore identity-driven projection ownership, move repository-specific prebuilt mechanics into `kotlin-winrt-build-convention`, restore mutually exclusive XAML families, keep Windows App SDK independent from a Windows SDK artifact version, and remove JVM reflection from reference-array classification.

**Architecture:** The public Kotlin/WinRT plugin owns generic identity and generation behavior only. A repository convention plugin derives prebuilt build references, publication metadata, and validation from standard Gradle dependencies. Reference arrays use explicit element metadata or `reified T`; dynamic arrays share one conservative common classifier.

**Tech Stack:** Kotlin 2.4 Multiplatform, Gradle 9.4, Kotlin DSL convention plugins, Maven Publish, Gradle TestKit, JUnit/kotlin.test, JVM and Kotlin/Native `mingwX64`.

## Global Constraints

- Follow `.cswinrt/src/Projections/Windows`, `Windows.UI.Xaml`, and `WinAppSDK`.
- `Windows.UI.Xaml` and `Microsoft.UI.Xaml` are mutually exclusive projection families.
- Windows SDK is a non-published build/identity reference for XAML, WebView2, and App SDK prebuilt artifacts.
- App SDK publishes WebView2 but never publishes a Windows SDK dependency.
- Prebuilt mechanics belong in `kotlin-winrt-build-convention`, not the public Kotlin/WinRT plugin.
- The existing identity graph is the only generated-type ownership graph.
- Shared runtime code uses KMP `KClass` and explicit metadata, never Java component reflection.
- Each task updates `PLAN.md`, validates JVM and `mingwX64` where applicable, and ends in an atomic commit.

---

### Task 1: Reject Conflicting Dependency Identities

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTProjectionsTask.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Produces: `validateDependencyProjectionIdentityOwnership(identityFiles: Iterable<File>)`.
- Consumes: `readProjectionSurfaceIdentity(File)`.

- [ ] **Step 1: Add a failing duplicate-owner test**

Create two identity JSON files that both claim `Windows.UI.Xaml.Data.BindableAttribute`. Assert the new validator fails and names the type plus both files. Add a disjoint control case that succeeds.

```kotlin
val failure = assertFailsWith<IllegalStateException> {
    validateDependencyProjectionIdentityOwnership(listOf(firstIdentity, secondIdentity))
}
assertTrue(failure.message.orEmpty().contains("Windows.UI.Xaml.Data.BindableAttribute"))
```

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat -p winrt-gradle-plugin :test --tests "*dependency_projection_identity*" --rerun-tasks --max-workers=1
```

Expected: FAIL because projected dependency owners are currently unioned.

- [ ] **Step 3: Implement one ownership validator**

```kotlin
internal fun validateDependencyProjectionIdentityOwnership(identityFiles: Iterable<File>) {
    val projectedTypeOwners = linkedMapOf<String, File>()
    val sourceAdditionOwners = linkedMapOf<String, File>()
    identityFiles.sortedBy(File::getAbsolutePath).forEach { identityFile ->
        val identity = readProjectionSurfaceIdentity(identityFile)
        identity.currentShapeProjectedTypes().forEach { typeName ->
            requireUniqueDependencyOwner("Projected type", typeName, identityFile, projectedTypeOwners)
        }
        identity.sourceAdditions.forEach { typeName ->
            requireUniqueDependencyOwner("Source addition", typeName, identityFile, sourceAdditionOwners)
        }
    }
}
```

Call it before dependency-owned suppression and reuse it from source-addition collection.

- [ ] **Step 4: Verify GREEN and commit**

```powershell
./gradlew.bat -p winrt-gradle-plugin :test --tests "*dependency_projection_identity*" --tests "*dependency_owned_projection*" --max-workers=1
git add winrt-gradle-plugin PLAN.md
git commit -m "fix(gradle): reject conflicting projection identities"
```

### Task 2: Create The Repository Prebuilt Convention

**Files:**
- Modify: `kotlin-winrt-build-convention/build.gradle.kts`
- Create: `kotlin-winrt-build-convention/src/main/kotlin/io/github/composefluent/winrt/build/WinRTPrebuiltProjectionConventionPlugin.kt`
- Create: `kotlin-winrt-build-convention/src/main/kotlin/io/github/composefluent/winrt/build/ValidatePrebuiltProjectionOutputTask.kt`
- Create: `kotlin-winrt-build-convention/src/main/kotlin/io/github/composefluent/winrt/build/ValidatePrebuiltProjectionPublicationTask.kt`
- Create: `kotlin-winrt-build-convention/src/main/kotlin/io/github/composefluent/winrt/build/ProjectionArtifactVersion.kt`
- Create: `kotlin-winrt-build-convention/src/test/kotlin/io/github/composefluent/winrt/build/WinRTPrebuiltProjectionConventionPluginTest.kt`
- Create: `kotlin-winrt-build-convention/src/test/kotlin/io/github/composefluent/winrt/build/ValidatePrebuiltProjectionOutputTaskTest.kt`
- Create: `kotlin-winrt-build-convention/src/test/kotlin/io/github/composefluent/winrt/build/ValidatePrebuiltProjectionPublicationTaskTest.kt`
- Delete: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/ValidateGeneratedWinRTProjectionOutputTask.kt`
- Delete: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/ValidateSplitProjectionPublicationTask.kt`
- Delete: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/ValidateGeneratedWinRTProjectionOutputTaskTest.kt`
- Delete: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/ValidateSplitProjectionPublicationTaskTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Produces plugin id `winrt.prebuilt-projection`.
- Reads `commonMainCompileOnly` as non-published projection references.
- Reads `commonMainApi` as real public dependencies.
- Produces `auditGeneratedWinRTProjectionOutput` and `validatePrebuiltProjectionPublication` tasks.

- [ ] **Step 1: Add a failing TestKit fixture**

Create `sdk` and `projection` projects. Apply `winrt.prebuilt-projection` and declare:

```kotlin
dependencies {
    commonMainCompileOnly(project(":sdk"))
}
```

Assert the SDK project appears in `kotlinWinRTLibraryDependencyIdentity`, `check` depends on the generated-output audit, and the publication validator exists. Add a POM fixture proving compile-only references are absent while `commonMainApi` dependencies use compile scope.

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat -p kotlin-winrt-build-convention test --tests "*WinRTPrebuiltProjectionConventionPluginTest*" --rerun-tasks --max-workers=1
```

Expected: FAIL because the plugin does not exist.

- [ ] **Step 3: Register and implement the convention**

Register in `kotlin-winrt-build-convention/build.gradle.kts`:

```kotlin
gradlePlugin {
    plugins {
        create("winRTPrebuiltProjection") {
            id = "winrt.prebuilt-projection"
            implementationClass = "io.github.composefluent.winrt.build.WinRTPrebuiltProjectionConventionPlugin"
        }
    }
}
```

The convention applies `build-convention` and `winrt.publish`. Prebuilt modules continue to apply `io.github.compose-fluent.winrt` explicitly; the convention uses `pluginManager.withPlugin("io.github.compose-fluent.winrt")` to lazily mirror `commonMainCompileOnly` project dependencies into the existing identity configuration, register both validation tasks, and wire the audit into `check`. The convention build must not depend on or include the public plugin build, and it must not use `evaluationDependsOn`.

- [ ] **Step 4: Move publication/output validation**

Move repository-specific split POM/module validation from `winrt-gradle-plugin` into `ValidatePrebuiltProjectionPublicationTask`. The task checks JVM, mingw, and KMP metadata and derives required API modules from `commonMainApi`.

Move generated-source and prebuilt cross-artifact class checking from `ValidateGeneratedWinRTProjectionOutputTask` into `ValidatePrebuiltProjectionOutputTask`. Its comparison set is the current artifact plus direct compile-only/API project references, never a hand-maintained peer graph. Update the root projection smoke module to use the convention-build task type as well.

- [ ] **Step 5: Verify GREEN and commit**

```powershell
./gradlew.bat -p kotlin-winrt-build-convention test --max-workers=1
./gradlew.bat -p winrt-gradle-plugin test --tests "*KotlinWinRTPluginTest*" --max-workers=1
git add kotlin-winrt-build-convention winrt-gradle-plugin PLAN.md
git commit -m "build: centralize prebuilt projection conventions"
```

### Task 3: Restore Prebuilt Projection Families

**Files:**
- Modify: `winrt-projections/windows-sdk/build.gradle.kts`
- Modify: `winrt-projections/windows-webview2/build.gradle.kts`
- Modify: `winrt-projections/windows-ui-xaml/build.gradle.kts`
- Modify: `winrt-projections/windows-app-sdk/build.gradle.kts`
- Modify: `winrt-projections/windows-webview2-consumer-fixture/build.gradle.kts`
- Modify: `winrt-projections/windows-ui-xaml-consumer-fixture/build.gradle.kts`
- Modify: `winrt-projections/windows-app-sdk-consumer-fixture/build.gradle.kts`
- Modify: root `build.gradle.kts`
- Modify: `README.md`
- Modify: `PLAN.md`

**Interfaces:**
- XAML/WebView2/App SDK use Windows SDK through `commonMainCompileOnly`.
- App SDK alone publishes WebView2 through `commonMainApi`.
- Legal compositions: SDK + Windows.UI.Xaml; SDK + WebView2 + App SDK.

- [ ] **Step 1: Add failing publication/family tests**

Assert:

```text
App SDK POM: no Windows SDK, WebView2 compile dependency present
Windows.UI.Xaml POM: no Windows SDK
WebView2 POM: no Windows SDK
SDK + Windows.UI.Xaml: valid
SDK + WebView2 + App SDK: valid
Windows.UI.Xaml + App SDK: duplicate identity owner failure
```

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat validateWinRTSplitProjectionPublication --rerun-tasks --max-workers=1
```

Expected: FAIL because current publications bind Windows SDK and the XAML filters hide the family conflict.

- [ ] **Step 3: Simplify prebuilt scripts**

Apply `id("winrt.prebuilt-projection")`. Remove per-module `evaluationDependsOn`, manual identity additions, `pom.withXml`, audit registration, `check` wiring, SDK project version mutation, and local `projectionArtifactVersion` definitions.

Use:

```kotlin
// Windows.UI.Xaml and WebView2
dependencies {
    commonMainCompileOnly(project(":winrt-projections:windows-sdk"))
}

// App SDK
dependencies {
    commonMainCompileOnly(project(":winrt-projections:windows-sdk"))
    commonMainApi(project(":winrt-projections:windows-webview2"))
}
```

- [ ] **Step 4: Restore XAML independence**

Remove the `BindableAttribute` and `ContentPropertyAttribute` exclusions from `windows-ui-xaml`. Keep only `.cswinrt`-aligned exclusions and the separately tracked Maps collision. Do not make App SDK and Windows.UI.Xaml dependencies of each other.

- [ ] **Step 5: Simplify consumers**

Remove all explicit `kotlinWinRTLibraryDependencyIdentity` declarations. Each non-SDK consumer independently declares its selected SDK coordinate plus its target coordinate. App SDK receives WebView2 transitively.

- [ ] **Step 6: Verify and commit**

```powershell
./gradlew.bat -I gradle/nuget-root.init.gradle validateWinRTSplitProjectionPublication --max-workers=1
git add winrt-projections build.gradle.kts README.md PLAN.md
git commit -m "fix(projections): restore independent xaml families"
```

### Task 4: Make Reference Array Typing Explicit

**Files:**
- Delete: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ReferenceArrayComponentType.kt`
- Delete: `winrt-runtime/src/jvmMain/kotlin/io/github/composefluent/winrt/runtime/ReferenceArrayComponentType.jvm.kt`
- Delete: `winrt-runtime/src/mingwX64Main/kotlin/io/github/composefluent/winrt/runtime/ReferenceArrayComponentType.mingwX64.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WinRTTypeClassifier.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/KClassExtensions.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ValueBoxingMetadata.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ValueBoxing.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/Marshalers.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/InspectableValueSupport.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WinRTObjectMarshaller.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ComWrappersSupport.kt`
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionArrayAbiRenderer.kt`
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionAbiRenderer.kt`
- Test: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/ValueBoxingTest.kt`
- Test: `winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Produces common classifier `classifyReferenceArray(value: Array<*>, declaredElementType: KClass<*>? = null)`.
- Produces `Marshaler.inspectableArray(elementType: KClass<*>): Marshaler<Any?>` and reified convenience `Marshaler.inspectableArray<T>()`.
- `inspectableAny()` remains dynamic and supplies no declared element type.

- [ ] **Step 1: Add failing common tests**

Cover explicit `Any`, explicit `Int`, dynamic homogeneous, dynamic heterogeneous, empty, and all-null arrays. Add a source audit that rejects `.componentType`, `.javaClass`, and `type.java` in reference-array classification.

```kotlin
assertEquals(IID.IReferenceArrayOfObject, referenceArrayIid(arrayOf<Any?>(1, 2), Any::class))
assertEquals(IID.IReferenceArrayOfInt32, referenceArrayIid(arrayOf(1, 2), Int::class))
assertEquals(IID.IReferenceArrayOfObject, referenceArrayIid(emptyArray<Any?>(), null))
assertEquals(IID.IReferenceArrayOfObject, referenceArrayIid(arrayOf<Any?>(1, "text"), null))
```

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat :winrt-runtime:jvmTest --tests "*ValueBoxingTest*reference*array*" --rerun-tasks --max-workers=1
./gradlew.bat :winrt-runtime:compileTestKotlinMingwX64 --max-workers=1
```

Expected: FAIL because explicit typing does not exist and JVM reflection remains.

- [ ] **Step 3: Remove expect/actual reflection**

Delete the three component-type files. `WinRTTypeClassifier.arrayElementType` retains primitive arrays only. Implement one shared classifier:

```kotlin
private fun classifyReferenceArray(value: Array<*>, declaredElementType: KClass<*>?): WinRTValueTypeMetadata {
    declaredElementType?.let { return descriptorForClass(it) ?: objectMetadata }
    var inferred: WinRTValueTypeMetadata? = null
    value.filterNotNull().forEach { element ->
        val descriptor = descriptorForValue(element) ?: return objectMetadata
        if (inferred != null && inferred != descriptor) return objectMetadata
        inferred = descriptor
    }
    return inferred ?: objectMetadata
}
```

- [ ] **Step 4: Carry explicit metadata through marshaling**

Add explicit and reified `inspectableArray` factories. The selected descriptor must drive the reference-array IID, `IPropertyValue` array type, and runtime class name through synthetic CCW creation. Do not infer from the runtime array class.

- [ ] **Step 5: Update generated known-array paths**

Render `Marshaler.inspectableArray<QualifiedElementType>()` whenever generator/authoring metadata knows a reference-array element type. Keep `inspectableAny()` only for truly dynamic `Any?`.

- [ ] **Step 6: Verify parity and commit**

```powershell
./gradlew.bat :winrt-runtime:jvmTest :winrt-runtime:mingwX64Test :winrt-generator:test --max-workers=1
git add winrt-runtime winrt-generator winrt-authoring PLAN.md
git commit -m "fix(runtime): make reference array typing explicit"
```

### Task 5: Run The Final Cross-Target Gate

**Files:**
- Modify: root `build.gradle.kts`
- Modify: this plan file
- Modify: `PLAN.md`

- [ ] **Step 1: Update aggregate validation**

The final gate must include convention tests, public plugin tests, identity conflict coverage, both legal projection families, invalid mixed-family validation, JVM/Native runtime tests, generator tests, publication metadata, and WinUI/non-WinUI sample compiles.

- [ ] **Step 2: Run the complete Windows gate**

```powershell
$env:NUGET_PACKAGES='F:\Dependencies\nuget'
$env:KONAN_DATA_DIR='C:\Users\Sanlorng\.konan'
./gradlew.bat -I gradle/nuget-root.init.gradle validateProjectReviewRemediation --max-workers=1
```

Expected: `BUILD SUCCESSFUL`; the invalid family fixture fails only inside its expected-failure validator.

- [ ] **Step 3: Audit source and publication boundaries**

```powershell
git diff --check
rg -n "evaluationDependsOn|kotlinWinRTLibraryDependencyIdentity|pom.withXml" winrt-projections
rg -n "componentType|javaClass|type\.java" winrt-runtime/src -g '*.kt'
```

Expected: no prebuilt-script duplication and no reference-array Java reflection.

- [ ] **Step 4: Complete plan and commit**

Mark this plan and Review-Followup-01 complete.

```powershell
git add build.gradle.kts docs/superpowers/plans/2026-07-13-projection-topology-and-array-type-boundary.md PLAN.md
git commit -m "test: validate projection topology convergence"
```
