# Kotlin WinRT IntelliJ integration

This is a standalone IDE build beneath the compiler tooling owner. It does not
add IntelliJ dependencies to the WinRT runtime, metadata, generator or main
application build. The shared Kotlin/JVM 17 Tooling API contract is imported from
`windows-toolkit-gradle-plugin/ide-model`; plugin code targets Java 25, matching
the validated IDE runtimes and the metadata toolchain.

The initial target is IntelliJ IDEA 2026.2.2 (build 262). Compose and Jewel are
provided by that IDE through `composeUI()`; a separate desktop runtime or
Material theme is not packaged. `WinRTToolWindowFactory` uses Jewel's IDE theme
bridge. Gradle sync requests `WinRTIdeModel`, materializes transport proxies into
IDE data, and publishes module snapshots to the Compose tool window. Reimport
replaces a build's models, including removal of previously configured modules.

## Compatibility boundary

| IDE / compiler combination | Compose/Jewel adaptation | FIR adaptation | Validation |
| --- | --- | --- | --- |
| Windows IDEA 2026.2.2 / IU-262.10315.125, analysis compiler 2.4.20-ij262-52, JBR 25 | Bundled Compose/Jewel bridge | Separate fir-adapter compiled from the owning frontend sources | Platform/editor/Analysis API tests, native Gradle import and cold workspace recovery, real XAMLC and JVM WinUI updates |
| Android Studio / AI-261.26222.65.2614.16204760, analysis compiler 2.4.255-dev-255, JBR 25 | That distribution's bundled Compose/Jewel bridge | Recompiled against its analysis compiler; native Kotlin reference resolution shared with 262 | Platform/editor/Analysis API tests, native project/module gallery registration, native Gradle import/cache recovery and real XAMLC |
| Android Studio Canary / AI-262.10315.125.2622.16434108, analysis compiler 2.4.255-dev-255, JBR 25 | That distribution's bundled Compose/Jewel bridge | Recompiled against its analysis compiler | Platform/editor/Analysis API tests, native project/module gallery registration, native Gradle import/cache recovery and real XAMLC |
| Other distributions or patch releases | Check their bundled UI APIs | Rebuild and validate against their analysis compiler | No compatibility claim |

Each package's metadata accepts only its SDK's build line (261 or 262). Packages
are rebuilt for each distribution; one binary is not declared compatible across
both lines. The JVM 17 ide-model transport stays independent of both adapters.
The analysis compiler is read from the Kotlin plugin's analysis libraries, rather
than its separate JPS compiler. Changes to the build compiler do not implicitly
upgrade the IDE adapter; embedded Kotlin is selected by the IDE distribution.
The current baseline is JVM first. Native development transport and Native
application/template validation remain separate parity work.

Forms use the IDE theme bridge, scroll long wizard/module/source lists, and expose
text-field accessible names. Project services use IDE-injected scopes, UI requests
use composition scopes, and listeners are registered with their disposable owner.
Cancelled NuGet requests cannot clear a newer request's busy state. These API and
lifecycle choices do not replace interactive acceptance: theme switching, 100/150/200%
scaling, Tab/Shift-Tab and keyboard activation, dialog focus/return focus, a Windows
screen reader, cancellation during real work and closing/reopening a full IDE must
still be checked in the installed desktop distribution. Platform tests validate
editor semantics and native import/cache recovery; they do not claim those UI checks.

The tool window displays targets, source sets, SDK/Kotlin versions and declared
NuGet references, with navigation to the module build script and explicit
application manifest. NuGet references describe configuration rather than
restore success. The imported project graph is used to restore state after
reopening the IDE.

Build and test on Windows from the repository root:

```powershell
./gradlew.bat -p winrt-compiler-plugin/ide :test buildPlugin verifyPluginStructure
./gradlew.bat -p winrt-compiler-plugin/ide runIde
```

