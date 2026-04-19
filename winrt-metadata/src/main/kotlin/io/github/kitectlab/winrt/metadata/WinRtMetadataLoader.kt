package io.github.kitectlab.winrt.metadata

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

object WinRtMetadataLoader {
    fun load(paths: List<Path>): WinRtMetadataModel {
        val discoveredFiles = discoverMetadataFiles(paths)
        val namespaces = discoveredFiles
            .flatMap { WinRtCliMetadataFile.parse(it) }
            .groupBy { it.namespace }
            .map { (namespace, types) ->
                WinRtNamespace(
                    name = namespace,
                    types = types,
                )
            }
        return WinRtMetadataModel(namespaces).normalized()
    }

    fun load(vararg paths: Path): WinRtMetadataModel = load(paths.toList())

    internal fun discoverMetadataFiles(paths: List<Path>): List<Path> =
        paths.asSequence()
            .flatMap { path ->
                when {
                    path.isDirectory() -> Files.walk(path).use { stream ->
                        stream.asSequence()
                            .filter(Files::isRegularFile)
                            .filter(::looksLikeCliMetadataCandidate)
                            .toList()
                            .asSequence()
                    }

                    Files.isRegularFile(path) -> sequenceOf(path)
                    else -> emptySequence()
                }
            }
            .map(::canonicalizePath)
            .distinctBy(::canonicalPathKey)
            .sortedBy(::canonicalPathKey)
            .toList()

    private fun looksLikeCliMetadataCandidate(path: Path): Boolean =
        path.extension.lowercase() in setOf("winmd", "dll", "exe")

    private fun canonicalizePath(path: Path): Path =
        runCatching { path.toAbsolutePath().normalize() }.getOrElse { path.normalize() }

    private fun canonicalPathKey(path: Path): String =
        canonicalizePath(path).toString().let { value ->
            if (isWindows()) value.lowercase() else value
        }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}

