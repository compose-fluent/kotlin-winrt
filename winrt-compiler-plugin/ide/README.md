# Kotlin WinRT IntelliJ integration

This is a standalone IDE build beneath the compiler tooling owner. It does not
add IntelliJ dependencies to the WinRT runtime, metadata, generator or main
application build. The shared Kotlin/JVM 17 Tooling API contract is imported from
`windows-toolkit-gradle-plugin/ide-model`; plugin code targets Java 25, matching
the 262 IDE runtime and the metadata toolchain.

The initial target is IntelliJ IDEA 2026.2.2 (build 262). Compose and Jewel are
provided by that IDE through `composeUI()`; a separate desktop runtime or
Material theme is not packaged. `WinRTToolWindowFactory` uses Jewel's IDE theme
bridge. Gradle sync requests `WinRTIdeModel`, materializes transport proxies into
IDE data, and publishes module snapshots to the Compose tool window. Reimport
replaces a build's models, including removal of previously configured modules.

The tool window displays targets, source sets, SDK/Kotlin versions and declared
NuGet references, with navigation to the module build script and explicit
application manifest. NuGet references describe configuration rather than
restore success. The imported project graph is used to restore state after
reopening the IDE.

Build and test on Windows from the repository root:

```powershell
./gradlew.bat -p winrt-compiler-plugin/ide test buildPlugin verifyPluginStructure
./gradlew.bat -p winrt-compiler-plugin/ide runIde
```

To reuse a locally installed SDK, add
`'-PkotlinWinRT.ide.path=D:/Program Files/JetBrains IDEA'` to the command. The
selected local installation must match the supported build line.

The IDE plugin distribution is produced under `build/distributions`. Application
runtime/projection behavior remains in the existing Kotlin modules corresponding
to `.cswinrt`; IDE UI and analysis adapters do not own ABI behavior.

## Analysis integration

The compiler tooling already owns the XAML declaration, supertype and name
checking rules in `XamlFirRegistrar`. `fir-adapter` recompiles those exact sources
against the IDE's embedded Kotlin compiler, with the existing metadata model.
The bundled FIR provider replaces recognized WinRT compiler JARs; its independent
JAR contains frontend/model dependencies and uses the IDE's Kotlin/coroutine
classes. The build plugin's IR extensions and semantic-export file operations
do not enter IDE analysis.

Gradle exports each declaration pass's task, roots, compiler directory and input
and declaration paths. Existing declaration outputs are read in the background;
missing or invalid outputs remove stale generated members. Reimport and output
changes refresh snapshots. Snapshot publication invalidates both cached compiler
configurations and Kotlin analysis sessions. `WinRTFirAnalysisTest` exercises
the actual IDE plugin loader and Analysis API: a generated name and connector
supertype appear, a name replacement removes the old member, and an empty index
removes the replacement. The adapter is coupled to build 262 APIs and must be
rebuilt/validated for another embedded compiler.

The tool window's **Prepare XAML analysis** action runs the existing declaration
and projection preparation tasks through the IDE's Gradle runner. Once their
input/header and compiler artifacts are available, local XAML document edits
are debounced and compiled in a cancellable background pass. Short-lived copies
contain unsaved text; the prepared references and MSBuild resource identities
are retained. XAMLC's existing DOM/harvester produces the declaration index,
with diagnostic paths mapped back to the original files. Source files and build
outputs are never overwritten by this producer. Invalid markup clears generated
symbols; revisions prevent an older result from replacing a newer edit.

`WinRTXamlDocumentCompilerTest` optionally exercises real document events and
the Windows compiler, including renaming an unsaved element, invalid markup and
temporary-file cleanup. Supply `-Pwinrt.ide.xamlInput=<prepared input.json>` and
`-Pwinrt.ide.xamlCompiler=<compiler directory>` to enable it. Newly added XAML
files and changes requiring a new Kotlin application schema still require
Gradle synchronization and preparation. The producer does not run an application
build on each keystroke.

## XAML editor

`.xaml` files use the platform XML parser, highlighting and completion handlers.
The IDE loads prepared XAML inputs' reference WinMD in the background through
`WinRTMetadataLoader`. XML descriptors obtain inherited properties/events from
that model and its existing interface closure. Presentation namespace aliases
follow XAMLC's `DirectUISchemaContext`; `using:` namespaces also query Kotlin's
class index within the module's dependency scope. There is no separate list of
WinUI controls. Enum/Boolean values, namespaces, classes and event handlers are
completion candidates; matching static accessor pairs resolve attached values.

PSI references resolve `x:Class` and event handlers to Kotlin, and descriptors
navigate custom controls and projected types. Kotlin class gutter markers link
to their XAML documents. Generated FIR named-element usages navigate back to
`x:Name`, using the resolved callable owner rather than matching source text.
The generated declarations remain compiler-owned. Native XML editing and
reference renaming use IDE command/undo machinery.