To reuse a locally installed SDK, add
`'-PkotlinWinRT.ide.path=D:/Program Files/JetBrains IDEA'` to the command. The
selected local installation must match a validated build line. When switching
distributions, supply `-PkotlinWinRT.ide.variant=<simple-name>`, for example
`as-261` or `as-canary-262`, together with
`--project-cache-dir <absolute IDE build root>/.gradle/variants/<name>`.
Settings require that cache path and select separate output directories before
Kotlin plugins initialize their source sets, including `fir-adapter` and
`ide-model`. Sandboxes are isolated as well. Gradle tracks previous outputs per
task; sharing its project cache lets one SDK build delete another's classes,
even with distinct output directories. Both layers are isolated so packages and
running native tests retain the selected distribution's adapters.
Run SDK builds sequentially: included toolchain producers are shared, and Windows
can lock their JARs while native Gradle import uses them.

The `as-` variants also compile the Android Studio gallery adapter against
`org.jetbrains.android` and include its optional extension descriptor. The IDEA
package omits that adapter and dependency; Android's replaced project/module
galleries are not the IDEA generator extension points.

The baseline distribution is produced under `build/distributions`; named SDK
variants use `build/variants/<name>/distributions`. Application
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
removes the replacement. The adapter is coupled to its selected SDK and must be
rebuilt/validated for another embedded compiler.

Shared KMP source sets such as `winuiMain` retain their metadata compiler and
existing Kotlin options. After KGP commits a successful import, the IDE attaches
the same XAMLC declaration input to their facets. Applying this during the earlier
post-processing phase loses the options to KGP's final shared-source settings.
Cached workspace recovery also restores this frontend configuration; the owning
Gradle business compilations and their IR configuration remain separate.

The tool window's **Prepare XAML analysis** action runs the existing declaration
and projection preparation tasks through the IDE's Gradle runner. Once their
input/header and compiler artifacts are available, local XAML document edits
are debounced and compiled in a cancellable background pass. Short-lived copies
contain unsaved text; the prepared references and MSBuild resource identities
are retained. XAMLC's existing DOM/harvester produces the declaration index,
with diagnostic paths mapped back to the original files. Source files and build
outputs are never overwritten by this producer. Invalid markup clears generated
symbols; revisions prevent an older result from replacing a newer edit.

Snapshot directories use a hash of the IDE system directory and project identity.
If that system path exceeds XAMLC's Windows path budget, snapshots use a short
directory beneath the user's temporary directory. Invocation-owned directories
are removed after the child compiler exits, including cancellation. Short copied
source names keep their original package identities through `MSBuild_Link`.

`WinRTXamlDocumentCompilerTest` optionally exercises real document events,
the Windows compiler and the IDE FIR adapter together. It validates unsaved
name addition/removal/rename, generated members and unresolved-reference
diagnostics, preservation across model reimport, invalid markup and temporary
cleanup. Only SDK contracts are fixture sources; named properties come from FIR.
It also observes a live child XAMLC process, cancels the analysis invocation,
and verifies process exit, removal of its owned temporary snapshot and unchanged
source files.
Supply `-Pwinrt.ide.xamlInput=<prepared input.json>` and
`-Pwinrt.ide.xamlCompiler=<compiler directory>` to enable it. Newly added XAML
files and changes requiring a new Kotlin application schema still require
Gradle synchronization and preparation. The producer does not run an application
build on each keystroke.

`WinRTGradleImportTest` optionally opens a standalone template through the real
Gradle resolver, imports generated SDK sources and Kotlin compiler options, then
checks SDK and XAML FIR members before an application build. Actual editor
completion and bidirectional navigation cover generated names and the dependent
control module. Unsaved XAML addition/removal/rename updates members and diagnostics;
native Gradle synchronization preserves those edits through its document save.
It saves the platform workspace cache and checks recovered source roots, Kotlin
facets and analysis after reopening, without reimporting a cached Gradle graph.
Supply `-Pwinrt.ide.importProject=<prepared template under .gradle>`; prepare only
`analyzeWinRTXaml` and `generateWinRTProjections` first. Native import tests receive
a 4 GiB heap budget for the real SDK and included toolchain.

The optional `-Pwinrt.ide.importPhase=import` saves the edited template and native
workspace cache. A separate invocation with `importPhase=reopen` requires a
different IDE host process, asserts that the platform loaded its cache, and tests
completion/navigation without Gradle synchronization. Supplying an existing
`recoveredHotReloadSession` also verifies that the newly built WinUI application
loaded its authored control and returns the edited Text through an actual SDK
getter on its UI thread. Full desktop UI restart acceptance remains separate.
Windows external-project paths use the platform's forward-slash representation
consistently, required by its persisted-cache validation.

