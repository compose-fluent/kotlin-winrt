# XAML development updates

The common property engine uses the compiler's existing XAML type/member
registrations. Property discovery reuses `WinUiAuthoredTypeMetadata.customProperty`,
corresponding to CsWinRT's generated `ICustomProperty` implementation in
`.cswinrt/src/WinRT.Runtime/Projections/ICustomPropertyProvider.net5.cs`.
Literal conversion reuses `convertWinRTXamlLiteral` and the generated SDK converter.
There is no reflection fallback or separate WinRT type classification table.

For SDK controls, the compiler's existing typed member emitter supplies development
getters/setters for referenced presentation types and their bases. These entries
are registered only in the member lookup by Kotlin type; they do not advertise SDK
classes as authored XAML types or replace the SDK's native type provider. SDK member
discovery and inherited-member ownership remain in the metadata owner. Applications
without the development environment variable do not register these accessors.

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

Applied WinUI Styles are sealed. Their update constructs a fresh local dictionary
with the SDK's `XamlReader.Load`, as used by CsWinRT's resource tests in
`src/Tests/ObjectLifetimeTests/CustomGroupedItemPages.cs`. Generated application
glue supplies that typed SDK loader; the common runtime never parses XAML or
implements another projection model. The transaction keeps the original live
dictionary and controls, replaces values with the same explicit string keys and
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
participate in the update. Changing dictionary keys also requires rebuilding.
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
events, bindings, templates, property elements or authored controls. Removing a
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
| Names and connection IDs | XAMLC pass 2, WinRTXamlPageDeclaration and XamlPageBodies | Pair XBF and connection declarations from the same compiler generation; map retained slots within their original name scope. |
| Generated fields and binding calls | XamlFirRegistrar, XamlPageBodies and XamlCompiledBindingBodies | Match the loaded Kotlin class shape and typed accessor/handler signatures. |
| Event lifetime | Generated projected add/remove methods and WinRT event sources; CsWinRT Interop/EventSource{TDelegate}.cs | Capture each add result and its typed remover; own subscriptions by component, connection ID and generation. |
| Binding lifetime | WinRTXamlBindingState and WinRTXamlBindingScope; XAMLC CSharpPagePass2 StopTracking / DisconnectUnloadedObject | Release listeners and deferred targets before disconnecting; initialize the newly connected scope once after commit. |

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

The current XamlPageBodies event emitter calls typed add methods but does not keep
their EventRegistrationToken results for a reconnect/disconnect transaction. Its
binding state does release owned binding listeners, but that does not remove plain
XAML event handlers or make generated fields dynamically extensible. Subscription
ownership and a compiler-emitted reconnect surface are prerequisites for such an
extension; neither is synthesized inside the IDE or hidden in samples.

Adding, removing or renaming x:Name can change generated fields/accessors. A changed
x:Bind path can change private typed calls, generated callbacks and tracking state.
Standard JVM [class redefinition](https://docs.oracle.com/en/java/javase/25/docs/specs/jvmti.html#RedefineClasses)
does not allow arbitrary field/method schema changes, and Kotlin/Native cannot use
that JVM mechanism. Therefore the shared baseline rebuilds/restarts for these
changes. Retargeting an event to an already compiled compatible handler could be a
future ABI-preserving artifact, after subscription ownership exists. Supporting
new field/binding shapes would require a separately designed code-loading boundary;
Kotlin code Hot Reload and page recreation are outside the current support scope.

The JVM transport starts only when `KOTLIN_WINRT_HOT_RELOAD_DIRECTORY` is supplied
to a development process. It uses an ephemeral loopback port, a random 256-bit token,
bounded binary framing and a session file restricted to its owner. Tokens are not
part of display models. Invalid clients do not propagate errors into application
code. Shutdown closes sockets, rejects queued updates and removes only the session
file created by this transport.

The property engine is common Kotlin and is compiled for JVM and `mingwX64`.
The development transport currently exists only on JVM. Native explicitly rejects
an enabled development session. Winsock transport, owner-restricted session files,
shutdown/cancellation and end-to-end Native UI-thread verification remain Native
parity work; JVM validation does not claim those capabilities.
