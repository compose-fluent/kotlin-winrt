# XAML development property and resource updates

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
restarting. Subsequent instances receive successful literal property overrides when
their original XBF finishes loading. The engine preserves the existing object graph,
events and compiled bindings; graph/binding/name changes require their own compiler
and lifecycle contract and are not handled by this protocol.

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