## XAML editor

`.xaml` files use the platform XML parser, highlighting and completion handlers.
The IDE loads prepared XAML inputs' reference WinMD in the background through
`WinRTMetadataLoader`. XML descriptors obtain inherited properties/events from
that model and its existing interface closure. Presentation namespace aliases
follow XAMLC's `DirectUISchemaContext`; `using:` namespaces also query Kotlin's
class index within the module's dependency scope. There is no separate list of
WinUI controls. Enum/Boolean values, namespaces, classes and event handlers are
completion candidates. Static accessors resolve attached values, including
getter-only collections such as `VisualStateManager.VisualStateGroups`.

PSI references resolve `x:Class` and event handlers to Kotlin, and descriptors
navigate custom controls and projected types. Kotlin class gutter markers link
to their XAML documents. Generated FIR named-element usages navigate back to
`x:Name`, using the resolved callable owner rather than matching source text.
The generated declarations remain compiler-owned. Native XML editing and
reference renaming use IDE command/undo machinery.

Event references, completion and diagnostics share Kotlin Analysis API results.
The actual projected `add<Event>` parameter supplies a closed delegate `invoke`
signature, including generic substitution and nullability. Handler validation
follows `XamlSemanticExport` and `XamlPageBodies`: one ordinary instance method,
Unit return, no suspend, generic, context, extension or vararg parameters, and
parameters accepting the delegate inputs. Missing dependencies defer the
parameter check; the IDE does not guess a control-specific signature.

Event-value completion includes **Create event handler** for the entered name,
or an element-name/event suggestion when the value is empty. A missing handler
also has an Alt+Enter quick fix. Creation adds a private Kotlin method with the
actual delegate parameter types, shortens imports and opens its body. It uses
native cross-file undo and never duplicates an existing method. Creation waits
for the projected delegate signature and writable owning class to be available.

`WinRTXamlEditorTest` covers the real XML pipeline with a WinMD written and read
by the metadata owner, closed generic event signatures, inherited handlers,
completion filtering and native diagnostic ranges. `WinRTFirAnalysisTest` also
covers generated-member source navigation and native x:Name renaming across
Kotlin and XML usages. Binding paths use source spans from the XAMLC
`BindingPath.g4` grammar and Kotlin type scopes for inherited and generic
receivers, calls, casts, indexers, static members and attached getters. Template
boundaries follow WinMD inheritance; source-matched harvester connections supply
their data types. A stale declaration location is never matched by line alone.
Incomplete expressions retain completion. Ordinary Binding has no assumed data
type, but explicit ElementName sources resolve within their name scope.

Markup extensions override the enclosing XML string color. Keywords, options,
punctuation, literals, resolved members and resource keys have separate styles
under **Editor → Color Scheme → Kotlin WinRT XAML**. Unknown typed `x:Bind` members
receive errors; an ordinary Binding with unknown runtime data stays neutral.
Resource warnings cover only the unresolved key. Ordinary and dependency-property
attribute names use actual Kotlin members; Ctrl+B also offers the real
`…Property` registration for a DP, including inherited and attached properties.

SDK resource dictionaries exceeding the platform's XML PSI size limit are
indexed in the background; their keys navigate to the original file offsets.
Relative `ResourceDictionary.Source` URIs resolve from each dictionary's
compiler-provided `MSBuild_Link`, including `.` and `..` segments. Source-relative
file navigation is also available before Gradle import.

Native references/usage search connect x:Class, handlers, typed binding paths
and element names. The native Kotlin rename processor updates resolved XML
usages; class rename also adds its required same-basename XAML file to the
transaction. A Compose/Jewel x:Name form delegates to the native rename/usage
preview machinery. Identical names in other templates and shadowing Kotlin
variables are excluded by resolution, not a text replacement. Dynamic Binding
paths with an unknown source remain unchanged. `WinRTXamlBindingTest` validates
these paths, completion insertion and native refactoring scope boundaries.
Private handlers and typed properties referenced by XAML contribute to Kotlin's
native Usage Code Vision and unused-symbol inspection. The indexed search still
resolves ownership; another class's matching name does not imply a use. Private
XAML references participate in the same native rename preview and undo transaction
despite Kotlin's normal private-file search restriction. Projected uppercase
property spelling is retained when renaming a lowercase Kotlin property.

