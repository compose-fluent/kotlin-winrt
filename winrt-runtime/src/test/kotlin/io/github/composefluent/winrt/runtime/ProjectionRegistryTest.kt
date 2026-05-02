package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private class SampleRuntimeClass

    private class SampleMappedTypeHelper

    private class SampleRuntimeClassHelper

    private class SampleMappedType

    private class SampleAnnotatedPublicType

    private class SampleAnnotatedHelper

    private class SampleStruct

    private class PlainManagedType

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
}
