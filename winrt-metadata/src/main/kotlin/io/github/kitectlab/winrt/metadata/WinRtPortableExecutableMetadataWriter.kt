package io.github.kitectlab.winrt.metadata

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object WinRtPortableExecutableMetadataWriter {
    fun writeEmptyWinmd(
        assemblyName: String,
        outputFile: Path,
    ) {
        Files.createDirectories(outputFile.parent)
        Files.write(outputFile, WinmdBuilder(assemblyName, emptyList()).build())
    }

    fun writeAuthoredWinmd(
        assemblyName: String,
        runtimeClasses: List<WinRtAuthoredRuntimeClassDescriptor>,
        outputFile: Path,
    ) {
        Files.createDirectories(outputFile.parent)
        Files.write(outputFile, WinmdBuilder(assemblyName, runtimeClasses).build())
    }
}

private class WinmdBuilder(
    private val assemblyName: String,
    private val runtimeClasses: List<WinRtAuthoredRuntimeClassDescriptor>,
) {
    private val strings = IndexedStringHeap()
    private val blobs = IndexedBlobHeap()
    private val guid = byteArrayOf(
        0x10, 0x32, 0x54, 0x76, 0x98.toByte(), 0xba.toByte(), 0xdc.toByte(), 0xfe.toByte(),
        0x10, 0x32, 0x54, 0x76, 0x98.toByte(), 0xba.toByte(), 0xdc.toByte(), 0xfe.toByte(),
    )

    fun build(): ByteArray {
        val moduleName = strings.index("$assemblyName.winmd")
        val moduleTypeName = strings.index("<Module>")
        val assemblyNameIndex = strings.index(assemblyName)
        val attributeTypeNames = buildSet {
            if (runtimeClasses.isNotEmpty()) {
                add(WINDOWS_FOUNDATION_METADATA_VERSION)
            }
            runtimeClasses.forEach { descriptor ->
                if (descriptor.isActivatable) {
                    add(WINDOWS_FOUNDATION_METADATA_ACTIVATABLE)
                }
                if (descriptor.interfaceNames.isNotEmpty()) {
                    add(WINDOWS_FOUNDATION_METADATA_DEFAULT)
                }
                if (descriptor.overridableInterfaceNames.isNotEmpty()) {
                    add(WINDOWS_FOUNDATION_METADATA_OVERRIDABLE)
                }
            }
        }
        val baseTypeRefs = runtimeClasses
            .flatMap { descriptor ->
                listOf(descriptor.baseRuntimeClassName ?: "System.Object") + descriptor.interfaceNames
            }
            .plus(attributeTypeNames)
            .distinct()
            .map { qualifiedName -> TypeRefRow(qualifiedName) }
        val metadataRoot = metadataRoot(
            tables = tablesStream(moduleName, moduleTypeName, assemblyNameIndex, baseTypeRefs),
            strings = strings.bytes(),
            guid = guid,
            blob = blobs.bytes(),
        )
        val cliHeaderRva = SECTION_RVA
        val metadataRva = SECTION_RVA + CLI_HEADER_SIZE
        val sectionData = BinaryWriter().apply {
            int32(CLI_HEADER_SIZE)
            int16(2)
            int16(5)
            int32(metadataRva)
            int32(metadataRoot.size)
            int32(1)
            int32(0)
            repeat((CLI_HEADER_SIZE - size)) { int8(0) }
            bytes(metadataRoot)
        }.toByteArray()
        val sectionRawSize = align(sectionData.size, FILE_ALIGNMENT)
        val image = BinaryWriter()
        writeDosHeader(image)
        writePeHeaders(image, sectionData.size, sectionRawSize)
        image.padTo(SIZE_OF_HEADERS)
        image.bytes(sectionData)
        image.padTo(SIZE_OF_HEADERS + sectionRawSize)
        return image.toByteArray()
    }

    private fun tablesStream(
        moduleName: Int,
        moduleTypeName: Int,
        assemblyNameIndex: Int,
        typeRefs: List<TypeRefRow>,
    ): ByteArray {
        val writer = BinaryWriter()
        val attributeMemberRefs = attributeMemberRefs(typeRefs)
        val customAttributes = typeDefCustomAttributes(attributeMemberRefs) +
            interfaceImplCustomAttributes(attributeMemberRefs)
        val validMask = (1L shl TABLE_MODULE) or
            (if (typeRefs.isEmpty()) 0L else 1L shl TABLE_TYPE_REF) or
            (1L shl TABLE_TYPE_DEF) or
            (if (runtimeClasses.any { it.interfaceNames.isNotEmpty() }) 1L shl TABLE_INTERFACE_IMPL else 0L) or
            (if (attributeMemberRefs.isEmpty()) 0L else 1L shl TABLE_MEMBER_REF) or
            (if (customAttributes.isEmpty()) 0L else 1L shl TABLE_CUSTOM_ATTRIBUTE) or
            (1L shl TABLE_ASSEMBLY)
        writer.int32(0)
        writer.int8(2)
        writer.int8(0)
        writer.int8(0)
        writer.int8(1)
        writer.int64(validMask)
        writer.int64(validMask)
        writer.int32(1)
        if (typeRefs.isNotEmpty()) {
            writer.int32(typeRefs.size)
        }
        writer.int32(1 + runtimeClasses.size)
        if (runtimeClasses.any { it.interfaceNames.isNotEmpty() }) {
            writer.int32(runtimeClasses.sumOf { it.interfaceNames.size })
        }
        if (attributeMemberRefs.isNotEmpty()) {
            writer.int32(attributeMemberRefs.size)
        }
        if (customAttributes.isNotEmpty()) {
            writer.int32(customAttributes.size)
        }
        writer.int32(1)
        writer.int16(0)
        writer.index(moduleName)
        writer.index(1)
        writer.index(0)
        writer.index(0)
        typeRefs.forEach { typeRef ->
            writer.index(0)
            writer.index(strings.index(typeRef.name))
            writer.index(strings.index(typeRef.namespace))
        }
        writer.int32(0)
        writer.index(moduleTypeName)
        writer.index(0)
        writer.index(0)
        writer.index(1)
        writer.index(1)
        runtimeClasses.forEach { descriptor ->
            val namespace = descriptor.runtimeClassName.substringBeforeLast('.', missingDelimiterValue = "")
            val name = descriptor.runtimeClassName.substringAfterLast('.')
            val baseTypeName = descriptor.baseRuntimeClassName ?: "System.Object"
            val baseTypeRefRowId = typeRefs.indexOfFirst { typeRef -> typeRef.qualifiedName == baseTypeName } + 1
            writer.int32(TYPE_ATTRIBUTES_PUBLIC or TYPE_ATTRIBUTES_WINDOWS_RUNTIME or TYPE_ATTRIBUTES_BEFORE_FIELD_INIT or if (descriptor.isSealed) TYPE_ATTRIBUTES_SEALED else 0)
            writer.index(strings.index(name))
            writer.index(strings.index(namespace))
            writer.index((baseTypeRefRowId shl 2) or CODED_TYPE_DEF_OR_REF_TYPE_REF)
            writer.index(1)
            writer.index(1)
        }
        runtimeClasses.forEachIndexed { classIndex, descriptor ->
            descriptor.interfaceNames.forEach { interfaceName ->
                val interfaceTypeRefRowId = typeRefs.indexOfFirst { typeRef -> typeRef.qualifiedName == interfaceName } + 1
                writer.index(classIndex + 2)
                writer.index((interfaceTypeRefRowId shl 2) or CODED_TYPE_DEF_OR_REF_TYPE_REF)
            }
        }
        attributeMemberRefs.forEach { memberRef ->
            writer.index((memberRef.typeRefRowId shl CODED_MEMBER_REF_PARENT_TAG_BITS) or CODED_MEMBER_REF_PARENT_TYPE_REF)
            writer.index(strings.index(".ctor"))
            writer.index(memberRef.signatureBlobIndex)
        }
        customAttributes.forEach { attribute ->
            writer.index(attribute.parentToken)
            writer.index((attribute.memberRefRowId shl CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS) or CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF)
            writer.index(attribute.valueBlobIndex)
        }
        writer.int32(0x00008004)
        writer.int16(1)
        writer.int16(0)
        writer.int16(0)
        writer.int16(0)
        writer.int32(0x00000200)
        writer.index(0)
        writer.index(assemblyNameIndex)
        writer.index(0)
        return writer.toByteArray()
    }

    private fun attributeMemberRefs(typeRefs: List<TypeRefRow>): List<AttributeMemberRefRow> =
        listOf(
            WINDOWS_FOUNDATION_METADATA_DEFAULT to emptyList<Int>(),
            WINDOWS_FOUNDATION_METADATA_OVERRIDABLE to emptyList(),
            WINDOWS_FOUNDATION_METADATA_ACTIVATABLE to listOf(ELEMENT_TYPE_U4),
            WINDOWS_FOUNDATION_METADATA_VERSION to listOf(ELEMENT_TYPE_U4),
        )
            .mapNotNull { typeName ->
                val typeRefRowId = typeRefs.indexOfFirst { typeRef -> typeRef.qualifiedName == typeName.first } + 1
                typeRefRowId.takeIf { it > 0 }?.let { rowId ->
                    AttributeMemberRefRow(
                        attributeTypeName = typeName.first,
                        typeRefRowId = rowId,
                        signatureBlobIndex = blobs.index(methodSignatureBlob(typeName.second)),
                    )
                }
            }

    private fun interfaceImplCustomAttributes(memberRefs: List<AttributeMemberRefRow>): List<CustomAttributeRow> {
        val memberRefRowIds = memberRefs
            .mapIndexed { index, memberRef -> memberRef.attributeTypeName to index + 1 }
            .toMap()
        val rows = mutableListOf<CustomAttributeRow>()
        val emptyAttributeBlobIndex = blobs.index(emptyCustomAttributeBlob())
        var interfaceImplRowId = 1
        runtimeClasses.forEach { descriptor ->
            descriptor.interfaceNames.forEach { interfaceName ->
                if (interfaceName == descriptor.interfaceNames.first()) {
                    memberRefRowIds[WINDOWS_FOUNDATION_METADATA_DEFAULT]?.let { memberRefRowId ->
                        rows += CustomAttributeRow(
                            parentToken = hasCustomAttributeToken(
                                rowId = interfaceImplRowId,
                                tag = CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL,
                            ),
                            memberRefRowId = memberRefRowId,
                            valueBlobIndex = emptyAttributeBlobIndex,
                        )
                    }
                }
                if (interfaceName in descriptor.overridableInterfaceNames) {
                    memberRefRowIds[WINDOWS_FOUNDATION_METADATA_OVERRIDABLE]?.let { memberRefRowId ->
                        rows += CustomAttributeRow(
                            parentToken = hasCustomAttributeToken(
                                rowId = interfaceImplRowId,
                                tag = CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL,
                            ),
                            memberRefRowId = memberRefRowId,
                            valueBlobIndex = emptyAttributeBlobIndex,
                        )
                    }
                }
                interfaceImplRowId += 1
            }
        }
        return rows
    }

    private fun typeDefCustomAttributes(memberRefs: List<AttributeMemberRefRow>): List<CustomAttributeRow> {
        val memberRefRowIds = memberRefs
            .mapIndexed { index, memberRef -> memberRef.attributeTypeName to index + 1 }
            .toMap()
        val rows = mutableListOf<CustomAttributeRow>()
        runtimeClasses.forEachIndexed { index, descriptor ->
            val typeDefRowId = index + 2
            memberRefRowIds[WINDOWS_FOUNDATION_METADATA_VERSION]?.let { memberRefRowId ->
                rows += CustomAttributeRow(
                    parentToken = hasCustomAttributeToken(
                        rowId = typeDefRowId,
                        tag = CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF,
                    ),
                    memberRefRowId = memberRefRowId,
                    valueBlobIndex = blobs.index(uint32CustomAttributeBlob(DEFAULT_VERSION)),
                )
            }
            if (descriptor.isActivatable) {
                memberRefRowIds[WINDOWS_FOUNDATION_METADATA_ACTIVATABLE]?.let { memberRefRowId ->
                    rows += CustomAttributeRow(
                        parentToken = hasCustomAttributeToken(
                            rowId = typeDefRowId,
                            tag = CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF,
                        ),
                        memberRefRowId = memberRefRowId,
                        valueBlobIndex = blobs.index(uint32CustomAttributeBlob(DEFAULT_VERSION)),
                    )
                }
            }
        }
        return rows
    }

    private fun methodSignatureBlob(parameterElementTypes: List<Int>): ByteArray =
        byteArrayOf(
            CALL_CONV_HASTHIS.toByte(),
            parameterElementTypes.size.toByte(),
            ELEMENT_TYPE_VOID.toByte(),
            *parameterElementTypes.map(Int::toByte).toByteArray(),
        )

    private fun hasCustomAttributeToken(rowId: Int, tag: Int): Int =
        (rowId shl CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS) or tag

    private fun emptyCustomAttributeBlob(): ByteArray =
        byteArrayOf(
            0x01,
            0x00,
            0x00,
            0x00,
        )

    private fun uint32CustomAttributeBlob(value: Int): ByteArray =
        byteArrayOf(
            0x01,
            0x00,
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte(),
            0x00,
            0x00,
        )

    private fun metadataRoot(
        tables: ByteArray,
        strings: ByteArray,
        guid: ByteArray,
        blob: ByteArray,
    ): ByteArray {
        val version = "WindowsRuntime 1.4\u0000".toByteArray(StandardCharsets.UTF_8)
        val rootHeaderSize = 16 + align(version.size, 4) + 2 + 2
        val streamHeaders = listOf(
            StreamPart("#~", tables),
            StreamPart("#Strings", strings),
            StreamPart("#GUID", guid),
            StreamPart("#Blob", blob),
        )
        val streamHeaderSize = streamHeaders.sumOf { 8 + align(it.name.length + 1, 4) }
        var offset = align(rootHeaderSize + streamHeaderSize, 4)
        val writer = BinaryWriter()
        writer.int32(0x424A5342)
        writer.int16(1)
        writer.int16(1)
        writer.int32(0)
        writer.int32(version.size)
        writer.bytes(version)
        writer.padTo(16 + align(version.size, 4))
        writer.int16(0)
        writer.int16(streamHeaders.size)
        streamHeaders.forEach { stream ->
            writer.int32(offset)
            writer.int32(stream.bytes.size)
            writer.paddedAscii(stream.name)
            offset += align(stream.bytes.size, 4)
        }
        writer.padTo(align(rootHeaderSize + streamHeaderSize, 4))
        streamHeaders.forEach { stream ->
            writer.bytes(stream.bytes)
            writer.padTo(align(writer.size, 4))
        }
        return writer.toByteArray()
    }

    private fun writeDosHeader(writer: BinaryWriter) {
        writer.int16(0x5A4D)
        writer.padTo(0x3C)
        writer.int32(PE_HEADER_OFFSET)
        writer.padTo(PE_HEADER_OFFSET)
    }

    private fun writePeHeaders(
        writer: BinaryWriter,
        sectionDataSize: Int,
        sectionRawSize: Int,
    ) {
        writer.int32(0x00004550)
        writer.int16(0x014C)
        writer.int16(1)
        writer.int32(0)
        writer.int32(0)
        writer.int32(0)
        writer.int16(0x00E0)
        writer.int16(0x2102)
        writer.int16(0x010B)
        writer.int8(8)
        writer.int8(0)
        writer.int32(sectionRawSize)
        writer.int32(0)
        writer.int32(0)
        writer.int32(0)
        writer.int32(SECTION_RVA)
        writer.int32(SECTION_RVA)
        writer.int32(0x00400000)
        writer.int32(SECTION_ALIGNMENT)
        writer.int32(FILE_ALIGNMENT)
        writer.int16(4)
        writer.int16(0)
        writer.int16(0)
        writer.int16(0)
        writer.int16(4)
        writer.int16(0)
        writer.int32(0)
        writer.int32(align(SECTION_RVA + sectionDataSize, SECTION_ALIGNMENT))
        writer.int32(SIZE_OF_HEADERS)
        writer.int32(0)
        writer.int16(2)
        writer.int16(0)
        writer.int32(0x100000)
        writer.int32(0x1000)
        writer.int32(0x100000)
        writer.int32(0x1000)
        writer.int32(0)
        writer.int32(16)
        repeat(14) {
            writer.int32(0)
            writer.int32(0)
        }
        writer.int32(SECTION_RVA)
        writer.int32(CLI_HEADER_SIZE)
        writer.int32(0)
        writer.int32(0)
        writer.ascii(".text")
        writer.padTo(writer.size + (8 - ".text".length))
        writer.int32(sectionDataSize)
        writer.int32(SECTION_RVA)
        writer.int32(sectionRawSize)
        writer.int32(SIZE_OF_HEADERS)
        writer.int32(0)
        writer.int32(0)
        writer.int16(0)
        writer.int16(0)
        writer.int32(0x60000020)
    }

    private data class StreamPart(val name: String, val bytes: ByteArray)
    private data class TypeRefRow(val qualifiedName: String) {
        val namespace: String = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
        val name: String = qualifiedName.substringAfterLast('.')
    }
    private data class AttributeMemberRefRow(
        val attributeTypeName: String,
        val typeRefRowId: Int,
        val signatureBlobIndex: Int,
    )
    private data class CustomAttributeRow(
        val parentToken: Int,
        val memberRefRowId: Int,
        val valueBlobIndex: Int,
    )

    private companion object {
        const val PE_HEADER_OFFSET = 0x80
        const val FILE_ALIGNMENT = 0x200
        const val SECTION_ALIGNMENT = 0x2000
        const val SIZE_OF_HEADERS = 0x200
        const val SECTION_RVA = 0x2000
        const val CLI_HEADER_SIZE = 72
        const val TABLE_MODULE = 0
        const val TABLE_TYPE_REF = 1
        const val TABLE_TYPE_DEF = 2
        const val TABLE_INTERFACE_IMPL = 9
        const val TABLE_MEMBER_REF = 10
        const val TABLE_CUSTOM_ATTRIBUTE = 12
        const val TABLE_ASSEMBLY = 0x20
        const val CODED_TYPE_DEF_OR_REF_TYPE_REF = 1
        const val CODED_MEMBER_REF_PARENT_TYPE_REF = 1
        const val CODED_MEMBER_REF_PARENT_TAG_BITS = 3
        const val CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF = 3
        const val CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS = 3
        const val CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF = 3
        const val CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL = 5
        const val CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS = 5
        const val CALL_CONV_HASTHIS = 0x20
        const val ELEMENT_TYPE_VOID = 0x01
        const val ELEMENT_TYPE_U4 = 0x09
        const val DEFAULT_VERSION = 1
        const val TYPE_ATTRIBUTES_PUBLIC = 0x00000001
        const val TYPE_ATTRIBUTES_WINDOWS_RUNTIME = 0x00004000
        const val TYPE_ATTRIBUTES_SEALED = 0x00000100
        const val TYPE_ATTRIBUTES_BEFORE_FIELD_INIT = 0x00100000
        const val WINDOWS_FOUNDATION_METADATA_DEFAULT = "Windows.Foundation.Metadata.DefaultAttribute"
        const val WINDOWS_FOUNDATION_METADATA_OVERRIDABLE = "Windows.Foundation.Metadata.OverridableAttribute"
        const val WINDOWS_FOUNDATION_METADATA_ACTIVATABLE = "Windows.Foundation.Metadata.ActivatableAttribute"
        const val WINDOWS_FOUNDATION_METADATA_VERSION = "Windows.Foundation.Metadata.VersionAttribute"
    }
}

