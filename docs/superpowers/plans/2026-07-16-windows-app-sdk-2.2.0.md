# Windows App SDK 2.2.0 Default Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Windows App SDK `2.2.0` the repository-wide active default for projection builds, samples, documentation, CI, and publication validation.

**Architecture:** Keep the existing Gradle property and per-module fallback ownership. Update only active baseline literals and current-default fixtures; preserve explicit property overrides, projection ownership boundaries, and historical validation records.

**Tech Stack:** Kotlin Multiplatform, Gradle Kotlin DSL, GitHub Actions YAML, PowerShell, Windows App SDK NuGet metadata.

## Global Constraints

- The active Windows App SDK default is exactly `2.2.0`.
- The Windows SDK projection baseline remains `10.0.26100.0`.
- The WebView2 projection baseline remains `1.0.3719.77`.
- Explicit `kotlinWinRT.projections.windowsAppSdkVersion` and `kotlinWinRT.samples.windowsAppSdkVersion` overrides continue to win over defaults.
- Historical design/implementation records that state a prior `2.1.3` validation remain unchanged.
- Validation runs from Windows through `.\gradlew.bat` and must not include the pre-existing `.gradle-review/` or `.worktrees/` directories in commits.

---

### Task 1: Replace active Windows App SDK baseline literals

**Files:**
- Modify: `gradle.properties:18`
- Modify: `build.gradle.kts:175-184`
- Modify: `winrt-projections/build.gradle.kts:9-10`
- Modify: `winrt-projections/windows-app-sdk/build.gradle.kts:17-19`
- Modify: `winrt-samples/build.gradle.kts:70-72`
- Modify: `winrt-samples/winui-kmp-app/build.gradle.kts:11-13`
- Modify: `winrt-samples/winui-kmp-library/build.gradle.kts:13-15`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt:1138,9389`
- Modify: `README.md:8,192,197,223,261`
- Modify: `.github/workflows/ci.yml:66,107`
- Modify: `.github/workflows/publish-snapshot.yml:57,90`
- Modify: `.github/workflows/publish-projections.yml:29`

**Interfaces:**
- Consumes: Existing Gradle properties, per-module `Provider` fallbacks, workflow inputs, and current-default test fixtures.
- Produces: One active `2.2.0` baseline while preserving all existing override property names and artifact coordinates.

- [ ] **Step 1: Record the pre-change active references**

Run:

```powershell
rg -n -F "2.1.3" gradle.properties build.gradle.kts README.md .github winrt-projections winrt-samples winrt-gradle-plugin/src/test
```

Expected: matches are limited to the active defaults, current examples, workflow cache/restore inputs, and the two fixtures listed above.

- [ ] **Step 2: Update each active literal to `2.2.0`**

Apply these exact substitutions in the listed files:

```text
kotlinWinRT.projections.windowsAppSdkVersion=2.1.3
  -> kotlinWinRT.projections.windowsAppSdkVersion=2.2.0
.orElse("2.1.3")
  -> .orElse("2.2.0")
Microsoft.WindowsAppSDK@2.1.3
  -> Microsoft.WindowsAppSDK@2.2.0
Microsoft.WindowsAppSDK-2.1.3
  -> Microsoft.WindowsAppSDK-2.2.0
WindowsAppSDK-2.1.3
  -> WindowsAppSDK-2.2.0
```

Change the README badge and current dependency examples from `2.1.3` to `2.2.0`. Do not change `docs/superpowers/plans/**` or historical `PLAN.md` completion notes.

- [ ] **Step 3: Run the stale-active-baseline guard**

Run the same `rg` command from Step 1.

Expected: no output and exit code `1`; any match indicates an active baseline was missed.

- [ ] **Step 4: Inspect the diff for scope and formatting**

Run `git diff --check`, `git diff --stat`, and `git status --short` as separate commands.

Expected: only the listed baseline/configuration/docs/workflow/test files are modified; `.gradle-review/` and `.worktrees/` remain untracked and unstaged.

### Task 2: Validate the default projection and close the plan item

**Files:**
- Modify: `PLAN.md:Current Focus Queue`
- Test: `winrt-projections/windows-app-sdk` JVM and `mingwX64` compile tasks

**Interfaces:**
- Consumes: The `2.2.0` defaults produced by Task 1 and the existing Windows SDK/WebView2 baselines.
- Produces: Verified generated Windows App SDK projection compilation and a completed root-plan item.

- [ ] **Step 1: Compile the Windows App SDK metadata and JVM projection with repository defaults**

Run:

```powershell
.\gradlew.bat :winrt-projections:windows-app-sdk:compileCommonMainKotlinMetadata :winrt-projections:windows-app-sdk:compileKotlinJvm --no-configuration-cache --max-workers=1
```

Expected: both tasks succeed while resolving the `Microsoft.WindowsAppSDK` `2.2.0` metadata closure.

- [ ] **Step 2: Compile the native projection when the Windows Kotlin/Native toolchain is available**

Run:

```powershell
.\gradlew.bat :winrt-projections:windows-app-sdk:compileKotlinMingwX64 --no-configuration-cache --max-workers=1
```

Expected: the task succeeds; if the local native toolchain is unavailable, record the concrete toolchain blocker in the final report without changing the default back to `2.1.3`.

- [ ] **Step 3: Mark the root plan item complete**

Change the `Default-WASDK-2.2.0` item in `PLAN.md` from `- [ ] 正在做` to `- [x]`, retaining the design path, `.cswinrt` mapping, and validation result.

- [ ] **Step 4: Commit the coherent version-baseline slice**

Run:

```powershell
git add -- gradle.properties build.gradle.kts README.md .github/workflows/ci.yml .github/workflows/publish-snapshot.yml .github/workflows/publish-projections.yml winrt-projections/build.gradle.kts winrt-projections/windows-app-sdk/build.gradle.kts winrt-samples/build.gradle.kts winrt-samples/winui-kmp-app/build.gradle.kts winrt-samples/winui-kmp-library/build.gradle.kts winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt PLAN.md docs/superpowers/plans/2026-07-16-windows-app-sdk-2.2.0.md
git commit -m "build: upgrade default Windows App SDK to 2.2.0"
```

