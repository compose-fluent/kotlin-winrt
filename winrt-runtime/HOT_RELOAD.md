# XAML development updates

The development transport applies to JVM and Kotlin/Native mingwX64 desktop WinUI
applications, and to Kotlin/Native UWP AppContainer applications. UWP package
activation and session handoff are owned by the Gradle toolkit; see the
[UWP guide](../docs/UWP.md) for configuration and the remaining JVM limitation.

The common property engine uses the compiler's existing XAML type/member
registrations. Property discovery reuses `WinUIAuthoredTypeMetadata.customProperty`,
corresponding to CsWinRT's generated `ICustomProperty` implementation in
`.cswinrt/src/WinRT.Runtime/Projections/ICustomPropertyProvider.net5.cs`.
Literal conversion reuses `convertWinUIXamlLiteral` and the generated SDK converter.
There is no reflection fallback or separate WinRT type classification table.

For SDK controls, the compiler's existing typed member emitter supplies development
getters/setters for referenced presentation types and their bases. These entries
are registered only in the member lookup by Kotlin type; they do not advertise SDK
classes as authored XAML types or replace the SDK's native type provider. SDK member
discovery and inherited-member ownership remain in the metadata owner. Applications
without the development environment variable do not register these accessors.

Attached literal properties use the same transaction and conversion path. The IDE
resolves the XAML namespace and declaring owner through WinMD and sends a qualified
member name, such as `Microsoft.UI.Xaml.Controls.Canvas.Left`. Generated development
member tables reuse CSharpTypeInfoPass2's typed static Get/Set delegates; the runtime
looks up the declaring owner's table without advertising SDK types as authored types.
Qualified attached entries remain distinct from same-named instance properties.
Targets are checked by those typed delegates before mutation. Setter rollback and
future-instance replay are identical to ordinary properties. Existing resource
consumer replacement and unnamed SDK fragments use the same attached-member lookup.
Old binaries without these generated accessors need rebuilding once. Protocol v6
adds removal of literal dependency-property attributes, including attached ones.
Generated SDK delegates capture ReadLocalValue before mutation, call ClearValue to
restore styling/default precedence, and restore the original local value on rollback
(or clear it again if it was UnsetValue). Successful resets replay after future
instances load their original XBF. Ordinary CLR/Kotlin properties without a DP
accessor reject reset; removing binding/resource expressions remains unsupported.
This SDK adaptation extends XAMLC's typed member delegates rather than adding
reflection or another runtime property classification table.

Generated application metadata supplies a factory that captures the current
`DispatcherQueue` on the component's UI thread. Updates use `TryEnqueue`, following
`.cswinrt/src/Projections/WinAppSDK/Microsoft.UI.Dispatching.DispatcherQueueSynchronizationContext.cs`.
The compiler records objects received by the existing XAMLC connection path;
completion makes a component available to development clients. The registry stores
managed weak references to components/elements and rejects updates across UI threads.

Each page records its compilation fingerprint and current update version. A request
must match the current fingerprint and advance the version by one. Every target,
getter and value conversion is prepared before setters run. Setter failures restore
the previous values; rollback failure or a component created during mutation requires
restarting. Subsequent instances receive successful updates when their original XBF
finishes loading. Connected object identity, events and compiled bindings stay with
retained elements; changed connections require their own compiler contract.

Protocol version 2 adds bounded object paths through generated property getters,
projected dictionary keys and collection indices. An index includes the expected
collection size; a changed live shape rejects the update. Mutable resource values,
such as a shared SolidColorBrush, receive literal property updates in place,
preserving StaticResource and ThemeResource consumers' references.
The IDE also addresses mutable entries in inline merged and theme dictionaries,
including a compiled ResourceDictionary root. Dictionary keys, group structure
and collection sizes must remain stable; source dictionaries and sealed values
still require rebuilding. This path changes the existing resource object, leaving
the consuming resource expression intact.

Applied WinUI Styles are sealed. Their update constructs a fresh local dictionary
with the SDK's `XamlReader.Load`, as used by CsWinRT's resource tests in
`src/Tests/ObjectLifetimeTests/CustomGroupedItemPages.cs`. Generated application
glue supplies that typed SDK loader; the common runtime never parses XAML or
implements another projection model. The transaction keeps the original live
dictionary and controls, replaces explicit string-keyed values and
reassigns this page's explicit StaticResource consumers. Readbacks inspect effective
style properties on the UI thread. Prepared replacements also supply subsequent
literal overrides when a new component finishes loading its original XBF.