private class WinRtCliMetadataFile private constructor(
    private val path: Path,
    private val buffer: ByteBuffer,
) {
    fun parseTypes(): List<WinRtTypeDefinition> {
        val peHeaderOffset = buffer.intAt(DOS_PE_HEADER_POINTER)
        require(peHeaderOffset > 0) {
            "${path.name} does not contain a valid PE header pointer."
        }
        require(buffer.intAt(peHeaderOffset) == PE_SIGNATURE) {
            "${path.name} is not a PE/CLI metadata file."
        }

        val coffHeaderOffset = peHeaderOffset + PE_SIGNATURE_SIZE
        val sectionCount = buffer.shortAt(coffHeaderOffset + COFF_SECTION_COUNT_OFFSET).toInt() and 0xFFFF
        val optionalHeaderSize = buffer.shortAt(coffHeaderOffset + COFF_OPTIONAL_HEADER_SIZE_OFFSET).toInt() and 0xFFFF
        val optionalHeaderOffset = coffHeaderOffset + COFF_HEADER_SIZE
        val cliHeaderRva = readCliHeaderRva(optionalHeaderOffset)
        require(cliHeaderRva != 0) {
            "${path.name} does not expose a CLI header."
        }

        val sectionsOffset = optionalHeaderOffset + optionalHeaderSize
        val sections = (0 until sectionCount).map { index ->
            val offset = sectionsOffset + index * SECTION_HEADER_SIZE
            SectionHeader(
                virtualSize = buffer.intAt(offset + SECTION_VIRTUAL_SIZE_OFFSET),
                virtualAddress = buffer.intAt(offset + SECTION_VIRTUAL_ADDRESS_OFFSET),
                sizeOfRawData = buffer.intAt(offset + SECTION_RAW_DATA_SIZE_OFFSET),
                pointerToRawData = buffer.intAt(offset + SECTION_POINTER_TO_RAW_DATA_OFFSET),
            )
        }

        val cliHeaderOffset = rvaToFileOffset(cliHeaderRva, sections)
        val metadataRootRva = buffer.intAt(cliHeaderOffset + CLI_METADATA_RVA_OFFSET)
        val metadataRootOffset = rvaToFileOffset(metadataRootRva, sections)
        val metadataRoot = MetadataRoot.parse(buffer, metadataRootOffset)
        val stringsHeap = metadataRoot.requiredStream("#Strings")
        val tablesStream = metadataRoot.stream("#~") ?: metadataRoot.requiredStream("#-")
        val tables = MetadataTables.parse(buffer, tablesStream.offset, stringsHeap)
        return tables.toTypeDefinitions()
    }

    private fun readCliHeaderRva(optionalHeaderOffset: Int): Int {
        val magic = buffer.shortAt(optionalHeaderOffset).toInt() and 0xFFFF
        val dataDirectoryBase = when (magic) {
            OPTIONAL_HEADER_MAGIC_PE32 -> optionalHeaderOffset + PE32_DATA_DIRECTORIES_OFFSET
            OPTIONAL_HEADER_MAGIC_PE32_PLUS -> optionalHeaderOffset + PE32_PLUS_DATA_DIRECTORIES_OFFSET
            else -> error("${path.name} has unsupported PE optional header magic 0x${magic.toString(16)}")
        }
        return buffer.intAt(dataDirectoryBase + CLI_DATA_DIRECTORY_INDEX * DATA_DIRECTORY_SIZE)
    }

    private fun rvaToFileOffset(rva: Int, sections: List<SectionHeader>): Int {
        val section = sections.firstOrNull { header ->
            val span = maxOf(header.virtualSize, header.sizeOfRawData)
            rva >= header.virtualAddress && rva < header.virtualAddress + span
        } ?: error("${path.name} does not map RVA 0x${rva.toString(16)} to a section")
        return section.pointerToRawData + (rva - section.virtualAddress)
    }

    companion object {
        private const val DOS_PE_HEADER_POINTER = 0x3C
        private const val PE_SIGNATURE = 0x00004550
        private const val PE_SIGNATURE_SIZE = 4
        private const val COFF_HEADER_SIZE = 20
        private const val COFF_SECTION_COUNT_OFFSET = 2
        private const val COFF_OPTIONAL_HEADER_SIZE_OFFSET = 16
        private const val OPTIONAL_HEADER_MAGIC_PE32 = 0x10B
        private const val OPTIONAL_HEADER_MAGIC_PE32_PLUS = 0x20B
        private const val PE32_DATA_DIRECTORIES_OFFSET = 96
        private const val PE32_PLUS_DATA_DIRECTORIES_OFFSET = 112
        private const val CLI_DATA_DIRECTORY_INDEX = 14
        private const val DATA_DIRECTORY_SIZE = 8
        private const val SECTION_HEADER_SIZE = 40
        private const val SECTION_VIRTUAL_SIZE_OFFSET = 8
        private const val SECTION_VIRTUAL_ADDRESS_OFFSET = 12
        private const val SECTION_RAW_DATA_SIZE_OFFSET = 16
        private const val SECTION_POINTER_TO_RAW_DATA_OFFSET = 20
        private const val CLI_METADATA_RVA_OFFSET = 8

        fun parse(path: Path): List<WinRtTypeDefinition> {
            path.inputStream().use { input ->
                val bytes = input.readAllBytes()
                return WinRtCliMetadataFile(
                    path = path,
                    buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN),
                ).parseTypes()
            }
        }
    }
}

private data class SectionHeader(
    val virtualSize: Int,
    val virtualAddress: Int,
    val sizeOfRawData: Int,
    val pointerToRawData: Int,
)