## Project and module wizard

The IDE's New Project / New Module host exposes **Kotlin WinRT**. In Android
Studio, select that entry in the native gallery. New Project presents the six
templates and **Create Kotlin WinRT project…**, which opens the common wizard;
Studio's Android Next chain has no extension hook for replacing its SDK/activity
steps. New Module uses Studio's normal Next/Finish flow with the same form and
template writer. WinRT creation does not require an Android SDK or activity.

Its configuration
page uses the platform Compose/Jewel bridge. JVM templates cover console and WinUI
XAML applications, a WinRT library, a WinUI control library and an AppX resource
library and a shared SDK projection library. A new project's `winrt-projections`
module owns SDK generation; all consumer modules reference it, following
the shared projection ownership in `.cswinrt/src/Projections`. Consumers exclude
SDK interop additions from local generation as well as using metadata-only SDK
references. New projects use the plugin's bundled Maven toolchain and Gradle
wrapper and select a full JDK 25. An optional local toolchain checkout is available
under Advanced for toolchain development. New modules add a
literal Gradle include to the existing settings document through an IDE command;
existing files are rejected. Module references use explicit Gradle project paths.
Gradle synchronization and project startup import permanent **Kotlin WinRT
Application** profiles into the IDE's Run configuration list, with a Windows
application icon and a Compose editor for application selection and launch options.
Available JVM/Native and packaged/unpackaged variants use the existing Gradle
launch tasks. Aliases are collapsed and native debug executables are preferred
over release executables. Recognized legacy Gradle application profiles migrate
to this type while retaining custom names, selection, environment variables,
runner options and storage location. Other Gradle profiles remain unchanged.
Unpackaged WinUI applications explicitly select SelfContained deployment so
their activation manifest and local runtime DLLs form a complete launch layout.
Optional preparation runs after the first successful model import.

Windows App SDK is a version dropdown populated from the selected NuGet V3
source, with explicit prerelease selection and refresh. Windows SDK is a dropdown
of complete locally installed versions discovered through the shared registry-first
SDK locator. The wizard explains that the chosen Windows SDK must be installed
locally with headers, libraries, metadata and packaging tools.

Both application templates carry the supplied 122 PNG assets, including all
scale, target-size and theme variants. The manifest uses their base resource
names; the launcher ICO wraps six original PNGs without resizing or re-encoding.
Library templates do not include application icons or package manifests.
Tests cover original icon payloads, manifest references, file protection and
module undo/redo. `WinRTTemplateGenerationTest` takes
`-Pwinrt.ide.templateOutput=<fresh directory under .gradle>` and creates both
applications and their library modules through the wizard's native write commands.
It saves a document edit referencing the control library, then the import test
completes the editing workflow before application compilation. These saved
consumers are built and launched through actual Windows hosts: the console prints
its greeting, and the combined WinUI application/control/resource/library consumer
loads the authored control and reports the edited native Text value.

Right-click a Kotlin source directory in an imported WinRT module and choose
**New → XAML Page** or **New → XAML UserControl**. The Compose dialog infers the
package from existing Kotlin files or the path beneath the source root, and lets
it be edited. Both actions create matching `.kt` and `.xaml` files in one undoable
command; `x:Class` names the Kotlin declaration. Component initialization uses
the existing compiler construction hook. **New → XAML ResourceDictionary** creates
a standalone `.xaml` file and is also available in AppX resource roots. Generated
build directories are excluded, and a conflict with either companion file rejects
the entire creation. New XAML files reuse analysis preparation after
creation when the module exports that compilation.

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

**Manifest Designer** is the default manifest editor and is also available from
Overview and Resources. Its Source tab remains available. XML validation uses
the module's installed SDK XSDs, including namespace-only imports; the four
restricted/Windows-capability schemas omitted by the SDK are bundled verbatim
from Microsoft's MIT-licensed MSIX schema sources. Unknown attributes still fail
validation; recognized capabilities and COM/desktop extensions do not become
false unresolved-namespace errors.

## NuGet package management

