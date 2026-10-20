package io.github.composefluent.windows.toolkit.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import kotlin.metadata.*
import kotlin.metadata.jvm.*
import kotlinx.serialization.json.*

class ProjectionAbiInventoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun binary_fingerprint_is_ordered_and_independent_from_producer_release_version() {
        val file = temporary.newFile()
        fun fingerprint(version: String, declarations: Map<String, String>, compiler: String = "2.4.0"): String {
            writeProjectionBinaryContract(file, "jvm", compiler, "sample:sdk", version, declarations)
            return Json.parseToJsonElement(file.readText()).jsonObject.getValue("fingerprint").jsonPrimitive.content
        }
        val original = fingerprint("1.0", linkedMapOf("B.class" to "b", "A.class" to "a"))
        assertEquals(original, fingerprint("2.0", linkedMapOf("A.class" to "a", "B.class" to "b")))
        assertNotEquals(original, fingerprint("1.0", mapOf("A.class" to "changed", "B.class" to "b")))
        assertNotEquals(original, fingerprint("1.0", mapOf("A.class" to "a", "B.class" to "b"), "2.4.20"))
    }

    @Test fun java_overlay_bodies_are_not_part_of_the_compiled_contract() {
        fun inventory(value: Int, methodName: String): Map<String, String> {
            val root = temporary.newFolder()
            val writer = ClassWriter(0)
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "JavaOverlay", null, "java/lang/Object", null)
            writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, methodName, "()I", null, null).apply {
                visitCode(); visitLdcInsn(value); visitInsn(Opcodes.IRETURN); visitMaxs(1, 0); visitEnd()
            }
            writer.visitEnd()
            root.resolve("JavaOverlay.class").writeBytes(writer.toByteArray())
            return projectionAbiInventory(root)
        }
        assertEquals(inventory(1, "value"), inventory(200, "value"))
        assertNotEquals(inventory(1, "value"), inventory(1, "renamed"))
    }

    @OptIn(UnstableMetadataApi::class)
    @Test fun omits_empty_file_facades_but_never_type_alias_declarations() {
        fun inventory(alias: Boolean): Map<String, String> {
            val root = temporary.newFolder()
            val declarations = KmPackage().apply {
                if (alias) typeAliases += KmTypeAlias("Name").apply {
                    underlyingType = KmType().apply { classifier = KmClassifier.Class("kotlin/String") }
                    expandedType = KmType().apply { classifier = KmClassifier.Class("kotlin/String") }
                    visibility = Visibility.PUBLIC
                }
            }
            val header = KotlinClassMetadata.FileFacade(declarations, JvmMetadataVersion.LATEST_STABLE_SUPPORTED, 0).write()
            val writer = ClassWriter(0)
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "FixtureKt", null, "java/lang/Object", null)
            writer.visitAnnotation("Lkotlin/Metadata;", true).apply {
                visit("k", header.kind)
                visit("mv", header.metadataVersion)
                visitArray("d1").apply { header.data1.forEach { visit(null, it) }; visitEnd() }
                visitArray("d2").apply { header.data2.forEach { visit(null, it) }; visitEnd() }
                visitEnd()
            }
            writer.visitEnd()
            root.resolve("FixtureKt.class").writeBytes(writer.toByteArray())
            val module = KmModule().apply { packageParts[""] = KmPackageParts(mutableListOf("FixtureKt"), mutableMapOf()) }
            root.resolve("fixture.kotlin_module").writeBytes(KotlinModuleMetadata(module, JvmMetadataVersion.LATEST_STABLE_SUPPORTED).write())
            return projectionAbiInventory(root)
        }
        assertEquals(setOf("fixture.kotlin_module"), inventory(false).keys)
        assertEquals(setOf("FixtureKt.class", "fixture.kotlin_module"), inventory(true).keys)
    }

    @Test fun ignores_source_locations_but_preserves_inline_code_and_kotlin_metadata() {
        // These are already ABI-producer outputs. Ordinary bodies have been erased there;
        // bodies still present here (inline/PublishedApi) must participate in compatibility.
        fun inventory(line: Int, constant: Int, metadata: String): Map<String, String> {
            val root = temporary.newFolder()
            val writer = ClassWriter(0)
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null)
            writer.visitSource("Fixture$line.kt", null)
            writer.visitAnnotation("Lkotlin/Metadata;", true).apply {
                visitArray("d2").apply { visit(null, metadata); visitEnd() }
                visitEnd()
            }
            writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "inlined", "()I", null, null).apply {
                visitCode()
                val label = Label()
                visitLabel(label)
                visitLineNumber(line, label)
                visitLdcInsn(constant)
                visitInsn(Opcodes.IRETURN)
                visitMaxs(1, 0)
                visitEnd()
            }
            writer.visitEnd()
            root.resolve("Fixture.class").writeBytes(writer.toByteArray())
            return projectionAbiInventory(root)
        }
        val original = inventory(1, 42, "name")
        assertEquals(original, inventory(100, 42, "name"))
        assertNotEquals(original, inventory(1, 43, "name"))
        assertNotEquals(original, inventory(1, 42, "renamed"))
    }
}