private data class MetadataRoot(
    val streams: List<MetadataStream>,
) {
    fun stream(name: String): MetadataStream? = streams.firstOrNull { it.name == name }

    fun requiredStream(name: String): MetadataStream =
        requireNotNull(stream(name)) { "Missing metadata stream $name" }

    companion object {
        private const val METADATA_SIGNATURE = 0x424A5342

        fun parse(buffer: ByteBuffer, rootOffset: Int): MetadataRoot {
            require(buffer.intAt(rootOffset) == METADATA_SIGNATURE) {
                "Invalid CLI metadata root signature."
            }
            val versionLength = buffer.intAt(rootOffset + 12)
            val versionPaddedLength = alignToFour(versionLength)
            val streamCountOffset = rootOffset + 16 + versionPaddedLength + 2
            val streamCount = buffer.shortAt(streamCountOffset).toInt() and 0xFFFF
            var currentOffset = streamCountOffset + 2
            val streams = buildList(streamCount) {
                repeat(streamCount) {
                    val relativeOffset = buffer.intAt(currentOffset)
                    val size = buffer.intAt(currentOffset + 4)
                    val name = buffer.readPaddedString(currentOffset + 8)
                    add(MetadataStream(name, rootOffset + relativeOffset, size))
                    currentOffset += 8 + alignToFour(name.length + 1)
                }
            }
            return MetadataRoot(streams)
        }
    }
}

private data class MetadataStream(
    val name: String,
    val offset: Int,
    val size: Int,
)