The Compose **NuGet** tab selects an imported module and a configured source,
provides **Browse**, **Installed** and **Updates**, searches the NuGet V3 index,
shows paged results and the exact selected version's registration details, and installs,
updates or removes direct Kotlin DSL package declarations. Source browsing
applies machine/user/project sources, `clear`/`remove`, disabled sources and
environment expansion. Requests use the IDE HTTP client/proxy, bounded responses
and timeouts. Plaintext source credentials or NuGet source credential environment
variables remain in memory and are sent only to the source's HTTPS origin;
redirects do not forward them. Encrypted credentials and external credential
providers remain supported by WinApp restore; private-source browsing may require
a credential environment variable. V2/local feeds can still use exact ID/version
declarations and restored-package browsing.

Details include authors, publication date, download count, license/project links
and target-framework dependency groups with navigation to dependency packages.
Version selection follows NuGet's numeric core/prerelease ordering, including
four-part versions. A wide panel places the list beside details; a narrow panel
stacks them. Updates checks the module's direct packages against the selected feed.

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

Resource references use native XML PSI. Manifest logos and `ms-appx` paths navigate
to source file candidates; `ms-resource` and `x:Uid` navigate to localized `.resw`
names, including unsaved edits. XAML resource keys resolve local, merged, theme and
application dictionaries and participate in completion. The index enumerates files
and reads the toolkit's staging reports in the background. Qualifier variants are
navigation candidates, not a claim about MRT's selected runtime value. References
are soft and unresolved sources have a diagnostic describing dependency/runtime
limits. Foreign package authorities and opaque compiled dictionaries require their
own source mapping; they are not resolved to unrelated local files.

System colors and brushes supplied dynamically by WinUI's `FrameworkTheming`
resolve even though they have no `x:Key` declaration in SDK dictionaries. Navigation
opens a read-only declaration view with each key's type and Windows provider;
runtime color values are not invented. Application dictionary overrides retain
priority. This includes `SystemColorWindowColor` and `SystemColorWindowTextColor`;
misspelled keys still produce the normal unresolved-resource diagnostic.

## Preview and Visual Tree

The Kotlin WinRT tool window contains **Preview** and **Visual Tree** tabs.
Live Preview captures a connected development application's actual WinUI content;
clicking its image selects the deepest visual at that position. Both tabs share
component, instance and visual selection. The expandable Jewel tree supports
keyboard selection and includes real control-template
children, and the property view reads effective values through generated accessors
on the owner's UI dispatcher. **Go to XAML** locates named source elements;
template internals locate the nearest source control or fall back to the component's XAML.

Each XAML editor has native **Code / Split / Preview** buttons in its top-right
toolbar. The normal XML editor remains the code pane, with native navigation,
completion, caret state and refactoring. The selected mode survives reopening.
New documents default to **Split**, opening the designer automatically for the
document's imported module. Saved mode choices still survive reopening. The
preview pane is active only while visible in the selected editor. The static
image fits the available preview area; **Visual Tree and properties** adds the
inspector when needed.

The default follows UWP's design-time workflow: it uses a fixed SDK-only WinUI
host and reads current XAML, without compiling the application, running XAMLC,
constructing the user's Application or invoking user main. Application Kotlin
errors do not block this designer. The first launch prepares a host for the
selected Windows App SDK and Windows SDK; its SDK compilation is cached across
projects with matching SDKs and compiler/runtime inputs. Subsequent document
edits reuse that process. The selected SDK must be installed/restorable, and the
configured JDK and Windows native toolchain must be available for first preparation.
**Retry preview** reruns preparation after a failure.

