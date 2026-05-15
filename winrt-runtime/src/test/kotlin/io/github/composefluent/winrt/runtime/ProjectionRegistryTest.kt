package io.github.composefluent.winrt.runtime

import io.github.composefluent.winrt.projections.support.FallbackIndexedRuntimeClass
import io.github.composefluent.winrt.projections.support.GENERATED_REGISTRAR_INTERFACE_TYPE_HANDLE
import io.github.composefluent.winrt.projections.support.GeneratedRegistrarInterfaceProjection
import io.github.composefluent.winrt.projections.support.GeneratedRegistrarRuntimeClass
import java.lang.foreign.Arena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Instant

class ProjectionRegistryTest {
    @Test
    fun projections_registry_round_trips_custom_mappings_and_runtime_class_defaults() {
        ComWrappersSupport.clearRegistriesForTests()
        registerTestTypeDescriptors()

        assertTrue(
            Projections.registerCustomAbiTypeMapping(
                publicType = SampleMappedType::class,
                helperType = SampleMappedTypeHelper::class,
                abiTypeName = "Contoso.IMappedType",
            ),
        )
        assertTrue(
            Projections.registerCustomAbiTypeMapping(
                publicType = SampleRuntimeClass::class,
                helperType = SampleRuntimeClassHelper::class,
                abiTypeName = "Contoso.SampleRuntimeClass",
                isRuntimeClass = true,
            ),
        )
        assertTrue(
            Projections.registerDefaultInterfaceType(
                runtimeClass = SampleRuntimeClass::class,
                defaultInterface = SampleDefaultInterface::class,
            ),
        )

        assertEquals(SampleMappedTypeHelper::class, Projections.findCustomHelperTypeMapping(SampleMappedType::class))
        assertEquals(SampleMappedType::class, Projections.findCustomPublicTypeForAbiType(SampleMappedTypeHelper::class))
        assertEquals(SampleMappedType::class, Projections.findCustomKClassForAbiTypeName("Contoso.IMappedType"))
        assertEquals("Contoso.IMappedType", Projections.findCustomAbiTypeNameForType(SampleMappedType::class))
        assertEquals(SampleDefaultInterface::class, Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(SampleRuntimeClass::class))
        assertTrue(Projections.isTypeWindowsRuntimeType(SampleRuntimeClass::class))
        assertFalse(Projections.isTypeWindowsRuntimeType(PlainManagedType::class))
    }