private class MetadataTables private constructor(
    private val buffer: ByteBuffer,
    private val stringsHeap: MetadataStream,
    private val rowCounts: IntArray,
    private val tableOffsets: IntArray,
    private val stringIndexSize: Int,
) {
    fun toTypeDefinitions(): List<WinRtTypeDefinition> {
        val typeRefNames = readTypeRefQualifiedNames()
        val typeDefs = readRawTypeDefs()
        val typeDefNames = typeDefs.map { raw -> raw.qualifiedName }.toTypedArray()

        return typeDefs
            .filterNot { it.name == "<Module>" }
            .map { raw ->
                WinRtTypeDefinition(
                    namespace = raw.namespace,
                    name = raw.name,
                    kind = raw.classify(typeDefNames, typeRefNames),
                )
            }
    }

    private fun readTypeRefQualifiedNames(): Array<String> {
        val rowCount = rowCounts[TABLE_TYPE_REF]
        if (rowCount == 0) {
            return emptyArray()
        }

        val rows = Array(rowCount) { "" }
        var cursor = tableOffsets[TABLE_TYPE_REF]
        repeat(rowCount) { rowIndex ->
            cursor += codedIndexSize(CODED_RESOLUTION_SCOPE)
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val namespace = readStringAt(cursor)
            cursor += stringIndexSize
            rows[rowIndex] = if (namespace.isEmpty()) name else "$namespace.$name"
        }
        return rows
    }

    private fun readRawTypeDefs(): List<RawTypeDef> {
        val rowCount = rowCounts[TABLE_TYPE_DEF]
        if (rowCount == 0) {
            return emptyList()
        }

        val rows = ArrayList<RawTypeDef>(rowCount)
        var cursor = tableOffsets[TABLE_TYPE_DEF]
        repeat(rowCount) {
            val flags = buffer.intAt(cursor)
            cursor += 4
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val namespace = readStringAt(cursor)
            cursor += stringIndexSize
            val extendsToken = readIndex(cursor, codedIndexSize(CODED_TYPE_DEF_OR_REF))
            cursor += codedIndexSize(CODED_TYPE_DEF_OR_REF)
            cursor += simpleIndexSize(TABLE_FIELD)
            cursor += simpleIndexSize(TABLE_METHOD_DEF)
            rows += RawTypeDef(flags, namespace, name, extendsToken)
        }
        return rows
    }

    private fun RawTypeDef.classify(typeDefNames: Array<String>, typeRefNames: Array<String>): WinRtTypeKind {
        if (flags and TYPE_ATTRIBUTE_INTERFACE != 0) {
            return WinRtTypeKind.Interface
        }

        val extendsName = decodeTypeDefOrRefQualifiedName(extendsToken, typeDefNames, typeRefNames)
        return when (extendsName) {
            "System.Enum" -> WinRtTypeKind.Enum
            "System.ValueType" -> WinRtTypeKind.Struct
            "System.MulticastDelegate" -> WinRtTypeKind.Delegate
            else -> WinRtTypeKind.RuntimeClass
        }
    }

    private fun decodeTypeDefOrRefQualifiedName(
        token: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
    ): String? {
        if (token == 0) {
            return null
        }
        val tag = token and CODED_TYPE_DEF_OR_REF_TAG_MASK
        val rowId = token ushr CODED_TYPE_DEF_OR_REF_TAG_BITS
        if (rowId == 0) {
            return null
        }
        return when (tag) {
            CODED_TYPE_DEF_OR_REF_TYPE_DEF -> typeDefNames.getOrNull(rowId - 1)
            CODED_TYPE_DEF_OR_REF_TYPE_REF -> typeRefNames.getOrNull(rowId - 1)
            else -> null
        }
    }

    private fun readStringAt(indexOffset: Int): String =
        buffer.readHeapString(stringsHeap.offset, readIndex(indexOffset, stringIndexSize))

    private fun readIndex(offset: Int, size: Int): Int = readIndex(offset, size, buffer)

    private fun simpleIndexSize(tableId: Int): Int =
        if (rowCounts[tableId] < (1 shl 16)) 2 else 4

    private fun codedIndexSize(codedIndex: CodedIndex): Int =
        if (codedIndex.maxRowCount(rowCounts) < (1 shl (16 - codedIndex.tagBits))) 2 else 4

    companion object {
        private const val TABLE_MODULE = 0x00
        private const val TABLE_TYPE_REF = 0x01
        private const val TABLE_TYPE_DEF = 0x02
        private const val TABLE_FIELD = 0x04
        private const val TABLE_METHOD_DEF = 0x06
        private const val TABLE_MODULE_REF = 0x1A
        private const val TABLE_TYPE_SPEC = 0x1B
        private const val TABLE_ASSEMBLY_REF = 0x23

        private const val TYPE_ATTRIBUTE_INTERFACE = 0x20

        private const val CODED_TYPE_DEF_OR_REF_TYPE_DEF = 0
        private const val CODED_TYPE_DEF_OR_REF_TYPE_REF = 1
        private const val CODED_TYPE_DEF_OR_REF_TAG_BITS = 2
        private const val CODED_TYPE_DEF_OR_REF_TAG_MASK = (1 shl CODED_TYPE_DEF_OR_REF_TAG_BITS) - 1

        private val CODED_RESOLUTION_SCOPE = CodedIndex(
            tagBits = 2,
            tables = intArrayOf(TABLE_MODULE, TABLE_MODULE_REF, TABLE_ASSEMBLY_REF, TABLE_TYPE_REF),
        )
        private val CODED_TYPE_DEF_OR_REF = CodedIndex(
            tagBits = CODED_TYPE_DEF_OR_REF_TAG_BITS,
            tables = intArrayOf(TABLE_TYPE_DEF, TABLE_TYPE_REF, TABLE_TYPE_SPEC),
        )

        fun parse(buffer: ByteBuffer, offset: Int, stringsHeap: MetadataStream): MetadataTables {
            val heapSizes = buffer.byteAt(offset + 6).toInt() and 0xFF
            val validMask = buffer.longAt(offset + 8)
            val rowCounts = IntArray(64)
            var rowCursor = offset + 24
            for (tableId in 0 until 64) {
                if ((validMask ushr tableId) and 1L == 1L) {
                    rowCounts[tableId] = buffer.intAt(rowCursor)
                    rowCursor += 4
                }
            }

            val stringIndexSize = if (heapSizes and 0x01 != 0) 4 else 2
            val guidIndexSize = if (heapSizes and 0x02 != 0) 4 else 2
            val blobIndexSize = if (heapSizes and 0x04 != 0) 4 else 2
            val tableOffsets = IntArray(64)
            var tableCursor = rowCursor

            for (tableId in 0..TABLE_TYPE_DEF) {
                if (rowCounts[tableId] == 0) {
                    continue
                }
                tableOffsets[tableId] = tableCursor
                tableCursor += rowCounts[tableId] * rowSize(tableId, rowCounts, stringIndexSize, guidIndexSize, blobIndexSize)
            }

            return MetadataTables(
                buffer = buffer,
                stringsHeap = stringsHeap,
                rowCounts = rowCounts,
                tableOffsets = tableOffsets,
                stringIndexSize = stringIndexSize,
            )
        }

        private fun rowSize(
            tableId: Int,
            rowCounts: IntArray,
            stringIndexSize: Int,
            guidIndexSize: Int,
            blobIndexSize: Int,
        ): Int = when (tableId) {
            TABLE_MODULE -> 2 + stringIndexSize + guidIndexSize + guidIndexSize + guidIndexSize
            TABLE_TYPE_REF -> codedIndexSize(CODED_RESOLUTION_SCOPE, rowCounts) + stringIndexSize + stringIndexSize
            TABLE_TYPE_DEF -> 4 + stringIndexSize + stringIndexSize + codedIndexSize(CODED_TYPE_DEF_OR_REF, rowCounts) + simpleIndexSize(TABLE_FIELD, rowCounts) + simpleIndexSize(TABLE_METHOD_DEF, rowCounts)
            else -> error("Metadata 2.1 parser does not yet support table 0x${tableId.toString(16)}")
        }

        private fun simpleIndexSize(tableId: Int, rowCounts: IntArray): Int =
            if (rowCounts[tableId] < (1 shl 16)) 2 else 4

        private fun codedIndexSize(codedIndex: CodedIndex, rowCounts: IntArray): Int =
            if (codedIndex.maxRowCount(rowCounts) < (1 shl (16 - codedIndex.tagBits))) 2 else 4
    }
}

