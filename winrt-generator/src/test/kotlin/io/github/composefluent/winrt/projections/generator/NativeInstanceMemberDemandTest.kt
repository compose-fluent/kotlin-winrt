package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.*
import org.junit.Test

class NativeInstanceMemberDemandTest {
    @Test fun component_dependencies_remain_registered_for_native_construction_discovery() {
        // CsWinRT InteropGenerator keeps component ownership separate from its SDK
        // projection modules. A source component must not turn SDK classes into authored
        // mappings: Native demand discovery needs their constructor references too.
        val files = KotlinProjectionGenerator(emitSupportFiles = true,
            projectionContext = WinRTMetadataProjectionContext(emptyList(), component = true),
            authoredRuntimeClassNames = setOf("User.Authored"), emitJvmAuthoringHostExports = false)
            .generate(model)
        val registrar = files.single { it.relativePath == "kotlin-winrt-support/projection-registrar.tsv" }.contents
        assertTrue(registrar, registrar.contains("app.Reader\tApp.Reader\tRuntimeClass"))
        assertFalse(files.any { it.relativePath.contains("AuthoringMetadataTypeMappingHelper") })
    }

    @Test fun protected_callbacks_are_rooted_by_exposed_hierarchies_instead_of_every_projected_type() {
        // CsWinRT InteropTypeDiscovery.TryTrackExposedUserDefinedType ignores SDK
        // projected types. Only exposed user implementations need callback bodies.
        val callbacks = reader.copy(name = "IReaderOverrides", isProjectionInternal = true,
            iid = Guid("11111111-2222-3333-4444-555555555582"), properties = emptyList(),
            methods = listOf(WinRTMethodDefinition("OnRead", "Int32", methodRowId = 30)))
        val owner = runtimeClass.copy(methods = runtimeClass.methods + callbacks.methods, implementedInterfaces = listOf(
            WinRTInterfaceImplementationDefinition(reader.qualifiedName, isDefault = true),
            WinRTInterfaceImplementationDefinition(callbacks.qualifiedName, isProtected = true, isOverridable = true)))
        val derived = owner.copy(name = "Derived", baseTypeName = owner.qualifiedName,
            methods = emptyList(), properties = emptyList(), implementedInterfaces = emptyList())
        val graph = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, callbacks, owner, derived))))
        val unused = NativeInstanceMemberDemand.create(graph, emptySet(), emptySet(), emptySet())
        assertFalse(unused.retain(callbacks.qualifiedName, "onRead"))
        assertFalse(unused.retain(owner.qualifiedName, "onRead"))
        for (required in listOf(
            NativeInstanceMemberDemand.create(graph, emptySet(), setOf(derived.qualifiedName), emptySet()),
            NativeInstanceMemberDemand.create(graph, setOf("${owner.qualifiedName}\tonRead"), emptySet(), emptySet()),
            NativeInstanceMemberDemand.create(graph, emptySet(), emptySet()),
        )) {
            assertTrue(required.retain(callbacks.qualifiedName, "onRead"))
            assertTrue(required.retain(owner.qualifiedName, "onRead"))
        }
    }

    @Test fun only_emitted_static_members_discover_ccw_inputs_without_changing_abi_slots() {
        // CsWinRT 3 InteropGenerator.Discover analyzes business uses and Emit consumes
        // that state. A raw static factory interface is ABI metadata, not a CCW root.
        val statics = WinRTTypeDefinition("App", "IStatics", WinRTTypeKind.Interface, isExclusiveTo = true,
            iid = Guid("11111111-2222-3333-4444-555555555581"), methods = listOf(
                WinRTMethodDefinition("Read", "Int32", methodRowId = 20),
                WinRTMethodDefinition("Accept", "Unit", methodRowId = 21,
                    parameters = listOf(WinRTParameterDefinition("value", "App.IReader"))),
                WinRTMethodDefinition("Accept", "Unit", methodRowId = 22,
                    parameters = listOf(WinRTParameterDefinition("value", "App.IReader"), WinRTParameterDefinition("count", "Int32"))),
            ), properties = listOf(WinRTPropertyDefinition("Reader", "App.IReader",
                getterMethodName = "get_Reader", setterMethodName = "put_Reader", getterMethodRowId = 23, setterMethodRowId = 24)))
        val tools = WinRTTypeDefinition("App", "Tools", WinRTTypeKind.RuntimeClass, isStaticType = true,
            activation = WinRTActivationShape(staticInterfaceNames = listOf(statics.qualifiedName)))
        val graph = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, statics, tools))))
        fun demand(members: Set<String>?, abiOnly: Set<String> = setOf(statics.qualifiedName)) =
            NativeInstanceMemberDemand.create(graph, emptySet(), emptySet(), emptySet(), members, abiOnly)
        val unused = demand(setOf("App.Tools\tread"))
        assertTrue(unused.ccwInputTypes.isEmpty())
        assertFalse(unused.retain("App.IReader", "unused"))
        for (members in listOf(null, setOf("App.Tools\taccept"), setOf("App.Tools\treader"), setOf("App.Tools\t*"))) {
            val required = demand(members)
            assertEquals(setOf("App.IReader"), required.ccwInputTypes)
            assertTrue(required.retain("App.IReader", "unused"))
        }
        // An ordinary projected static-interface use remains a complete interface.
        assertEquals(setOf("App.IReader"), demand(emptySet(), emptySet()).ccwInputTypes)
        val sources = KotlinProjectionGenerator(nativeStaticMemberDemand = setOf("App.Tools\tread"),
            nativeAbiOnlyInterfaces = setOf(statics.qualifiedName), nativeInstanceMemberDemand = unused)
            .generate(graph).associate { it.relativePath to it.contents }
        assertFalse(sources.keys.any { it.contains("ProjectedInterfaceCcwFactories") })
        val abi = sources.getValue("app/IStatics.kt")
        assertTrue(abi, abi.contains("READ_SLOT: Int = 6"))
        assertTrue(abi, abi.contains("Int = 7"))
        assertTrue(abi, abi.contains("Int = 8"))
        assertFalse(abi, abi.contains("class NativeProjection"))
        assertTrue(sources.getValue("app/Tools.kt").contains("fun read("))
    }

    @Test fun unused_composable_factory_drops_helpers_but_exposed_types_keep_them() {
        val factory = WinRTTypeDefinition("App", "IReaderFactory", WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555580"), methods = listOf(
                WinRTMethodDefinition("CreateInstance", "App.Reader", parameters = listOf(
                    WinRTParameterDefinition("baseInterface", "Object"), WinRTParameterDefinition("innerInterface", "Object")))))
        val composable = runtimeClass.copy(activation = WinRTActivationShape(isActivatable = true, factories = listOf(
            WinRTAttributedFactoryShape(factory.qualifiedName, WinRTAttributedFactoryKind.Composable, isVisible = true))))
        val graph = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, composable, factory))))
        fun source(exposed: Set<String>) = KotlinProjectionGenerator(nativeInstanceMemberDemand =
            NativeInstanceMemberDemand.create(graph, emptySet(), exposed, emptySet())).generate(graph)
            .first { it.relativePath == "app/Reader.kt" }.contents
        assertFalse(source(emptySet()).contains("object ComposableFactory"))
        assertFalse(source(emptySet()).contains("ComposableFactory.createInstance"))
        assertTrue(source(setOf("App.Reader")).contains("object ComposableFactory"))
        assertTrue(source(setOf("App.Reader")).contains("createInstanceForSubclass"))
    }
    @Test fun construction_demand_keeps_factories_only_for_construction_and_exposed_base_paths() {
        // CsWinRT write_factory_constructors/write_composable_constructors: type identity
        // and RCW wrapping do not require activation; derived construction still does.
        val activatable = runtimeClass.copy(activation = WinRTActivationShape(isActivatable = true))
        val derived = activatable.copy(name = "Derived", baseTypeName = "App.Reader")
        val graph = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, activatable, derived))))
        fun source(demand: NativeInstanceMemberDemand?) = KotlinProjectionGenerator(nativeInstanceMemberDemand = demand)
            .generate(graph).first { it.relativePath == "app/Reader.kt" }.contents
        val unused = NativeInstanceMemberDemand.create(graph, emptySet(), emptySet(), emptySet())
        assertFalse(source(unused).contains("object ActivationFactory"))
        assertFalse(source(unused).contains("ActivationFactory.activate()"))
        assertTrue(source(unused).contains("fun wrap("))
        for (demand in listOf(null,
            NativeInstanceMemberDemand.create(graph, emptySet(), emptySet()),
            NativeInstanceMemberDemand.create(graph, emptySet(), emptySet(), setOf("App.Derived")),
            NativeInstanceMemberDemand.create(graph, emptySet(), setOf("App.Derived"), emptySet()),
            NativeInstanceMemberDemand.create(graph, setOf("App.Reader\t*"), emptySet(), emptySet()),
        )) assertTrue(source(demand).contains("object ActivationFactory"))
    }
    @Test fun unrelated_homonyms_do_not_share_demand_but_inherited_dispatch_does() {
        // CsWinRT write_class_members tracks each interface, not a global method name.
        val unrelated = reader.copy(namespace = "Other", name = "IReader",
            iid = Guid("11111111-2222-3333-4444-555555555579"))
        val derived = runtimeClass.copy(name = "Derived", baseTypeName = "App.Reader", methods = emptyList(), properties = emptyList())
        val sibling = runtimeClass.copy(name = "Sibling")
        val graph = WinRTMetadataModel(listOf(
            WinRTNamespace("App", listOf(reader, runtimeClass, derived, sibling)),
            WinRTNamespace("Other", listOf(unrelated))))
        for (owner in listOf("App.Derived", "App.IReader")) {
            val demand = NativeInstanceMemberDemand.create(graph, setOf("$owner\tread"), emptySet())
            for (related in listOf("App.IReader", "App.Reader", "App.Derived", "App.Sibling")) {
                assertTrue(related, demand.retain(related, "read"))
                assertFalse(related, demand.retain(related, "unused"))
            }
            assertFalse(demand.retain("Other.IReader", "read"))
            val unrelatedSource = KotlinProjectionGenerator(nativeInstanceMemberDemand = demand).generate(graph)
                .first { it.relativePath == "other/IReader.kt" }.contents
            assertFalse(unrelatedSource, unrelatedSource.contains("fun read("))
        }
        val exposed = NativeInstanceMemberDemand.create(graph, emptySet(), setOf("App.Derived"))
        assertTrue(exposed.retain("App.IReader", "unused"))
        assertFalse(exposed.retain("Other.IReader", "unused"))
        assertEquals(exposed.omittedMembers, NativeInstanceMemberDemand.create(
            graph.copy(namespaces = graph.namespaces.reversed()), emptySet(), setOf("App.Derived")).omittedMembers)
    }

    // CsWinRT code_writers.h write_class_members/write_interface_member_signatures
    // and InteropTypeDiscovery exposed hierarchies: outbound wrappers may be sliced,
    // inbound contracts and original ABI slot planning must remain complete.
    private val reader = WinRTTypeDefinition("App", "IReader", WinRTTypeKind.Interface,
        iid = Guid("11111111-2222-3333-4444-555555555571"), methods = listOf(
            WinRTMethodDefinition("Unused", "Int32", methodRowId = 1),
            WinRTMethodDefinition("Read", "Int32", methodRowId = 2),
            WinRTMethodDefinition("Read", "Int32", methodRowId = 3,
                parameters = listOf(WinRTParameterDefinition("value", "Int32"))),
        ), properties = listOf(WinRTPropertyDefinition("Setting", "Int32",
            getterMethodName = "get_Setting", setterMethodName = "put_Setting", getterMethodRowId = 4, setterMethodRowId = 5)))
    private val runtimeClass = WinRTTypeDefinition("App", "Reader", WinRTTypeKind.RuntimeClass,
        defaultInterfaceName = reader.qualifiedName, methods = reader.methods, properties = reader.properties)
    private val model = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, runtimeClass))))

    @Test fun unused_forwarding_surfaces_do_not_initialize_interface_proxy_caches() {
        // CsWinRT code_writers.h materializes interface caches on demand. Preserve
        // the full contract and exposed hierarchy, but omit unused private initializers.
        fun source(demand: NativeInstanceMemberDemand?) = KotlinProjectionGenerator(
            nativeInstanceMemberDemand = demand,
        ).generate(model).first { it.relativePath == "app/Reader.kt" }.contents
        val empty = NativeInstanceMemberDemand.create(model, emptySet(), emptySet())
        assertFalse(empty.requiresInstanceSurface("App.IReader"))
        assertFalse(source(empty).contains("_iReaderProjection"))
        for (demand in listOf(null,
            NativeInstanceMemberDemand.create(model, setOf("App.Reader\tread"), emptySet()),
            NativeInstanceMemberDemand.create(model, emptySet(), setOf("App.Reader")),
        )) assertTrue(source(demand).contains("_iReaderProjection"))
    }

    @Test fun instance_and_interface_dispatch_are_sliced_together_without_renumbering_slots() {
        val demand = NativeInstanceMemberDemand.create(model, setOf("App.Reader\tread", "App.IReader\tsetting"), emptySet())
        val files = KotlinProjectionGenerator(nativeInstanceMemberDemand = demand).generate(model)
            .associate { it.relativePath to it.contents }
        for (name in listOf("app/IReader.kt", "app/Reader.kt")) {
            val source = files.getValue(name)
            assertFalse(source, source.contains("fun unused("))
            assertTrue(source, source.contains("fun read("))
            assertTrue(source, source.contains("var setting:"))
        }
        val source = files.getValue("app/IReader.kt")
        assertTrue(source, source.contains("UNUSED_SLOT: Int = 6"))
        assertTrue(source, source.contains("Int = 7"))
        assertTrue(source, source.contains("Int = 8"))
        assertTrue(source, source.contains("set(`value`)"))
        val full = KotlinProjectionGenerator().generate(model).first { it.relativePath == "app/IReader.kt" }.contents
        assertTrue(full, full.contains("fun unused("))
    }

    @Test fun exposed_inheritance_and_name_based_use_keep_full_virtual_contracts() {
        for (demand in listOf(
            NativeInstanceMemberDemand.create(model, emptySet(), setOf("App.Reader")),
            NativeInstanceMemberDemand.create(model, setOf("App.Reader\t*"), emptySet()),
        )) assertTrue(demand.omittedMembers.toString(), demand.retain("App.IReader", "unused"))
    }

    @Test fun interfaces_accepted_by_abi_calls_keep_their_inbound_members() {
        val consumer = WinRTTypeDefinition("App", "IConsumer", WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555572"), methods = listOf(
                WinRTMethodDefinition("Accept", "Unit", methodRowId = 10,
                    parameters = listOf(WinRTParameterDefinition("reader", "App.IReader")))))
        val withConsumer = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, runtimeClass, consumer))))
        val unused = NativeInstanceMemberDemand.create(withConsumer, emptySet(), emptySet())
        assertFalse(unused.retain("App.IReader", "unused"))
        assertTrue(unused.ccwInputTypes.isEmpty())
        val demand = NativeInstanceMemberDemand.create(withConsumer, setOf("App.IConsumer\taccept"), emptySet())
        assertTrue(demand.omittedMembers.toString(), demand.retain("App.IReader", "unused"))
        fun ccwFiles(demand: NativeInstanceMemberDemand?) = KotlinProjectionGenerator(
            nativeInstanceMemberDemand = demand,
        ).generate(withConsumer).filter { it.relativePath.contains("ProjectedInterfaceCcwFactories") }
        assertTrue(ccwFiles(unused).isEmpty())
        assertTrue(ccwFiles(demand).isNotEmpty())
        assertTrue(ccwFiles(null).isNotEmpty())
    }

    @Test fun ccw_parameter_dependencies_converge_across_cycles_and_exposed_hierarchies() {
        // CsWinRT InteropTypeDiscovery recursively marks exposed implementations;
        // dependencies discovered only after retaining a CCW must not be omitted.
        fun iface(name: String, input: String, id: Int) = WinRTTypeDefinition("App", name, WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-55555555557$id"), methods = listOf(
                WinRTMethodDefinition("Accept", "Unit", methodRowId = id,
                    parameters = listOf(WinRTParameterDefinition("value", "App.$input")))))
        val types = listOf(iface("IStart", "IA", 1), iface("IA", "IB", 2), iface("IB", "IA", 3))
        val graph = WinRTMetadataModel(listOf(WinRTNamespace("App", types)))
        for (demand in listOf(
            NativeInstanceMemberDemand.create(graph, setOf("App.IStart\taccept"), emptySet()),
            NativeInstanceMemberDemand.create(graph, emptySet(), setOf("App.IStart")),
        )) {
            assertEquals(setOf("App.IA", "App.IB"), demand.ccwInputTypes)
            assertTrue(demand.retain("App.IA", "accept"))
            assertTrue(demand.retain("App.IB", "accept"))
        }
        val forward = NativeInstanceMemberDemand.create(graph, setOf("App.IStart\taccept"), emptySet())
        val reversed = NativeInstanceMemberDemand.create(
            WinRTMetadataModel(listOf(WinRTNamespace("App", types.reversed()))), setOf("App.IStart\taccept"), emptySet())
        assertEquals(forward.omittedMembers, reversed.omittedMembers)
        assertEquals(forward.ccwInputTypes, reversed.ccwInputTypes)
    }

    @Test fun property_input_and_factory_interface_obligations_keep_complete_ccws() {
        val setter = WinRTTypeDefinition("App", "ISetter", WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555578"),
            properties = listOf(WinRTPropertyDefinition("Reader", "App.IReader",
                getterMethodName = "get_Reader", setterMethodName = "put_Reader",
                getterMethodRowId = 11, setterMethodRowId = 12)))
        val graph = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, setter))))
        assertTrue(NativeInstanceMemberDemand.create(graph, emptySet(), emptySet()).ccwInputTypes.isEmpty())
        val selected = NativeInstanceMemberDemand.create(graph, setOf("App.ISetter\treader"), emptySet())
        assertEquals(setOf("App.IReader"), selected.ccwInputTypes)
        assertTrue(selected.retain("App.IReader", "unused"))
        // Static/factory interfaces remain complete even without explicit instance uses.
        val factoryOwner = runtimeClass.copy(activation = runtimeClass.activation.copy(
            staticInterfaceNames = listOf("App.ISetter")))
        val withFactory = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(reader, setter, factoryOwner))))
        assertEquals(setOf("App.IReader"), NativeInstanceMemberDemand.create(withFactory, emptySet(), emptySet()).ccwInputTypes)
    }

    @Test fun event_families_and_generic_obligations_are_retained_as_units() {
        val events = reader.copy(events = listOf(WinRTEventDefinition("Changed", "App.Handler",
            addMethodName = "add_Changed", removeMethodName = "remove_Changed", addMethodRowId = 6, removeMethodRowId = 7)))
        val handler = WinRTTypeDefinition("App", "Handler", WinRTTypeKind.Delegate,
            iid = Guid("11111111-2222-3333-4444-555555555573"),
            methods = listOf(WinRTMethodDefinition("Invoke", "Unit", methodRowId = 8)))
        val eventModel = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(events, handler))))
        val unused = NativeInstanceMemberDemand.create(eventModel, emptySet(), emptySet())
        assertTrue(unused.omittedMembers.toString(), listOf("changed", "addChanged", "removeChanged").none { unused.retain("App.IReader", it) })
        val selected = NativeInstanceMemberDemand.create(eventModel, setOf("App.IReader\tremoveChanged"), emptySet())
        assertTrue(selected.omittedMembers.toString(), listOf("changed", "addChanged", "removeChanged").all { selected.retain("App.IReader", it) })
        // CsWinRT 3 InteropGenerator.Emit generates helper types from discovery state.
        // Removing an event wrapper must also remove its owner-specific helper, while
        // the complete contract/default generation still supplies that helper.
        fun helperSources(demand: NativeInstanceMemberDemand?) = KotlinProjectionGenerator(
            emitSupportFiles = true, nativeInstanceMemberDemand = demand,
        ).generate(eventModel).filter { it.relativePath.contains("WinRTEventProjectionHelper") }
        assertTrue(helperSources(unused).isEmpty())
        assertTrue(helperSources(selected).isNotEmpty())
        assertTrue(helperSources(null).isNotEmpty())
        val generic = reader.copy(name = "IGeneric", genericParameterCount = 1)
        val genericModel = WinRTMetadataModel(listOf(WinRTNamespace("App", listOf(generic))))
        assertTrue("Generic adapters need full dispatch families",
            NativeInstanceMemberDemand.create(genericModel, emptySet(), emptySet()).retain("App.IGeneric", "unused"))
    }
}
