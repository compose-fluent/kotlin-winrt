package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteDescriptor
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteOwnership
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterRole
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultStrategy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteValueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinProjectionCallSiteDescriptorsTest {
    @Test
    fun simple_runtime_owned_getter_has_a_complete_canonical_descriptor() {
        val descriptor = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple("getInt32")
            .canonicalCallSiteDescriptorOrNull()

        assertEquals(WinRTProjectionCallSiteResultStrategy.SCALAR_OUT, descriptor?.resultStrategy)
        assertEquals(WinRTProjectionCallSiteValueKind.INT32, descriptor?.result?.kind)
        assertEquals(WinRTProjectionCallSiteHResultPolicy.CHECK, descriptor?.hResultPolicy)
        assertEquals(descriptor, WinRTProjectionCallSiteDescriptor.parse(requireNotNull(descriptor).encode()))
    }

    @Test
    fun no_exception_getter_and_hstring_ownership_remain_distinct_merge_keys() {
        val checked = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple("getBoolean")
            .canonicalCallSiteDescriptorOrNull()
        val unchecked = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple("getNoExceptionBoolean")
            .canonicalCallSiteDescriptorOrNull()
        val string = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple("getString")
            .canonicalCallSiteDescriptorOrNull()

        assertEquals(WinRTProjectionCallSiteHResultPolicy.CHECK, checked?.hResultPolicy)
        assertEquals(WinRTProjectionCallSiteHResultPolicy.IGNORE, unchecked?.hResultPolicy)
        assertEquals(WinRTProjectionCallSiteResultStrategy.STRING_OUT, string?.resultStrategy)
        assertEquals(WinRTProjectionCallSiteOwnership.OWNED, string?.result?.ownership)
    }

    @Test
    fun module_descriptor_arguments_share_the_same_model_as_simple_calls() {
        val simple = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple("setString")
            .canonicalCallSiteDescriptorOrNull()
        val descriptor = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.DescriptorUnit(listOf("String"))
            .canonicalCallSiteDescriptorOrNull()

        assertEquals(simple, descriptor)
        assertEquals(WinRTProjectionCallSiteOwnership.BORROWED, descriptor?.parameters?.single()?.value?.ownership)
    }

    @Test
    fun concrete_struct_helpers_keep_projected_type_in_the_merge_key() {
        val first = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct(
            typeName = ClassName("sample", "FirstStruct"),
            sizeBytes = 8,
            alignmentBytes = 4,
        )
        val second = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct(
            typeName = ClassName("sample", "SecondStruct"),
            sizeBytes = 8,
            alignmentBytes = 4,
        )
        val firstGetter = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.StructGetter(first)
        val secondGetter = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.StructGetter(second)

        assertNotEquals(firstGetter, secondGetter)
        assertNotEquals(firstGetter.functionName, secondGetter.functionName)

        val descriptor = requireNotNull(firstGetter.canonicalCallSiteDescriptorOrNull())
        assertEquals(WinRTProjectionCallSiteResultStrategy.STRUCT_OUT, descriptor.resultStrategy)
        assertEquals(WinRTProjectionCallSiteValueKind.STRUCT, descriptor.result.kind)
        assertEquals(8, descriptor.result.sizeBytes)
        assertEquals(4, descriptor.result.alignmentBytes)
        assertEquals(WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER, descriptor.parameters.single().role)
        assertTrue(descriptor.encode().contains("STRUCT~OWNED~0~8~4"))
    }

    @Test
    fun rendered_struct_helpers_use_concrete_types_for_get_set_and_call() {
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val first = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct(
            typeName = ClassName("sample", "FirstStruct"),
            sizeBytes = 8,
            alignmentBytes = 4,
        )
        val second = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct(
            typeName = ClassName("sample", "SecondStruct"),
            sizeBytes = 8,
            alignmentBytes = 4,
        )
        support.structGetter("instance", CodeBlock.of("slot"), first, CodeBlock.of("FirstStruct.Metadata"))
        support.structGetter("instance", CodeBlock.of("slot"), second, CodeBlock.of("SecondStruct.Metadata"))
        support.structSetter(
            "instance",
            CodeBlock.of("slot"),
            first,
            CodeBlock.of("value"),
            CodeBlock.of("FirstStruct.Metadata"),
        )
        support.descriptorStruct(
            "instance",
            CodeBlock.of("slot"),
            first,
            CodeBlock.of("FirstStruct.Metadata"),
            listOf(DescriptorIntrinsicArgument("Int32", listOf(CodeBlock.of("value")))),
        )

        val contents = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).single().contents

        assertFalse(contents.contains("fun <T>"))
        assertTrue(contents.contains("getStruct_sample_FirstStruct_8_4"))
        assertTrue(contents.contains("getStruct_sample_SecondStruct_8_4"))
        assertTrue(contents.contains("setStruct_sample_FirstStruct_8_4"))
        assertTrue(contents.contains("callStruct_sample_FirstStruct_8_4_Int32"))
        assertTrue(contents.contains("adapter: NativeStructAdapter<FirstStruct>"))
        assertTrue(contents.contains("adapter: NativeStructAdapter<SecondStruct>"))
        assertTrue(contents.contains("`value`: FirstStruct"))
        assertTrue(contents.contains("STRUCT~OWNED~0~8~4"))
    }

    @Test
    fun annotated_runtime_catalog_preserves_the_decided_fourteen_entry_set() {
        val expectedNames = setOf(
            "getString",
            "getBoolean",
            "getNoExceptionBoolean",
            "getInt32",
            "getUInt32",
            "getInt64",
            "getUInt64",
            "getFloat",
            "getDouble",
            "setString",
            "setInt32",
            "setUInt32",
            "setInt64",
            "setUInt64",
        )
        val declarations = KotlinRuntimeOwnedProjectionCallSites.catalog.declarations

        assertEquals(expectedNames, declarations.map { declaration -> declaration.functionName }.toSet())
        assertEquals(expectedNames.size, declarations.size)
        declarations.forEach { declaration ->
            val call = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple(declaration.functionName)
            assertEquals(
                declaration.descriptor,
                call.canonicalCallSiteDescriptorOrNull(),
            )
            assertEquals(
                declaration,
                KotlinRuntimeOwnedProjectionCallSites.declarationFor(call),
            )
        }
    }

    @Test
    fun unannotated_simple_intrinsics_are_not_runtime_owned_call_sites() {
        listOf("setBoolean", "setFloat", "setDouble").forEach { functionName ->
            val call = KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple(functionName)
            assertNull(KotlinRuntimeOwnedProjectionCallSites.declarationFor(call))
        }
    }
}