private class IndexedStringHeap {
    private val bytes = ByteArrayOutputStream().apply { write(0) }
    private val indexes = linkedMapOf<String, Int>()

    fun index(value: String): Int =
        indexes.getOrPut(value) {
            val index = bytes.size()
            bytes.write(value.toByteArray(StandardCharsets.UTF_8))
            bytes.write(0)
            index
        }

    fun bytes(): ByteArray = bytes.toByteArray()
}

private class IndexedBlobHeap {
    private val bytes = ByteArrayOutputStream().apply { write(0) }
    private val indexes = linkedMapOf<List<Byte>, Int>()

    fun index(value: ByteArray): Int =
        indexes.getOrPut(value.toList()) {
            val index = bytes.size()
            require(value.size < 0x80) { "Large WinMD blobs are not supported yet." }
            bytes.write(value.size)
            bytes.write(value)
            index
        }

    fun bytes(): ByteArray = bytes.toByteArray()
}

private class BinaryWriter {
    private val out = ByteArrayOutputStream()
    val size: Int get() = out.size()

    fun int8(value: Int) {
        out.write(value and 0xFF)
    }

    fun int16(value: Int) {
        int8(value)
        int8(value ushr 8)
    }

    fun int32(value: Int) {
        int8(value)
        int8(value ushr 8)
        int8(value ushr 16)
        int8(value ushr 24)
    }

    fun int64(value: Long) {
        int32(value.toInt())
        int32((value ushr 32).toInt())
    }

    fun index(value: Int) = int16(value)

    fun ascii(value: String) {
        bytes(value.toByteArray(StandardCharsets.US_ASCII))
    }

    fun paddedAscii(value: String) {
        ascii(value)
        int8(0)
        padTo(align(size, 4))
    }

    fun bytes(value: ByteArray) {
        out.write(value)
    }

    fun padTo(targetSize: Int) {
        while (size < targetSize) {
            int8(0)
        }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

private fun align(value: Int, alignment: Int): Int =
    ((value + alignment - 1) / alignment) * alignment
