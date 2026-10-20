@file:OptIn(kotlin.metadata.jvm.UnstableMetadataApi::class)

package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.KotlinModuleMetadata
import java.io.File
import java.security.MessageDigest

/** Compare Kotlin's ABI-generator outputs, including metadata and retained inline bytecode. */
@CacheableTask
abstract class VerifyWinRTReferenceAbiTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val implementationAbi: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceAbi: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val implementation = projectionAbiInventory(implementationAbi.get().asFile)
        val reference = projectionAbiInventory(referenceAbi.get().asFile)
        val differences = (implementation.keys + reference.keys).sorted().filter {
            implementation[it] != reference[it]
        }
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(differences.joinToString("\n", postfix = "\n"))
        if (differences.isNotEmpty()) throw GradleException(
            "WinRT reference and implementation ABI differ in ${differences.size} files. " +
                "See $report. First differences: ${differences.take(8).joinToString()}",
        )
    }
}

internal fun projectionAbiInventory(directory: File): Map<String, String> {
    val files = directory.walkTopDown().filter {
        it.isFile && (it.extension == "class" || it.extension == "kotlin_module")
    }.toList()
    // JVM lambda lowering can leave an empty file facade after ABI stripping. It has no
    // source declaration, but the compiler still lists it in the module's package index.
    val emptyFacades = files.filter { it.extension == "class" && isEmptyKotlinFileFacade(it.readBytes()) }
        .mapTo(mutableSetOf()) { it.relativeTo(directory).invariantSeparatorsPath.removeSuffix(".class") }
    return files.filterNot { it.relativeTo(directory).invariantSeparatorsPath.removeSuffix(".class") in emptyFacades }
        .associate { file ->
            val bytes = if (file.extension == "class") {
                val writer = ClassWriter(0)
                val reader = ClassReader(file.readBytes())
                var kotlinMetadata = false
                reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                        if (descriptor == "Lkotlin/Metadata;") kotlinMetadata = true
                        return null
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                // Debug locations refer to implementation source bodies and are not ABI.
                // Java/ASM overlays have no Kotlin inline body contract. Their structural
                // byte stream is hashed only, never loaded as an executable class.
                reader.accept(writer, ClassReader.SKIP_DEBUG or if (kotlinMetadata) 0 else ClassReader.SKIP_CODE)
                writer.toByteArray()
            } else KotlinModuleMetadata.read(file.readBytes()).apply {
                kmModule.packageParts.values.forEach { parts -> parts.fileFacades.removeAll(emptyFacades) }
                kmModule.packageParts.entries.removeAll { (_, parts) ->
                    parts.fileFacades.isEmpty() && parts.multiFileClassParts.isEmpty()
                }
            }.write()
            file.relativeTo(directory).invariantSeparatorsPath to
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
}

private fun isEmptyKotlinFileFacade(bytes: ByteArray): Boolean {
    var members = 0
    var kind = 0
    var version = intArrayOf()
    var extraInt = 0
    var extraString = ""
    var packageName = ""
    val data1 = mutableListOf<String>()
    val data2 = mutableListOf<String>()
    ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            members++
            return null
        }
        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            members++
            return null
        }
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (descriptor != "Lkotlin/Metadata;") return null
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visit(name: String, value: Any) {
                    when (name) {
                        "k" -> kind = value as Int
                        "mv" -> version = value as IntArray
                        "xi" -> extraInt = value as Int
                        "xs" -> extraString = value as String
                        "pn" -> packageName = value as String
                    }
                }
                override fun visitArray(name: String): AnnotationVisitor? {
                    val values = when (name) { "d1" -> data1; "d2" -> data2; else -> return null }
                    return object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visit(name: String?, value: Any) { values += value as String }
                    }
                }
            }
        }
    }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    if (kind != 2 || members != 0) return false
    val metadata = KotlinClassMetadata.readStrict(Metadata(kind = kind, metadataVersion = version,
        data1 = data1.toTypedArray(), data2 = data2.toTypedArray(), extraString = extraString,
        packageName = packageName, extraInt = extraInt)) as KotlinClassMetadata.FileFacade
    return metadata.kmPackage.let { it.functions.isEmpty() && it.properties.isEmpty() && it.typeAliases.isEmpty() }
}