The IDE currently recognizes resources on root or connected named owners. Style
replacement requires a self-contained local dictionary and resolvable named/root
consumers. Implicit keys, merged/external/theme dictionaries, references captured
by another dictionary/template, new names, events and binding expressions require
rebuilding. A ThemeResource consumer cannot be reassigned as a local value without
losing its theme expression, so it rejects replacement rather than claiming theme
invalidation. References held outside this page's XAML/connected objects do not
participate in the update. A local explicit dictionary may add or remove keys;
the previous key set is checked before mutation, and a removed key must have no
remaining consumers. These dictionary changes use ordered replay for future
instances, so later replacements validate against the preceding generation.
Parser, key-shape, getter, conversion or setter failure preserves the previous
version; attempted mutations roll back dictionary values and consumer properties
together. Rollback failure still requires restarting.

Protocol version 3 adds child-collection transactions. The IDE obtains the content
property from WinMD ContentPropertyAttribute, and mutable-vector shape from the
metadata owner's existing collection descriptor and interface closure. It does not
maintain a list of container controls. A collection update carries its expected live
size and a new sequence of original indices or bounded SDK markup fragments. Each
original child may occur only once. Generated SDK glue loads new UIElements through
XamlReader.Load; common code uses the existing projected MutableList contract.
IndexOf, RemoveAt and indexed insertion correspond to CsWinRT's IList.net5.cs and
retain native instances rather than recreating controls. Native IndexOf supplies
identity comparison even when Kotlin wrappers differ. Overlapping collection paths
require separate updates. No page-recreation path is provided.

All fragment parsing, live-size checks and subsequent property accessors are
prepared before mutation. Prepared collection overlays address new/reordered
unnamed children by their new index; setters and readbacks participate in the same
transaction. Failure restores the original sequence and property values. Graph
updates and later property/resource updates are replayed in order for new instances
after their compiled XBF connects, with a 64-entry / 8 MiB history limit. Rebuild
and restart clears the history. The size guard detects application-added/removed
children; same-size application reordering is outside the supported contract.

Existing connected children may move within the same collection. New or removed
subtrees must use unnamed SDK presentation elements with literal properties and no
events, bindings, templates or authored controls. Resolved SDK property elements
(such as Border.Background with a literal SolidColorBrush) are accepted inside
these new/removed subtrees; their descendants follow the same connection guards. Removing a
connected child, changing its type/name/parent, or changing a content property to a
different object requires rebuilding. SDK Loaded/Unloaded callbacks can run during
remove/insert, so object identity and explicit values are retained but keyboard
focus, selection, animation and transient lifecycle state are not promised. Rollback
restores the tree and properties; it cannot undo arbitrary user event side effects.

Protocol v3 carries no executable code, connection IDs, generated fields or
subscription replacements. Compiled-connection changes require rebuilding.

## Compiled-connection contract

This is the responsibility boundary for a future connection-update artifact, not
an implemented protocol extension. The existing compiler must remain the owner of
generated fields, handler signatures and binding bodies. The IDE must not compile
its own interpreted binding language or substitute reflection for private Kotlin
member access.

| Input / operation | Existing owner and reference | Update requirement |
| --- | --- | --- |
| Names and connection IDs | XAMLC pass 2, WinUIXamlPageDeclaration and XamlPageBodies | Pair XBF and connection declarations from the same compiler generation; map retained slots within their original name scope. |
| Generated fields and binding calls | XamlFirRegistrar, XamlPageBodies and XamlCompiledBindingBodies | Match the loaded Kotlin class shape and typed accessor/handler signatures. |
| Event lifetime | Generated projected add/remove methods and WinRT event sources; CsWinRT Interop/EventSource{TDelegate}.cs | Capture each add result and its typed remover; own subscriptions by component, connection ID and generation. |
| Binding lifetime | WinUIXamlBindingState and WinUIXamlBindingScope; XAMLC CSharpPagePass2 StopTracking / DisconnectUnloadedObject | Release listeners and deferred targets before disconnecting; initialize the newly connected scope once after commit. |

