package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.*
import io.github.composefluent.winrt.metadata.*
import org.junit.Assert.*
import org.junit.Test

class KotlinProjectionReferenceDeclarationsTest {
    @Test fun constructor_promotion_preserves_keyword_parameter_initializers() {
        val type = TypeSpec.classBuilder("Entry").addModifiers(KModifier.DATA)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("value", INT).build())
            .addProperty(PropertySpec.builder("value", INT).initializer("value").build()).build()
        val reference = FileSpec.builder("probe", "Entry").addType(type).build().referenceDeclarations().toString()
        assertTrue(reference, reference.contains("val `value`: Int"))
        assertFalse(reference, reference.contains("WinRT reference projection"))
    }

    @Test fun reference_uses_the_same_signatures_and_preserves_inline_defaults_and_constructor_properties() {
        val type = TypeSpec.classBuilder("Value").addModifiers(KModifier.DATA)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter(
                ParameterSpec.builder("number", INT).defaultValue("42").build()).build())
            .addProperty(PropertySpec.builder("number", INT).initializer("number").build())
            .addFunction(FunSpec.builder("normal").returns(INT).addStatement("return number + 17").build())
            .addFunction(FunSpec.builder("inlined").addModifiers(KModifier.INLINE).returns(INT)
                .addStatement("return normal() + 1").build())
            .addInitializerBlock(CodeBlock.of("println(%S)", "implementation initializer"))
            .build()
        val reference = FileSpec.builder("probe", "Value").addType(type).build().referenceDeclarations().toString()
        assertTrue(reference, reference.contains("val number: Int = 42"))
        assertTrue(reference, reference.contains("normal() + 1"))
        assertFalse(reference, reference.contains("number + 17"))
        assertFalse(reference, reference.contains("implementation initializer"))
    }

    @Test fun real_struct_declarations_keep_their_shape_without_runtime_method_bodies() {
        val model = WinRTMetadataModel(listOf(WinRTNamespace("ReferenceFixture", listOf(
            WinRTTypeDefinition(namespace = "ReferenceFixture", name = "Point", kind = WinRTTypeKind.Struct,
                fields = listOf(WinRTFieldDefinition("X", "Int32"), WinRTFieldDefinition("Y", "Int32"))),
        ))))
        val implementation = KotlinProjectionGenerator().generate(model).first { it.relativePath.endsWith("/Point.kt") }
        val reference = KotlinProjectionGenerator(referenceProjection = true).generate(model)
            .first { it.relativePath.endsWith("/Point.kt") }
        assertTrue(reference.contents, reference.contents.contains("public class Point("))
        assertTrue(implementation.contents, implementation.contents.contains("public class Point("))
        assertFalse(reference.contents, reference.contents.contains("import java."))
        assertTrue(reference.contents, reference.contents.contains("WinRT reference projection"))
        assertNotEquals(implementation.contents, reference.contents)
        assertEquals(implementation.relativePath, reference.relativePath)
    }
}
