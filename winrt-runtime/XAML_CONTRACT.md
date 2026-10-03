# XAML runtime contract

Ownership follows `.cswinrt/src/WinRT.Runtime/ComWrappersSupport.cs` (`GetInterfaceTableEntries`), `.cswinrt/src/cswinrt/code_writers.h` (`write_composable_constructors`), and `.cswinrt/src/WinRT.Runtime/Interop/EventSource{TDelegate}.cs`.

- Generated `IComponentConnector` is another interface in the page type CCW definition. `createComposableCCWForObject` constructs that outer before invoking the native factory. The connector must never be a separate object substituted for the page.
- `Connect` receives a borrowed inspectable. Use existing `WinRTObjectMarshaller.fromAbi` and the normal typed projection conversion; do not attach ownership to the incoming pointer or release it. Store the resolved control in a generated instance field.
- `GetBindingConnector` returns a caller-owned interface pointer when a binding scope exists. Without such a scope, initialize the out pointer to null and return success. Reuse normal interface marshaling for non-null scopes.
- The actual native Page aggregation and `Application.LoadComponent` call must be verified in Gallery. An ABI-shaped synthetic host only proves outer interface identity and callback mechanics.
- As in CsWinRT `MarshalInspectable<T>.CreateMarshaler2`, a derived page crosses `Object`/`IInspectable` boundaries as its controlling managed outer. The nondelegating native inner implements inherited SDK ABI calls and must not replace that object identity in activation results or component loading.
- Generated event code uses projected event add/remove APIs, `EventSource` and existing delegate/reference tracking. It must not maintain an independent global callback registry. Any generated unsubscribe hook must retain the same handler instance used for subscription.
- Named controls and initialization state are fields on each page. Kotlin constructor calls are lowered to `initializeWinRTXamlComponent(constructorCall)` after the complete constructor returns, including secondary constructor bodies. Delegating base-constructor calls are not wrapped. Kotlin 2.4 rich constructor references contain invocation bodies and follow the same lowering. The managed `WinRTXamlComponent` interface carries the contract across compilation boundaries; it has no IID or CCW entry.
- User code overrides `initializeComponent()`, calls `super.initializeComponent()` to execute the generated guarded load, and then accesses named controls. There is no separate initialized callback. Repeated base calls preserve the loaded tree and subscriptions; arbitrary user override statements are ordinary Kotlin code and are not automatically idempotent. Constructor exceptions skip initialization; initialization exceptions propagate before the call returns an instance.
- A separate generated construction guard owns the automatic call to the user override. Authoring's activation boundary ensures completion before marshaling the result; if a compiled constructor call already completed it, the activation check does nothing. This also covers factories supplied by callers that do not use compiler lowering. Failure is retained and no partially initialized result is published by the activation factory.
- This is the user-requested C++/WinRT lifecycle alignment: `strings/base_implements.h` owns `create_and_initialize`, and `nuget/readme.md#initializecomponent` documents construction followed by initialization. CsWinRT remains the ABI/composition reference. The plugin must be enabled in consuming Kotlin modules; reflection bypassing compiled Kotlin call sites must explicitly use `initializeWinRTXamlComponent` after construction. Native target validation remains a separate gate.
- XamlCompiler `CSharpPagePass1.tt` guards reentrant loading before `LoadComponent`; Kotlin keeps this guard. To satisfy the approved failure contract, final generated code distinguishes loading, loaded and failed: successful repeat and synchronous reentry do nothing; failure propagates and subsequent attempts fail with the original cause rather than duplicate partially installed subscriptions. This is a narrow documented difference from the C# boolean guard. No thread-safe background XAML loading is implied.

`XamlConnectorIdentityTest` verifies same outer IUnknown, same managed receiver, per-instance dispatch, borrowed target ownership and null connector output using a synthetic connector-shaped interface (its GUID is not the WinUI IID). `EventRuntimeInfrastructureCommonTest` verifies existing token/handler ownership. Native execution and real WinUI loading remain separate integration gates.

## Authored XAML types

`WinRTXamlTypeDefinition` and `WinRTXamlMemberDefinition` correspond to the
`XamlUserType` activator and `XamlMember` getter/setter delegates emitted by
XamlCompiler's `CSharpTypeInfoPass2.tt`. CsWinRT owns the underlying CCW/RCW
identity and inspectable marshaling; Kotlin reuses those runtime responsibilities.
Generated accessors perform direct Kotlin calls. The runtime does not reflect
over properties, interpret markup, or duplicate value-type classification.
The generated registration includes Kotlin `KClass` values for the native base
and member types. When the SDK metadata provider has no IXamlType for one of
those system types, Kotlin supplies the `XamlSystemBaseType` identity and
underlying type that XamlCompiler's C# output would put in its type table.

Registered definitions extend the existing authored identity provider with
`IsConstructible`, `ActivateInstance`, `ContentProperty`, and `GetMember`.
Activation completes the two-phase XAML construction contract before publishing
the owned inspectable result. Getter outputs are owned by the caller; setter
inputs are borrowed. Members not declared on the type delegate to its base.
Identity-only registrations retain their existing nonconstructible behavior.