An artifact must include the expected source/version, the loaded Kotlin class ABI
fingerprint, projection/toolchain identities, an old-to-new connection map, paired
XBF/declarations, and compiler-generated typed reconnect operations. Template
realizations need their own generation and name-scope identity; a page-wide numeric
connection ID is insufficient. Any ABI, projection or name-scope mismatch rejects
the artifact before touching live objects.

On the owning UI thread, prepare new objects and typed operations first. Suspend
updates, stop the affected binding scopes, and remove every old event subscription
using its original target/token before detaching objects or clearing generated
fields. Commit the tree, fields and name-scope registrations together; then attach
new events and initialize bindings once. A failed attach must remove newly added
tokens, restore the old tree/fields, recreate old subscriptions and reinitialize
old bindings. Cleanup failure requires restart; arbitrary handler side effects
cannot be rolled back. Future instances must receive the same complete artifact
generation instead of mixing old connector IDs with new XBF.

The XamlPageBodies event emitter retains ordinary non-template event subscriptions
in a per-page WinUIXamlConnectionState. Reconnecting a compiled slot first removes
its previous subscriptions from the original source using the original token (or
handler for mapped events). Failed cleanup attempts the remaining removers and
blocks further connection changes. This follows CsWinRT EventSource's paired
Subscribe/Unsubscribe ownership. Binding tracking remains separately owned.
Compiled event bindings have a separate connection state on the page binding owner
or individual template scope. Tracking refresh and recycling retain those event
connections; replacing a slot or unloading a deferred element releases them.
Ordinary template events follow their target lifetime, matching CSharpPagePass2's
page Connect method; binding-scope recycling does not disconnect these handlers.
A transactional compiler-emitted reconnect artifact also remains separate;
retaining event subscriptions does not make generated fields extensible.

