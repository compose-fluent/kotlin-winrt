# XAML development property updates

The common property engine uses the compiler's existing XAML type/member
registrations. Property discovery reuses `WinUiAuthoredTypeMetadata.customProperty`,
corresponding to CsWinRT's generated `ICustomProperty` implementation in
`.cswinrt/src/WinRT.Runtime/Projections/ICustomPropertyProvider.net5.cs`.
Literal conversion reuses `convertWinRTXamlLiteral` and the generated SDK converter.
There is no reflection fallback or separate WinRT type classification table.

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