The Kotlin source scanner now emits an application-only WinMD before
XamlCompiler pass 1. It recognizes adjacent same-basename Kotlin/XAML classes,
their WinRT bases, constructors callable without arguments, and explicitly typed public or
internal instance properties. An explicitly declared type is required for a
property exposed to XAML because the first compiler pass precedes Kotlin IR.
The scanner emits Kotlin registration sources with direct constructors and
getter/setter calls. The page's generated load method registers all application
types before invoking `Application.LoadComponent`. Kotlin semantic compilation
then emits the authoritative application WinMD for pass 2.

Application-only XAML model activators accept constructors whose parameters all
have Kotlin defaults, matching semantic export's direct `Type()` factory. Public
WinRT component authoring retains CsWinRT's separate zero-parameter ABI rule.

Gallery pages and shared controls use the pre-analysis WinMD, isolated semantic
compilation, final XAML compilation, and main Kotlin compilation stages.
Compiled bindings use generated calls with template scopes, phase tracking,
deferred elements, converters and binding updates. JVM and Native execution
remain separate integration checks, recorded in the Gallery migration inventory.

The semantic compilation now exports declared public/internal instance
properties into the application-only `KotlinXaml.winmd`. It uses Kotlin IR
types and accessor visibility, excludes generated `x:Name` properties and
private state, and preserves nullable value types as `IReference<T>`. This
schema is separate from authored component ABI export. The metadata writer
owns Property/MethodSemantics encoding, following CsWinRT
`WinRTTypeWriter.AddPropertyDefinition`. The initial XAML analysis consumes
the scanner's application declaration WinMD. `@WinRTXamlContentProperty` maps
implicit child content to an explicitly typed property in both passes and in
the generated runtime metadata.

Writable Kotlin map properties expose the same dictionary contract as
XamlCompiler's `XamlUserType`: `IsDictionary`, `KeyType`, `ItemType`, and a
generated typed `AddToMap` callback. Writable lists expose `IsCollection` and
`AddToVector`. Both use the shared container type lookup, including XBF type
queries before `IXamlMember.Type` is requested. Read-only maps do not advertise
an insertion callback.

Closed arrays, nullable values and collections carry explicit compiled type shapes.
`FullName` uses XBF's standard names (for example `Int32[]` and
``Windows.Foundation.IReference`1<Int32>``), while `UnderlyingType`, `BoxedType`,
`ItemType` and `KeyType` preserve the corresponding metadata identity. Nested
type arguments cannot be reconstructed from an erased Kotlin `KClass`.
Nullable string conversion delegates to the inner type's converter; system type
fallbacks leave SDK-owned parsing to the SDK metadata provider, as in C#.
Classes implementing projected list or map interfaces carry the same typed
insertion contract. Their schema uses closed InterfaceImpl TypeSpec signatures,
following `WinRTTypeWriter.GetTypeSpecification`.

Attached members retain the getter's target type, allow getter-only access, and
advertise a dependency property only when its static DependencyProperty exists.
Content properties can refer to inherited members. A definition's initializer
initializes its own companion or object, and ordinary model classes receive
bindable metadata without being registered as Windows Runtime components.

`@WinRTXamlCreateFromString("Parse")` names a public companion/object factory
accepting one String and returning the represented value. A fully qualified
factory on another type is also supported. Both schema passes emit WinUI's
`CreateFromStringAttribute.MethodName`, and the owning module compiles the typed
factory delegate for `IXamlType.CreateFromString`. No runtime method reflection
is used. Factory results that are XAML components complete initialization before
being marshaled, just as activation results do. Private companions do not export
public static members, while representable private inputs remain available to
the page's compiled bindings.

XAML libraries publish their declaration schema in the existing library identity's
`xamlSchemaRecords`, separately from `authoredMetadataRecords`. Consumers materialize
these records only for XAML header analysis and compilation; they do not generate
projections or activation hosts for the application schema. Each library uses a
`<module>.KotlinXaml` assembly identity and a matching dependency WinMD filename.
Both XAML passes recognize dependency model names as their original Kotlin names.
The existing compiler support identity carries the runtime registrars, while AppX
resource variants carry the compiled XAML/XBF payload.

Model-only Kotlin libraries use an isolated semantic compilation to export their
public XAML schema, including inferred property types. Their normal JAR/KLIB
contains generated accessors and a registrar published through the same library
identity. Unsupported implementation members are omitted from this schema;
XAML page schemas retain strict diagnostics. Windows SDK metadata is compiler
input for collection and nullable value type signatures, and does not require
generating SDK bindings in the model library. Ordinary classes implementing
runtime-mapped interfaces such as `INotifyPropertyChanged` use the existing CCW
adapters; only explicit authoring or an unmapped ABI shape requires a component.

Resource-only XAML libraries use the same resource variants without exporting
authored types or SDK projections. An empty semantic schema records their
identity; no binding converter or template connector is emitted. Consumer PRI
staging merges the XBF files at their original package-relative dictionary paths.
The Gallery's `resources` module owns its shared grid and text styles, while its
`models` module owns the plain Kotlin Recipe model used by ItemsRepeater.

When an `x:Name` conflicts with an inherited Kotlin property, FIR reports an error
including the XAML path, line, column, and conflicting property. Rename the element
explicitly in XAML and Kotlin. Generated accessors retain the literal XAML name;
no automatic prefix or alias is introduced on JVM or Native.
