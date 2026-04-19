package io.github.kitectlab.winrt.metadata

import io.github.kitectlab.winrt.runtime.Guid
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
        val blobHeap = metadataRoot.requiredStream("#Blob")
        val stringsHeap = metadataRoot.requiredStream("#Strings")
        val tablesStream = metadataRoot.stream("#~") ?: metadataRoot.requiredStream("#-")
        val tables = MetadataTables.parse(buffer, tablesStream.offset, stringsHeap, blobHeap)
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
    private val blobHeap: MetadataStream,
    private val stringsHeap: MetadataStream,
    private val rowCounts: IntArray,
    private val tableOffsets: IntArray,
    private val stringIndexSize: Int,
    private val blobIndexSize: Int,
) {
    fun toTypeDefinitions(): List<WinRtTypeDefinition> {
        val typeDefs = readRawTypeDefs()
        val rawMethods = readRawMethodDefs()
        val rawParams = readRawParams()
        val typeRefNames = readTypeRefQualifiedNames()
        val typeDefNames = typeDefs.map { raw -> raw.qualifiedName }.toTypedArray()
        val methodOwnerTypeNames = buildMethodOwnerTypeNames(typeDefs, typeDefNames)
        val memberRefOwnerTypeNames = readMemberRefOwnerTypeNames(typeDefNames, typeRefNames, methodOwnerTypeNames)
        val customAttributes = readCustomAttributes(methodOwnerTypeNames, memberRefOwnerTypeNames)
        val interfaceImplsByOwner = readInterfaceImplementations(typeDefNames, typeRefNames, customAttributes)
        val propertyRowIdsByOwner = readMemberMap(TABLE_PROPERTY_MAP, TABLE_PROPERTY)
        val eventRowIdsByOwner = readMemberMap(TABLE_EVENT_MAP, TABLE_EVENT)
        val rawProperties = readRawProperties()
        val rawEvents = readRawEvents(typeDefNames, typeRefNames)
        val methodSemantics = readMethodSemantics()
        val parameterRowsByMethod = buildParameterRowsByMethod(rawMethods, rawParams)

        return typeDefs
            .mapIndexedNotNull { index, raw ->
                if (raw.name == "<Module>") {
                    return@mapIndexedNotNull null
                }
                val rowId = index + 1
                val typeAttributes = customAttributes[OwnerKey(TABLE_TYPE_DEF, rowId)].orEmpty()
                val implementedInterfaces = interfaceImplsByOwner[rowId].orEmpty()
                val defaultInterfaceName = implementedInterfaces.firstOrNull { it.isDefault }?.interfaceName
                WinRtTypeDefinition(
                    namespace = raw.namespace,
                    name = raw.name,
                    kind = raw.classify(typeDefNames, typeRefNames),
                    iid = extractGuid(typeAttributes),
                    baseTypeName = decodeTypeDefOrRefQualifiedName(raw.extendsToken, typeDefNames, typeRefNames),
                    isProjectionInternal = typeAttributes.any { it.typeName == WINRT_INTEROP_PROJECTION_INTERNAL },
                    isExclusiveTo = typeAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO },
                    isApiContract = typeAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_API_CONTRACT },
                    isAttributeType = decodeTypeDefOrRefQualifiedName(raw.extendsToken, typeDefNames, typeRefNames) == SYSTEM_ATTRIBUTE,
                    isStaticType = raw.classify(typeDefNames, typeRefNames) == WinRtTypeKind.RuntimeClass &&
                        (raw.flags and TYPE_ATTRIBUTE_ABSTRACT) != 0,
                    isSealedType = (raw.flags and TYPE_ATTRIBUTE_SEALED) != 0,
                    defaultInterfaceName = defaultInterfaceName,
                    implementedInterfaces = implementedInterfaces,
                    genericParameterCount = raw.genericParameterCount,
                    activation = extractActivationShape(typeAttributes),
                    methods = readMethodDefinitions(
                        typeIndex = index,
                        typeDefs = typeDefs,
                        rawMethods = rawMethods,
                        parameterRowsByMethod = parameterRowsByMethod,
                        typeDefNames = typeDefNames,
                        typeRefNames = typeRefNames,
                        semanticMethodRowIds = methodSemantics.semanticMethodRowIds,
                    ),
                    properties = propertyRowIdsByOwner[rowId].orEmpty().mapNotNull { propertyRowId ->
                        buildPropertyDefinition(
                            propertyRowId = propertyRowId,
                            rawProperties = rawProperties,
                            propertyAccessors = methodSemantics.propertyAccessorsByPropertyRowId,
                            rawMethods = rawMethods,
                            typeDefNames = typeDefNames,
                            typeRefNames = typeRefNames,
                        )
                    },
                    events = eventRowIdsByOwner[rowId].orEmpty().mapNotNull { eventRowId ->
                        buildEventDefinition(
                            eventRowId = eventRowId,
                            rawEvents = rawEvents,
                            eventAccessors = methodSemantics.eventAccessorsByEventRowId,
                            rawMethods = rawMethods,
                        )
                    },
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
            val rawName = readStringAt(cursor)
            cursor += stringIndexSize
            val namespace = readStringAt(cursor)
            cursor += stringIndexSize
            val extendsToken = readIndex(cursor, codedIndexSize(CODED_TYPE_DEF_OR_REF))
            cursor += codedIndexSize(CODED_TYPE_DEF_OR_REF)
            val fieldListStart = readIndex(cursor, simpleIndexSize(TABLE_FIELD))
            cursor += simpleIndexSize(TABLE_FIELD)
            val methodListStart = readIndex(cursor, simpleIndexSize(TABLE_METHOD_DEF))
            cursor += simpleIndexSize(TABLE_METHOD_DEF)
            val (name, genericParameterCount) = splitGenericArity(rawName)
            rows += RawTypeDef(flags, namespace, name, genericParameterCount, extendsToken, fieldListStart, methodListStart)
        }
        return rows
    }

    private fun readRawMethodDefs(): List<RawMethodDef> {
        val rowCount = rowCounts[TABLE_METHOD_DEF]
        if (rowCount == 0) {
            return emptyList()
        }

        val rows = ArrayList<RawMethodDef>(rowCount)
        var cursor = tableOffsets[TABLE_METHOD_DEF]
        repeat(rowCount) {
            cursor += 4
            cursor += 2
            val flags = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val signatureBlobIndex = readIndex(cursor, blobIndexSize)
            cursor += blobIndexSize
            val paramListStart = readIndex(cursor, simpleIndexSize(TABLE_PARAM))
            cursor += simpleIndexSize(TABLE_PARAM)
            rows += RawMethodDef(flags, name, signatureBlobIndex, paramListStart)
        }
        return rows
    }

    private fun readRawParams(): List<RawParam> {
        val rowCount = rowCounts[TABLE_PARAM]
        if (rowCount == 0) {
            return emptyList()
        }

        val rows = ArrayList<RawParam>(rowCount)
        var cursor = tableOffsets[TABLE_PARAM]
        repeat(rowCount) {
            val flags = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val sequence = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            rows += RawParam(flags, sequence, name)
        }
        return rows
    }

    private fun readRawProperties(): List<RawProperty> {
        val rowCount = rowCounts[TABLE_PROPERTY]
        if (rowCount == 0) {
            return emptyList()
        }

        val rows = ArrayList<RawProperty>(rowCount)
        var cursor = tableOffsets[TABLE_PROPERTY]
        repeat(rowCount) {
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val signatureBlobIndex = readIndex(cursor, blobIndexSize)
            cursor += blobIndexSize
            rows += RawProperty(name, signatureBlobIndex)
        }
        return rows
    }

    private fun readRawEvents(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
    ): List<RawEvent> {
        val rowCount = rowCounts[TABLE_EVENT]
        if (rowCount == 0) {
            return emptyList()
        }

        val rows = ArrayList<RawEvent>(rowCount)
        var cursor = tableOffsets[TABLE_EVENT]
        repeat(rowCount) {
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val eventTypeToken = readIndex(cursor, codedIndexSize(CODED_TYPE_DEF_OR_REF))
            cursor += codedIndexSize(CODED_TYPE_DEF_OR_REF)
            rows += RawEvent(
                name = name,
                delegateTypeName = normalizeSignatureTypeName(
                    decodeTypeDefOrRefQualifiedName(eventTypeToken, typeDefNames, typeRefNames),
                ),
            )
        }
        return rows
    }

    private fun readMemberMap(mapTableId: Int, memberTableId: Int): Map<Int, List<Int>> {
        val rowCount = rowCounts[mapTableId]
        val memberRowCount = rowCounts[memberTableId]
        if (rowCount == 0 || memberRowCount == 0) {
            return emptyMap()
        }

        val owners = ArrayList<Pair<Int, Int>>(rowCount)
        var cursor = tableOffsets[mapTableId]
        repeat(rowCount) {
            val ownerRowId = readIndex(cursor, simpleIndexSize(TABLE_TYPE_DEF))
            cursor += simpleIndexSize(TABLE_TYPE_DEF)
            val memberListStart = readIndex(cursor, simpleIndexSize(memberTableId))
            cursor += simpleIndexSize(memberTableId)
            owners += ownerRowId to memberListStart
        }

        return owners.mapIndexedNotNull { index, (ownerRowId, start) ->
            if (start <= 0) {
                return@mapIndexedNotNull null
            }
            val endExclusive = owners.getOrNull(index + 1)?.second ?: (memberRowCount + 1)
            ownerRowId to (start until endExclusive).toList()
        }.toMap()
    }

    private fun readMethodDefinitions(
        typeIndex: Int,
        typeDefs: List<RawTypeDef>,
        rawMethods: List<RawMethodDef>,
        parameterRowsByMethod: Map<Int, List<RawParam>>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        semanticMethodRowIds: Set<Int>,
    ): List<WinRtMethodDefinition> {
        if (rawMethods.isEmpty()) {
            return emptyList()
        }

        val typeDef = typeDefs[typeIndex]
        val start = typeDef.methodListStart.coerceAtLeast(1)
        val endExclusive = (typeDefs.getOrNull(typeIndex + 1)?.methodListStart ?: (rawMethods.size + 1)).coerceAtMost(rawMethods.size + 1)
        if (start >= endExclusive) {
            return emptyList()
        }

        return (start until endExclusive)
            .filterNot { it in semanticMethodRowIds }
            .mapNotNull { methodRowId ->
                val rawMethod = rawMethods.getOrNull(methodRowId - 1) ?: return@mapNotNull null
                if (rawMethod.name == ".ctor" || rawMethod.name == ".cctor") {
                    return@mapNotNull null
                }
                val signature = readMethodSignature(rawMethod.signatureBlobIndex, typeDefNames, typeRefNames)
                WinRtMethodDefinition(
                    name = rawMethod.name,
                    returnTypeName = signature.returnType.typeName,
                    parameters = signature.parameters.mapIndexed { parameterIndex, parameterType ->
                        val parameterRow = parameterRowsByMethod[methodRowId].orEmpty().firstOrNull { it.sequence == parameterIndex + 1 }
                        WinRtParameterDefinition(
                            name = parameterRow?.name?.takeIf(String::isNotBlank) ?: "arg${parameterIndex + 1}",
                            typeName = parameterType.typeName,
                            direction = parameterDirectionFor(parameterRow, parameterType.isByRef),
                        )
                    },
                    isStatic = rawMethod.flags and METHOD_ATTRIBUTE_STATIC != 0,
                    methodRowId = methodRowId,
                )
            }
    }

    private fun buildPropertyDefinition(
        propertyRowId: Int,
        rawProperties: List<RawProperty>,
        propertyAccessors: Map<Int, PropertyAccessorRows>,
        rawMethods: List<RawMethodDef>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
    ): WinRtPropertyDefinition? {
        val rawProperty = rawProperties.getOrNull(propertyRowId - 1) ?: return null
        val accessors = propertyAccessors[propertyRowId] ?: PropertyAccessorRows()
        val getter = accessors.getterMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val setter = accessors.setterMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val signature = readPropertySignature(rawProperty.signatureBlobIndex, typeDefNames, typeRefNames)
        return WinRtPropertyDefinition(
            name = rawProperty.name,
            typeName = signature.type.typeName,
            isStatic = listOfNotNull(getter, setter).any { it.flags and METHOD_ATTRIBUTE_STATIC != 0 },
            getterMethodName = getter?.name,
            setterMethodName = setter?.name,
            getterMethodRowId = accessors.getterMethodRowId,
            setterMethodRowId = accessors.setterMethodRowId,
        )
    }

    private fun buildEventDefinition(
        eventRowId: Int,
        rawEvents: List<RawEvent>,
        eventAccessors: Map<Int, EventAccessorRows>,
        rawMethods: List<RawMethodDef>,
    ): WinRtEventDefinition? {
        val rawEvent = rawEvents.getOrNull(eventRowId - 1) ?: return null
        val accessors = eventAccessors[eventRowId] ?: EventAccessorRows()
        val addMethod = accessors.addMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val removeMethod = accessors.removeMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        return WinRtEventDefinition(
            name = rawEvent.name,
            delegateTypeName = rawEvent.delegateTypeName,
            isStatic = listOfNotNull(addMethod, removeMethod).any { it.flags and METHOD_ATTRIBUTE_STATIC != 0 },
            addMethodName = addMethod?.name,
            removeMethodName = removeMethod?.name,
            addMethodRowId = accessors.addMethodRowId,
            removeMethodRowId = accessors.removeMethodRowId,
        )
    }

    private fun buildParameterRowsByMethod(
        rawMethods: List<RawMethodDef>,
        rawParams: List<RawParam>,
    ): Map<Int, List<RawParam>> {
        if (rawMethods.isEmpty() || rawParams.isEmpty()) {
            return emptyMap()
        }

        return buildMap(rawMethods.size) {
            rawMethods.forEachIndexed { index, rawMethod ->
                val start = rawMethod.paramListStart.coerceAtLeast(1)
                val endExclusive = (rawMethods.getOrNull(index + 1)?.paramListStart ?: (rawParams.size + 1)).coerceAtMost(rawParams.size + 1)
                if (start >= endExclusive) {
                    return@forEachIndexed
                }
                put(
                    index + 1,
                    rawParams.subList(start - 1, endExclusive - 1)
                        .sortedBy(RawParam::sequence),
                )
            }
        }
    }

    private fun readInterfaceImplementations(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        customAttributes: Map<OwnerKey, List<DecodedCustomAttribute>>,
    ): Map<Int, List<WinRtInterfaceImplementationDefinition>> {
        val rowCount = rowCounts[TABLE_INTERFACE_IMPL]
        if (rowCount == 0) {
            return emptyMap()
        }

        var cursor = tableOffsets[TABLE_INTERFACE_IMPL]
        return buildList<Pair<Int, WinRtInterfaceImplementationDefinition>>(rowCount) {
            repeat(rowCount) { rowIndex ->
                val classRowId = readIndex(cursor, simpleIndexSize(TABLE_TYPE_DEF))
                cursor += simpleIndexSize(TABLE_TYPE_DEF)
                val interfaceToken = readIndex(cursor, codedIndexSize(CODED_TYPE_DEF_OR_REF))
                cursor += codedIndexSize(CODED_TYPE_DEF_OR_REF)
                val attributeNames = customAttributes[OwnerKey(TABLE_INTERFACE_IMPL, rowIndex + 1)].orEmpty()
                    .map(DecodedCustomAttribute::typeName)
                    .toSet()
                val interfaceName = decodeTypeDefOrRefQualifiedName(interfaceToken, typeDefNames, typeRefNames)
                    ?: return@repeat
                add(
                    classRowId to WinRtInterfaceImplementationDefinition(
                        interfaceName = interfaceName,
                        isDefault = WINDOWS_FOUNDATION_METADATA_DEFAULT in attributeNames,
                        isOverridable = WINDOWS_FOUNDATION_METADATA_OVERRIDABLE in attributeNames,
                        isProtected = WINDOWS_FOUNDATION_METADATA_PROTECTED in attributeNames,
                    ),
                )
            }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, values) ->
                values
                    .groupBy(WinRtInterfaceImplementationDefinition::interfaceName)
                    .values
                    .map { duplicates -> duplicates.reduce(WinRtInterfaceImplementationDefinition::merge) }
                    .sortedBy(WinRtInterfaceImplementationDefinition::interfaceName)
            }
    }

    private fun readCustomAttributes(
        methodOwnerTypeNames: Array<String?>,
        memberRefOwnerTypeNames: Array<String?>,
    ): Map<OwnerKey, List<DecodedCustomAttribute>> {
        val rowCount = rowCounts[TABLE_CUSTOM_ATTRIBUTE]
        if (rowCount == 0) {
            return emptyMap()
        }

        var cursor = tableOffsets[TABLE_CUSTOM_ATTRIBUTE]
        return buildList<Pair<OwnerKey, DecodedCustomAttribute>>(rowCount) {
            repeat(rowCount) {
                val parentToken = readIndex(cursor, codedIndexSize(CODED_HAS_CUSTOM_ATTRIBUTE))
                cursor += codedIndexSize(CODED_HAS_CUSTOM_ATTRIBUTE)
                val typeToken = readIndex(cursor, codedIndexSize(CODED_CUSTOM_ATTRIBUTE_TYPE))
                cursor += codedIndexSize(CODED_CUSTOM_ATTRIBUTE_TYPE)
                val valueIndex = readIndex(cursor, blobIndexSize)
                cursor += blobIndexSize

                val owner = decodeHasCustomAttribute(parentToken) ?: return@repeat
                val attributeTypeName = decodeCustomAttributeTypeName(typeToken, methodOwnerTypeNames, memberRefOwnerTypeNames)
                    ?: return@repeat
                add(owner to DecodedCustomAttribute(attributeTypeName, readCustomAttributeStringArguments(valueIndex)))
            }
        }.groupBy({ it.first }, { it.second })
    }

    private fun readMemberRefOwnerTypeNames(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        methodOwnerTypeNames: Array<String?>,
    ): Array<String?> {
        val rowCount = rowCounts[TABLE_MEMBER_REF]
        if (rowCount == 0) {
            return emptyArray()
        }

        val rows = arrayOfNulls<String>(rowCount)
        var cursor = tableOffsets[TABLE_MEMBER_REF]
        repeat(rowCount) { rowIndex ->
            val parentToken = readIndex(cursor, codedIndexSize(CODED_MEMBER_REF_PARENT))
            cursor += codedIndexSize(CODED_MEMBER_REF_PARENT)
            cursor += stringIndexSize
            cursor += blobIndexSize
            val parentTag = parentToken and CODED_MEMBER_REF_PARENT_TAG_MASK
            val parentRowId = parentToken ushr CODED_MEMBER_REF_PARENT_TAG_BITS
            rows[rowIndex] = when (parentTag) {
                CODED_MEMBER_REF_PARENT_TYPE_DEF -> typeDefNames.getOrNull(parentRowId - 1)
                CODED_MEMBER_REF_PARENT_TYPE_REF -> typeRefNames.getOrNull(parentRowId - 1)
                CODED_MEMBER_REF_PARENT_METHOD_DEF -> methodOwnerTypeNames.getOrNull(parentRowId)
                else -> null
            }
        }
        return rows
    }

    private fun buildMethodOwnerTypeNames(typeDefs: List<RawTypeDef>, typeDefNames: Array<String>): Array<String?> {
        val methodRowCount = rowCounts[TABLE_METHOD_DEF]
        val owners = arrayOfNulls<String>(methodRowCount + 1)
        if (methodRowCount == 0) {
            return owners
        }

        typeDefs.forEachIndexed { index, typeDef ->
            val start = typeDef.methodListStart
            val endExclusive = typeDefs.getOrNull(index + 1)?.methodListStart ?: (methodRowCount + 1)
            if (start <= 0 || start >= endExclusive) {
                return@forEachIndexed
            }
            for (methodRowId in start until endExclusive) {
                owners[methodRowId] = typeDefNames[index]
            }
        }
        return owners
    }

    private fun readMethodSemantics(): MethodSemanticsData {
        val rowCount = rowCounts[TABLE_METHOD_SEMANTICS]
        if (rowCount == 0) {
            return MethodSemanticsData()
        }

        var cursor = tableOffsets[TABLE_METHOD_SEMANTICS]
        val propertyAccessors = mutableMapOf<Int, PropertyAccessorRows>()
        val eventAccessors = mutableMapOf<Int, EventAccessorRows>()
        val semanticMethodRowIds = mutableSetOf<Int>()
        repeat(rowCount) {
            val semantics = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val methodRowId = readIndex(cursor, simpleIndexSize(TABLE_METHOD_DEF))
            cursor += simpleIndexSize(TABLE_METHOD_DEF)
            val associationToken = readIndex(cursor, codedIndexSize(CODED_HAS_SEMANTICS))
            cursor += codedIndexSize(CODED_HAS_SEMANTICS)

            val associationTag = associationToken and CODED_HAS_SEMANTICS_TAG_MASK
            val associationRowId = associationToken ushr CODED_HAS_SEMANTICS_TAG_BITS
            when {
                associationTag == CODED_HAS_SEMANTICS_PROPERTY && semantics == METHOD_SEMANTICS_GETTER -> {
                    propertyAccessors[associationRowId] = (propertyAccessors[associationRowId] ?: PropertyAccessorRows()).copy(getterMethodRowId = methodRowId)
                    semanticMethodRowIds += methodRowId
                }

                associationTag == CODED_HAS_SEMANTICS_PROPERTY && semantics == METHOD_SEMANTICS_SETTER -> {
                    propertyAccessors[associationRowId] = (propertyAccessors[associationRowId] ?: PropertyAccessorRows()).copy(setterMethodRowId = methodRowId)
                    semanticMethodRowIds += methodRowId
                }

                associationTag == CODED_HAS_SEMANTICS_EVENT && semantics == METHOD_SEMANTICS_ADD_ON -> {
                    eventAccessors[associationRowId] = (eventAccessors[associationRowId] ?: EventAccessorRows()).copy(addMethodRowId = methodRowId)
                    semanticMethodRowIds += methodRowId
                }

                associationTag == CODED_HAS_SEMANTICS_EVENT && semantics == METHOD_SEMANTICS_REMOVE_ON -> {
                    eventAccessors[associationRowId] = (eventAccessors[associationRowId] ?: EventAccessorRows()).copy(removeMethodRowId = methodRowId)
                    semanticMethodRowIds += methodRowId
                }
            }
        }
        return MethodSemanticsData(
            propertyAccessorsByPropertyRowId = propertyAccessors,
            eventAccessorsByEventRowId = eventAccessors,
            semanticMethodRowIds = semanticMethodRowIds,
        )
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

    private fun decodeCustomAttributeTypeName(
        token: Int,
        methodOwnerTypeNames: Array<String?>,
        memberRefOwnerTypeNames: Array<String?>,
    ): String? {
        if (token == 0) {
            return null
        }
        val tag = token and CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_MASK
        val rowId = token ushr CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS
        if (rowId == 0) {
            return null
        }
        return when (tag) {
            CODED_CUSTOM_ATTRIBUTE_TYPE_METHOD_DEF -> methodOwnerTypeNames.getOrNull(rowId)
            CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF -> memberRefOwnerTypeNames.getOrNull(rowId - 1)
            else -> null
        }
    }

    private fun decodeHasCustomAttribute(token: Int): OwnerKey? {
        if (token == 0) {
            return null
        }
        val tag = token and CODED_HAS_CUSTOM_ATTRIBUTE_TAG_MASK
        val rowId = token ushr CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS
        if (rowId == 0) {
            return null
        }
        return when (tag) {
            CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF -> OwnerKey(TABLE_TYPE_DEF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL -> OwnerKey(TABLE_INTERFACE_IMPL, rowId)
            else -> null
        }
    }

    private fun readCustomAttributeStringArguments(blobIndex: Int): List<String> {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.size < 2 || bytes[0] != 0x01.toByte() || bytes[1] != 0x00.toByte()) {
            return emptyList()
        }
        val values = mutableListOf<String>()
        var cursor = 2
        while (cursor < bytes.size) {
            if (bytes.size - cursor <= 2) {
                break
            }
            val decoded = bytes.readSerializedString(cursor) ?: break
            values += decoded.first
            cursor = decoded.second
        }
        return values
    }

    private fun extractGuid(attributes: List<DecodedCustomAttribute>): Guid? {
        val value = attributes.firstOrNull { it.typeName in GUID_ATTRIBUTE_NAMES }
            ?.stringArguments
            ?.firstOrNull()
            ?: return null
        return runCatching { Guid(value) }.getOrNull()
    }

    private fun extractActivationShape(attributes: List<DecodedCustomAttribute>): WinRtActivationShape {
        val activatable = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_ACTIVATABLE }
        val staticAttributes = attributes.filter { it.typeName == WINDOWS_FOUNDATION_METADATA_STATIC }
        val composable = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_COMPOSABLE }
        return WinRtActivationShape(
            isActivatable = activatable != null,
            activatableFactoryInterfaceName = activatable?.stringArguments?.firstOrNull(),
            staticInterfaceNames = staticAttributes.mapNotNull { it.stringArguments.firstOrNull() },
            composableFactoryInterfaceName = composable?.stringArguments?.firstOrNull(),
        )
    }

    private fun readMethodSignature(
        blobIndex: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
    ): ParsedMethodSignature {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return ParsedMethodSignature(ParsedTypeSignature("Unit"), emptyList())
        }

        val reader = SignatureReader(bytes, typeDefNames, typeRefNames)
        val callingConvention = reader.readByte()
        if (callingConvention and CALL_CONV_GENERIC != 0) {
            reader.readCompressedUnsignedInt()
        }
        val parameterCount = reader.readCompressedUnsignedInt()
        val returnType = reader.readType()
        val parameters = buildList(parameterCount) {
            repeat(parameterCount) {
                add(reader.readType())
            }
        }
        return ParsedMethodSignature(returnType, parameters)
    }

    private fun readPropertySignature(
        blobIndex: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
    ): ParsedPropertySignature {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return ParsedPropertySignature(ParsedTypeSignature("Any"))
        }

        val reader = SignatureReader(bytes, typeDefNames, typeRefNames)
        reader.readByte()
        val parameterCount = reader.readCompressedUnsignedInt()
        val type = reader.readType()
        repeat(parameterCount) {
            reader.readType()
        }
        return ParsedPropertySignature(type)
    }

    private fun parameterDirectionFor(rawParam: RawParam?, isByRef: Boolean): WinRtParameterDirection = when {
        !isByRef -> WinRtParameterDirection.In
        rawParam == null -> WinRtParameterDirection.Ref
        rawParam.flags and PARAM_ATTRIBUTE_OUT != 0 && rawParam.flags and PARAM_ATTRIBUTE_IN == 0 -> WinRtParameterDirection.Out
        else -> WinRtParameterDirection.Ref
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
        private const val TABLE_FIELD_PTR = 0x03
        private const val TABLE_FIELD = 0x04
        private const val TABLE_METHOD_PTR = 0x05
        private const val TABLE_METHOD_DEF = 0x06
        private const val TABLE_PARAM_PTR = 0x07
        private const val TABLE_PARAM = 0x08
        private const val TABLE_INTERFACE_IMPL = 0x09
        private const val TABLE_MEMBER_REF = 0x0A
        private const val TABLE_CONSTANT = 0x0B
        private const val TABLE_CUSTOM_ATTRIBUTE = 0x0C
        private const val TABLE_STANDALONE_SIG = 0x11
        private const val TABLE_EVENT_MAP = 0x12
        private const val TABLE_EVENT_PTR = 0x13
        private const val TABLE_EVENT = 0x14
        private const val TABLE_PROPERTY_MAP = 0x15
        private const val TABLE_PROPERTY_PTR = 0x16
        private const val TABLE_PROPERTY = 0x17
        private const val TABLE_METHOD_SEMANTICS = 0x18
        private const val TABLE_METHOD_IMPL = 0x19
        private const val TABLE_MODULE_REF = 0x1A
        private const val TABLE_TYPE_SPEC = 0x1B
        private const val TABLE_ASSEMBLY = 0x20
        private const val TABLE_ASSEMBLY_REF = 0x23
        private const val TABLE_GENERIC_PARAM = 0x2A

        private const val TYPE_ATTRIBUTE_INTERFACE = 0x20
        private const val METHOD_ATTRIBUTE_STATIC = 0x0010
        private const val PARAM_ATTRIBUTE_IN = 0x0001
        private const val PARAM_ATTRIBUTE_OUT = 0x0002

        private const val METHOD_SEMANTICS_SETTER = 0x0001
        private const val METHOD_SEMANTICS_GETTER = 0x0002
        private const val METHOD_SEMANTICS_ADD_ON = 0x0008
        private const val METHOD_SEMANTICS_REMOVE_ON = 0x0010

        private const val CALL_CONV_GENERIC = 0x10

        private const val CODED_MEMBER_REF_PARENT_TYPE_DEF = 0
        private const val CODED_MEMBER_REF_PARENT_TYPE_REF = 1
        private const val CODED_MEMBER_REF_PARENT_METHOD_DEF = 3
        private const val CODED_MEMBER_REF_PARENT_TAG_BITS = 3
        private const val CODED_MEMBER_REF_PARENT_TAG_MASK = (1 shl CODED_MEMBER_REF_PARENT_TAG_BITS) - 1

        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_METHOD_DEF = 2
        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF = 3
        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS = 3
        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_MASK = (1 shl CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS) - 1

        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF = 3
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL = 5
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS = 5
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TAG_MASK = (1 shl CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS) - 1

        private val GUID_ATTRIBUTE_NAMES = setOf(
            "System.Runtime.InteropServices.GuidAttribute",
            "Windows.Foundation.Metadata.GuidAttribute",
        )
        private const val WINRT_INTEROP_PROJECTION_INTERNAL = "WinRT.Interop.ProjectionInternalAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO = "Windows.Foundation.Metadata.ExclusiveToAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_API_CONTRACT = "Windows.Foundation.Metadata.ApiContractAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_DEFAULT = "Windows.Foundation.Metadata.DefaultAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_OVERRIDABLE = "Windows.Foundation.Metadata.OverridableAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_PROTECTED = "Windows.Foundation.Metadata.ProtectedAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_ACTIVATABLE = "Windows.Foundation.Metadata.ActivatableAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_STATIC = "Windows.Foundation.Metadata.StaticAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_COMPOSABLE = "Windows.Foundation.Metadata.ComposableAttribute"
        private const val SYSTEM_ATTRIBUTE = "System.Attribute"
        private const val TYPE_ATTRIBUTE_ABSTRACT = 0x00000080
        private const val TYPE_ATTRIBUTE_SEALED = 0x00000100

        private const val CODED_TYPE_DEF_OR_REF_TYPE_DEF = 0
        private const val CODED_TYPE_DEF_OR_REF_TYPE_REF = 1
        private const val CODED_TYPE_DEF_OR_REF_TAG_BITS = 2
        private const val CODED_TYPE_DEF_OR_REF_TAG_MASK = (1 shl CODED_TYPE_DEF_OR_REF_TAG_BITS) - 1

        private const val CODED_HAS_SEMANTICS_EVENT = 0
        private const val CODED_HAS_SEMANTICS_PROPERTY = 1
        private const val CODED_HAS_SEMANTICS_TAG_BITS = 1
        private const val CODED_HAS_SEMANTICS_TAG_MASK = (1 shl CODED_HAS_SEMANTICS_TAG_BITS) - 1

        private val CODED_RESOLUTION_SCOPE = CodedIndex(
            tagBits = 2,
            tables = intArrayOf(TABLE_MODULE, TABLE_MODULE_REF, TABLE_ASSEMBLY_REF, TABLE_TYPE_REF),
        )
        private val CODED_TYPE_DEF_OR_REF = CodedIndex(
            tagBits = CODED_TYPE_DEF_OR_REF_TAG_BITS,
            tables = intArrayOf(TABLE_TYPE_DEF, TABLE_TYPE_REF, TABLE_TYPE_SPEC),
        )
        private val CODED_MEMBER_REF_PARENT = CodedIndex(
            tagBits = CODED_MEMBER_REF_PARENT_TAG_BITS,
            tables = intArrayOf(TABLE_TYPE_DEF, TABLE_TYPE_REF, TABLE_MODULE_REF, TABLE_METHOD_DEF, TABLE_TYPE_SPEC),
        )
        private val CODED_HAS_CUSTOM_ATTRIBUTE = CodedIndex(
            tagBits = CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS,
            tables = intArrayOf(
                TABLE_METHOD_DEF,
                TABLE_FIELD,
                TABLE_TYPE_REF,
                TABLE_TYPE_DEF,
                TABLE_PARAM,
                TABLE_INTERFACE_IMPL,
                TABLE_MEMBER_REF,
                TABLE_MODULE,
                -1,
                -1,
                -1,
                TABLE_STANDALONE_SIG,
                TABLE_MODULE_REF,
                TABLE_TYPE_SPEC,
                TABLE_ASSEMBLY,
                TABLE_ASSEMBLY_REF,
                -1,
                -1,
                -1,
                TABLE_GENERIC_PARAM,
                -1,
                -1,
            ),
        )
        private val CODED_CUSTOM_ATTRIBUTE_TYPE = CodedIndex(
            tagBits = CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS,
            tables = intArrayOf(-1, -1, TABLE_METHOD_DEF, TABLE_MEMBER_REF),
        )
        private val CODED_HAS_CONSTANT = CodedIndex(
            tagBits = 2,
            tables = intArrayOf(TABLE_FIELD, TABLE_PARAM, -1),
        )
        private val CODED_HAS_SEMANTICS = CodedIndex(
            tagBits = CODED_HAS_SEMANTICS_TAG_BITS,
            tables = intArrayOf(TABLE_EVENT, TABLE_PROPERTY),
        )
        private val CODED_METHOD_DEF_OR_REF = CodedIndex(
            tagBits = 1,
            tables = intArrayOf(TABLE_METHOD_DEF, TABLE_MEMBER_REF),
        )

        fun parse(buffer: ByteBuffer, offset: Int, stringsHeap: MetadataStream, blobHeap: MetadataStream): MetadataTables {
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

            for (tableId in 0..TABLE_GENERIC_PARAM) {
                if (rowCounts[tableId] == 0) {
                    continue
                }
                val currentRowSize = rowSize(tableId, rowCounts, stringIndexSize, guidIndexSize, blobIndexSize)
                if (currentRowSize == null) {
                    error("Metadata 2.4 parser does not yet support table 0x${tableId.toString(16)}")
                }
                tableOffsets[tableId] = tableCursor
                tableCursor += rowCounts[tableId] * currentRowSize
            }

            return MetadataTables(
                buffer = buffer,
                blobHeap = blobHeap,
                stringsHeap = stringsHeap,
                rowCounts = rowCounts,
                tableOffsets = tableOffsets,
                stringIndexSize = stringIndexSize,
                blobIndexSize = blobIndexSize,
            )
        }

        private fun rowSize(
            tableId: Int,
            rowCounts: IntArray,
            stringIndexSize: Int,
            guidIndexSize: Int,
            blobIndexSize: Int,
        ): Int? = when (tableId) {
            TABLE_MODULE -> 2 + stringIndexSize + guidIndexSize * 3
            TABLE_TYPE_REF -> codedIndexSize(CODED_RESOLUTION_SCOPE, rowCounts) + stringIndexSize + stringIndexSize
            TABLE_TYPE_DEF -> 4 + stringIndexSize + stringIndexSize + codedIndexSize(CODED_TYPE_DEF_OR_REF, rowCounts) + simpleIndexSize(TABLE_FIELD, rowCounts) + simpleIndexSize(TABLE_METHOD_DEF, rowCounts)
            TABLE_FIELD_PTR -> simpleIndexSize(TABLE_FIELD, rowCounts)
            TABLE_FIELD -> 2 + stringIndexSize + blobIndexSize
            TABLE_METHOD_PTR -> simpleIndexSize(TABLE_METHOD_DEF, rowCounts)
            TABLE_METHOD_DEF -> 8 + stringIndexSize + blobIndexSize + simpleIndexSize(TABLE_PARAM, rowCounts)
            TABLE_PARAM_PTR -> simpleIndexSize(TABLE_PARAM, rowCounts)
            TABLE_PARAM -> 4 + stringIndexSize
            TABLE_INTERFACE_IMPL -> simpleIndexSize(TABLE_TYPE_DEF, rowCounts) + codedIndexSize(CODED_TYPE_DEF_OR_REF, rowCounts)
            TABLE_MEMBER_REF -> codedIndexSize(CODED_MEMBER_REF_PARENT, rowCounts) + stringIndexSize + blobIndexSize
            TABLE_CONSTANT -> 2 + codedIndexSize(CODED_HAS_CONSTANT, rowCounts) + blobIndexSize
            TABLE_CUSTOM_ATTRIBUTE -> codedIndexSize(CODED_HAS_CUSTOM_ATTRIBUTE, rowCounts) + codedIndexSize(CODED_CUSTOM_ATTRIBUTE_TYPE, rowCounts) + blobIndexSize
            TABLE_STANDALONE_SIG -> blobIndexSize
            TABLE_EVENT_MAP -> simpleIndexSize(TABLE_TYPE_DEF, rowCounts) + simpleIndexSize(TABLE_EVENT, rowCounts)
            TABLE_EVENT_PTR -> simpleIndexSize(TABLE_EVENT, rowCounts)
            TABLE_EVENT -> 2 + stringIndexSize + codedIndexSize(CODED_TYPE_DEF_OR_REF, rowCounts)
            TABLE_PROPERTY_MAP -> simpleIndexSize(TABLE_TYPE_DEF, rowCounts) + simpleIndexSize(TABLE_PROPERTY, rowCounts)
            TABLE_PROPERTY_PTR -> simpleIndexSize(TABLE_PROPERTY, rowCounts)
            TABLE_PROPERTY -> 2 + stringIndexSize + blobIndexSize
            TABLE_METHOD_SEMANTICS -> 2 + simpleIndexSize(TABLE_METHOD_DEF, rowCounts) + codedIndexSize(CODED_HAS_SEMANTICS, rowCounts)
            TABLE_METHOD_IMPL -> simpleIndexSize(TABLE_TYPE_DEF, rowCounts) + codedIndexSize(CODED_METHOD_DEF_OR_REF, rowCounts) * 2
            TABLE_MODULE_REF -> stringIndexSize
            TABLE_TYPE_SPEC -> blobIndexSize
            TABLE_ASSEMBLY -> 16 + blobIndexSize + stringIndexSize * 2
            TABLE_ASSEMBLY_REF -> 12 + 4 + blobIndexSize + stringIndexSize * 2 + blobIndexSize
            TABLE_GENERIC_PARAM -> 4 + 2 + stringIndexSize
            else -> null
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
    val genericParameterCount: Int,
    val extendsToken: Int,
    val fieldListStart: Int,
    val methodListStart: Int,
) {
    val qualifiedName: String
        get() = if (namespace.isEmpty()) name else "$namespace.$name"
}

private data class RawMethodDef(
    val flags: Int,
    val name: String,
    val signatureBlobIndex: Int,
    val paramListStart: Int,
)

private data class RawParam(
    val flags: Int,
    val sequence: Int,
    val name: String,
)

private data class RawProperty(
    val name: String,
    val signatureBlobIndex: Int,
)

private data class RawEvent(
    val name: String,
    val delegateTypeName: String,
)

private data class CodedIndex(
    val tagBits: Int,
    val tables: IntArray,
) {
    fun maxRowCount(rowCounts: IntArray): Int = tables.maxOf { tableId -> if (tableId >= 0) rowCounts[tableId] else 0 }
}

private data class OwnerKey(
    val tableId: Int,
    val rowId: Int,
)

private data class DecodedCustomAttribute(
    val typeName: String,
    val stringArguments: List<String>,
)

private data class PropertyAccessorRows(
    val getterMethodRowId: Int? = null,
    val setterMethodRowId: Int? = null,
) {
    fun orEmpty(): PropertyAccessorRows = this
}

private data class EventAccessorRows(
    val addMethodRowId: Int? = null,
    val removeMethodRowId: Int? = null,
) {
    fun orEmpty(): EventAccessorRows = this
}

private data class MethodSemanticsData(
    val propertyAccessorsByPropertyRowId: Map<Int, PropertyAccessorRows> = emptyMap(),
    val eventAccessorsByEventRowId: Map<Int, EventAccessorRows> = emptyMap(),
    val semanticMethodRowIds: Set<Int> = emptySet(),
)

private data class ParsedTypeSignature(
    val typeName: String,
    val isByRef: Boolean = false,
)

private data class ParsedMethodSignature(
    val returnType: ParsedTypeSignature,
    val parameters: List<ParsedTypeSignature>,
)

private data class ParsedPropertySignature(
    val type: ParsedTypeSignature,
)

private class SignatureReader(
    private val bytes: ByteArray,
    private val typeDefNames: Array<String>,
    private val typeRefNames: Array<String>,
) {
    private var cursor: Int = 0

    fun readByte(): Int = bytes[cursor++].toInt() and 0xFF

    fun readCompressedUnsignedInt(): Int {
        val (value, nextCursor) = bytes.readCompressedUnsignedInt(cursor)
        cursor = nextCursor
        return value
    }

    fun readType(): ParsedTypeSignature {
        while (cursor < bytes.size) {
            when (peekByte()) {
                ELEMENT_TYPE_CMOD_REQD, ELEMENT_TYPE_CMOD_OPT -> {
                    readByte()
                    readCompressedUnsignedInt()
                }

                ELEMENT_TYPE_SENTINEL, ELEMENT_TYPE_PINNED -> readByte()
                else -> break
            }
        }
        if (cursor >= bytes.size) {
            return ParsedTypeSignature("Any")
        }

        return when (val elementType = readByte()) {
            ELEMENT_TYPE_VOID -> ParsedTypeSignature("Unit")
            ELEMENT_TYPE_BOOLEAN -> ParsedTypeSignature("Boolean")
            ELEMENT_TYPE_CHAR -> ParsedTypeSignature("Char")
            ELEMENT_TYPE_I1 -> ParsedTypeSignature("Byte")
            ELEMENT_TYPE_U1 -> ParsedTypeSignature("UByte")
            ELEMENT_TYPE_I2 -> ParsedTypeSignature("Short")
            ELEMENT_TYPE_U2 -> ParsedTypeSignature("UShort")
            ELEMENT_TYPE_I4 -> ParsedTypeSignature("Int")
            ELEMENT_TYPE_U4 -> ParsedTypeSignature("UInt")
            ELEMENT_TYPE_I8 -> ParsedTypeSignature("Long")
            ELEMENT_TYPE_U8 -> ParsedTypeSignature("ULong")
            ELEMENT_TYPE_R4 -> ParsedTypeSignature("Float")
            ELEMENT_TYPE_R8 -> ParsedTypeSignature("Double")
            ELEMENT_TYPE_STRING -> ParsedTypeSignature("String")
            ELEMENT_TYPE_OBJECT -> ParsedTypeSignature("Any")
            ELEMENT_TYPE_I -> ParsedTypeSignature("Long")
            ELEMENT_TYPE_U -> ParsedTypeSignature("ULong")
            ELEMENT_TYPE_BYREF -> readType().copy(isByRef = true)
            ELEMENT_TYPE_PTR -> readType()
            ELEMENT_TYPE_CLASS, ELEMENT_TYPE_VALUETYPE -> ParsedTypeSignature(
                normalizeSignatureTypeName(decodeTypeToken(readCompressedUnsignedInt())),
            )

            ELEMENT_TYPE_VAR -> ParsedTypeSignature("T${readCompressedUnsignedInt()}")
            ELEMENT_TYPE_MVAR -> ParsedTypeSignature("M${readCompressedUnsignedInt()}")
            ELEMENT_TYPE_SZARRAY -> ParsedTypeSignature("Array<${readType().typeName}>")
            ELEMENT_TYPE_ARRAY -> {
                val element = readType()
                val rank = readCompressedUnsignedInt()
                val sizes = readCompressedUnsignedInt()
                repeat(sizes) { readCompressedUnsignedInt() }
                val lowerBounds = readCompressedUnsignedInt()
                repeat(lowerBounds) { readCompressedUnsignedInt() }
                ParsedTypeSignature(if (rank <= 1) "Array<${element.typeName}>" else "Array<${element.typeName}>")
            }

            ELEMENT_TYPE_GENERICINST -> {
                val genericKind = readByte()
                val genericTypeName = if (genericKind == ELEMENT_TYPE_CLASS || genericKind == ELEMENT_TYPE_VALUETYPE) {
                    normalizeSignatureTypeName(decodeTypeToken(readCompressedUnsignedInt()))
                } else {
                    "Any"
                }
                val argumentCount = readCompressedUnsignedInt()
                val arguments = buildList(argumentCount) {
                    repeat(argumentCount) {
                        add(readType().typeName)
                    }
                }
                ParsedTypeSignature("$genericTypeName<${arguments.joinToString(", ")}>")
            }

            ELEMENT_TYPE_TYPEDBYREF, ELEMENT_TYPE_FNPTR, ELEMENT_TYPE_INTERNAL -> ParsedTypeSignature("Any")
            else -> ParsedTypeSignature(if (elementType == ELEMENT_TYPE_END) "Any" else "Any")
        }
    }

    private fun peekByte(): Int = bytes[cursor].toInt() and 0xFF

    private fun decodeTypeToken(token: Int): String? {
        val tag = token and TYPE_DEF_OR_REF_TAG_MASK
        val rowId = token ushr TYPE_DEF_OR_REF_TAG_BITS
        if (rowId == 0) {
            return null
        }
        return when (tag) {
            TYPE_DEF_OR_REF_TYPE_DEF -> typeDefNames.getOrNull(rowId - 1)
            TYPE_DEF_OR_REF_TYPE_REF -> typeRefNames.getOrNull(rowId - 1)
            else -> null
        }
    }

    companion object {
        private const val TYPE_DEF_OR_REF_TYPE_DEF = 0
        private const val TYPE_DEF_OR_REF_TYPE_REF = 1
        private const val TYPE_DEF_OR_REF_TAG_BITS = 2
        private const val TYPE_DEF_OR_REF_TAG_MASK = (1 shl TYPE_DEF_OR_REF_TAG_BITS) - 1

        private const val ELEMENT_TYPE_END = 0x00
        private const val ELEMENT_TYPE_VOID = 0x01
        private const val ELEMENT_TYPE_BOOLEAN = 0x02
        private const val ELEMENT_TYPE_CHAR = 0x03
        private const val ELEMENT_TYPE_I1 = 0x04
        private const val ELEMENT_TYPE_U1 = 0x05
        private const val ELEMENT_TYPE_I2 = 0x06
        private const val ELEMENT_TYPE_U2 = 0x07
        private const val ELEMENT_TYPE_I4 = 0x08
        private const val ELEMENT_TYPE_U4 = 0x09
        private const val ELEMENT_TYPE_I8 = 0x0A
        private const val ELEMENT_TYPE_U8 = 0x0B
        private const val ELEMENT_TYPE_R4 = 0x0C
        private const val ELEMENT_TYPE_R8 = 0x0D
        private const val ELEMENT_TYPE_STRING = 0x0E
        private const val ELEMENT_TYPE_PTR = 0x0F
        private const val ELEMENT_TYPE_BYREF = 0x10
        private const val ELEMENT_TYPE_VALUETYPE = 0x11
        private const val ELEMENT_TYPE_CLASS = 0x12
        private const val ELEMENT_TYPE_VAR = 0x13
        private const val ELEMENT_TYPE_ARRAY = 0x14
        private const val ELEMENT_TYPE_GENERICINST = 0x15
        private const val ELEMENT_TYPE_TYPEDBYREF = 0x16
        private const val ELEMENT_TYPE_I = 0x18
        private const val ELEMENT_TYPE_U = 0x19
        private const val ELEMENT_TYPE_FNPTR = 0x1B
        private const val ELEMENT_TYPE_OBJECT = 0x1C
        private const val ELEMENT_TYPE_SZARRAY = 0x1D
        private const val ELEMENT_TYPE_MVAR = 0x1E
        private const val ELEMENT_TYPE_CMOD_REQD = 0x1F
        private const val ELEMENT_TYPE_CMOD_OPT = 0x20
        private const val ELEMENT_TYPE_INTERNAL = 0x21
        private const val ELEMENT_TYPE_SENTINEL = 0x41
        private const val ELEMENT_TYPE_PINNED = 0x45
    }
}

private fun normalizeSignatureTypeName(typeName: String?): String = when (typeName) {
    null -> "Any"
    "System.Void" -> "Unit"
    "System.Boolean" -> "Boolean"
    "System.Char" -> "Char"
    "System.SByte" -> "Byte"
    "System.Byte" -> "UByte"
    "System.Int16" -> "Short"
    "System.UInt16" -> "UShort"
    "System.Int32" -> "Int"
    "System.UInt32" -> "UInt"
    "System.Int64" -> "Long"
    "System.UInt64" -> "ULong"
    "System.Single" -> "Float"
    "System.Double" -> "Double"
    "System.String" -> "String"
    "System.Object" -> "Any"
    else -> typeName
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

private fun ByteBuffer.readBlob(heapOffset: Int, index: Int): ByteArray {
    if (index == 0) {
        return ByteArray(0)
    }
    val (length, cursor) = readCompressedUnsignedInt(heapOffset + index)
    val bytes = ByteArray(length)
    for (offset in 0 until length) {
        bytes[offset] = get(cursor + offset)
    }
    return bytes
}

private fun ByteArray.readSerializedString(offset: Int): Pair<String, Int>? {
    if (offset >= size) {
        return null
    }
    if (this[offset] == 0xFF.toByte()) {
        return "" to (offset + 1)
    }
    val (length, cursor) = readCompressedUnsignedInt(offset)
    if (cursor + length > size) {
        return null
    }
    return copyOfRange(cursor, cursor + length).decodeToString() to (cursor + length)
}

private fun ByteBuffer.readCompressedUnsignedInt(offset: Int): Pair<Int, Int> {
    val first = get(offset).toInt() and 0xFF
    return when {
        first and 0x80 == 0 -> first to (offset + 1)
        first and 0xC0 == 0x80 -> (((first and 0x3F) shl 8) or (get(offset + 1).toInt() and 0xFF)) to (offset + 2)
        else -> {
            val second = get(offset + 1).toInt() and 0xFF
            val third = get(offset + 2).toInt() and 0xFF
            val fourth = get(offset + 3).toInt() and 0xFF
            (((first and 0x1F) shl 24) or (second shl 16) or (third shl 8) or fourth) to (offset + 4)
        }
    }
}

private fun splitGenericArity(typeName: String): Pair<String, Int> {
    val backtick = typeName.lastIndexOf('`')
    if (backtick <= 0 || backtick == typeName.lastIndex) {
        return typeName to 0
    }
    val arity = typeName.substring(backtick + 1).toIntOrNull() ?: return typeName to 0
    return typeName.substring(0, backtick) to arity
}

private fun ByteArray.readCompressedUnsignedInt(offset: Int): Pair<Int, Int> {
    val first = this[offset].toInt() and 0xFF
    return when {
        first and 0x80 == 0 -> first to (offset + 1)
        first and 0xC0 == 0x80 -> (((first and 0x3F) shl 8) or (this[offset + 1].toInt() and 0xFF)) to (offset + 2)
        else -> {
            val second = this[offset + 1].toInt() and 0xFF
            val third = this[offset + 2].toInt() and 0xFF
            val fourth = this[offset + 3].toInt() and 0xFF
            (((first and 0x1F) shl 24) or (second shl 16) or (third shl 8) or fourth) to (offset + 4)
        }
    }
}

private fun readIndex(offset: Int, size: Int, buffer: ByteBuffer): Int =
    when (size) {
        2 -> buffer.shortAt(offset).toInt() and 0xFFFF
        4 -> buffer.intAt(offset)
        else -> error("Unsupported index size $size")
    }

private fun alignToFour(size: Int): Int = (size + 3) and 3.inv()