`WinRTDevelopmentLaunchTest` invokes the production session from EDT without
write intent, as Compose effects and clicks do. It checks automatic SDK preview
and retry preserve unsaved XAML, while project-code preview and Hot Reload save
documents through the platform's EDT/write-intent boundary before preparation.
The optional `WinRTPreviewLaunchIntegrationTest` accepts
`-Pwinrt.ide.sdkPreviewProject=<prepared SDK-only fixture root under .gradle>`.
The fixture needs a Gradle wrapper, an `:app` module and its prepared SDK designer.
The test opens the actual Compose preview, launches through the IDE's native
Gradle runner and waits for a WinUI image. Validation uses a fixture whose
application Kotlin compilation deliberately fails, so application compilation
cannot silently become a designer prerequisite.
It also loads the Gallery's actual application and merged dictionaries and
renders its ToggleButton page and main window. The SDK-only designer preserves
built-in visual children and local resources inside unavailable custom containers;
their appearance uses a placeholder rather than executing the custom control.
Loose design dictionaries establish theme resources before loading merged styles.
A rejected design document clears the previous image and reports its XAML/resource
error; refreshing after a correction renders the new document.
The artboard uses the preview theme's page background so transparent pages remain
legible in both light and dark IDE themes.

**Enable project code (requires compilation)** switches to the existing isolated
application-derived host to render custom Kotlin controls. Packaged project-code
hosts use a separate `.preview` identity and deployment folder and can run beside
the application's `.dev` package. The default SDK designer is independent of the
application's packaging mode. Static and live sessions have independent
connections and process ownership; closing the project stops its design host.
Synchronize Gradle after updating the toolkit to import version 6 of the IDE model.

Static rendering preserves StaticResource, ThemeResource, local dictionaries,
application resources and their original package-relative Source paths. In the
default designer, relative and `ms-appx:///` dictionaries are read recursively
from source, including unsaved edits. Named project ResourceDictionary classes
can contribute their XAML without executing their constructors. Missing files
and dictionary cycles are reported. Passive image/font assets are copied to the
module's owned designer folder. Preview options select viewport dimensions and
theme. Supported `d:` values and inline design children replace
compiled `x:Bind` expressions; otherwise control defaults apply. Event handlers
and compiler-only directives are removed from the design copy with a visible
notice. Regular Binding still needs an available design DataContext. With project
code disabled, custom controls retain layout as visible placeholders and custom
resource objects are excluded. Enabling project code permits their normal
constructors. There is no simulation of Kotlin code or binding execution, and
this feature does not provide UWP's drag-and-drop designer.

WinUI owns layout/rendering. Captures are premultiplied BGRA8, scaled to at most
768 pixels per side, and bounded to 2048 visual nodes. RenderTargetBitmap's own
limitations apply, including content outside the captured visual such as popups.
The protocol is version 4: rebuild applications using an older development host
before connecting the updated IDE plugin. Runtime inspection contracts and typed
adapters are shared Kotlin; the development transport currently supports JVM.

## XAML Hot Reload

The Compose **Hot Reload** tab starts an imported JVM application's
actual Gradle launch task with a fresh development session. Gradle owns the build,
Run output and build cancellation. The panel applies unsaved XAML document changes
automatically or on request, reports property results and source versions, and
provides reconnect, disconnect, stop and rebuild/restart actions. Disconnect keeps
the application running; stopping verifies the lifetime of the process launched
by this session. A disconnected build can be cancelled in its Gradle Run window.

Both unpackaged JVM hosts and framework-dependent packaged JVM development runs
are supported. Packaged launches use `runWinAppPackage...`, preserving package
identity through WinApp CLI registration and activation. The broker does not inherit
the IDE's environment, so a reserved startup argument carries the fresh session
directory. The native JVM host consumes it before VM creation and does not pass it
to application `main`. No token or development configuration is staged in the
package. The imported launch points to the registered AppX layout's executable,
which remains subject to the same process and lifetime checks. WinApp 0.6's
self-contained folder-run limitation and Native's missing development transport
remain explicit; neither is a general packaged XAML Hot Reload limitation.

The IDE workspace saves the confirmed launch's module, task, session directory,
process ID and start time, without its authentication token. After reopening a
project, **Reconnect** restores the connection only after matching the imported
launch, module-owned directory, executable and original process lifetime. Source
fingerprints must still match before applying changes. Unconfirmed launches and
expired/reused process IDs require a new session. Closing a project releases its
connection while the application can continue running. Disconnect/reconnect/close
cancel outstanding update jobs and close their sockets; an obsolete response
cannot overwrite a newer session's state. Reconnect reconciles a patch that
committed before its response was lost.