    @Test
    fun type_name_support_resolves_registered_projection_types_and_base_type_fallbacks() {
        ComWrappersSupport.clearRegistriesForTests()
        registerTestTypeDescriptors()

        ComWrappersSupport.registerProjectionAssembly(SampleRuntimeClass::class)
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(
            mapOf("Contoso.DerivedRuntimeClass" to "Contoso.SampleRuntimeClass"),
        )

        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findRcwKClassByNameCached("Contoso.SampleRuntimeClass"))
        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findRcwKClassByNameCached("Contoso.DerivedRuntimeClass"))
        assertEquals(String::class, TypeNameSupport.findKClassByNameCached("String"))
        assertNull(TypeNameSupport.findRcwKClassByNameCached("Contoso.Missing"))
    }

    @Test
    fun compiler_generated_projection_type_index_registers_structured_runtime_metadata() {
        ComWrappersSupport.clearRegistriesForTests()

        registerCompilerGeneratedProjectionTypeIndex(
            kClass = SampleRuntimeClass::class,
            projectedTypeName = "Contoso.GeneratedRuntimeClass",
            kind = "RuntimeClass",
            baseTypeName = "Contoso.SampleRuntimeClass",
        )

        val typeId = WinRtTypeRegistry.findByClass(SampleRuntimeClass::class)
        assertEquals("Contoso.GeneratedRuntimeClass", typeId?.projectedTypeName)
        assertEquals("Contoso.GeneratedRuntimeClass", typeId?.runtimeClassName)
        assertTrue(typeId?.isRuntimeClass == true)
        assertTrue(typeId?.isWindowsRuntimeType == true)
        assertTrue(typeId?.aliases?.contains("Contoso.GeneratedRuntimeClass") == true)
        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findRcwKClassByNameCached("Contoso.GeneratedRuntimeClass"))

        TypeNameSupport.registerProjectionTypeBaseTypeMapping(
            mapOf("Contoso.DerivedGeneratedRuntimeClass" to "Contoso.GeneratedRuntimeClass"),
        )
        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findRcwKClassByNameCached("Contoso.DerivedGeneratedRuntimeClass"))
    }

    @Test
    fun compiler_generated_projection_registrar_registers_class_literal_type_index() {
        ComWrappersSupport.clearRegistriesForTests()

        registerCompilerGeneratedProjectionTypeIndexes()

        val typeId = WinRtTypeRegistry.findByClass(GeneratedRegistrarRuntimeClass::class)
        assertEquals("Contoso.GeneratedRegistrarRuntimeClass", typeId?.projectedTypeName)
        assertEquals("Contoso.GeneratedRegistrarRuntimeClass", typeId?.runtimeClassName)
        assertTrue(typeId?.isRuntimeClass == true)
        assertEquals(
            GeneratedRegistrarRuntimeClass::class,
            TypeNameSupport.findRcwKClassByNameCached("Contoso.GeneratedRegistrarRuntimeClass"),
        )
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(
            mapOf("Contoso.GeneratedRegistrarDerived" to "Contoso.GeneratedRegistrarRuntimeClass"),
        )
        assertEquals(
            GeneratedRegistrarRuntimeClass::class,
            TypeNameSupport.findRcwKClassByNameCached("Contoso.GeneratedRegistrarDerived"),
        )
    }

    @Test
    fun compiler_generated_projection_loader_keeps_resource_indexes_after_fixed_registrar() {
        ComWrappersSupport.clearRegistriesForTests()

        registerCompilerGeneratedProjectionTypeIndexes()

        val typeId = WinRtTypeRegistry.findByClass(FallbackIndexedRuntimeClass::class)
        assertEquals("Contoso.FallbackIndexedRuntimeClass", typeId?.projectedTypeName)
        assertEquals("Contoso.FallbackIndexedRuntimeClass", typeId?.runtimeClassName)
        assertTrue(typeId?.isRuntimeClass == true)
        assertEquals(
            FallbackIndexedRuntimeClass::class,
            TypeNameSupport.findRcwKClassByNameCached("Contoso.FallbackIndexedRuntimeClass"),
        )
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(
            mapOf("Contoso.FallbackIndexedDerived" to "Contoso.FallbackIndexedRuntimeClass"),
        )
        assertEquals(
            FallbackIndexedRuntimeClass::class,
            TypeNameSupport.findRcwKClassByNameCached("Contoso.FallbackIndexedDerived"),
        )
    }

    @Test
    fun compiler_generated_event_source_loader_keeps_resource_entries_after_fixed_registry() {
        WinRtEventSourceRuntime.clearForTests()

        registerCompilerGeneratedEventSources()

        val descriptor = WinRtEventSourceRuntime.descriptorFor(
            eventType = "Windows.Foundation.TypedEventHandler<System.Object, Contoso.GeneratedEventArgs>",
            ownerType = "Contoso.ResourceIndexedOwner",
        )
        assertEquals("Contoso.ResourceIndexedOwner", descriptor?.ownerType)
        assertEquals(Guid("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeeee"), descriptor?.interfaceId)
        assertEquals(listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE), descriptor?.parameterKinds)
        assertEquals(WinRtDelegateValueKind.UNIT, descriptor?.returnKind)
        assertTrue(descriptor?.eventSourceFactory != null)
    }

    @Test
    fun compiler_generated_event_source_loader_upgrades_incomplete_duplicate_entries() {
        WinRtEventSourceRuntime.clearForTests()
        WinRtEventSourceRuntime.registerEventSource(
            WinRtEventSourceDescriptor(
                eventType = "Windows.Foundation.TypedEventHandler<System.Object, Contoso.GeneratedEventArgs>",
                ownerType = "Contoso.ResourceIndexedOwner",
                sourceClass = "_EventSource_Windows_Foundation_TypedEventHandler_System_Object__Contoso_GeneratedEventArgs_",
                abiEventType = "Windows.Foundation.TypedEventHandler`2",
                genericArguments = listOf("System.Object", "Contoso.GeneratedEventArgs"),
            ),
        )

        val ownerHost = WinRtInspectableComObject.inspectableBox("owner", "Contoso.ResourceIndexedOwner")
        val owner = ownerHost.createPrimaryReference()
        val source = try {
            WinRtEventSourceRuntime.createEventSource(
                eventType = "Windows.Foundation.TypedEventHandler<System.Object, Contoso.GeneratedEventArgs>",
                ownerType = "Contoso.ResourceIndexedOwner",
                objectReference = owner,
                vtableIndexForAddHandler = IInspectableVftblSlots.FirstCustom,
            )
        } finally {
            owner.close()
            ownerHost.close()
        }
        val descriptor = WinRtEventSourceRuntime.descriptorFor(
            eventType = "Windows.Foundation.TypedEventHandler<System.Object, Contoso.GeneratedEventArgs>",
            ownerType = "Contoso.ResourceIndexedOwner",
        )

        assertNotNull(source)
        assertEquals(Guid("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeeee"), descriptor?.interfaceId)
        assertEquals(listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE), descriptor?.parameterKinds)
        assertTrue(descriptor?.eventSourceFactory != null)
    }

    @Test
    fun generated_event_source_runtime_maps_acronym_namespaces_to_generated_packages() {
        assertEquals(
            "microsoft.ui.xaml.RoutedEventHandler",
            projectedJvmClassName("Microsoft.UI.Xaml.RoutedEventHandler"),
        )
        assertEquals(
            "windows.foundation.TypedEventHandler",
            projectedJvmClassName("Windows.Foundation.TypedEventHandler"),
        )
    }

    @Test
    fun generated_event_source_runtime_invokes_kotlin_lambda_handlers_accessibly() {
        val received = mutableListOf<String>()
        val handler: (Any?, Any?) -> Unit = { _, args -> received += args?.javaClass?.simpleName ?: "null" }

        invokeHandler(handler, listOf("sender", "args"))

        assertEquals(listOf("String"), received)
    }

    @Test
    fun generated_event_source_runtime_wraps_declared_projected_event_argument_type() {
        val reference = IInspectableReference(
            Arena.ofAuto().allocate(8).asNativePointer().asRawComPtr(),
            preventReleaseOnDispose = true,
        )

        val projected = projectTypedReference("Contoso.GeneratedEventArgs", reference)

        assertTrue(projected is contoso.GeneratedEventArgs)
        assertSame(reference, (projected as contoso.GeneratedEventArgs).reference)
    }

    @Test
    fun generated_interface_projection_wrap_retries_compiler_generated_registry_on_miss() {
        ComWrappersSupport.clearRegistriesForTests()
        clearGeneratedInterfaceProjectionFactoriesForTest()
        val nativeReference = IUnknownReference(
            Arena.ofAuto().allocate(8).asNativePointer().asRawComPtr(),
            GENERATED_REGISTRAR_INTERFACE_TYPE_HANDLE.interfaceId,
            preventReleaseOnDispose = true,
        )

        val projected = ComWrappersSupport.wrapGeneratedInterfaceProjection(
            GENERATED_REGISTRAR_INTERFACE_TYPE_HANDLE,
            nativeReference,
        ) as GeneratedRegistrarInterfaceProjection

        assertSame(nativeReference, projected.nativeObject)
    }

    @Test
    fun generated_interface_projection_registry_wraps_by_type_handle_and_type_name() {
        ComWrappersSupport.clearRegistriesForTests()
        val typeHandle = WinRtTypeHandle(
            projectedTypeName = "Contoso.IGeneratedInterface",
            interfaceId = Guid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        )
        val nativeReference = IUnknownReference(
            Arena.ofAuto().allocate(8).asNativePointer().asRawComPtr(),
            typeHandle.interfaceId,
            preventReleaseOnDispose = true,
        )
        val projected = SampleGeneratedInterfaceProjection(nativeReference)

        assertTrue(
            ComWrappersSupport.registerInterfaceProjectionFactory(typeHandle) { instance ->
                assertSame(nativeReference, instance)
                projected
            },
        )

        assertSame(projected, ComWrappersSupport.wrapGeneratedInterfaceProjection(typeHandle, nativeReference))
        assertSame(projected, ComWrappersSupport.wrapGeneratedInterfaceProjection(typeHandle.projectedTypeName, nativeReference))
    }

    @Test
    fun generated_interface_projection_object_return_uses_object_marshaller_from_abi() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val typeHandle = WinRtTypeHandle("Contoso.IGeneratedObjectReturn", interfaceId)
        val payload = GeneratedObjectPayload("payload")
        val returnedReference = ComWrappersSupport.createCCWForObject(payload, IID.IInspectable)
        var returnedPointer = PlatformAbi.nullPointer
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = interfaceId,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { args ->
                            returnedPointer = PlatformAbi.fromRawComPtr(returnedReference.getRefPointer())
                            PlatformAbi.writePointer(args[0] as RawAddress, returnedPointer)
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )
        val nativeReference = host.createReference(interfaceId)
        val unknownReference = IUnknownReference(nativeReference.getRefPointer(), interfaceId)
        try {
            val projected = WinRtGeneratedInterfaceProjectionRuntime.create(
                interfaceClass = GeneratedObjectReturnProjection::class.java,
                typeHandle = typeHandle,
                nativeObject = unknownReference,
                members = listOf(
                    GeneratedInterfaceProjectionMemberDescriptor(
                        kind = GeneratedInterfaceProjectionMemberKind.Method,
                        jvmName = "getObjectValue",
                        slot = IInspectableVftblSlots.FirstCustom,
                        returnKind = GeneratedInterfaceProjectionValueKind.Object,
                        parameterKinds = emptyList(),
                        suppressHResultCheck = false,
                    ),
                ),
            ) as GeneratedObjectReturnProjection

            assertSame(payload, projected.getObjectValue())
        } finally {
            if (!PlatformAbi.isNull(returnedPointer)) {
                IUnknownReference(returnedPointer.asRawComPtr(), IID.IInspectable).close()
            }
            unknownReference.close()
            nativeReference.close()
            returnedReference.close()
            host.close()
        }
    }

    @Test
    fun generated_interface_projection_no_arg_unit_uses_fixed_vtable_primitive() {
        val interfaceId = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeef")
        val typeHandle = WinRtTypeHandle("Contoso.IGeneratedNoArgUnit", interfaceId)
        var invocationCount = 0
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = interfaceId,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(ComMethodSignature()) {
                            invocationCount += 1
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )
        val nativeReference = host.createReference(interfaceId)
        val unknownReference = IUnknownReference(nativeReference.getRefPointer(), interfaceId)
        try {
            val projected = WinRtGeneratedInterfaceProjectionRuntime.create(
                interfaceClass = GeneratedNoArgUnitProjection::class.java,
                typeHandle = typeHandle,
                nativeObject = unknownReference,
                members = listOf(
                    GeneratedInterfaceProjectionMemberDescriptor(
                        kind = GeneratedInterfaceProjectionMemberKind.Method,
                        jvmName = "invoke",
                        slot = IInspectableVftblSlots.FirstCustom,
                        returnKind = GeneratedInterfaceProjectionValueKind.Unit,
                        parameterKinds = emptyList(),
                        suppressHResultCheck = false,
                    ),
                ),
            ) as GeneratedNoArgUnitProjection

            projected.invoke()

            assertEquals(1, invocationCount)
        } finally {
            unknownReference.close()
            nativeReference.close()
            host.close()
        }
    }

    @Test
    fun type_name_support_uses_non_winrt_runtime_class_lookup_hooks() {
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.registerTypeRuntimeClassNameLookup { type ->
            if (type == PlainManagedType::class) {
                "Contoso.LookupRegisteredRuntimeClass"
            } else {
                null
            }
        }

        assertEquals(
            "Contoso.LookupRegisteredRuntimeClass",
            TypeNameSupport.getNameForType(
                PlainManagedType::class,
                setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
            ),
        )
    }

    @Test
    fun type_extensions_and_guid_generator_follow_runtime_registry_contracts() {
        ComWrappersSupport.clearRegistriesForTests()
        registerTestTypeDescriptors()

        Projections.registerCustomAbiTypeMapping(
            publicType = SampleMappedType::class,
            helperType = SampleMappedTypeHelper::class,
            abiTypeName = "Contoso.IMappedType",
        )
        Projections.registerDefaultInterfaceType(
            runtimeClass = SampleRuntimeClass::class,
            defaultInterface = SampleDefaultInterface::class,
        )

        assertEquals(SampleAnnotatedHelper::class, TypeExtensions.findHelperType(SampleAnnotatedPublicType::class))
        assertEquals(SampleMappedTypeHelper::class, TypeExtensions.findHelperType(SampleMappedType::class))
        assertEquals(Guid("11111111-1111-1111-1111-111111111111"), GuidGenerator.getGuid(SampleDefaultInterface::class))
        assertEquals(Guid("22222222-2222-2222-2222-222222222222"), GuidGenerator.getIID(SampleMappedTypeHelper::class))
        assertEquals("struct(Contoso.SampleStruct;i4;string)", GuidGenerator.getSignature(SampleStruct::class))
        assertEquals(
            "rc(Contoso.SampleRuntimeClass;{11111111-1111-1111-1111-111111111111})",
            GuidGenerator.getSignature(SampleRuntimeClass::class),
        )
        assertEquals(
            ParameterizedInterfaceId.createFromSignature("rc(Contoso.SampleRuntimeClass;{11111111-1111-1111-1111-111111111111})"),
            GuidGenerator.createIID(SampleRuntimeClass::class),
        )
    }

    @Test
    fun guid_generator_uses_registered_default_interface_signature_when_kclass_cannot_represent_it() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtTypeRegistry.register<SampleGenericRuntimeClass>(
            projectedTypeName = "Contoso.SampleGenericRuntimeClass",
            runtimeClassName = "Contoso.SampleGenericRuntimeClass",
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = SampleGenericRuntimeClass::class,
            helperType = SampleRuntimeClassHelper::class,
            abiTypeName = "Contoso.SampleGenericRuntimeClass",
            isRuntimeClass = true,
        )
        Projections.registerDefaultInterfaceTypeName(
            runtimeClassName = "Contoso.SampleGenericRuntimeClass",
            defaultInterfaceName = "Contoso.IVector<String>",
            defaultInterfaceSignature = "pinterface({aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa};string)",
        )

        assertEquals(
            "rc(Contoso.SampleGenericRuntimeClass;pinterface({aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa};string))",
            GuidGenerator.getSignature(SampleGenericRuntimeClass::class),
        )
        assertEquals(
            "Contoso.IVector<String>",
            Projections.tryGetDefaultInterfaceTypeNameForRuntimeClassName("Contoso.SampleGenericRuntimeClass"),
        )
    }

    @Test
    fun intrinsic_type_classification_is_shared_across_runtime_helpers() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals("Int8", TypeNameSupport.getNameForType(Byte::class))
        assertEquals(UByte::class, TypeNameSupport.findKClassByNameCached("UInt8"))
        assertEquals(Byte::class, TypeNameSupport.findKClassByNameCached("Int8"))
        assertEquals(Any::class, TypeNameSupport.findKClassByNameCached("Object"))
        assertTrue(Projections.isTypeWindowsRuntimeType(Char::class))
        assertEquals("i1", GuidGenerator.getSignature(Byte::class))
        assertEquals("u1", GuidGenerator.getSignature(UByte::class))
        assertEquals("cinterface(IInspectable)", GuidGenerator.getSignature(Any::class))
    }

    @Test
    fun runtime_117_system_projection_mappings_follow_cswinrt_owner_set() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals(DateTimeProjection::class, TypeExtensions.findHelperType(Instant::class))
        assertEquals(TimeSpanProjection::class, TypeExtensions.findHelperType(Duration::class))
        assertEquals(UriProjection::class, TypeExtensions.findHelperType(WinRtUri::class))
        assertEquals(IClosableProjection::class, TypeExtensions.findHelperType(AutoCloseable::class))
        assertTrue(Projections.isTypeWindowsRuntimeType(KClass::class))
        assertEquals(
            WinRtReferenceTypeNames.boxedReference("Windows.UI.Xaml.Interop.TypeName"),
            WinRtValueBoxing.boxedRuntimeClassNameForType(KClass::class),
        )
        assertEquals(WinRtTypeKind.Metadata.ordinal, TypeProjection.fromManaged(KClass::class).kind)
        assertEquals(
            "rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})",
            GuidGenerator.getSignature(WinRtUri::class),
        )
        assertEquals(
            "Windows.Foundation.IReference`1<Int32>",
            TypeNameSupport.getNameForType(Int::class, setOf(TypeNameGenerationFlag.GenerateBoxedName)),
        )
        assertEquals("kotlin.Array", TypeNameSupport.getNameForType(Array<String>::class, setOf(TypeNameGenerationFlag.GenerateBoxedName)))
    }

    @Test
    fun explicit_runtime_metadata_registration_backfills_runtime_type_tables() {
        ComWrappersSupport.clearRegistriesForTests()
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = AnnotatedStruct::class,
            projectedTypeName = "Contoso.AnnotatedStruct",
            signature = "struct(Contoso.AnnotatedStruct;i4)",
            isWindowsRuntimeType = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = AnnotatedDefaultInterface::class,
            projectedTypeName = "Contoso.AnnotatedDefaultInterface",
            iid = Guid("44444444-4444-4444-4444-444444444444"),
            isWindowsRuntimeType = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = AnnotatedRuntimeClass::class,
            projectedTypeName = "Contoso.AnnotatedRuntimeClass",
            helperType = AnnotatedRuntimeClassHelper::class,
            guid = Guid("44444444-4444-4444-4444-444444444444"),
            runtimeClassName = "Contoso.AnnotatedRuntimeClass",
            defaultInterface = AnnotatedDefaultInterface::class,
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )

        assertTrue(Projections.isTypeWindowsRuntimeType(AnnotatedStruct::class))
        assertEquals("Contoso.AnnotatedStruct", TypeNameSupport.getNameForType(AnnotatedStruct::class))
        assertEquals("struct(Contoso.AnnotatedStruct;i4)", GuidGenerator.getSignature(AnnotatedStruct::class))

        assertEquals(AnnotatedRuntimeClassHelper::class, TypeExtensions.findHelperType(AnnotatedRuntimeClass::class))
        assertEquals(AnnotatedDefaultInterface::class, Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(AnnotatedRuntimeClass::class))
        assertEquals(
            "rc(Contoso.AnnotatedRuntimeClass;{44444444-4444-4444-4444-444444444444})",
            GuidGenerator.getSignature(AnnotatedRuntimeClass::class),
        )
        assertEquals(Guid("44444444-4444-4444-4444-444444444444"), GuidGenerator.getGuid(AnnotatedDefaultInterface::class))
    }

    @Test
    fun type_name_support_resolves_boxed_reference_runtime_names_like_cswinrt() {
        ComWrappersSupport.clearRegistriesForTests()
        registerTestTypeDescriptors()
        ComWrappersSupport.registerProjectionAssembly(TestProjectedEnum::class)

        assertEquals(String::class, TypeNameSupport.findKClassByNameCached("Windows.Foundation.IReference`1<String>"))
        assertEquals(KClass::class, TypeNameSupport.findKClassByNameCached("Windows.Foundation.IReference`1<Windows.UI.Xaml.Interop.TypeName>"))
        assertEquals(Exception::class, TypeNameSupport.findKClassByNameCached("Windows.Foundation.IReference`1<Windows.Foundation.HResult>"))
        assertEquals(IntArray::class, TypeNameSupport.findKClassByNameCached("Windows.Foundation.IReferenceArray`1<Int32>"))
        assertEquals(TestProjectedEnum::class, TypeNameSupport.findKClassByNameCached("Windows.Foundation.IReference`1<Contoso.Priority>"))
        assertEquals("Contoso.Priority", TypeNameSupport.getNameForType(TestProjectedEnum::class))
    }

    private interface SampleDefaultInterface

    private interface SampleGeneratedInterface

    private interface GeneratedObjectReturnProjection {
        fun getObjectValue(): Any?
    }

    private interface GeneratedNoArgUnitProjection {
        fun invoke()
    }

    private class SampleRuntimeClass

    private class SampleGenericRuntimeClass

    private data class GeneratedObjectPayload(val value: String)

    private class SampleMappedTypeHelper

    private class SampleRuntimeClassHelper

    private class SampleMappedType

    private class SampleAnnotatedPublicType

    private class SampleAnnotatedHelper

    private class SampleStruct

    private class PlainManagedType

    private class SampleGeneratedInterfaceProjection(
        override val nativeObject: ComObjectReference,
    ) : SampleGeneratedInterface, IWinRTObject

    @WinRtGuid("44444444-4444-4444-4444-444444444444")
    private interface AnnotatedDefaultInterface

    private class AnnotatedRuntimeClassHelper

    @WindowsRuntimeType("rc(Contoso.AnnotatedRuntimeClass;{44444444-4444-4444-4444-444444444444})")
    @WindowsRuntimeHelperType(AnnotatedRuntimeClassHelper::class)
    @WinRtDefaultInterface(AnnotatedDefaultInterface::class)
    private class AnnotatedRuntimeClass

    @WindowsRuntimeType("struct(Contoso.AnnotatedStruct;i4)")
    private class AnnotatedStruct

    private enum class TestProjectedEnum(
        val abiValue: Int,
    ) {
        Low(0),
        High(2),
    }

    private fun registerTestTypeDescriptors() {
        WinRtTypeRegistry.register<SampleDefaultInterface>(
            projectedTypeName = SampleDefaultInterface::class.qualifiedName ?: SampleDefaultInterface::class.toString(),
            guid = Guid("11111111-1111-1111-1111-111111111111"),
            iid = Guid("11111111-1111-1111-1111-111111111111"),
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<SampleRuntimeClass>(
            projectedTypeName = "Contoso.SampleRuntimeClass",
            helperType = SampleRuntimeClassHelper::class,
            defaultInterface = SampleDefaultInterface::class,
            runtimeClassName = "Contoso.SampleRuntimeClass",
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<SampleMappedType>(
            projectedTypeName = "Contoso.IMappedType",
            helperType = SampleMappedTypeHelper::class,
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<SampleMappedTypeHelper>(
            projectedTypeName = SampleMappedTypeHelper::class.qualifiedName ?: SampleMappedTypeHelper::class.toString(),
            guid = Guid("22222222-2222-2222-2222-222222222222"),
            iid = Guid("22222222-2222-2222-2222-222222222222"),
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<SampleAnnotatedPublicType>(
            projectedTypeName = SampleAnnotatedPublicType::class.qualifiedName ?: SampleAnnotatedPublicType::class.toString(),
            helperType = SampleAnnotatedHelper::class,
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<SampleAnnotatedHelper>(
            projectedTypeName = SampleAnnotatedHelper::class.qualifiedName ?: SampleAnnotatedHelper::class.toString(),
            guid = Guid("33333333-3333-3333-3333-333333333333"),
            iid = Guid("33333333-3333-3333-3333-333333333333"),
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<SampleStruct>(
            projectedTypeName = "Contoso.SampleStruct",
            signature = "struct(Contoso.SampleStruct;i4;string)",
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<TestProjectedEnum>(
            projectedTypeName = "Contoso.Priority",
            signature = "enum(Contoso.Priority;i4)",
            enumAbiValue = { value -> (value as TestProjectedEnum).abiValue },
            isWindowsRuntimeType = true,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun clearGeneratedInterfaceProjectionFactoriesForTest() {
        listOf("interfaceProjectionFactoriesByHandle", "interfaceProjectionFactoriesByTypeName").forEach { fieldName ->
            val field = ComWrappersSupport::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            (field.get(ComWrappersSupport) as ConcurrentCacheMap<Any, Any>).clear()
        }
    }
}
