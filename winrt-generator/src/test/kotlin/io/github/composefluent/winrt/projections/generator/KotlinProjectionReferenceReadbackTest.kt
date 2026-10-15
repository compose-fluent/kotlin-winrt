package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinProjectionReferenceReadbackTest {
    private fun reference(element: KotlinProjectionAbiTypeBinding) = KotlinProjectionAbiTypeBinding(
        kind = KotlinProjectionAbiValueKind.Reference,
        typeName = "Windows.Foundation.IReference<${element.typeName}>",
        interfaceId = Guid("61C17706-2D65-11E0-9AE8-D48564015472"),
        typeArguments = listOf(element),
    )

    @Test
    fun known_value_outputs_use_typed_unboxing_and_keep_the_outer_release() {
        // CsWinRT 3 WindowsRuntimeValueTypeMarshaller.UnboxToManaged<T> uses
        // the declared T's ABI shape; only erased values need IID lookup.
        val renderer = KotlinProjectionRenderer()
        val binding = reference(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int"))
        val codec = renderer.buildCallSiteRecipe(binding).outputCodecs.values.single()
        val source = codec.body.toString()
        assertTrue(source, source.contains("fromAbi<kotlin.Int>"))
        assertTrue(source, source.contains("sizeBytes = 4"))
        assertTrue(source, source.contains("alignmentBytes = 4"))
        assertTrue(source, source.contains("readInt32(__valueOut)"))
        assertTrue(source, source.contains("finally"))
        assertTrue(source, source.contains("releaseRaw(__abi)"))
        assertFalse(source, source.contains("disposeValue"))
        val callback = renderer.referenceReadbackExpression(binding, WINRT_REFERENCE_PROJECTION_CLASS_NAME, "output").toString()
        assertTrue(callback, callback.contains("fromAbi<kotlin.Int>"))
        assertTrue(callback, callback.contains("readPointer(output)"))
        assertFalse(callback, callback.contains("releaseRaw"))
    }

    @Test
    fun typed_value_layouts_and_cleanup_come_from_existing_storage_recipes() {
        val renderer = KotlinProjectionRenderer()
        fun expression(value: KotlinProjectionAbiTypeBinding): String = requireNotNull(
            renderer.typedReferenceReadbackExpression(reference(value), CodeBlock.of("pointer")),
        ).toString()
        val guid = expression(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.GuidValue, "Guid"))
        assertTrue(guid, guid.contains("alignmentBytes = 4L"))
        assertTrue(guid, guid.contains("readGuid(__valueOut)"))
        val enum = expression(KotlinProjectionAbiTypeBinding(
            KotlinProjectionAbiValueKind.Enum, "Sample.UnsignedEnum", enumUnderlyingType = WinRTIntegralType.UInt32,
        ))
        assertTrue(enum, enum.contains("readInt32(__valueOut).toUInt()"))
        val scalar = expression(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Struct, "Windows.Foundation.TimeSpan"))
        assertTrue(scalar, scalar.contains("sizeBytes = 8L"))
        assertTrue(scalar, scalar.contains("alignmentBytes = 8L"))
        assertFalse(scalar, scalar.contains("disposeValue"))
        val nonBlittable = expression(KotlinProjectionAbiTypeBinding(
            KotlinProjectionAbiValueKind.Struct, "Sample.StringValue", abiSize = 8, abiAlignment = 8,
            structFieldBindings = listOf(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String")),
            structFieldOffsets = listOf(0),
        ))
        assertTrue(nonBlittable, nonBlittable.contains("Metadata.fromAbi(__valueOut)"))
        assertTrue(nonBlittable, nonBlittable.contains("Metadata.disposeAbi(__valueOut)"))
        val typeName = expression(KotlinProjectionAbiTypeBinding(
            KotlinProjectionAbiValueKind.Struct, "Windows.UI.Xaml.Interop.TypeName",
        ))
        assertTrue(typeName, typeName.contains("fromAbi<kotlin.reflect.KClass<*>?>"))
        assertTrue(typeName, typeName.contains("disposeTypeNameAbi(__valueOut)"))
    }

    @Test
    fun unknown_value_shapes_keep_the_dynamic_adapter_path() {
        val renderer = KotlinProjectionRenderer()
        assertNull(renderer.typedReferenceReadbackExpression(
            reference(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.GenericParameter, "T")),
            CodeBlock.of("pointer"),
        ))
        assertNull(renderer.typedReferenceReadbackExpression(
            reference(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Struct, "Windows.Foundation.Point")),
            CodeBlock.of("pointer"),
        ))
        val referenceArray = KotlinProjectionAbiTypeBinding(
            KotlinProjectionAbiValueKind.ReferenceArray, "Windows.Foundation.IReferenceArray<Int>",
            interfaceId = Guid("61C17707-2D65-11E0-9AE8-D48564015472"),
            typeArguments = listOf(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int")),
        )
        assertNull(renderer.typedReferenceReadbackExpression(referenceArray, CodeBlock.of("pointer")))
        assertNotNull(renderer.referenceReadbackExpression(referenceArray, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME, "output"))
    }
}
