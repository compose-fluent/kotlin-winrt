# kotlin-winrt

[![Publish Snapshot](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml/badge.svg?branch=master)](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml)
[![Snapshot](https://img.shields.io/maven-metadata/v?label=snapshot&metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fio%2Fgithub%2Fcompose-fluent%2Fwinrt-runtime%2Fmaven-metadata.xml)](https://central.sonatype.com/repository/maven-snapshots/io/github/compose-fluent/winrt-runtime/maven-metadata.xml)

`kotlin-winrt` is a Windows-focused Kotlin workspace for building WinRT and WinUI 3 applications from:

- Kotlin/JVM
- Kotlin/Native `mingwX64`

The repository is organized around a layered runtime and generation pipeline:

- `winrt-runtime`: WinRT ABI, COM interop, activation, marshaling, and runtime helpers
- `winrt-metadata`: WinMD loading and normalized metadata model construction
- `winrt-generator`: Kotlin source generation for WinRT and WinUI projection bindings
- `winrt-projections`: checked-in generated projection output and projection support assets
- `winrt-authoring`: authoring and hosting support for Kotlin-authored WinRT types
- `winrt-samples`: sample applications and validation surfaces

## Development status

This repository currently provides:

- a layered Kotlin workspace for WinRT runtime, metadata, generation, projection, authoring, and samples
- multiplatform runtime abstractions for COM and WinRT
- a WinMD inspection and code generation pipeline
- JVM and `mingwX64` runtime build coverage
- a JVM interop baseline built around the Foreign Function and Memory API

The native JVM and WinUI 3 runtime bridge is intentionally kept behind interfaces so the project can evolve without rewriting generated bindings.

## Using Snapshot Builds

Snapshot artifacts are published to Maven Central's snapshot repository under the group `io.github.compose-fluent`.
Add the snapshot repository only for snapshot versions, then add the runtime dependency:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "mavenCentralSnapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
}
```

Use the runtime from a Kotlin/JVM project:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-runtime-jvm:0.1.0-SNAPSHOT")
}
```

Use the runtime from a Kotlin Multiplatform project when sharing WinRT-facing code between JVM and `mingwX64`:

```kotlin
kotlin {
    jvm()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.compose-fluent:winrt-runtime:0.1.0-SNAPSHOT")
        }
    }
}
```

Add authoring or tooling modules only when the project needs those surfaces directly:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-authoring:0.1.0-SNAPSHOT")
    implementation("io.github.compose-fluent:winrt-metadata:0.1.0-SNAPSHOT")
    implementation("io.github.compose-fluent:winrt-generator:0.1.0-SNAPSHOT")
}
```

## Basic Usage

`kotlin-winrt` projects normally have three parts:

- add `winrt-runtime`
- declare the WinRT namespaces or types that should be projected
- initialize the WinRT runtime before calling projected APIs

For example, a JVM project that uses `Windows.Data.Json` can configure projection generation like this:

```kotlin
plugins {
    kotlin("jvm")
    id("io.github.composefluent.winrt")
}

dependencies {
    implementation("io.github.compose-fluent:winrt-runtime-jvm:0.1.0-SNAPSHOT")
}

winRt {
    namespace("Windows.Data.Json")
}
```

Then use the generated Kotlin projection types from normal Kotlin code:

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

For Windows App SDK or WinUI projection work, add the corresponding WinMD sources and opt into the specific types that the application needs:

```kotlin
winRt {
    windowsSdk(includeExtensions = true)
    nugetPackage("Microsoft.WindowsAppSDK", "1.8.260317003")

    type("Microsoft.UI.Xaml.Application")
    type("Microsoft.UI.Xaml.Window")
    type("Microsoft.UI.Xaml.Controls.Button")
    type("Windows.Foundation.Uri")
}
```

## Projection references

`kotlin-winrt` does not define WinRT projection behavior from scratch. When deciding how runtime classes, default interfaces, implemented interfaces, delegates, generic interfaces, parameterized IIDs, and WinUI activation behavior should work, this repository treats the official Microsoft projections as the reference point:

- [C++/WinRT](https://github.com/microsoft/cppwinrt)
- [CsWinRT](https://github.com/microsoft/CsWinRT)

When local behavior disagrees with those projections, the intent is to change `kotlin-winrt` to match the reference projection model instead of inventing project-specific rules.

## Build

Use Windows for full build and runtime validation. JVM builds target JDK 25 because the JVM-side Win32/COM bridge is based on the FFM API in `java.lang.foreign`.

### Linux/macOS static verification

```bash
./gradlew test
```

### Windows validation

```powershell
.\gradlew.bat test
.\gradlew.bat :winrt-samples:check
```

The sample prints guidance when the process is not running on Windows.