The compiler records source fingerprints and named objects through XAMLC's existing
connection path. Its existing typed member emitter supplies SDK property accessors
only when development is enabled; SDK type-provider behavior remains unchanged.
The runtime captures each component's DispatcherQueue and applies literal property
changes on that UI thread. Getters and conversions finish before mutation; setter
failure rolls back prior values, and rollback failure requires restarting. Hashes
and monotonic versions reject stale requests; reconnect reconciles an uncertain
response before another update. Managed weak references preserve component lifetime
and successful overrides apply to subsequently created instances.

The transport uses an authenticated, bounded loopback protocol and an owner-restricted
session file. It starts only when the development environment variable is present.
There is no Kotlin reflection. Root and connected named elements support ordinary
writable literal properties. Mutable keyed resources update through the existing
projected dictionaries, preserving shared brush identity and resource expressions.
For a sealed Style, the SDK loads a self-contained replacement dictionary; the
transaction keeps the live dictionary and controls and refreshes this page's explicit
StaticResource consumers. Readbacks show effective setter properties after applying
the style. Failure restores dictionary values and consumers together. New components
receive both current resources and subsequent literal overrides.

WinMD content properties and mutable-vector interfaces drive child-collection
updates. Existing children can reorder within the same collection. The SDK loads
new unnamed presentation subtrees with literal properties; unnamed disconnected
subtrees can be removed. The transaction preserves retained native objects, checks
the live collection size and rolls back changes on failure. New/reordered unnamed
children use checked property/index paths. Future component instances replay graph
and subsequent property/resource updates in order (64 entries / 8 MiB). Moving
controls can trigger Loaded/Unloaded and affect focus; transient lifecycle state is
not guaranteed. Application changes to collection order are outside this contract.

Style replacement currently requires the same explicit string keys and named/root
owners and consumers. ThemeResource consumers, implicit keys, external/merged/theme
dictionaries, captures in other dictionaries/templates, connected-element removal,
type/name/parent changes, new names or authored controls,
compiled events/bindings, attached properties and Kotlin changes require rebuilding
and restarting. There is no page-recreation path. See
`winrt-runtime/HOT_RELOAD.md` for the owning runtime contract and Native parity gaps.

The optional graph-host test uses `-Pwinrt.ide.hotReloadGraphSession=<fresh session>`,
`-Pwinrt.ide.hotReloadGraphSource=<MainWindow.xaml>` and `-Pwinrt.ide.xamlInput=<input.json>`.
It exercises actual native UIElementCollection reorder/add/remove, retains a Width
value absent from XAML, and verifies failed-setter rollback after a collection edit.
The baseline fixture has Layout with Greeting/Second TextBlocks and one unchanged
authored control. Run resource and graph host tests in separate fresh processes.

`WinRTHotReloadTest` covers markup classification, the compiler's source fingerprint
and a real authenticated loopback client. Its optional actual-host test accepts
`-Pwinrt.ide.hotReloadSession=<session directory>` and checks native text, dimensions,
brush conversion and failed-setter rollback through compiler-generated accessors.
Its resource-host test accepts `-Pwinrt.ide.hotReloadResourcesSession=<fresh session>`
and `-Pwinrt.ide.hotReloadResourcesSource=<MainWindow.xaml>` for a template with a
named Layout, local Accent brush/GreetingStyle and Greeting/Second TextBlocks. It
checks native shared colors, sealed styles, effective font sizes, transactional
rollback and subsequent brush changes through the SDK loader/accessors.
`WinRTHotReloadServiceTest` validates native state serialization/service recreation,
cancelled handshake/update sockets and lifetime rejection against a controlled
loopback peer. Add `-Pwinrt.ide.recoveredHotReloadSession=<module session directory>`
to `WinRTGradleImportTest` to save through the real project component store, close
and reopen its imported project, recover the session and apply an unsaved Text edit
to an existing WinUI application. This uses the native project lifecycle inside
the platform host; full desktop IDE restart remains separate interactive acceptance.
The common runtime engine compiles for `mingwX64`, but its development transport and
end-to-end IDE support currently target JVM. The compatibility matrix records
backend validation for installed SDKs; desktop interaction still requires
acceptance in each distribution.

The implementation queue remains in the local, uncommitted
`IDE_SUPPORT_LOCAL_PLAN.md`; this README records module boundaries and supported
behavior rather than revising root `PLAN.md`.
