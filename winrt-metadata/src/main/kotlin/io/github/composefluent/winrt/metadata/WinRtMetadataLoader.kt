package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.Guid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.inputStream
import kotlin.io.path.name

object WinRtMetadataLoader {
    fun load(paths: List<Path>): WinRtMetadataModel {
        return WinRtMetadataSourceResolver.resolvePathInputs(paths).load()
    }

    fun loadSources(sources: List<WinRtMetadataSource>): WinRtMetadataModel {
        return WinRtMetadataSourceResolver.resolve(sources).load()
    }

    fun load(context: WinRtMetadataProjectionContext): WinRtMetadataModel {
        return context.load()
    }

    fun loadAuxiliaryTableInventory(sources: List<WinRtMetadataSource>): WinRtMetadataAuxiliaryTableInventory {
        val cache = WinRtMetadataSourceResolver.resolve(sources)
        return loadAuxiliaryTableInventoryFromFiles(cache.files)
    }

    fun loadAuxiliaryTableInventory(vararg sources: WinRtMetadataSource): WinRtMetadataAuxiliaryTableInventory =
        loadAuxiliaryTableInventory(sources.toList())

    internal fun loadDiscoveredFiles(files: List<Path>): WinRtMetadataModel {
        val namespaces = files
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

    fun load(vararg sources: WinRtMetadataSource): WinRtMetadataModel = loadSources(sources.toList())

    internal fun discoverMetadataFiles(paths: List<Path>): List<Path> =
        WinRtMetadataSourceResolver.resolvePathInputs(paths).files

    internal fun loadAuxiliaryTableInventoryFromFiles(files: List<Path>): WinRtMetadataAuxiliaryTableInventory =
        WinRtMetadataAuxiliaryTableInventory(
            files = files.map { file -> WinRtCliMetadataFile.parseAuxiliaryTableInventory(file) },
        )
}

private class WinRtCliMetadataFile private constructor(
    private val path: Path,
    private val buffer: ByteBuffer,
) {
    fun parseAuxiliaryTableInventory(): WinRtMetadataFileAuxiliaryTableInventory =
        WinRtMetadataFileAuxiliaryTableInventory(
            file = path,
            tables = parseTables().auxiliaryTableInventory(),
        )

    fun parseTypes(): List<WinRtTypeDefinition> {
        return parseTables().toTypeDefinitions()
    }

    private fun parseTables(): MetadataTables {
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
        return MetadataTables.parse(buffer, tablesStream.offset, stringsHeap, blobHeap)
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

        fun parseAuxiliaryTableInventory(path: Path): WinRtMetadataFileAuxiliaryTableInventory {
            path.inputStream().use { input ->
                val bytes = input.readAllBytes()
                return WinRtCliMetadataFile(
                    path = path,
                    buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN),
                ).parseAuxiliaryTableInventory()
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
    private val guidIndexSize: Int,
    private val blobIndexSize: Int,
) {
    fun auxiliaryTableInventory(): List<WinRtMetadataAuxiliaryTableDescriptor> =
        AUXILIARY_TABLE_IDS.map { tableId ->
            WinRtMetadataAuxiliaryTableDescriptor(
                tableId = tableId,
                tableName = tableName(tableId),
                rowCount = rowCounts[tableId],
                rowSize = rowSize(tableId, rowCounts, stringIndexSize, guidIndexSize, blobIndexSize) ?: 0,
                modeled = tableId in MODELED_AUXILIARY_TABLE_IDS,
            )
        }

    fun toTypeDefinitions(): List<WinRtTypeDefinition> {
        val typeDefs = readRawTypeDefs()
        val rawFields = readRawFields()
        val rawMethods = readRawMethodDefs()
        val rawParams = readRawParams()
        val typeRefNames = readTypeRefQualifiedNames()
        val typeDefNames = typeDefs.map { raw -> raw.qualifiedName }.toTypedArray()
        val typeSpecTypes = readTypeSpecTypes(typeDefNames, typeRefNames)
        val methodOwnerTypeNames = buildMethodOwnerTypeNames(typeDefs, typeDefNames)
        val memberRefs = readRawMemberRefs(typeDefNames, typeRefNames, typeSpecTypes, methodOwnerTypeNames)
        val methodImplementationsByOwner = readMethodImplementations(typeDefNames, rawMethods, memberRefs, methodOwnerTypeNames)
        val customAttributes = readCustomAttributes(rawMethods, methodOwnerTypeNames, memberRefs, typeDefNames, typeRefNames, typeSpecTypes)
        val fieldConstants = readFieldConstants()
        val parameterConstants = readParameterConstants()
        val classLayouts = readClassLayouts()
        val fieldOffsets = readFieldLayouts()
        val genericParametersByOwner = readGenericParameters(typeDefNames, typeRefNames, typeSpecTypes)
        val interfaceImplsByOwner = readInterfaceImplementations(typeDefNames, typeRefNames, typeSpecTypes, customAttributes)
        val propertyRowIdsByOwner = readMemberMap(TABLE_PROPERTY_MAP, TABLE_PROPERTY)
        val eventRowIdsByOwner = readMemberMap(TABLE_EVENT_MAP, TABLE_EVENT)
        val rawProperties = readRawProperties()
        val rawEvents = readRawEvents(typeDefNames, typeRefNames, typeSpecTypes)
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
                val genericParameters = genericParametersByOwner.typeParametersByType[rowId].orEmpty()
                val kind = raw.classify(typeDefNames, typeRefNames, typeSpecTypes)
                val fields = readFieldDefinitions(
                    typeIndex = index,
                    typeDefs = typeDefs,
                    rawFields = rawFields,
                    fieldConstants = fieldConstants,
                    fieldOffsets = fieldOffsets,
                    typeDefNames = typeDefNames,
                    typeRefNames = typeRefNames,
                    typeSpecTypes = typeSpecTypes,
                )
                val layout = (classLayouts[rowId] ?: WinRtTypeLayout()).copy(kind = raw.layoutKind())
                val abiLayout = computeTypeAbiLayout(kind, fields, layout)
                WinRtTypeDefinition(
                    namespace = raw.namespace,
                    name = raw.name,
                    kind = kind,
                    iid = extractGuid(typeAttributes),
                    baseTypeName = decodeTypeDefOrRefQualifiedName(raw.extendsToken, typeDefNames, typeRefNames, typeSpecTypes),
                    enumUnderlyingType = readEnumUnderlyingType(
                        typeIndex = index,
                        typeDefs = typeDefs,
                        rawFields = rawFields,
                        typeDefNames = typeDefNames,
                        typeRefNames = typeRefNames,
                        typeSpecTypes = typeSpecTypes,
                    ),
                    enumMembers = readEnumMembers(
                        kind = kind,
                        typeIndex = index,
                        typeDefs = typeDefs,
                        rawFields = rawFields,
                        fieldConstants = fieldConstants,
                    ),
                    fields = fields,
                    layout = layout,
                    isBlittable = abiLayout.isBlittable,
                    abiSize = abiLayout.size,
                    abiAlignment = abiLayout.alignment,
                    isProjectionInternal = typeAttributes.any { it.typeName == WINRT_INTEROP_PROJECTION_INTERNAL },
                    isExclusiveTo = typeAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO },
                    isApiContract = typeAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_API_CONTRACT },
                    isAttributeType = decodeTypeDefOrRefQualifiedName(raw.extendsToken, typeDefNames, typeRefNames, typeSpecTypes) == SYSTEM_ATTRIBUTE,
                    isStaticType = raw.classify(typeDefNames, typeRefNames, typeSpecTypes) == WinRtTypeKind.RuntimeClass &&
                        (raw.flags and TYPE_ATTRIBUTE_ABSTRACT) != 0,
                    isSealedType = (raw.flags and TYPE_ATTRIBUTE_SEALED) != 0,
                    isFastAbi = typeAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_FAST_ABI },
                    gcPressureAmount = extractGcPressureAmount(raw.flags, typeAttributes),
                    defaultInterfaceName = defaultInterfaceName,
                    implementedInterfaces = implementedInterfaces,
                    methodImplementations = methodImplementationsByOwner[rowId].orEmpty(),
                    genericParameterCount = maxOf(raw.genericParameterCount, genericParameters.size),
                    genericParameters = genericParameters,
                    customAttributes = typeAttributes,
                    activation = extractActivationShape(typeAttributes),
                    availability = extractAvailability(typeAttributes),
                    methods = readMethodDefinitions(
                        typeIndex = index,
                        typeDefs = typeDefs,
                        rawMethods = rawMethods,
                        parameterRowsByMethod = parameterRowsByMethod,
                        typeDefNames = typeDefNames,
                        typeRefNames = typeRefNames,
                        typeSpecTypes = typeSpecTypes,
                        semanticMethodRowIds = methodSemantics.semanticMethodRowIds,
                        customAttributes = customAttributes,
                        parameterConstants = parameterConstants,
                        genericParametersByMethod = genericParametersByOwner.typeParametersByMethod,
                    ),
                    properties = propertyRowIdsByOwner[rowId].orEmpty().mapNotNull { propertyRowId ->
                        buildPropertyDefinition(
                            propertyRowId = propertyRowId,
                            rawProperties = rawProperties,
                            propertyAccessors = methodSemantics.propertyAccessorsByPropertyRowId,
                            rawMethods = rawMethods,
                            typeDefNames = typeDefNames,
                            typeRefNames = typeRefNames,
                            typeSpecTypes = typeSpecTypes,
                            customAttributes = customAttributes,
                        )
                    },
                    events = eventRowIdsByOwner[rowId].orEmpty().mapNotNull { eventRowId ->
                        buildEventDefinition(
                            eventRowId = eventRowId,
                            rawEvents = rawEvents,
                            eventAccessors = methodSemantics.eventAccessorsByEventRowId,
                            rawMethods = rawMethods,
                            typeDefNames = typeDefNames,
                            typeRefNames = typeRefNames,
                            typeSpecTypes = typeSpecTypes,
                            customAttributes = customAttributes,
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
            val (name, _) = splitGenericArity(readStringAt(cursor))
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

    private fun readTypeSpecTypes(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
    ): Array<WinRtTypeRef> {
        val rowCount = rowCounts[TABLE_TYPE_SPEC]
        if (rowCount == 0) {
            return emptyArray()
        }
        val rows = Array(rowCount) { WinRtTypeRef.unknown() }
        var cursor = tableOffsets[TABLE_TYPE_SPEC]
        repeat(rowCount) { rowIndex ->
            val signatureBlobIndex = readIndex(cursor, blobIndexSize)
            cursor += blobIndexSize
            rows[rowIndex] = readTypeSpecSignature(signatureBlobIndex, typeDefNames, typeRefNames, rows)
                .type
                .normalized()
        }
        return rows
    }

    private fun readRawFields(): List<RawField> {
        val rowCount = rowCounts[TABLE_FIELD]
        if (rowCount == 0) {
            return emptyList()
        }

        val rows = ArrayList<RawField>(rowCount)
        var cursor = tableOffsets[TABLE_FIELD]
        repeat(rowCount) {
            val flags = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val signatureBlobIndex = readIndex(cursor, blobIndexSize)
            cursor += blobIndexSize
            rows += RawField(flags, name, signatureBlobIndex)
        }
        return rows
    }

    private fun readClassLayouts(): Map<Int, WinRtTypeLayout> {
        val rowCount = rowCounts[TABLE_CLASS_LAYOUT]
        if (rowCount == 0) {
            return emptyMap()
        }
        var cursor = tableOffsets[TABLE_CLASS_LAYOUT]
        return buildMap {
            repeat(rowCount) {
                val packingSize = buffer.shortAt(cursor).toInt() and 0xFFFF
                cursor += 2
                val classSize = buffer.intAt(cursor)
                cursor += 4
                val parentRowId = readIndex(cursor, simpleIndexSize(TABLE_TYPE_DEF))
                cursor += simpleIndexSize(TABLE_TYPE_DEF)
                if (parentRowId > 0) {
                    put(
                        parentRowId,
                        WinRtTypeLayout(
                            packingSize = packingSize.takeIf { it > 0 },
                            classSize = classSize.takeIf { it > 0 },
                        ),
                    )
                }
            }
        }
    }

    private fun readFieldLayouts(): Map<Int, Int> {
        val rowCount = rowCounts[TABLE_FIELD_LAYOUT]
        if (rowCount == 0) {
            return emptyMap()
        }
        var cursor = tableOffsets[TABLE_FIELD_LAYOUT]
        return buildMap {
            repeat(rowCount) {
                val offset = buffer.intAt(cursor)
                cursor += 4
                val fieldRowId = readIndex(cursor, simpleIndexSize(TABLE_FIELD))
                cursor += simpleIndexSize(TABLE_FIELD)
                if (fieldRowId > 0) {
                    put(fieldRowId, offset)
                }
            }
        }
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
        repeat(rowCount) { rowIndex ->
            val flags = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val sequence = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            rows += RawParam(rowIndex + 1, flags, sequence, name)
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
            val flags = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val signatureBlobIndex = readIndex(cursor, blobIndexSize)
            cursor += blobIndexSize
            rows += RawProperty(flags, name, signatureBlobIndex)
        }
        return rows
    }

    private fun readRawEvents(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
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
                    decodeTypeDefOrRefQualifiedName(eventTypeToken, typeDefNames, typeRefNames, typeSpecTypes),
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
        typeSpecTypes: Array<WinRtTypeRef>,
        semanticMethodRowIds: Set<Int>,
        customAttributes: Map<OwnerKey, List<DecodedCustomAttribute>>,
        parameterConstants: Map<Int, DecodedFieldConstant>,
        genericParametersByMethod: Map<Int, List<WinRtGenericParameterDefinition>>,
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
                val signature = readMethodSignature(rawMethod.signatureBlobIndex, typeDefNames, typeRefNames, typeSpecTypes)
                val methodAttributes = customAttributes[OwnerKey(TABLE_METHOD_DEF, methodRowId)].orEmpty()
                val returnParameterRow = parameterRowsByMethod[methodRowId].orEmpty().firstOrNull { it.sequence == 0 }
                val genericParameters = genericParametersByMethod[methodRowId].orEmpty()
                WinRtMethodDefinition(
                    name = rawMethod.name,
                    returnTypeName = signature.returnType.typeName,
                    returnTypeIsByRef = signature.returnType.isByRef,
                    returnTypeSignature = signature.returnType.type,
                    genericParameterCount = genericParameters.size,
                    genericParameters = genericParameters,
                    parameters = signature.parameters.mapIndexed { parameterIndex, parameterType ->
                        val parameterRow = parameterRowsByMethod[methodRowId].orEmpty().firstOrNull { it.sequence == parameterIndex + 1 }
                        val parameterConstant = parameterRow?.let { parameterConstants[it.rowId] }
                        WinRtParameterDefinition(
                            name = parameterRow?.name?.takeIf(String::isNotBlank) ?: "arg${parameterIndex + 1}",
                            typeName = parameterType.typeName,
                            direction = parameterDirectionFor(parameterRow, parameterType.isByRef),
                            typeIsByRef = parameterType.isByRef,
                            isInParameter = parameterRow?.flags?.and(PARAM_ATTRIBUTE_IN) != 0,
                            isOutParameter = parameterRow?.flags?.and(PARAM_ATTRIBUTE_OUT) != 0,
                            hasDefaultValue = parameterRow?.flags?.and(PARAM_ATTRIBUTE_HAS_DEFAULT) != 0 || parameterConstant != null,
                            defaultValueBits = parameterConstant?.valueBits,
                            defaultValueElementType = parameterConstant?.type,
                            typeSignature = parameterType.type,
                        )
                    },
                    isStatic = rawMethod.flags and METHOD_ATTRIBUTE_STATIC != 0,
                    visibility = methodVisibility(rawMethod.flags),
                    isSpecialName = rawMethod.flags and METHOD_ATTRIBUTE_SPECIAL_NAME != 0,
                    isRuntimeSpecialName = rawMethod.flags and METHOD_ATTRIBUTE_RT_SPECIAL_NAME != 0,
                    overloadName = methodAttributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_OVERLOAD }
                        ?.fixedArguments
                        ?.stringAt(0),
                    isDefaultOverload = methodAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_DEFAULT_OVERLOAD },
                    isNoException = methodAttributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_NO_EXCEPTION } || isRemoveOverload(rawMethod),
                    isRemoveOverload = isRemoveOverload(rawMethod),
                    isObjectEquals = isObjectEquals(rawMethod, signature),
                    isClassEquals = isClassEquals(rawMethod, signature, typeDefs[typeIndex].qualifiedName),
                    isObjectGetHashCode = isObjectGetHashCode(rawMethod, signature),
                    returnParameterAttributes = returnParameterRow?.let {
                        customAttributes[OwnerKey(TABLE_PARAM, it.rowId)]
                    }.orEmpty(),
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
        typeSpecTypes: Array<WinRtTypeRef>,
        customAttributes: Map<OwnerKey, List<DecodedCustomAttribute>>,
    ): WinRtPropertyDefinition? {
        val rawProperty = rawProperties.getOrNull(propertyRowId - 1) ?: return null
        val accessors = propertyAccessors[propertyRowId] ?: PropertyAccessorRows()
        val getter = accessors.getterMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val setter = accessors.setterMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val signature = readPropertySignature(rawProperty.signatureBlobIndex, typeDefNames, typeRefNames, typeSpecTypes)
        return WinRtPropertyDefinition(
            name = rawProperty.name,
            typeName = signature.type.typeName,
            isStatic = listOfNotNull(getter, setter).any { it.flags and METHOD_ATTRIBUTE_STATIC != 0 },
            getterMethodName = getter?.name,
            setterMethodName = setter?.name,
            getterMethodRowId = accessors.getterMethodRowId,
            setterMethodRowId = accessors.setterMethodRowId,
            isNoException = customAttributes[OwnerKey(TABLE_PROPERTY, propertyRowId)].orEmpty()
                .any { it.typeName == WINDOWS_FOUNDATION_METADATA_NO_EXCEPTION },
            hasValidAccessors = accessors.hasOnlyGetterSetter && (getter != null || setter != null),
            typeSignature = signature.type.type,
        )
    }

    private fun buildEventDefinition(
        eventRowId: Int,
        rawEvents: List<RawEvent>,
        eventAccessors: Map<Int, EventAccessorRows>,
        rawMethods: List<RawMethodDef>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
        customAttributes: Map<OwnerKey, List<DecodedCustomAttribute>>,
    ): WinRtEventDefinition? {
        val rawEvent = rawEvents.getOrNull(eventRowId - 1) ?: return null
        val accessors = eventAccessors[eventRowId] ?: EventAccessorRows()
        val addMethod = accessors.addMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val removeMethod = accessors.removeMethodRowId?.let { rawMethods.getOrNull(it - 1) }
        val delegateType = listOfNotNull(addMethod, removeMethod)
            .firstNotNullOfOrNull { method ->
                readMethodSignature(method.signatureBlobIndex, typeDefNames, typeRefNames, typeSpecTypes)
                    .parameters
                    .firstOrNull()
                    ?.type
            }
            ?: WinRtTypeRef.fromDisplayName(rawEvent.delegateTypeName)
        return WinRtEventDefinition(
            name = rawEvent.name,
            delegateTypeName = delegateType.typeName,
            isStatic = listOfNotNull(addMethod, removeMethod).any { it.flags and METHOD_ATTRIBUTE_STATIC != 0 },
            addMethodName = addMethod?.name,
            removeMethodName = removeMethod?.name,
            addMethodRowId = accessors.addMethodRowId,
            removeMethodRowId = accessors.removeMethodRowId,
            hasValidAccessors = accessors.hasOnlyAddRemove && addMethod != null && removeMethod != null,
            delegateTypeSignature = delegateType,
        )
    }

    private fun readFieldDefinitions(
        typeIndex: Int,
        typeDefs: List<RawTypeDef>,
        rawFields: List<RawField>,
        fieldConstants: Map<Int, DecodedFieldConstant>,
        fieldOffsets: Map<Int, Int>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): List<WinRtFieldDefinition> =
        enumFields(typeIndex, typeDefs, rawFields).map { (rowId, field) ->
            val signature = readFieldSignature(field.signatureBlobIndex, typeDefNames, typeRefNames, typeSpecTypes)
            val constant = fieldConstants[rowId]
            val abiLayout = computeFieldAbiLayout(signature.type)
            WinRtFieldDefinition(
                name = field.name,
                typeName = signature.type.typeName,
                flags = field.flags,
                rowId = rowId,
                offset = fieldOffsets[rowId],
                isStatic = field.flags and FIELD_ATTRIBUTE_STATIC != 0,
                isLiteral = field.flags and FIELD_ATTRIBUTE_LITERAL != 0,
                isInitOnly = field.flags and FIELD_ATTRIBUTE_INIT_ONLY != 0,
                hasConstant = field.flags and FIELD_ATTRIBUTE_HAS_DEFAULT != 0 || constant != null,
                constantValueBits = constant?.valueBits,
                constantElementType = constant?.type,
                abiSize = abiLayout.size,
                abiAlignment = abiLayout.alignment,
                isBlittable = abiLayout.isBlittable,
                typeSignature = signature.type,
            )
        }

    private fun readEnumUnderlyingType(
        typeIndex: Int,
        typeDefs: List<RawTypeDef>,
        rawFields: List<RawField>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): WinRtIntegralType? {
        val valueField = enumFields(typeIndex, typeDefs, rawFields)
            .firstOrNull { it.second.name == "value__" }
            ?: return null
        val signature = readFieldSignature(valueField.second.signatureBlobIndex, typeDefNames, typeRefNames, typeSpecTypes)
        return signature.typeName.toIntegralType()
    }

    private fun readEnumMembers(
        kind: WinRtTypeKind,
        typeIndex: Int,
        typeDefs: List<RawTypeDef>,
        rawFields: List<RawField>,
        fieldConstants: Map<Int, DecodedFieldConstant>,
    ): List<WinRtEnumMemberDefinition> {
        if (kind != WinRtTypeKind.Enum) {
            return emptyList()
        }
        return enumFields(typeIndex, typeDefs, rawFields)
            .asSequence()
            .filterNot { (_, field) -> field.name == "value__" }
            .filter { (_, field) -> field.flags and FIELD_ATTRIBUTE_STATIC != 0 }
            .mapNotNull { (rowId, field) ->
                fieldConstants[rowId]?.let { constant ->
                    WinRtEnumMemberDefinition(
                        name = field.name,
                        valueBits = constant.valueBits,
                    )
                }
            }
            .toList()
    }

    private fun enumFields(
        typeIndex: Int,
        typeDefs: List<RawTypeDef>,
        rawFields: List<RawField>,
    ): List<Pair<Int, RawField>> {
        if (rawFields.isEmpty()) {
            return emptyList()
        }
        val typeDef = typeDefs[typeIndex]
        val start = typeDef.fieldListStart.coerceAtLeast(1)
        val endExclusive = (typeDefs.getOrNull(typeIndex + 1)?.fieldListStart ?: (rawFields.size + 1)).coerceAtMost(rawFields.size + 1)
        if (start >= endExclusive) {
            return emptyList()
        }
        return (start until endExclusive).mapNotNull { fieldRowId ->
            rawFields.getOrNull(fieldRowId - 1)?.let { fieldRowId to it }
        }
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

    private fun computeTypeAbiLayout(
        kind: WinRtTypeKind,
        fields: List<WinRtFieldDefinition>,
        layout: WinRtTypeLayout,
    ): ComputedAbiLayout {
        if (kind == WinRtTypeKind.Enum) {
            return ComputedAbiLayout(size = 4, alignment = 4, isBlittable = true)
        }
        if (kind != WinRtTypeKind.Struct) {
            return ComputedAbiLayout()
        }
        val instanceFields = fields.filterNot { it.isStatic || it.isLiteral }
        if (instanceFields.any { !it.isBlittable || it.abiSize == null || it.abiAlignment == null }) {
            return ComputedAbiLayout(isBlittable = false)
        }
        if (layout.kind == WinRtTypeLayoutKind.Explicit) {
            val size = layout.classSize ?: instanceFields.maxOfOrNull { (it.offset ?: 0) + (it.abiSize ?: 0) }
            val alignment = instanceFields.maxOfOrNull { it.abiAlignment ?: 1 } ?: 1
            return ComputedAbiLayout(size = size, alignment = alignment, isBlittable = true)
        }

        var offset = 0
        var maxAlignment = 1
        instanceFields.forEach { field ->
            val fieldAlignment = minOf(field.abiAlignment ?: 1, layout.packingSize ?: Int.MAX_VALUE)
            maxAlignment = maxOf(maxAlignment, fieldAlignment)
            offset = alignTo(offset, fieldAlignment)
            offset += field.abiSize ?: 0
        }
        val computedSize = alignTo(offset, maxAlignment)
        return ComputedAbiLayout(
            size = maxOf(layout.classSize ?: 0, computedSize).takeIf { it > 0 },
            alignment = maxAlignment,
            isBlittable = true,
        )
    }

    private fun computeFieldAbiLayout(type: WinRtTypeRef): ComputedAbiLayout {
        val normalized = type.normalized()
        return when (normalized.kind) {
            WinRtTypeRefKind.Named -> when (normalized.typeName) {
                "Byte", "UByte" -> ComputedAbiLayout(size = 1, alignment = 1, isBlittable = true)
                "Short", "UShort" -> ComputedAbiLayout(size = 2, alignment = 2, isBlittable = true)
                "Int", "UInt", "Float" -> ComputedAbiLayout(size = 4, alignment = 4, isBlittable = true)
                "Long", "ULong", "Double" -> ComputedAbiLayout(size = 8, alignment = 8, isBlittable = true)
                "Boolean", "Char", "String" -> ComputedAbiLayout(isBlittable = false)
                else -> ComputedAbiLayout(isBlittable = false)
            }

            WinRtTypeRefKind.Array,
            WinRtTypeRefKind.GenericTypeParameter,
            WinRtTypeRefKind.MethodTypeParameter,
            WinRtTypeRefKind.Unknown -> ComputedAbiLayout(isBlittable = false)
        }
    }

    private fun alignTo(value: Int, alignment: Int): Int {
        if (alignment <= 1) {
            return value
        }
        val remainder = value % alignment
        return if (remainder == 0) value else value + alignment - remainder
    }

    private fun readFieldConstants(): Map<Int, DecodedFieldConstant> {
        return readConstants(CODED_HAS_CONSTANT_FIELD)
    }

    private fun readParameterConstants(): Map<Int, DecodedFieldConstant> {
        return readConstants(CODED_HAS_CONSTANT_PARAM)
    }

    private fun readConstants(ownerTagToRead: Int): Map<Int, DecodedFieldConstant> {
        val rowCount = rowCounts[TABLE_CONSTANT]
        if (rowCount == 0) {
            return emptyMap()
        }

        var cursor = tableOffsets[TABLE_CONSTANT]
        return buildMap {
            repeat(rowCount) {
                val type = buffer.byteAt(cursor).toInt() and 0xFF
                cursor += 1
                cursor += 1
                val ownerToken = readIndex(cursor, codedIndexSize(CODED_HAS_CONSTANT))
                cursor += codedIndexSize(CODED_HAS_CONSTANT)
                val blobIndex = readIndex(cursor, blobIndexSize)
                cursor += blobIndexSize

                val ownerTag = ownerToken and CODED_HAS_CONSTANT_TAG_MASK
                val ownerRowId = ownerToken ushr CODED_HAS_CONSTANT_TAG_BITS
                if (ownerTag == ownerTagToRead && ownerRowId > 0) {
                    put(ownerRowId, decodeFieldConstant(type, blobIndex))
                }
            }
        }
    }

    private data class GenericParametersByOwner(
        val typeParametersByType: Map<Int, List<WinRtGenericParameterDefinition>>,
        val typeParametersByMethod: Map<Int, List<WinRtGenericParameterDefinition>>,
    )

    private fun readGenericParameters(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): GenericParametersByOwner {
        val rowCount = rowCounts[TABLE_GENERIC_PARAM]
        if (rowCount == 0) {
            return GenericParametersByOwner(emptyMap(), emptyMap())
        }
        val constraintsByParameter = readGenericParameterConstraints(typeDefNames, typeRefNames, typeSpecTypes)
        var cursor = tableOffsets[TABLE_GENERIC_PARAM]
        val typeParameters = mutableListOf<Pair<Int, WinRtGenericParameterDefinition>>()
        val methodParameters = mutableListOf<Pair<Int, WinRtGenericParameterDefinition>>()
        repeat(rowCount) { rowIndex ->
            val number = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val flags = buffer.shortAt(cursor).toInt() and 0xFFFF
            cursor += 2
            val ownerToken = readIndex(cursor, codedIndexSize(CODED_TYPE_OR_METHOD_DEF))
            cursor += codedIndexSize(CODED_TYPE_OR_METHOD_DEF)
            val name = readStringAt(cursor)
            cursor += stringIndexSize

            val ownerTag = ownerToken and CODED_TYPE_OR_METHOD_DEF_TAG_MASK
            val ownerRowId = ownerToken ushr CODED_TYPE_OR_METHOD_DEF_TAG_BITS
            if (ownerRowId <= 0) {
                return@repeat
            }
            val parameter = WinRtGenericParameterDefinition(
                name = name,
                index = number,
                flags = flags,
                constraints = constraintsByParameter[rowIndex + 1].orEmpty(),
            )
            when (ownerTag) {
                CODED_TYPE_OR_METHOD_DEF_TYPE_DEF -> typeParameters += ownerRowId to parameter
                CODED_TYPE_OR_METHOD_DEF_METHOD_DEF -> methodParameters += ownerRowId to parameter
            }
        }
        return GenericParametersByOwner(
            typeParametersByType = typeParameters.toGenericParameterMap(),
            typeParametersByMethod = methodParameters.toGenericParameterMap(),
        )
    }

    private fun List<Pair<Int, WinRtGenericParameterDefinition>>.toGenericParameterMap(): Map<Int, List<WinRtGenericParameterDefinition>> =
        groupBy({ it.first }, { it.second })
            .mapValues { (_, parameters) ->
                parameters
                    .groupBy(WinRtGenericParameterDefinition::index)
                    .values
                    .map { duplicates -> duplicates.reduce(WinRtGenericParameterDefinition::merge) }
                    .sortedBy(WinRtGenericParameterDefinition::index)
            }

    private fun readGenericParameterConstraints(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): Map<Int, List<String>> {
        val rowCount = rowCounts[TABLE_GENERIC_PARAM_CONSTRAINT]
        if (rowCount == 0) {
            return emptyMap()
        }
        var cursor = tableOffsets[TABLE_GENERIC_PARAM_CONSTRAINT]
        return buildList<Pair<Int, String>>(rowCount) {
            repeat(rowCount) {
                val ownerRowId = readIndex(cursor, simpleIndexSize(TABLE_GENERIC_PARAM))
                cursor += simpleIndexSize(TABLE_GENERIC_PARAM)
                val constraintToken = readIndex(cursor, codedIndexSize(CODED_TYPE_DEF_OR_REF))
                cursor += codedIndexSize(CODED_TYPE_DEF_OR_REF)
                val constraintName = decodeTypeDefOrRefQualifiedName(
                    constraintToken,
                    typeDefNames,
                    typeRefNames,
                    typeSpecTypes,
                )
                if (ownerRowId > 0 && constraintName != null) {
                    add(ownerRowId to constraintName)
                }
            }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, constraints) -> constraints.distinct().sorted() }
    }

    private fun readInterfaceImplementations(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
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
                val interfaceName = decodeTypeDefOrRefQualifiedName(interfaceToken, typeDefNames, typeRefNames, typeSpecTypes)
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

    private fun readMethodImplementations(
        typeDefNames: Array<String>,
        rawMethods: List<RawMethodDef>,
        memberRefs: Array<RawMemberRef?>,
        methodOwnerTypeNames: Array<String?>,
    ): Map<Int, List<WinRtMethodImplementationDefinition>> {
        val rowCount = rowCounts[TABLE_METHOD_IMPL]
        if (rowCount == 0) {
            return emptyMap()
        }

        var cursor = tableOffsets[TABLE_METHOD_IMPL]
        return buildList<Pair<Int, WinRtMethodImplementationDefinition>>(rowCount) {
            repeat(rowCount) {
                val classRowId = readIndex(cursor, simpleIndexSize(TABLE_TYPE_DEF))
                cursor += simpleIndexSize(TABLE_TYPE_DEF)
                val bodyToken = readIndex(cursor, codedIndexSize(CODED_METHOD_DEF_OR_REF))
                cursor += codedIndexSize(CODED_METHOD_DEF_OR_REF)
                val declarationToken = readIndex(cursor, codedIndexSize(CODED_METHOD_DEF_OR_REF))
                cursor += codedIndexSize(CODED_METHOD_DEF_OR_REF)
                val classTypeName = typeDefNames.getOrNull(classRowId - 1) ?: return@repeat
                add(
                    classRowId to WinRtMethodImplementationDefinition(
                        classTypeName = classTypeName,
                        body = decodeMethodDefOrRef(bodyToken, rawMethods, memberRefs, methodOwnerTypeNames),
                        declaration = decodeMethodDefOrRef(declarationToken, rawMethods, memberRefs, methodOwnerTypeNames),
                    ),
                )
            }
        }.groupBy({ it.first }, { it.second.normalized() })
            .mapValues { (_, implementations) ->
                implementations.distinct().sortedWith(
                    compareBy(
                        { it.declaration.ownerTypeName.orEmpty() },
                        { it.declaration.name.orEmpty() },
                        { it.body.name.orEmpty() },
                    ),
                )
            }
    }

    private fun decodeMethodDefOrRef(
        token: Int,
        rawMethods: List<RawMethodDef>,
        memberRefs: Array<RawMemberRef?>,
        methodOwnerTypeNames: Array<String?>,
    ): WinRtMethodImplementationMember {
        val tag = token and CODED_METHOD_DEF_OR_REF_TAG_MASK
        val rowId = token ushr CODED_METHOD_DEF_OR_REF_TAG_BITS
        return when (tag) {
            CODED_METHOD_DEF_OR_REF_METHOD_DEF -> {
                val method = rawMethods.getOrNull(rowId - 1)
                WinRtMethodImplementationMember(
                    kind = WinRtMethodImplementationMemberKind.MethodDefinition,
                    rowId = rowId,
                    name = method?.name,
                    ownerTypeName = methodOwnerTypeNames.getOrNull(rowId),
                )
            }

            CODED_METHOD_DEF_OR_REF_MEMBER_REF -> {
                val memberRef = memberRefs.getOrNull(rowId - 1)
                WinRtMethodImplementationMember(
                    kind = WinRtMethodImplementationMemberKind.MemberReference,
                    rowId = rowId,
                    name = memberRef?.name,
                    ownerTypeName = memberRef?.ownerTypeName,
                )
            }

            else -> WinRtMethodImplementationMember(
                kind = WinRtMethodImplementationMemberKind.Unknown,
                rowId = rowId,
            )
        }
    }

    private fun readCustomAttributes(
        rawMethods: List<RawMethodDef>,
        methodOwnerTypeNames: Array<String?>,
        memberRefs: Array<RawMemberRef?>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
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
                val attributeConstructor = decodeCustomAttributeConstructor(
                    token = typeToken,
                    rawMethods = rawMethods,
                    methodOwnerTypeNames = methodOwnerTypeNames,
                    memberRefs = memberRefs,
                    typeDefNames = typeDefNames,
                    typeRefNames = typeRefNames,
                    typeSpecTypes = typeSpecTypes,
                )
                    ?: return@repeat
                add(owner to readCustomAttribute(valueIndex, attributeConstructor))
            }
        }.groupBy({ it.first }, { it.second })
    }

    private fun readRawMemberRefs(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
        methodOwnerTypeNames: Array<String?>,
    ): Array<RawMemberRef?> {
        val rowCount = rowCounts[TABLE_MEMBER_REF]
        if (rowCount == 0) {
            return emptyArray()
        }

        val rows = arrayOfNulls<RawMemberRef>(rowCount)
        var cursor = tableOffsets[TABLE_MEMBER_REF]
        repeat(rowCount) { rowIndex ->
            val parentToken = readIndex(cursor, codedIndexSize(CODED_MEMBER_REF_PARENT))
            cursor += codedIndexSize(CODED_MEMBER_REF_PARENT)
            val name = readStringAt(cursor)
            cursor += stringIndexSize
            val signatureBlobIndex = readIndex(cursor, blobIndexSize)
            cursor += blobIndexSize
            val parentTag = parentToken and CODED_MEMBER_REF_PARENT_TAG_MASK
            val parentRowId = parentToken ushr CODED_MEMBER_REF_PARENT_TAG_BITS
            val ownerTypeName = when (parentTag) {
                CODED_MEMBER_REF_PARENT_TYPE_DEF -> typeDefNames.getOrNull(parentRowId - 1)
                CODED_MEMBER_REF_PARENT_TYPE_REF -> typeRefNames.getOrNull(parentRowId - 1)
                CODED_MEMBER_REF_PARENT_TYPE_SPEC -> typeSpecTypes.getOrNull(parentRowId - 1)?.typeName
                CODED_MEMBER_REF_PARENT_METHOD_DEF -> methodOwnerTypeNames.getOrNull(parentRowId)
                else -> null
            }
            rows[rowIndex] = ownerTypeName?.let {
                RawMemberRef(
                    ownerTypeName = it,
                    name = name,
                    signatureBlobIndex = signatureBlobIndex,
                )
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

                associationTag == CODED_HAS_SEMANTICS_PROPERTY -> {
                    propertyAccessors[associationRowId] = (propertyAccessors[associationRowId] ?: PropertyAccessorRows()).copy(hasOnlyGetterSetter = false)
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

                associationTag == CODED_HAS_SEMANTICS_EVENT -> {
                    eventAccessors[associationRowId] = (eventAccessors[associationRowId] ?: EventAccessorRows()).copy(hasOnlyAddRemove = false)
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

    private fun RawTypeDef.classify(
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): WinRtTypeKind {
        if (flags and TYPE_ATTRIBUTE_INTERFACE != 0) {
            return WinRtTypeKind.Interface
        }

        val extendsName = decodeTypeDefOrRefQualifiedName(extendsToken, typeDefNames, typeRefNames, typeSpecTypes)
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
        typeSpecTypes: Array<WinRtTypeRef>,
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
            CODED_TYPE_DEF_OR_REF_TYPE_SPEC -> typeSpecTypes.getOrNull(rowId - 1)?.typeName
            else -> null
        }
    }

    private fun decodeCustomAttributeConstructor(
        token: Int,
        rawMethods: List<RawMethodDef>,
        methodOwnerTypeNames: Array<String?>,
        memberRefs: Array<RawMemberRef?>,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): DecodedCustomAttributeConstructor? {
        if (token == 0) {
            return null
        }
        val tag = token and CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_MASK
        val rowId = token ushr CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS
        if (rowId == 0) {
            return null
        }
        return when (tag) {
            CODED_CUSTOM_ATTRIBUTE_TYPE_METHOD_DEF ->
                methodOwnerTypeNames.getOrNull(rowId)?.let { typeName ->
                    DecodedCustomAttributeConstructor(
                        typeName = typeName,
                        fixedArgumentTypes = readCustomAttributeConstructorArgumentTypes(
                            rawMethods.getOrNull(rowId - 1)?.signatureBlobIndex ?: 0,
                            typeDefNames,
                            typeRefNames,
                            typeSpecTypes,
                        ),
                    )
                }

            CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF ->
                memberRefs.getOrNull(rowId - 1)?.let { memberRef ->
                    DecodedCustomAttributeConstructor(
                        typeName = memberRef.ownerTypeName,
                        fixedArgumentTypes = readCustomAttributeConstructorArgumentTypes(
                            memberRef.signatureBlobIndex,
                            typeDefNames,
                            typeRefNames,
                            typeSpecTypes,
                        ),
                    )
                }

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
            CODED_HAS_CUSTOM_ATTRIBUTE_METHOD_DEF -> OwnerKey(TABLE_METHOD_DEF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_FIELD -> OwnerKey(TABLE_FIELD, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_REF -> OwnerKey(TABLE_TYPE_REF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF -> OwnerKey(TABLE_TYPE_DEF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_PARAM -> OwnerKey(TABLE_PARAM, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL -> OwnerKey(TABLE_INTERFACE_IMPL, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_MEMBER_REF -> OwnerKey(TABLE_MEMBER_REF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_PROPERTY -> OwnerKey(TABLE_PROPERTY, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_EVENT -> OwnerKey(TABLE_EVENT, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_MODULE -> OwnerKey(TABLE_MODULE, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_STANDALONE_SIG -> OwnerKey(TABLE_STANDALONE_SIG, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_MODULE_REF -> OwnerKey(TABLE_MODULE_REF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_SPEC -> OwnerKey(TABLE_TYPE_SPEC, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_ASSEMBLY -> OwnerKey(TABLE_ASSEMBLY, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_ASSEMBLY_REF -> OwnerKey(TABLE_ASSEMBLY_REF, rowId)
            CODED_HAS_CUSTOM_ATTRIBUTE_GENERIC_PARAM -> OwnerKey(TABLE_GENERIC_PARAM, rowId)
            else -> null
        }
    }

    private fun readCustomAttributeConstructorArgumentTypes(
        blobIndex: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): List<WinRtTypeRef> {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return emptyList()
        }
        val reader = SignatureReader(bytes, typeDefNames, typeRefNames, typeSpecTypes)
        val callingConvention = reader.readByte()
        if (callingConvention and CALL_CONV_GENERIC != 0) {
            reader.readCompressedUnsignedInt()
        }
        val parameterCount = reader.readCompressedUnsignedInt()
        reader.readType()
        return buildList(parameterCount) {
            repeat(parameterCount) {
                add(reader.readType().type)
            }
        }
    }

    private fun readCustomAttribute(
        blobIndex: Int,
        constructor: DecodedCustomAttributeConstructor,
    ): DecodedCustomAttribute {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.size < 2 || bytes[0] != 0x01.toByte() || bytes[1] != 0x00.toByte()) {
            return DecodedCustomAttribute(typeName = constructor.typeName)
        }
        val reader = CustomAttributeBlobReader(bytes)
        reader.readUInt16()
        val fixedArguments = constructor.fixedArgumentTypes.map { type ->
            reader.readFixedArgument(type)
        }
        val namedArguments = reader.readNamedArguments()
        return DecodedCustomAttribute(
            typeName = constructor.typeName,
            fixedArguments = fixedArguments,
            namedArguments = namedArguments,
        )
    }

    private fun decodeFieldConstant(type: Int, blobIndex: Int): DecodedFieldConstant {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        val valueBits = when (type) {
            0x02 -> bytes.firstOrNull()?.toInt()?.let { if (it == 0) 0uL else 1uL } ?: 0uL
            0x04 -> bytes.firstOrNull()?.toByte()?.toLong()?.toULong() ?: 0uL
            0x05 -> bytes.firstOrNull()?.toUByte()?.toULong() ?: 0uL
            0x06 -> bytes.toLittleEndianShort()?.toLong()?.toULong() ?: 0uL
            0x07, 0x03 -> bytes.toLittleEndianShort()?.toUShort()?.toULong() ?: 0uL
            0x08 -> bytes.toLittleEndianInt()?.toLong()?.toULong() ?: 0uL
            0x09 -> bytes.toLittleEndianInt()?.toUInt()?.toULong() ?: 0uL
            0x0A, 0x0B -> bytes.toLittleEndianLong()?.toULong() ?: 0uL
            else -> 0uL
        }
        return DecodedFieldConstant(type = type, valueBits = valueBits)
    }

    private fun extractGuid(attributes: List<DecodedCustomAttribute>): Guid? {
        val guidAttribute = attributes.firstOrNull { it.typeName in GUID_ATTRIBUTE_NAMES } ?: return null
        val value = guidAttribute.stringArguments.firstOrNull()
            ?: guidAttribute.fixedArguments.toGuidAttributeString()
            ?: return null
        return runCatching { Guid(value) }.getOrNull()
    }

    private fun List<WinRtCustomAttributeValue>.toGuidAttributeString(): String? {
        if (size != 11) {
            return null
        }
        val values = map { (it as? WinRtCustomAttributeValue.IntegralValue)?.value ?: return null }
        val data1 = values[0] and 0xFFFF_FFFFL
        val data2 = values[1] and 0xFFFFL
        val data3 = values[2] and 0xFFFFL
        val data4 = values.drop(3).map { it and 0xFFL }
        return String.format(
            Locale.ROOT,
            "%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X",
            data1,
            data2,
            data3,
            data4[0],
            data4[1],
            data4[2],
            data4[3],
            data4[4],
            data4[5],
            data4[6],
            data4[7],
        )
    }

    private fun extractActivationShape(attributes: List<DecodedCustomAttribute>): WinRtActivationShape {
        val activatable = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_ACTIVATABLE }
        val staticAttributes = attributes.filter { it.typeName == WINDOWS_FOUNDATION_METADATA_STATIC }
        val composable = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_COMPOSABLE }
        val factories = attributes.mapNotNull { attribute ->
            val kind = when (attribute.typeName) {
                WINDOWS_FOUNDATION_METADATA_ACTIVATABLE -> WinRtAttributedFactoryKind.Activatable
                WINDOWS_FOUNDATION_METADATA_STATIC -> WinRtAttributedFactoryKind.Static
                WINDOWS_FOUNDATION_METADATA_COMPOSABLE -> WinRtAttributedFactoryKind.Composable
                else -> return@mapNotNull null
            }
            val interfaceName = attribute.stringArguments.firstOrNull() ?: return@mapNotNull null
            WinRtAttributedFactoryShape(
                interfaceName = interfaceName,
                kind = kind,
                isVisible = kind == WinRtAttributedFactoryKind.Composable &&
                    attribute.fixedArguments.any { value ->
                        (value as? WinRtCustomAttributeValue.EnumValue)?.value == 2L ||
                            (value as? WinRtCustomAttributeValue.IntegralValue)?.value == 2L
                    },
            )
        }
        return WinRtActivationShape(
            isActivatable = activatable != null,
            activatableFactoryInterfaceName = activatable?.stringArguments?.firstOrNull(),
            staticInterfaceNames = staticAttributes.mapNotNull { it.stringArguments.firstOrNull() },
            composableFactoryInterfaceName = composable?.stringArguments?.firstOrNull(),
            factories = factories,
        )
    }

    private fun extractAvailability(attributes: List<DecodedCustomAttribute>): WinRtAvailabilityMetadata =
        WinRtAvailabilityMetadata(
            contractVersion = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_CONTRACT_VERSION }
                ?.toContractVersionMetadata(),
            version = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_VERSION }
                ?.fixedArguments
                ?.integralAt(0),
            previousContractVersions = attributes
                .filter { it.typeName == WINDOWS_FOUNDATION_METADATA_PREVIOUS_CONTRACT_VERSION }
                .mapNotNull { it.toContractVersionMetadata() },
            deprecations = attributes
                .filter { it.typeName == WINDOWS_FOUNDATION_METADATA_DEPRECATED }
                .map { attribute ->
                    WinRtDeprecationMetadata(
                        message = attribute.fixedArguments.stringAt(0),
                        kind = attribute.fixedArguments.enumOrIntegralAt(1),
                        version = attribute.fixedArguments.integralAt(2),
                        contractName = attribute.fixedArguments.contractNameAt(3),
                    )
                },
            threadingModel = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_THREADING }
                ?.fixedArguments
                ?.enumOrIntegralAt(0),
            marshalingBehavior = attributes.firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_MARSHALING_BEHAVIOR }
                ?.fixedArguments
                ?.enumOrIntegralAt(0),
            isMuse = attributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_MUSE },
            isWebHostHidden = attributes.any { it.typeName == WINDOWS_FOUNDATION_METADATA_WEB_HOST_HIDDEN },
        )

    private fun extractGcPressureAmount(typeFlags: Int, attributes: List<DecodedCustomAttribute>): Int {
        if ((typeFlags and TYPE_ATTRIBUTE_SEALED) == 0) {
            return 0
        }
        val amount = attributes
            .firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_GC_PRESSURE }
            ?.namedArguments
            ?.firstOrNull()
            ?.value
            ?.enumOrIntegralValue()
            ?: return 0
        return when (amount) {
            0L -> 12_000
            1L -> 120_000
            else -> 1_200_000
        }
    }

    private fun DecodedCustomAttribute.toContractVersionMetadata(): WinRtContractVersionMetadata? {
        val contractName = fixedArguments.contractNameAt(0) ?: return null
        val version = fixedArguments.integralAt(1) ?: return null
        val majorVersion = (version ushr 16).toInt()
        return WinRtContractVersionMetadata(
            contractName = contractName,
            version = version,
            majorVersion = majorVersion,
            platformVersion = getContractPlatform(contractName, majorVersion),
        )
    }

    private fun List<WinRtCustomAttributeValue>.contractNameAt(index: Int): String? =
        when (val value = getOrNull(index)) {
            is WinRtCustomAttributeValue.TypeValue -> value.typeName
            is WinRtCustomAttributeValue.StringValue -> value.value
            else -> null
        }

    private fun List<WinRtCustomAttributeValue>.stringAt(index: Int): String? =
        (getOrNull(index) as? WinRtCustomAttributeValue.StringValue)?.value

    private fun List<WinRtCustomAttributeValue>.integralAt(index: Int): Long? =
        (getOrNull(index) as? WinRtCustomAttributeValue.IntegralValue)?.value

    private fun List<WinRtCustomAttributeValue>.enumOrIntegralAt(index: Int): Long? =
        when (val value = getOrNull(index)) {
            is WinRtCustomAttributeValue.EnumValue -> value.value
            is WinRtCustomAttributeValue.IntegralValue -> value.value
            else -> null
        }

    private fun WinRtCustomAttributeValue.enumOrIntegralValue(): Long? =
        when (this) {
            is WinRtCustomAttributeValue.EnumValue -> value
            is WinRtCustomAttributeValue.IntegralValue -> value
            else -> null
        }

    private fun getContractPlatform(contractName: String, contractVersion: Int): String? =
        CONTRACT_PLATFORM_VERSIONS[contractName]?.get(contractVersion)

    private fun readMethodSignature(
        blobIndex: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): ParsedMethodSignature {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return ParsedMethodSignature(ParsedTypeSignature(WinRtTypeRef.named("Unit")), emptyList())
        }

        val reader = SignatureReader(bytes, typeDefNames, typeRefNames, typeSpecTypes)
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
        typeSpecTypes: Array<WinRtTypeRef>,
    ): ParsedPropertySignature {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return ParsedPropertySignature(ParsedTypeSignature(WinRtTypeRef.unknown()))
        }

        val reader = SignatureReader(bytes, typeDefNames, typeRefNames, typeSpecTypes)
        reader.readByte()
        val parameterCount = reader.readCompressedUnsignedInt()
        val type = reader.readType()
        repeat(parameterCount) {
            reader.readType()
        }
        return ParsedPropertySignature(type)
    }

    private fun readFieldSignature(
        blobIndex: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): ParsedTypeSignature {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return ParsedTypeSignature(WinRtTypeRef.unknown())
        }
        val reader = SignatureReader(bytes, typeDefNames, typeRefNames, typeSpecTypes)
        reader.readByte()
        return reader.readType()
    }

    private fun readTypeSpecSignature(
        blobIndex: Int,
        typeDefNames: Array<String>,
        typeRefNames: Array<String>,
        typeSpecTypes: Array<WinRtTypeRef>,
    ): ParsedTypeSignature {
        val bytes = buffer.readBlob(blobHeap.offset, blobIndex)
        if (bytes.isEmpty()) {
            return ParsedTypeSignature(WinRtTypeRef.unknown())
        }
        return SignatureReader(bytes, typeDefNames, typeRefNames, typeSpecTypes).readType()
    }

    private fun parameterDirectionFor(rawParam: RawParam?, isByRef: Boolean): WinRtParameterDirection = when {
        rawParam != null && rawParam.flags and PARAM_ATTRIBUTE_OUT != 0 && !isByRef && rawParam.flags and PARAM_ATTRIBUTE_IN == 0 ->
            WinRtParameterDirection.Out
        !isByRef -> WinRtParameterDirection.In
        rawParam == null -> WinRtParameterDirection.Ref
        rawParam.flags and PARAM_ATTRIBUTE_OUT != 0 && rawParam.flags and PARAM_ATTRIBUTE_IN == 0 -> WinRtParameterDirection.Out
        else -> WinRtParameterDirection.Ref
    }

    private fun methodVisibility(flags: Int): WinRtMethodVisibility =
        when (flags and METHOD_ATTRIBUTE_MEMBER_ACCESS_MASK) {
            METHOD_ATTRIBUTE_PRIVATE -> WinRtMethodVisibility.Private
            METHOD_ATTRIBUTE_FAM_AND_ASSEM -> WinRtMethodVisibility.FamilyAndAssembly
            METHOD_ATTRIBUTE_ASSEM -> WinRtMethodVisibility.Assembly
            METHOD_ATTRIBUTE_FAMILY -> WinRtMethodVisibility.Family
            METHOD_ATTRIBUTE_FAM_OR_ASSEM -> WinRtMethodVisibility.FamilyOrAssembly
            METHOD_ATTRIBUTE_PUBLIC -> WinRtMethodVisibility.Public
            else -> WinRtMethodVisibility.Unknown
        }

    private fun isRemoveOverload(rawMethod: RawMethodDef): Boolean =
        rawMethod.flags and METHOD_ATTRIBUTE_SPECIAL_NAME != 0 && rawMethod.name.startsWith("remove_")

    private fun isObjectEquals(rawMethod: RawMethodDef, signature: ParsedMethodSignature): Boolean =
        rawMethod.name == "Equals" &&
        signature.parameters.size == 1 &&
            isWinRtObjectTypeName(signature.parameters.single().typeName) &&
            signature.returnType.typeName == "Boolean"

    private fun isClassEquals(rawMethod: RawMethodDef, signature: ParsedMethodSignature, qualifiedTypeName: String): Boolean =
        rawMethod.name == "Equals" &&
        signature.parameters.size == 1 &&
            signature.parameters.single().typeName == qualifiedTypeName &&
            signature.returnType.typeName == "Boolean"

    private fun isObjectGetHashCode(rawMethod: RawMethodDef, signature: ParsedMethodSignature): Boolean =
        rawMethod.name == "GetHashCode" &&
        signature.parameters.isEmpty() && signature.returnType.typeName == "Int"

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
        private const val TABLE_FIELD_MARSHAL = 0x0D
        private const val TABLE_DECL_SECURITY = 0x0E
        private const val TABLE_CLASS_LAYOUT = 0x0F
        private const val TABLE_FIELD_LAYOUT = 0x10
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
        private const val TABLE_IMPL_MAP = 0x1C
        private const val TABLE_FIELD_RVA = 0x1D
        private const val TABLE_ENC_LOG = 0x1E
        private const val TABLE_ENC_MAP = 0x1F
        private const val TABLE_ASSEMBLY = 0x20
        private const val TABLE_ASSEMBLY_PROCESSOR = 0x21
        private const val TABLE_ASSEMBLY_OS = 0x22
        private const val TABLE_ASSEMBLY_REF = 0x23
        private const val TABLE_ASSEMBLY_REF_PROCESSOR = 0x24
        private const val TABLE_ASSEMBLY_REF_OS = 0x25
        private const val TABLE_FILE = 0x26
        private const val TABLE_EXPORTED_TYPE = 0x27
        private const val TABLE_MANIFEST_RESOURCE = 0x28
        private const val TABLE_NESTED_CLASS = 0x29
        private const val TABLE_GENERIC_PARAM = 0x2A
        private const val TABLE_METHOD_SPEC = 0x2B
        private const val TABLE_GENERIC_PARAM_CONSTRAINT = 0x2C

        private val AUXILIARY_TABLE_IDS = intArrayOf(
            TABLE_FIELD_MARSHAL,
            TABLE_DECL_SECURITY,
            TABLE_STANDALONE_SIG,
            TABLE_METHOD_IMPL,
            TABLE_MODULE_REF,
            TABLE_IMPL_MAP,
            TABLE_FIELD_RVA,
            TABLE_FILE,
            TABLE_EXPORTED_TYPE,
            TABLE_MANIFEST_RESOURCE,
            TABLE_NESTED_CLASS,
            TABLE_METHOD_SPEC,
        )

        private val MODELED_AUXILIARY_TABLE_IDS = intArrayOf(
            TABLE_STANDALONE_SIG,
            TABLE_METHOD_IMPL,
            TABLE_FILE,
            TABLE_EXPORTED_TYPE,
            TABLE_MANIFEST_RESOURCE,
            TABLE_NESTED_CLASS,
            TABLE_METHOD_SPEC,
        ).toSet()

        private const val TYPE_ATTRIBUTE_LAYOUT_MASK = 0x18
        private const val TYPE_ATTRIBUTE_SEQUENTIAL_LAYOUT = 0x08
        private const val TYPE_ATTRIBUTE_EXPLICIT_LAYOUT = 0x10
        private const val TYPE_ATTRIBUTE_INTERFACE = 0x20
        private const val FIELD_ATTRIBUTE_STATIC = 0x0010
        private const val FIELD_ATTRIBUTE_INIT_ONLY = 0x0020
        private const val FIELD_ATTRIBUTE_LITERAL = 0x0040
        private const val FIELD_ATTRIBUTE_HAS_DEFAULT = 0x8000
        private const val METHOD_ATTRIBUTE_MEMBER_ACCESS_MASK = 0x0007
        private const val METHOD_ATTRIBUTE_PRIVATE = 0x0001
        private const val METHOD_ATTRIBUTE_FAM_AND_ASSEM = 0x0002
        private const val METHOD_ATTRIBUTE_ASSEM = 0x0003
        private const val METHOD_ATTRIBUTE_FAMILY = 0x0004
        private const val METHOD_ATTRIBUTE_FAM_OR_ASSEM = 0x0005
        private const val METHOD_ATTRIBUTE_PUBLIC = 0x0006
        private const val METHOD_ATTRIBUTE_STATIC = 0x0010
        private const val METHOD_ATTRIBUTE_SPECIAL_NAME = 0x0800
        private const val METHOD_ATTRIBUTE_RT_SPECIAL_NAME = 0x1000
        private const val PARAM_ATTRIBUTE_IN = 0x0001
        private const val PARAM_ATTRIBUTE_OUT = 0x0002
        private const val PARAM_ATTRIBUTE_HAS_DEFAULT = 0x1000

        private const val METHOD_SEMANTICS_SETTER = 0x0001
        private const val METHOD_SEMANTICS_GETTER = 0x0002
        private const val METHOD_SEMANTICS_ADD_ON = 0x0008
        private const val METHOD_SEMANTICS_REMOVE_ON = 0x0010

        private const val CALL_CONV_GENERIC = 0x10

        private const val CODED_MEMBER_REF_PARENT_TYPE_DEF = 0
        private const val CODED_MEMBER_REF_PARENT_TYPE_REF = 1
        private const val CODED_MEMBER_REF_PARENT_TYPE_SPEC = 4
        private const val CODED_MEMBER_REF_PARENT_METHOD_DEF = 3
        private const val CODED_MEMBER_REF_PARENT_TAG_BITS = 3
        private const val CODED_MEMBER_REF_PARENT_TAG_MASK = (1 shl CODED_MEMBER_REF_PARENT_TAG_BITS) - 1

        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_METHOD_DEF = 2
        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF = 3
        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS = 3
        private const val CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_MASK = (1 shl CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS) - 1

        private const val CODED_HAS_CUSTOM_ATTRIBUTE_METHOD_DEF = 0
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_FIELD = 1
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_REF = 2
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF = 3
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_PARAM = 4
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_INTERFACE_IMPL = 5
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_MEMBER_REF = 6
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_MODULE = 7
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_PROPERTY = 9
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_EVENT = 10
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_STANDALONE_SIG = 11
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_MODULE_REF = 12
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_SPEC = 13
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_ASSEMBLY = 14
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_ASSEMBLY_REF = 15
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_GENERIC_PARAM = 19
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS = 5
        private const val CODED_HAS_CUSTOM_ATTRIBUTE_TAG_MASK = (1 shl CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS) - 1

        private const val CODED_HAS_CONSTANT_FIELD = 0
        private const val CODED_HAS_CONSTANT_PARAM = 1
        private const val CODED_HAS_CONSTANT_TAG_BITS = 2
        private const val CODED_HAS_CONSTANT_TAG_MASK = (1 shl CODED_HAS_CONSTANT_TAG_BITS) - 1

        private const val CODED_HAS_FIELD_MARSHAL_TAG_BITS = 1
        private const val CODED_HAS_DECL_SECURITY_TAG_BITS = 2
        private const val CODED_MEMBER_FORWARDED_TAG_BITS = 1
        private const val CODED_METHOD_DEF_OR_REF_METHOD_DEF = 0
        private const val CODED_METHOD_DEF_OR_REF_MEMBER_REF = 1
        private const val CODED_METHOD_DEF_OR_REF_TAG_BITS = 1
        private const val CODED_METHOD_DEF_OR_REF_TAG_MASK = (1 shl CODED_METHOD_DEF_OR_REF_TAG_BITS) - 1

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
        private const val WINDOWS_FOUNDATION_METADATA_CONTRACT_VERSION = "Windows.Foundation.Metadata.ContractVersionAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_VERSION = "Windows.Foundation.Metadata.VersionAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_PREVIOUS_CONTRACT_VERSION = "Windows.Foundation.Metadata.PreviousContractVersionAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_DEPRECATED = "Windows.Foundation.Metadata.DeprecatedAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_THREADING = "Windows.Foundation.Metadata.ThreadingAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_MARSHALING_BEHAVIOR = "Windows.Foundation.Metadata.MarshalingBehaviorAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_MUSE = "Windows.Foundation.Metadata.MuseAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_WEB_HOST_HIDDEN = "Windows.Foundation.Metadata.WebHostHiddenAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_FAST_ABI = "Windows.Foundation.Metadata.FastAbiAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_GC_PRESSURE = "Windows.Foundation.Metadata.GCPressureAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_OVERLOAD = "Windows.Foundation.Metadata.OverloadAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_DEFAULT_OVERLOAD = "Windows.Foundation.Metadata.DefaultOverloadAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_NO_EXCEPTION = "Windows.Foundation.Metadata.NoExceptionAttribute"
        private const val SYSTEM_ATTRIBUTE = "System.Attribute"
        private const val TYPE_ATTRIBUTE_ABSTRACT = 0x00000080
        private const val TYPE_ATTRIBUTE_SEALED = 0x00000100

        private val CONTRACT_PLATFORM_VERSIONS: Map<String, Map<Int, String>> = mapOf(
            "Windows.AI.MachineLearning.MachineLearningContract" to mapOf(
                1 to "10.0.17763.0",
                2 to "10.0.18362.0",
                3 to "10.0.19041.0",
            ),
            "Windows.AI.MachineLearning.Preview.MachineLearningPreviewContract" to mapOf(
                1 to "10.0.17134.0",
                2 to "10.0.17763.0",
            ),
            "Windows.ApplicationModel.Calls.Background.CallsBackgroundContract" to mapOf(
                1 to "10.0.17763.0",
                2 to "10.0.18362.0",
            ),
            "Windows.ApplicationModel.Calls.CallsPhoneContract" to mapOf(
                4 to "10.0.17763.0",
                5 to "10.0.18362.0",
            ),
            "Windows.ApplicationModel.Calls.CallsVoipContract" to mapOf(
                1 to "10.0.10586.0",
                2 to "10.0.16299.0",
                3 to "10.0.17134.0",
                4 to "10.0.17763.0",
            ),
            "Windows.ApplicationModel.CommunicationBlocking.CommunicationBlockingContract" to mapOf(
                2 to "10.0.17763.0",
            ),
            "Windows.ApplicationModel.SocialInfo.SocialInfoContract" to mapOf(
                1 to "10.0.14393.0",
                2 to "10.0.15063.0",
            ),
            "Windows.ApplicationModel.StartupTaskContract" to mapOf(
                2 to "10.0.16299.0",
                3 to "10.0.17134.0",
            ),
            "Windows.Devices.Custom.CustomDeviceContract" to mapOf(
                1 to "10.0.16299.0",
            ),
            "Windows.Devices.DevicesLowLevelContract" to mapOf(
                2 to "10.0.14393.0",
                3 to "10.0.15063.0",
            ),
            "Windows.Devices.Printers.PrintersContract" to mapOf(
                1 to "10.0.10586.0",
            ),
            "Windows.Devices.SmartCards.SmartCardBackgroundTriggerContract" to mapOf(
                3 to "10.0.16299.0",
            ),
            "Windows.Devices.SmartCards.SmartCardEmulatorContract" to mapOf(
                5 to "10.0.16299.0",
                6 to "10.0.17763.0",
            ),
            "Windows.Foundation.FoundationContract" to mapOf(
                1 to "10.0.10240.0",
                2 to "10.0.10586.0",
                3 to "10.0.15063.0",
                4 to "10.0.19041.0",
            ),
            "Windows.Foundation.UniversalApiContract" to mapOf(
                1 to "10.0.10240.0",
                2 to "10.0.10586.0",
                3 to "10.0.14393.0",
                4 to "10.0.15063.0",
                5 to "10.0.16299.0",
                6 to "10.0.17134.0",
                7 to "10.0.17763.0",
                8 to "10.0.18362.0",
                10 to "10.0.19041.0",
            ),
            "Windows.Foundation.VelocityIntegration.VelocityIntegrationContract" to mapOf(
                1 to "10.0.17134.0",
            ),
            "Windows.Gaming.XboxLive.StorageApiContract" to mapOf(
                1 to "10.0.16299.0",
            ),
            "Windows.Graphics.Printing3D.Printing3DContract" to mapOf(
                2 to "10.0.10586.0",
                3 to "10.0.14393.0",
                4 to "10.0.16299.0",
            ),
            "Windows.Networking.Connectivity.WwanContract" to mapOf(
                1 to "10.0.10240.0",
                2 to "10.0.17134.0",
            ),
            "Windows.Networking.Sockets.ControlChannelTriggerContract" to mapOf(
                3 to "10.0.17763.0",
            ),
            "Windows.Security.Isolation.IsolatedWindowsEnvironmentContract" to mapOf(
                1 to "10.0.19041.0",
            ),
            "Windows.Services.Maps.GuidanceContract" to mapOf(
                3 to "10.0.17763.0",
            ),
            "Windows.Services.Maps.LocalSearchContract" to mapOf(
                4 to "10.0.17763.0",
            ),
            "Windows.Services.Store.StoreContract" to mapOf(
                1 to "10.0.14393.0",
                2 to "10.0.15063.0",
                3 to "10.0.17134.0",
                4 to "10.0.17763.0",
            ),
            "Windows.Services.TargetedContent.TargetedContentContract" to mapOf(
                1 to "10.0.15063.0",
            ),
            "Windows.Storage.Provider.CloudFilesContract" to mapOf(
                4 to "10.0.19041.0",
            ),
            "Windows.System.Profile.ProfileHardwareTokenContract" to mapOf(
                1 to "10.0.14393.0",
            ),
            "Windows.System.Profile.ProfileSharedModeContract" to mapOf(
                1 to "10.0.14393.0",
                2 to "10.0.15063.0",
            ),
            "Windows.System.Profile.SystemManufacturers.SystemManufacturersContract" to mapOf(
                3 to "10.0.17763.0",
            ),
            "Windows.System.SystemManagementContract" to mapOf(
                6 to "10.0.17763.0",
                7 to "10.0.19041.0",
            ),
            "Windows.UI.ViewManagement.ViewManagementViewScalingContract" to mapOf(
                1 to "10.0.14393.0",
            ),
            "Windows.UI.Xaml.Core.Direct.XamlDirectContract" to mapOf(
                1 to "10.0.17763.0",
                2 to "10.0.18362.0",
            ),
        )

        private const val CODED_TYPE_DEF_OR_REF_TYPE_DEF = 0
        private const val CODED_TYPE_DEF_OR_REF_TYPE_REF = 1
        private const val CODED_TYPE_DEF_OR_REF_TYPE_SPEC = 2
        private const val CODED_TYPE_DEF_OR_REF_TAG_BITS = 2
        private const val CODED_TYPE_DEF_OR_REF_TAG_MASK = (1 shl CODED_TYPE_DEF_OR_REF_TAG_BITS) - 1

        private const val CODED_TYPE_OR_METHOD_DEF_TYPE_DEF = 0
        private const val CODED_TYPE_OR_METHOD_DEF_METHOD_DEF = 1
        private const val CODED_TYPE_OR_METHOD_DEF_TAG_BITS = 1
        private const val CODED_TYPE_OR_METHOD_DEF_TAG_MASK = (1 shl CODED_TYPE_OR_METHOD_DEF_TAG_BITS) - 1

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
        private val CODED_HAS_FIELD_MARSHAL = CodedIndex(
            tagBits = CODED_HAS_FIELD_MARSHAL_TAG_BITS,
            tables = intArrayOf(TABLE_FIELD, TABLE_PARAM),
        )
        private val CODED_HAS_DECL_SECURITY = CodedIndex(
            tagBits = CODED_HAS_DECL_SECURITY_TAG_BITS,
            tables = intArrayOf(TABLE_TYPE_DEF, TABLE_METHOD_DEF, TABLE_ASSEMBLY),
        )
        private val CODED_HAS_SEMANTICS = CodedIndex(
            tagBits = CODED_HAS_SEMANTICS_TAG_BITS,
            tables = intArrayOf(TABLE_EVENT, TABLE_PROPERTY),
        )
        private val CODED_MEMBER_FORWARDED = CodedIndex(
            tagBits = CODED_MEMBER_FORWARDED_TAG_BITS,
            tables = intArrayOf(TABLE_FIELD, TABLE_METHOD_DEF),
        )
        private val CODED_METHOD_DEF_OR_REF = CodedIndex(
            tagBits = 1,
            tables = intArrayOf(TABLE_METHOD_DEF, TABLE_MEMBER_REF),
        )
        private val CODED_TYPE_OR_METHOD_DEF = CodedIndex(
            tagBits = CODED_TYPE_OR_METHOD_DEF_TAG_BITS,
            tables = intArrayOf(TABLE_TYPE_DEF, TABLE_METHOD_DEF),
        )
        private val CODED_IMPLEMENTATION = CodedIndex(
            tagBits = 2,
            tables = intArrayOf(TABLE_FILE, TABLE_ASSEMBLY_REF, TABLE_EXPORTED_TYPE),
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

            for (tableId in 0..TABLE_GENERIC_PARAM_CONSTRAINT) {
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
                guidIndexSize = guidIndexSize,
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
            TABLE_FIELD_MARSHAL -> codedIndexSize(CODED_HAS_FIELD_MARSHAL, rowCounts) + blobIndexSize
            TABLE_DECL_SECURITY -> 2 + codedIndexSize(CODED_HAS_DECL_SECURITY, rowCounts) + blobIndexSize
            TABLE_CLASS_LAYOUT -> 6 + simpleIndexSize(TABLE_TYPE_DEF, rowCounts)
            TABLE_FIELD_LAYOUT -> 4 + simpleIndexSize(TABLE_FIELD, rowCounts)
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
            TABLE_IMPL_MAP -> 2 + codedIndexSize(CODED_MEMBER_FORWARDED, rowCounts) + stringIndexSize + simpleIndexSize(TABLE_MODULE_REF, rowCounts)
            TABLE_FIELD_RVA -> 4 + simpleIndexSize(TABLE_FIELD, rowCounts)
            TABLE_ENC_LOG -> 8
            TABLE_ENC_MAP -> 4
            TABLE_ASSEMBLY -> 16 + blobIndexSize + stringIndexSize * 2
            TABLE_ASSEMBLY_PROCESSOR -> 4
            TABLE_ASSEMBLY_OS -> 12
            TABLE_ASSEMBLY_REF -> 12 + 4 + blobIndexSize + stringIndexSize * 2 + blobIndexSize
            TABLE_ASSEMBLY_REF_PROCESSOR -> 4 + simpleIndexSize(TABLE_ASSEMBLY_REF, rowCounts)
            TABLE_ASSEMBLY_REF_OS -> 12 + simpleIndexSize(TABLE_ASSEMBLY_REF, rowCounts)
            TABLE_FILE -> 4 + stringIndexSize + blobIndexSize
            TABLE_EXPORTED_TYPE -> 8 + stringIndexSize * 2 + codedIndexSize(CODED_IMPLEMENTATION, rowCounts)
            TABLE_MANIFEST_RESOURCE -> 8 + stringIndexSize + codedIndexSize(CODED_IMPLEMENTATION, rowCounts)
            TABLE_NESTED_CLASS -> simpleIndexSize(TABLE_TYPE_DEF, rowCounts) * 2
            TABLE_GENERIC_PARAM -> 4 + codedIndexSize(CODED_TYPE_OR_METHOD_DEF, rowCounts) + stringIndexSize
            TABLE_METHOD_SPEC -> codedIndexSize(CODED_METHOD_DEF_OR_REF, rowCounts) + blobIndexSize
            TABLE_GENERIC_PARAM_CONSTRAINT -> simpleIndexSize(TABLE_GENERIC_PARAM, rowCounts) + codedIndexSize(CODED_TYPE_DEF_OR_REF, rowCounts)
            else -> null
        }

        private fun tableName(tableId: Int): String = when (tableId) {
            TABLE_FIELD_MARSHAL -> "FieldMarshal"
            TABLE_DECL_SECURITY -> "DeclSecurity"
            TABLE_STANDALONE_SIG -> "StandAloneSig"
            TABLE_METHOD_IMPL -> "MethodImpl"
            TABLE_MODULE_REF -> "ModuleRef"
            TABLE_IMPL_MAP -> "ImplMap"
            TABLE_FIELD_RVA -> "FieldRVA"
            TABLE_FILE -> "File"
            TABLE_EXPORTED_TYPE -> "ExportedType"
            TABLE_MANIFEST_RESOURCE -> "ManifestResource"
            TABLE_NESTED_CLASS -> "NestedClass"
            TABLE_METHOD_SPEC -> "MethodSpec"
            else -> "Table0x${tableId.toString(16)}"
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

    fun layoutKind(): WinRtTypeLayoutKind =
        when (flags and 0x18) {
            0x08 -> WinRtTypeLayoutKind.Sequential
            0x10 -> WinRtTypeLayoutKind.Explicit
            else -> WinRtTypeLayoutKind.Auto
        }
}

private data class RawField(
    val flags: Int,
    val name: String,
    val signatureBlobIndex: Int,
)

private data class RawMethodDef(
    val flags: Int,
    val name: String,
    val signatureBlobIndex: Int,
    val paramListStart: Int,
)

private data class RawParam(
    val rowId: Int,
    val flags: Int,
    val sequence: Int,
    val name: String,
)

private data class RawProperty(
    val flags: Int,
    val name: String,
    val signatureBlobIndex: Int,
)

private data class RawEvent(
    val name: String,
    val delegateTypeName: String,
)

private data class RawMemberRef(
    val ownerTypeName: String,
    val name: String,
    val signatureBlobIndex: Int,
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

private typealias DecodedCustomAttribute = WinRtCustomAttributeDefinition

private data class DecodedCustomAttributeConstructor(
    val typeName: String,
    val fixedArgumentTypes: List<WinRtTypeRef> = emptyList(),
)

private data class DecodedFieldConstant(
    val type: Int,
    val valueBits: ULong,
)

private data class ComputedAbiLayout(
    val size: Int? = null,
    val alignment: Int? = null,
    val isBlittable: Boolean = false,
)

private data class PropertyAccessorRows(
    val getterMethodRowId: Int? = null,
    val setterMethodRowId: Int? = null,
    val hasOnlyGetterSetter: Boolean = true,
) {
    fun orEmpty(): PropertyAccessorRows = this
}

private data class EventAccessorRows(
    val addMethodRowId: Int? = null,
    val removeMethodRowId: Int? = null,
    val hasOnlyAddRemove: Boolean = true,
) {
    fun orEmpty(): EventAccessorRows = this
}

private data class MethodSemanticsData(
    val propertyAccessorsByPropertyRowId: Map<Int, PropertyAccessorRows> = emptyMap(),
    val eventAccessorsByEventRowId: Map<Int, EventAccessorRows> = emptyMap(),
    val semanticMethodRowIds: Set<Int> = emptySet(),
)

private data class ParsedTypeSignature(
    val type: WinRtTypeRef,
) {
    val typeName: String
        get() = type.typeName

    val isByRef: Boolean
        get() = type.isByRef
}

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
    private val typeSpecTypes: Array<WinRtTypeRef>,
) {
    private var cursor: Int = 0

    fun readByte(): Int = bytes[cursor++].toInt() and 0xFF

    fun readCompressedUnsignedInt(): Int {
        val (value, nextCursor) = bytes.readCompressedUnsignedInt(cursor)
        cursor = nextCursor
        return value
    }

    fun readType(): ParsedTypeSignature {
        val requiredModifiers = mutableListOf<String>()
        val optionalModifiers = mutableListOf<String>()
        fun parsed(type: WinRtTypeRef): ParsedTypeSignature =
            ParsedTypeSignature(
                type.withSignatureFidelity(
                    rawSignature = bytes.toHexSignature(),
                    requiredModifiers = requiredModifiers,
                    optionalModifiers = optionalModifiers,
                ),
            )
        while (cursor < bytes.size) {
            when (peekByte()) {
                ELEMENT_TYPE_CMOD_REQD -> {
                    readByte()
                    requiredModifiers += normalizeSignatureTypeReferenceName(decodeTypeToken(readCompressedUnsignedInt()))
                }

                ELEMENT_TYPE_CMOD_OPT -> {
                    readByte()
                    optionalModifiers += normalizeSignatureTypeReferenceName(decodeTypeToken(readCompressedUnsignedInt()))
                }

                ELEMENT_TYPE_SENTINEL, ELEMENT_TYPE_PINNED -> readByte()
                else -> break
            }
        }
        if (cursor >= bytes.size) {
            return parsed(WinRtTypeRef.unknown())
        }

        return when (val elementType = readByte()) {
            ELEMENT_TYPE_VOID -> parsed(WinRtTypeRef.named("Unit"))
            ELEMENT_TYPE_BOOLEAN -> parsed(WinRtTypeRef.named("Boolean"))
            ELEMENT_TYPE_CHAR -> parsed(WinRtTypeRef.named("Char"))
            ELEMENT_TYPE_I1 -> parsed(WinRtTypeRef.named("Byte"))
            ELEMENT_TYPE_U1 -> parsed(WinRtTypeRef.named("UByte"))
            ELEMENT_TYPE_I2 -> parsed(WinRtTypeRef.named("Short"))
            ELEMENT_TYPE_U2 -> parsed(WinRtTypeRef.named("UShort"))
            ELEMENT_TYPE_I4 -> parsed(WinRtTypeRef.named("Int"))
            ELEMENT_TYPE_U4 -> parsed(WinRtTypeRef.named("UInt"))
            ELEMENT_TYPE_I8 -> parsed(WinRtTypeRef.named("Long"))
            ELEMENT_TYPE_U8 -> parsed(WinRtTypeRef.named("ULong"))
            ELEMENT_TYPE_R4 -> parsed(WinRtTypeRef.named("Float"))
            ELEMENT_TYPE_R8 -> parsed(WinRtTypeRef.named("Double"))
            ELEMENT_TYPE_STRING -> parsed(WinRtTypeRef.named("String"))
            ELEMENT_TYPE_OBJECT -> parsed(WinRtTypeRef.named("System.Object"))
            ELEMENT_TYPE_I -> parsed(WinRtTypeRef.named("Long"))
            ELEMENT_TYPE_U -> parsed(WinRtTypeRef.named("ULong"))
            ELEMENT_TYPE_BYREF -> parsed(readType().type.withByRef())
            ELEMENT_TYPE_PTR -> readType()
            ELEMENT_TYPE_CLASS, ELEMENT_TYPE_VALUETYPE -> parsed(
                WinRtTypeRef.named(normalizeSignatureTypeReferenceName(decodeTypeToken(readCompressedUnsignedInt()))),
            )

            ELEMENT_TYPE_VAR -> parsed(WinRtTypeRef.genericTypeParameter(readCompressedUnsignedInt()))
            ELEMENT_TYPE_MVAR -> parsed(WinRtTypeRef.methodTypeParameter(readCompressedUnsignedInt()))
            ELEMENT_TYPE_SZARRAY -> parsed(WinRtTypeRef.array(readType().type))
            ELEMENT_TYPE_ARRAY -> {
                val element = readType()
                val rank = readCompressedUnsignedInt()
                val sizes = readCompressedUnsignedInt()
                repeat(sizes) { readCompressedUnsignedInt() }
                val lowerBounds = readCompressedUnsignedInt()
                repeat(lowerBounds) { readCompressedUnsignedInt() }
                parsed(WinRtTypeRef.array(element.type, rank = rank))
            }

            ELEMENT_TYPE_GENERICINST -> {
                val genericKind = readByte()
                val genericTypeName = if (genericKind == ELEMENT_TYPE_CLASS || genericKind == ELEMENT_TYPE_VALUETYPE) {
                    normalizeSignatureTypeReferenceName(decodeTypeToken(readCompressedUnsignedInt()))
                } else {
                    null
                }
                val argumentCount = readCompressedUnsignedInt()
                val arguments = buildList(argumentCount) {
                    repeat(argumentCount) {
                        add(readType().type)
                    }
                }
                parsed(WinRtTypeRef.named(genericTypeName, arguments))
            }

            ELEMENT_TYPE_TYPEDBYREF, ELEMENT_TYPE_FNPTR, ELEMENT_TYPE_INTERNAL -> parsed(WinRtTypeRef.unknown())
            else -> parsed(if (elementType == ELEMENT_TYPE_END) WinRtTypeRef.unknown() else WinRtTypeRef.unknown())
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
            TYPE_DEF_OR_REF_TYPE_SPEC -> typeSpecTypes.getOrNull(rowId - 1)?.typeName
            else -> null
        }
    }

    companion object {
        private const val TYPE_DEF_OR_REF_TYPE_DEF = 0
        private const val TYPE_DEF_OR_REF_TYPE_REF = 1
        private const val TYPE_DEF_OR_REF_TYPE_SPEC = 2
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

private class CustomAttributeBlobReader(
    private val bytes: ByteArray,
) {
    private var cursor: Int = 0

    fun readUInt16(): Int {
        val value = bytes.readUInt16Le(cursor)
        cursor += 2
        return value
    }

    fun readFixedArgument(type: WinRtTypeRef): WinRtCustomAttributeValue =
        readValue(type.toCustomAttributeElementType())

    fun readNamedArguments(): List<WinRtCustomAttributeNamedArgument> {
        if (cursor + 2 > bytes.size) {
            return emptyList()
        }
        val count = readUInt16()
        return buildList(count) {
            repeat(count) {
                val kind = readByte()
                val isField = when (kind) {
                    SERIALIZATION_TYPE_FIELD -> true
                    SERIALIZATION_TYPE_PROPERTY -> false
                    else -> false
                }
                val type = readFieldOrPropType()
                val name = readSerializedString() ?: ""
                add(
                    WinRtCustomAttributeNamedArgument(
                        name = name,
                        value = readValue(type),
                        isField = isField,
                    ),
                )
            }
        }
    }

    private fun readFieldOrPropType(): CustomAttributeElementType {
        val marker = readByte()
        return when (marker) {
            ELEMENT_TYPE_BOOLEAN -> CustomAttributeElementType.Boolean
            ELEMENT_TYPE_CHAR -> CustomAttributeElementType.Char
            ELEMENT_TYPE_I1 -> CustomAttributeElementType.Int8
            ELEMENT_TYPE_U1 -> CustomAttributeElementType.UInt8
            ELEMENT_TYPE_I2 -> CustomAttributeElementType.Int16
            ELEMENT_TYPE_U2 -> CustomAttributeElementType.UInt16
            ELEMENT_TYPE_I4 -> CustomAttributeElementType.Int32
            ELEMENT_TYPE_U4 -> CustomAttributeElementType.UInt32
            ELEMENT_TYPE_I8 -> CustomAttributeElementType.Int64
            ELEMENT_TYPE_U8 -> CustomAttributeElementType.UInt64
            ELEMENT_TYPE_R4 -> CustomAttributeElementType.Float32
            ELEMENT_TYPE_R8 -> CustomAttributeElementType.Float64
            ELEMENT_TYPE_STRING -> CustomAttributeElementType.String
            SERIALIZATION_TYPE_TYPE -> CustomAttributeElementType.Type
            SERIALIZATION_TYPE_OBJECT -> CustomAttributeElementType.Object
            SERIALIZATION_TYPE_ENUM -> CustomAttributeElementType.Enum(readSerializedString().orEmpty())
            ELEMENT_TYPE_SZARRAY -> CustomAttributeElementType.Array(readFieldOrPropType())
            else -> CustomAttributeElementType.Unknown
        }
    }

    private fun readValue(type: CustomAttributeElementType): WinRtCustomAttributeValue =
        when (type) {
            CustomAttributeElementType.Boolean -> WinRtCustomAttributeValue.BooleanValue(readByte() != 0)
            CustomAttributeElementType.Char -> WinRtCustomAttributeValue.IntegralValue(readUInt16().toLong())
            CustomAttributeElementType.Int8 -> WinRtCustomAttributeValue.IntegralValue(readByte().toByte().toLong())
            CustomAttributeElementType.UInt8 -> WinRtCustomAttributeValue.IntegralValue(readByte().toLong())
            CustomAttributeElementType.Int16 -> WinRtCustomAttributeValue.IntegralValue(readInt16().toLong())
            CustomAttributeElementType.UInt16 -> WinRtCustomAttributeValue.IntegralValue(readUInt16().toLong())
            CustomAttributeElementType.Int32 -> WinRtCustomAttributeValue.IntegralValue(readInt32().toLong())
            CustomAttributeElementType.UInt32 -> WinRtCustomAttributeValue.IntegralValue(readUInt32().toLong())
            CustomAttributeElementType.Int64 -> WinRtCustomAttributeValue.IntegralValue(readInt64())
            CustomAttributeElementType.UInt64 -> WinRtCustomAttributeValue.IntegralValue(readInt64())
            CustomAttributeElementType.Float32 -> WinRtCustomAttributeValue.FloatingPointValue(Float.fromBits(readInt32()).toDouble())
            CustomAttributeElementType.Float64 -> WinRtCustomAttributeValue.FloatingPointValue(Double.fromBits(readInt64()))
            CustomAttributeElementType.String -> WinRtCustomAttributeValue.StringValue(readSerializedString())
            CustomAttributeElementType.Type -> WinRtCustomAttributeValue.TypeValue(readSerializedString())
            is CustomAttributeElementType.Enum -> WinRtCustomAttributeValue.EnumValue(type.typeName, readInt32().toLong())
            is CustomAttributeElementType.Array -> readArrayValue(type.elementType)
            CustomAttributeElementType.Object -> readBoxedValue()
            CustomAttributeElementType.Unknown -> WinRtCustomAttributeValue.NullValue
        }

    private fun readArrayValue(elementType: CustomAttributeElementType): WinRtCustomAttributeValue {
        val count = readInt32()
        if (count < 0) {
            return WinRtCustomAttributeValue.NullValue
        }
        return WinRtCustomAttributeValue.ArrayValue(
            buildList(count) {
                repeat(count) {
                    add(readValue(elementType))
                }
            },
        )
    }

    private fun readBoxedValue(): WinRtCustomAttributeValue =
        readValue(readFieldOrPropType())

    private fun readByte(): Int = bytes[cursor++].toInt() and 0xFF

    private fun readInt16(): Short {
        val value = java.nio.ByteBuffer.wrap(bytes, cursor, 2).order(ByteOrder.LITTLE_ENDIAN).short
        cursor += 2
        return value
    }

    private fun readInt32(): Int {
        val value = java.nio.ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.LITTLE_ENDIAN).int
        cursor += 4
        return value
    }

    private fun readUInt32(): Long = readInt32().toLong() and 0xFFFF_FFFFL

    private fun readInt64(): Long {
        val value = java.nio.ByteBuffer.wrap(bytes, cursor, 8).order(ByteOrder.LITTLE_ENDIAN).long
        cursor += 8
        return value
    }

    private fun readSerializedString(): String? {
        val decoded = bytes.readSerializedString(cursor) ?: return null
        cursor = decoded.second
        return decoded.first
    }

    private fun WinRtTypeRef.toCustomAttributeElementType(): CustomAttributeElementType =
        when (typeName) {
            "Boolean", "System.Boolean" -> CustomAttributeElementType.Boolean
            "Char", "System.Char" -> CustomAttributeElementType.Char
            "Byte", "System.SByte" -> CustomAttributeElementType.Int8
            "UByte", "System.Byte" -> CustomAttributeElementType.UInt8
            "Short", "System.Int16" -> CustomAttributeElementType.Int16
            "UShort", "System.UInt16" -> CustomAttributeElementType.UInt16
            "Int", "System.Int32" -> CustomAttributeElementType.Int32
            "UInt", "System.UInt32" -> CustomAttributeElementType.UInt32
            "Long", "System.Int64" -> CustomAttributeElementType.Int64
            "ULong", "System.UInt64" -> CustomAttributeElementType.UInt64
            "Float", "System.Single" -> CustomAttributeElementType.Float32
            "Double", "System.Double" -> CustomAttributeElementType.Float64
            "String", "System.String" -> CustomAttributeElementType.String
            "System.Type" -> CustomAttributeElementType.Type
            else -> when (kind) {
                WinRtTypeRefKind.Array -> CustomAttributeElementType.Array(
                    (elementType ?: WinRtTypeRef.unknown()).toCustomAttributeElementType(),
                )

                WinRtTypeRefKind.Named -> CustomAttributeElementType.Enum(typeName)
                else -> CustomAttributeElementType.Unknown
            }
        }

    private sealed interface CustomAttributeElementType {
        data object Boolean : CustomAttributeElementType
        data object Char : CustomAttributeElementType
        data object Int8 : CustomAttributeElementType
        data object UInt8 : CustomAttributeElementType
        data object Int16 : CustomAttributeElementType
        data object UInt16 : CustomAttributeElementType
        data object Int32 : CustomAttributeElementType
        data object UInt32 : CustomAttributeElementType
        data object Int64 : CustomAttributeElementType
        data object UInt64 : CustomAttributeElementType
        data object Float32 : CustomAttributeElementType
        data object Float64 : CustomAttributeElementType
        data object String : CustomAttributeElementType
        data object Type : CustomAttributeElementType
        data object Object : CustomAttributeElementType
        data class Enum(val typeName: kotlin.String) : CustomAttributeElementType
        data class Array(val elementType: CustomAttributeElementType) : CustomAttributeElementType
        data object Unknown : CustomAttributeElementType
    }

    private companion object {
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
        private const val ELEMENT_TYPE_SZARRAY = 0x1D
        private const val SERIALIZATION_TYPE_TYPE = 0x50
        private const val SERIALIZATION_TYPE_OBJECT = 0x51
        private const val SERIALIZATION_TYPE_FIELD = 0x53
        private const val SERIALIZATION_TYPE_PROPERTY = 0x54
        private const val SERIALIZATION_TYPE_ENUM = 0x55
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
    "System.Object" -> "System.Object"
    else -> typeName
}

private fun normalizeSignatureTypeReferenceName(typeName: String?): String {
    val normalized = normalizeSignatureTypeName(typeName)
    return splitGenericArity(normalized).first
}

private fun String.toIntegralType(): WinRtIntegralType? = when (this) {
    "Byte" -> WinRtIntegralType.Int8
    "UByte" -> WinRtIntegralType.UInt8
    "Short" -> WinRtIntegralType.Int16
    "UShort" -> WinRtIntegralType.UInt16
    "Int" -> WinRtIntegralType.Int32
    "UInt" -> WinRtIntegralType.UInt32
    "Long" -> WinRtIntegralType.Int64
    "ULong" -> WinRtIntegralType.UInt64
    else -> null
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

private fun ByteArray.toLittleEndianShort(): Short? =
    if (size < 2) null else ByteBuffer.wrap(copyOfRange(0, 2)).order(ByteOrder.LITTLE_ENDIAN).short

private fun ByteArray.toLittleEndianInt(): Int? =
    if (size < 4) null else ByteBuffer.wrap(copyOfRange(0, 4)).order(ByteOrder.LITTLE_ENDIAN).int

private fun ByteArray.toLittleEndianLong(): Long? =
    if (size < 8) null else ByteBuffer.wrap(copyOfRange(0, 8)).order(ByteOrder.LITTLE_ENDIAN).long

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
    val (length, cursor) = readCompressedUnsignedIntOrNull(offset) ?: return null
    if (cursor + length > size) {
        return null
    }
    return copyOfRange(cursor, cursor + length).decodeToString() to (cursor + length)
}

private fun ByteArray.findAsciiAttributeStrings(): List<String> {
    val values = mutableListOf<String>()
    var cursor = 0
    while (cursor < size) {
        while (cursor < size && !this[cursor].isAttributeStringByte()) {
            cursor++
        }
        val start = cursor
        while (cursor < size && this[cursor].isAttributeStringByte()) {
            cursor++
        }
        if (cursor - start >= 8) {
            values += copyOfRange(start, cursor).decodeToString()
        }
    }
    return values
}

private fun ByteArray.readGuidAttributeValue(): String? {
    if (size < 18) {
        return null
    }
    val data1 = readUInt32Le(2)
    val data2 = readUInt16Le(6)
    val data3 = readUInt16Le(8)
    val data4 = copyOfRange(10, 18)
    return "%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X".format(
        data1,
        data2,
        data3,
        data4[0].toInt() and 0xFF,
        data4[1].toInt() and 0xFF,
        data4[2].toInt() and 0xFF,
        data4[3].toInt() and 0xFF,
        data4[4].toInt() and 0xFF,
        data4[5].toInt() and 0xFF,
        data4[6].toInt() and 0xFF,
        data4[7].toInt() and 0xFF,
    )
}

private fun ByteArray.readUInt16Le(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.readUInt32Le(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

private fun Byte.isAttributeStringByte(): Boolean {
    val value = toInt() and 0xFF
    return value in 0x30..0x39 ||
        value in 0x41..0x5A ||
        value in 0x61..0x7A ||
        value == '.'.code ||
        value == '_'.code ||
        value == '-'.code ||
        value == '`'.code
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

private fun ByteArray.toHexSignature(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

private fun ByteArray.readCompressedUnsignedIntOrNull(offset: Int): Pair<Int, Int>? {
    if (offset >= size) {
        return null
    }
    val first = this[offset].toInt() and 0xFF
    return when {
        first and 0x80 == 0 -> first to (offset + 1)
        first and 0xC0 == 0x80 -> {
            if (offset + 1 >= size) null
            else (((first and 0x3F) shl 8) or (this[offset + 1].toInt() and 0xFF)) to (offset + 2)
        }
        else -> {
            if (offset + 3 >= size) {
                null
            } else {
                val second = this[offset + 1].toInt() and 0xFF
                val third = this[offset + 2].toInt() and 0xFF
                val fourth = this[offset + 3].toInt() and 0xFF
                (((first and 0x1F) shl 24) or (second shl 16) or (third shl 8) or fourth) to (offset + 4)
            }
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