private data class RawTypeDef(
    val flags: Int,
    val namespace: String,
    val name: String,
    val extendsToken: Int,
) {
    val qualifiedName: String
        get() = if (namespace.isEmpty()) name else "$namespace.$name"
}

private data class CodedIndex(
    val tagBits: Int,
    val tables: IntArray,
) {
    fun maxRowCount(rowCounts: IntArray): Int = tables.maxOf { rowCounts[it] }
}

private fun ByteBuffer.byteAt(offset: Int): Byte = get(offset)

private fun ByteBuffer.shortAt(offset: Int): Short = getShort(offset)

private fun ByteBuffer.intAt(offset: Int): Int = getInt(offset)

private fun ByteBuffer.longAt(offset: Int): Long = getLong(offset)

private fun ByteBuffer.readPaddedString(offset: Int): String {
    val bytes = ArrayList<Byte>()
    var cursor = offset
    while (true) {
        val value = get(cursor)
        if (value.toInt() == 0) {
            break
        }
        bytes += value
        cursor += 1
    }
    return bytes.toByteArray().decodeToString()
}

private fun ByteBuffer.readHeapString(heapOffset: Int, index: Int): String {
    if (index == 0) {
        return ""
    }
    val bytes = ArrayList<Byte>()
    var cursor = heapOffset + index
    while (true) {
        val value = get(cursor)
        if (value.toInt() == 0) {
            break
        }
        bytes += value
        cursor += 1
    }
    return bytes.toByteArray().decodeToString()
}

private fun readIndex(offset: Int, size: Int, buffer: ByteBuffer): Int =
    when (size) {
        2 -> buffer.shortAt(offset).toInt() and 0xFFFF
        4 -> buffer.intAt(offset)
        else -> error("Unsupported index size $size")
    }

private fun alignToFour(size: Int): Int = (size + 3) and 3.inv()