Adding, removing or renaming x:Name can change generated fields/accessors. A changed
x:Bind path can change private typed calls, generated callbacks and tracking state.
Standard JVM [class redefinition](https://docs.oracle.com/en/java/javase/25/docs/specs/jvmti.html#RedefineClasses)
does not allow arbitrary field/method schema changes, and Kotlin/Native cannot use
that JVM mechanism. Therefore the shared baseline rebuilds/restarts for these
changes. Retargeting an event to an already compiled compatible handler could be a
future ABI-preserving artifact, after subscription ownership exists. Supporting
new field/binding shapes would require a separately designed code-loading boundary;
Kotlin code Hot Reload and page recreation are outside the current support scope.

The development transport starts only when `KOTLIN_WINRT_HOT_RELOAD_DIRECTORY` is supplied
to a development process. It uses an ephemeral loopback port, a random 256-bit token,
bounded binary framing and a session file restricted to its owner. Tokens are not
part of display models. Invalid clients do not propagate errors into application
code. Shutdown closes sockets, rejects queued updates and removes only the session
file created by this transport.

The property engine and bounded wire codec are common Kotlin. The JVM stream
adapter and the mingwX64 Winsock adapter use the same protocol. Native accepts one
bounded request at a time on a development worker and dispatches mutations through
the existing UI queue. Closing the transport interrupts socket operations without
blocking the UI thread; the worker releases its storage after pending work exits.
Packaged native launches read the reserved development-directory startup argument,
matching the JVM launcher's environment setup. The native transport test exercises
authentication, an owner-thread property update, version advancement and cleanup.

Native hosts can select a local named pipe with
`KOTLIN_WINRT_DEVELOPMENT_TRANSPORT=pipe`. It uses the same authenticated frames,
bounded overlapped I/O and owner-thread dispatch. Its ACL includes only the current
user and, when present, the current AppContainer SID. The IDE accepts only the
process-bound pipe name and restricted LOCAL/AppContainer namespace recorded in
the session and cancels pending reads
on session close. A bounded receipt keeps DisconnectNamedPipe from discarding an
unread reply. Windows Native transport and IDE pipe interoperability tests cover
this channel. Real UWP Native acceptance also covers package activation, the
AppContainer namespace, preview rendering, live property updates and rollback.

The native application entry wrapper also reads these arguments before choosing
the project-code static designer. Preview mode starts the generated Application,
registers project and dependency XAML types, and retains an offscreen preview Window;
it does not invoke the user's main or Application constructor. This uses the
Application.Start/OnLaunched ownership split in CsWinRT's WinUIDesktopSample.
Project controls still execute their compiled constructors when XamlReader creates
them. Changing their Kotlin implementation requires rebuilding/restarting the host.

CsWinRT has no IDE socket/session counterpart. The Windows transport is a tooling
adaptation; projection ownership, custom-property accessors, conversion and UI
dispatch remain in their existing CsWinRT-aligned owners. This does not provide
Kotlin code replacement or new bindings/connections.

## Storyboard preview clocks

Protocol v7 adds isolated-designer clock commands for explicitly keyed Storyboard
resources on the preview root or an unambiguous named FrameworkElement. The
compiler emits SDK Begin/Pause/SeekAlignedToLastTick/Resume/Stop calls through the
normal generated projection (both WinUI and UWP). The runtime validates bounded
commands and rejects them for live application components. It does not implement
animation interpolation. The SDK retains easing, keyframes and dependent-animation
rules. Seeking reuses the current visual tree; replacing the design document stops
its old clock. Capture requests never advance or reset the clock themselves.

This tooling adaptation has no CsWinRT IDE transport counterpart. SDK projection
and TimeSpan-to-Kotlin-Duration ownership stay in the existing metadata/generator
and runtime mapping. Templates, implicit resource keys and VisualState-owned clocks
are not guessed from a page-wide name; their realization identity needs a separate
designer contract. The IDE keyframe editor edits uniquely identified local source
tracks with undo: Double, Color, Point and Object keyframes, bounded KeyTime and
attribute values. Existing spline/easing metadata is preserved. New frames use
linear or discrete interpolation (Object uses discrete). Complex object values,
ambiguous resource identities and template realizations remain source-only.
The timeline can create a keyed root storyboard and an SDK keyframe track for a
selected uniquely named element and writable dependency property. It preserves
existing dictionary/theme entries, rejects duplicate target tracks, selects the
created storyboard after preparation, and records the change as one undo command.

Selecting a millisecond keyframe seeks the preview to that frame. Existing frames
use a separate editable time field; moving the playhead alone never moves a frame.
Sub-millisecond or symbolic KeyTime values are not silently rounded by the editor.

## DataTemplate realization selection

Protocol v8 reports the SDK ContentControl.ContentTemplateRoot together with the
visual path of its consuming ContentControl, or its ancestor ItemsControl when
the actual DataTemplate COM identity matches ItemTemplate. Object comparison
reuses ComObjectReference.sameIdentity (CsWinRT IUnknown identity ownership);
visual names alone never establish a realization. The IDE resolves explicit
inline or StaticResource ContentTemplate/ItemTemplate source, including
explicit and theme-selected implicit styles with BasedOn inheritance. Selection
is scoped to the clicked realization. Editing changes the shared template source with one
undo command, and the property panel identifies that shared scope. Nested template consumers
are resolved through a bounded source closure, including external dictionaries;
cyclic references are rejected instead of guessing an owner.

Source-only navigation does not choose between repeated realizations. Template
selectors and ContentPresenter-only containers
are not inferred; they remain source-only
until an exact SDK-to-source ownership mapping is available. This selection
contract does not add live application template replacement or Kotlin reload.

The isolated designer restores the previous Window.Content when attaching or
laying out new markup fails after attachment. Deferred DataTemplate failures
therefore cannot leave the invalid tree attached for another layout pass. The
IDE keeps the last successful image with an error status; correcting the markup
renders in the same host without reconnecting. This does not suppress application
unhandled exceptions or change live application reload transactions.

Protocol v9 separates visual capture from effective-property reads. The static
canvas requests its image and SDK visual paths without invoking property getters
while the inspector is closed. Opening the inspector requests current values for
the selected realization; ordinary live inspection keeps its existing defaults.
The shared request flag is encoded on both JVM and Native transports. This is a
designer scheduling decision, not a change to generated ICustomProperty ownership
or the live reload transaction.
Effective-property display is supplied by the generated SDK adapter on both
JVM and Native. Thickness, corner radii and geometry use invariant component
order; grid lengths retain Auto/star units; colors and solid brushes use ARGB
hex, fonts use their source name and weights use their numeric value. Other
WinRT objects show their runtime type instead of a wrapper address. This is
read-only inspection, not a serialization contract for editing resources or
bindings. Getter and formatter failures remain local to a property.