`WinRTXamlEditorTest` covers the real XML pipeline with a WinMD written and read
by the metadata owner. `WinRTFirAnalysisTest` also covers generated-member
source navigation. Rich binding-path completion, resource dictionary lookup,
cross-language rename coverage and event-signature diagnostics still need
additional implementation/validation.

## Project and module wizard

The IDE's New Project / New Module host exposes **Kotlin WinRT**. Its configuration
page uses the platform Compose/Jewel bridge. JVM templates cover console and WinUI
XAML applications, a WinRT library, a WinUI control library and an AppX resource
library and a shared SDK projection library. A new project's `winrt-projections`
module owns SDK generation; all consumer modules reference it, following
the shared projection ownership in `.cswinrt/src/Projections`. Consumers exclude
SDK interop additions from local generation as well as using metadata-only SDK
references. New projects
reference a local Kotlin WinRT toolchain checkout, reuse its Gradle wrapper and
composite build, and select a full JDK 25. New modules add a
literal Gradle include to the existing settings document through an IDE command;
existing files are rejected. Module references use explicit Gradle project paths.
Application run configurations invoke the existing `runWindows` Gradle task.
Unpackaged WinUI applications explicitly select SelfContained deployment so
their activation manifest and local runtime DLLs form a complete launch layout.
Optional preparation runs after the first successful model import.

Both application templates carry the supplied 122 PNG assets, including all
scale, target-size and theme variants. The manifest uses their base resource
names; the launcher ICO wraps six original PNGs without resizing or re-encoding.
Library templates do not include application icons or package manifests.
Tests cover original icon payloads, manifest references, file protection and
module undo/redo. Generated standalone consumers are also compiled on Windows;
the console consumer is launched through the actual Windows host. The combined
WinUI application/control/resource/library consumer also creates a real window.

## Resources and package manifest

The Compose **Resources** tab switches between source sets and actual staged
application variants. It recompiles the packaging owner's `AppxResourceLayout`,
package-path checks and manifest validation source, so file override selection
has no IDE-specific copy. Staging reports retain source-set override chains and
dependency resource archives. Final package entries and actual language/scale
PRI candidates come from those reports; file grouping never predicts runtime
candidate selection. Images have bounded previews and can be imported without
overwriting existing resources. Resource roots navigate to the native project view.

AppxManifest XML and `.resw` files gain a Compose/Jewel form beside their native
XML editor. The form edits PSI in document commands, preserving unknown nodes,
namespaces, extensions, comments and processing instructions. Fields cover package
identity, applications, visual assets, languages, device families, capabilities,
protocols and file associations. Imported Windows minimum/tested versions remain
owned by Gradle and link to that configuration. `.resw` keys/values and translator
comments share the same document; duplicate keys and missing translations include
unsaved edits. Tests verify XML preservation, source undo/redo, stale edit protection,
resource provenance and actual PRI qualifier reading.

## NuGet package management

The Compose **NuGet** tab selects an imported module and a configured source,
searches the NuGet V3 index, shows descriptions and versions, and installs,
updates or removes direct Kotlin DSL package declarations. Source browsing
applies machine/user/project sources, `clear`/`remove`, disabled sources and
environment expansion. Requests use the IDE HTTP client/proxy, bounded responses
and timeouts. Plaintext source credentials or NuGet source credential environment
variables remain in memory and are sent only to the source's HTTPS origin;
redirects do not forward them. Encrypted credentials and external credential
providers remain supported by WinApp restore; private-source browsing may require
a credential environment variable. V2/local feeds can still use exact ID/version
declarations and restored-package browsing.

PSI edits preserve existing option lambdas and comments, participate in native undo,
and reject computed/conditional/ambiguous declarations. A selectable declaration
and source navigation let the user handle those configurations. Dependency changes
save that build document, synchronize Gradle, then queue the existing
`restoreWinAppDependencies` and `generateWinRTProjections` tasks after model import.
New dependencies default to metadata/runtime consumption; projection generation
is explicit, to preserve shared SDK projection ownership.

The inventory compiles the toolkit's pure WinApp schema-3 lock reader and package
path checks. It shows direct versus transitive/tooling entries, cache provenance,
missing files and stale/conflicting versions. Package contributions show available
WinMD, native candidates and the toolkit's actual CopyLocal evaluation for the
selected RID; arbitrary MSBuild targets are not run. Restored status does not claim
successful projection compilation or packaging, which have explicit preparation
and variant staging actions. Tests exercise native Kotlin script PSI/undo, computed
expression protection, source hierarchy/credential isolation, V3 service discovery,
the real IDE HTTP client and authoritative lock inventory.

Hot Reload still needs implementation. Hot Reload requires XAMLC
update artifacts and application-side lifecycle/UI-thread support. Native
targets are represented in the imported model; no Native or Android Studio
editing/runtime parity is claimed by this IDE baseline.

The implementation queue remains in the local, uncommitted
`IDE_SUPPORT_LOCAL_PLAN.md`; this README records module boundaries and supported
behavior rather than revising root `PLAN.md`.
