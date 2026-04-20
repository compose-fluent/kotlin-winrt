package io.github.kitectlab.winrt.runtime

import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionRegistryTest {
    @Test
    fun projections_registry_round_trips_custom_mappings_and_runtime_class_defaults() {
        ComWrappersSupport.clearRegistriesForTests()

        assertTrue(
            Projections.registerCustomAbiTypeMapping(
                publicType = SampleMappedType::class.java,
                helperType = SampleMappedTypeHelper::class.java,
                abiTypeName = "Contoso.IMappedType",
            ),
        )
        assertTrue(
            Projections.registerCustomAbiTypeMapping(
                publicType = SampleRuntimeClass::class.java,
                helperType = SampleRuntimeClassHelper::class.java,
                abiTypeName = "Contoso.SampleRuntimeClass",
                isRuntimeClass = true,
            ),
        )
        assertTrue(
            Projections.registerDefaultInterfaceType(
                runtimeClass = SampleRuntimeClass::class.java,
                defaultInterface = SampleDefaultInterface::class.java,
            ),
        )

        assertEquals(SampleMappedTypeHelper::class.java, Projections.findCustomHelperTypeMapping(SampleMappedType::class.java))
        assertEquals(SampleMappedType::class.java, Projections.findCustomPublicTypeForAbiType(SampleMappedTypeHelper::class.java))
        assertEquals(SampleMappedType::class.java, Projections.findCustomTypeForAbiTypeName("Contoso.IMappedType"))
        assertEquals("Contoso.IMappedType", Projections.findCustomAbiTypeNameForType(SampleMappedType::class.java))
        assertEquals(SampleDefaultInterface::class.java, Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(SampleRuntimeClass::class.java))
        assertTrue(Projections.isTypeWindowsRuntimeType(SampleRuntimeClass::class.java))
        assertFalse(Projections.isTypeWindowsRuntimeType(PlainManagedType::class.java))
    }

    @Test
    fun type_name_support_resolves_registered_projection_types_and_base_type_fallbacks() {
        ComWrappersSupport.clearRegistriesForTests()

        ComWrappersSupport.registerProjectionAssembly(SampleRuntimeClass::class.java)
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(
            mapOf("Contoso.DerivedRuntimeClass" to "Contoso.SampleRuntimeClass"),
        )

        assertEquals(SampleRuntimeClass::class.java, TypeNameSupport.findRcwTypeByNameCached("Contoso.SampleRuntimeClass"))
        assertEquals(SampleRuntimeClass::class.java, TypeNameSupport.findRcwTypeByNameCached("Contoso.DerivedRuntimeClass"))
        assertEquals(String::class.java, TypeNameSupport.findTypeByNameCached("String"))
        assertNull(TypeNameSupport.findRcwTypeByNameCached("Contoso.Missing"))
    }

    @Test
    fun type_name_support_uses_non_winrt_runtime_class_lookup_hooks() {
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.registerTypeRuntimeClassNameLookup { type ->
            if (type == PlainManagedType::class.java) {
                "Contoso.LookupRegisteredRuntimeClass"
            } else {
                null
            }
        }

        assertEquals(
            "Contoso.LookupRegisteredRuntimeClass",
            TypeNameSupport.getNameForType(
                PlainManagedType::class.java,
                setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
            ),
        )
    }

    @Test
    fun type_extensions_and_guid_generator_follow_runtime_registry_contracts() {
        ComWrappersSupport.clearRegistriesForTests()

        Projections.registerCustomAbiTypeMapping(
            publicType = SampleMappedType::class.java,
            helperType = SampleMappedTypeHelper::class.java,
            abiTypeName = "Contoso.IMappedType",
        )
        Projections.registerDefaultInterfaceType(
            runtimeClass = SampleRuntimeClass::class.java,
            defaultInterface = SampleDefaultInterface::class.java,
        )

        assertEquals(SampleAnnotatedHelper::class.java, TypeExtensions.findHelperType(SampleAnnotatedPublicType::class.java))
        assertEquals(SampleMappedTypeHelper::class.java, TypeExtensions.findHelperType(SampleMappedType::class.java))
        assertEquals(Guid("11111111-1111-1111-1111-111111111111"), GuidGenerator.getGuid(SampleDefaultInterface::class.java))
        assertEquals(Guid("22222222-2222-2222-2222-222222222222"), GuidGenerator.getIID(SampleMappedTypeHelper::class.java))
        assertEquals("struct(Contoso.SampleStruct;i4;string)", GuidGenerator.getSignature(SampleStruct::class.java))
        assertEquals(
            "rc(Contoso.SampleRuntimeClass;{11111111-1111-1111-1111-111111111111})",
            GuidGenerator.getSignature(SampleRuntimeClass::class.java),
        )
        assertEquals(
            ParameterizedInterfaceId.createFromSignature("rc(Contoso.SampleRuntimeClass;{11111111-1111-1111-1111-111111111111})"),
            GuidGenerator.createIID(SampleRuntimeClass::class.java),
        )
    }

    @Test
    fun intrinsic_type_classification_is_shared_across_runtime_helpers() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals("Int8", TypeNameSupport.getNameForType(java.lang.Byte::class.java))
        assertEquals(UByte::class.java, TypeNameSupport.findTypeByNameCached("UInt8"))
        assertEquals(java.lang.Byte::class.java, TypeNameSupport.findTypeByNameCached("Int8"))
        assertEquals(Any::class.java, TypeNameSupport.findTypeByNameCached("Object"))
        assertTrue(Projections.isTypeWindowsRuntimeType(java.lang.Character.TYPE))
        assertEquals("i1", GuidGenerator.getSignature(java.lang.Byte::class.java))
        assertEquals("u1", GuidGenerator.getSignature(UByte::class.java))
        assertEquals("cinterface(IInspectable)", GuidGenerator.getSignature(Any::class.java))
    }

    @Test
    fun runtime_117_system_projection_mappings_follow_cswinrt_owner_set() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals(DateTimeProjection::class.java, TypeExtensions.findHelperType(OffsetDateTime::class.java))
        assertEquals(TimeSpanProjection::class.java, TypeExtensions.findHelperType(Duration::class.java))
        assertEquals(UriProjection::class.java, TypeExtensions.findHelperType(URI::class.java))
        assertEquals(IClosableProjection::class.java, TypeExtensions.findHelperType(AutoCloseable::class.java))
        assertEquals("Windows.Foundation.Point", TypeNameSupport.getNameForType(Point::class.java))
        assertEquals(
            "rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})",
            GuidGenerator.getSignature(URI::class.java),
        )
        assertEquals(
            "Windows.Foundation.IReference`1<Int32>",
            TypeNameSupport.getNameForType(Int::class.javaObjectType, setOf(TypeNameGenerationFlag.GenerateBoxedName)),
        )
        assertEquals(
            "Windows.Foundation.IReferenceArray`1<String>",
            TypeNameSupport.getNameForType(Array<String>::class.java, setOf(TypeNameGenerationFlag.GenerateBoxedName)),
        )
    }

    @Test
    fun type_name_support_resolves_boxed_reference_runtime_names_like_cswinrt() {
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.registerProjectionAssembly(TestProjectedEnum::class.java)

        assertEquals(String::class.java, TypeNameSupport.findTypeByNameCached("Windows.Foundation.IReference`1<String>"))
        assertEquals(Class::class.java, TypeNameSupport.findTypeByNameCached("Windows.Foundation.IReference`1<Windows.UI.Xaml.Interop.TypeName>"))
        assertEquals(Exception::class.java, TypeNameSupport.findTypeByNameCached("Windows.Foundation.IReference`1<Windows.Foundation.HResult>"))
        assertEquals(IntArray::class.java, TypeNameSupport.findTypeByNameCached("Windows.Foundation.IReferenceArray`1<Int32>"))
        assertEquals(TestProjectedEnum::class.java, TypeNameSupport.findTypeByNameCached("Windows.Foundation.IReference`1<Contoso.Priority>"))
        assertEquals("Contoso.Priority", TypeNameSupport.getNameForType(TestProjectedEnum::class.java))
    }

    @WinRtGuid("11111111-1111-1111-1111-111111111111")
    private interface SampleDefaultInterface

    @WinRtRuntimeClassName("Contoso.SampleRuntimeClass")
    private class SampleRuntimeClass

    @WinRtGuid("22222222-2222-2222-2222-222222222222")
    private class SampleMappedTypeHelper {
        companion object {
            @JvmField
            val PIID: Guid = Guid("22222222-2222-2222-2222-222222222222")
        }
    }

    private class SampleRuntimeClassHelper

    private class SampleMappedType

    @WindowsRuntimeHelperType(SampleAnnotatedHelper::class)
    private class SampleAnnotatedPublicType

    @WinRtGuid("33333333-3333-3333-3333-333333333333")
    private class SampleAnnotatedHelper

    @WindowsRuntimeType("struct(Contoso.SampleStruct;i4;string)")
    private class SampleStruct

    private class PlainManagedType

    @WindowsRuntimeType("enum(Contoso.Priority;i4)")
    private enum class TestProjectedEnum(
        val abiValue: Int,
    ) {
        Low(0),
        High(2),
    }
}
