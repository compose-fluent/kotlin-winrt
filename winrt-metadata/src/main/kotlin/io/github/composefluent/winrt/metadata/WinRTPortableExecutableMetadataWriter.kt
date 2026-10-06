package io.github.composefluent.winrt.metadata

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object WinRTPortableExecutableMetadataWriter {
    /** Application-only XAML schema; never used as the exported component WinMD. */
    fun writeXamlApplicationWinmd(
        assemblyName: String,
        runtimeClasses: List<WinRTAuthoredRuntimeClassDescriptor>,
        members: Map<String, WinRTXamlApplicationTypeMembers>,
        outputFile: Path,
        externalTypeAssemblies: Map<String, String>,
        valueTypeNames: Set<String> = emptySet(),
    ) {
        writeXamlSchemaWinmd(assemblyName, runtimeClasses.map {
            WinRTXamlApplicationTypeDescriptor(it.runtimeClassName, it.baseRuntimeClassName,
                it.interfaceNames, it.isActivatable, it.isSealed)
        }, members, outputFile, externalTypeAssemblies, valueTypeNames)
    }

    /** Plain Kotlin data models participate in XAML schema without becoming exported WinRT components. */
    fun writeXamlSchemaWinmd(
        assemblyName: String,
        runtimeClasses: List<WinRTXamlApplicationTypeDescriptor>,
        members: Map<String, WinRTXamlApplicationTypeMembers>,
        outputFile: Path,
        externalTypeAssemblies: Map<String, String>,
        valueTypeNames: Set<String> = emptySet(),
    ) {
        require(members.keys.all { name -> runtimeClasses.any { it.runtimeClassName == name } })
        Files.createDirectories(outputFile.parent)
        writeIfChanged(outputFile, WinmdBuilder(assemblyName, runtimeClasses.map {
            // WinRTTypeWriter.AddComponentType uses the declared enum symbol's
            // System.Enum base. value__/literal fields alone do not make a CLR enum.
            WinmdClass(it.runtimeClassName, if (it.enumEntries != null) "System.Enum" else it.baseRuntimeClassName, it.interfaceNames,
                isActivatable = it.enumEntries == null && it.isActivatable, isSealed = it.enumEntries != null || it.isSealed, enumEntries = it.enumEntries)
        },
            externalTypeAssemblies = externalTypeAssemblies, applicationMembers = members, isApplicationSchema = true,
            valueTypeNames = valueTypeNames + runtimeClasses.filter { it.enumEntries != null }.map { it.runtimeClassName }).build())
    }

    fun writeEmptyWinmd(
        assemblyName: String,
        outputFile: Path,
    ) {
        Files.createDirectories(outputFile.parent)
        writeIfChanged(outputFile, WinmdBuilder(assemblyName, emptyList()).build())
    }

    fun writeAuthoredWinmd(
        assemblyName: String,
        runtimeClasses: List<WinRTAuthoredRuntimeClassDescriptor>,
        outputFile: Path,
        externalTypeAssemblies: Map<String, String>? = null,
    ) {
        Files.createDirectories(outputFile.parent)
        writeIfChanged(outputFile, WinmdBuilder(assemblyName, runtimeClasses.map(::WinmdClass), externalTypeAssemblies = externalTypeAssemblies).build())
    }

    fun writeProjectionFixtureWinmd(
        assemblyName: String,
        interfaces: List<WinRTPortableExecutableInterfaceDescriptor> = emptyList(),
        runtimeClasses: List<WinRTAuthoredRuntimeClassDescriptor> = emptyList(),
        outputFile: Path,
    ) {
        Files.createDirectories(outputFile.parent)
        writeIfChanged(outputFile, WinmdBuilder(assemblyName, runtimeClasses.map(::WinmdClass), interfaces).build())
    }

    private fun writeIfChanged(outputFile: Path, content: ByteArray) {
        if (Files.isRegularFile(outputFile) && Files.readAllBytes(outputFile).contentEquals(content)) return
        Files.write(outputFile, content)
    }
}

data class WinRTPortableExecutableInterfaceDescriptor(
    val interfaceName: String,
    val iid: String,
    val implementedInterfaceNames: List<String> = emptyList(),
)

data class WinRTXamlApplicationTypeDescriptor(
    val runtimeClassName: String,
    val baseRuntimeClassName: String? = null,
    val interfaceNames: List<String> = emptyList(),
    val isActivatable: Boolean = true,
    val isSealed: Boolean = true,
    val enumEntries: List<String>? = null,
)

private data class WinmdClass(
    val runtimeClassName: String,
    val baseRuntimeClassName: String? = null,
    val interfaceNames: List<String> = emptyList(),
    val overridableInterfaceNames: List<String> = emptyList(),
    val isActivatable: Boolean = true,
    val isSealed: Boolean = true,
    val activatableFactoryInterfaceName: String? = null,
    val staticFactoryInterfaceNames: List<String> = emptyList(),
    val enumEntries: List<String>? = null,
) {
    constructor(type: WinRTAuthoredRuntimeClassDescriptor) : this(type.runtimeClassName,
        type.baseRuntimeClassName, type.interfaceNames, type.overridableInterfaceNames,
        type.isActivatable, type.isSealed, type.activatableFactoryInterfaceName, type.staticFactoryInterfaceNames)
}

private class WinmdBuilder(
    private val assemblyName: String,
    private val runtimeClasses: List<WinmdClass>,
    private val interfaces: List<WinRTPortableExecutableInterfaceDescriptor> = emptyList(),
    private val externalTypeAssemblies: Map<String, String>? = null,
    private val applicationMembers: Map<String, WinRTXamlApplicationTypeMembers> = emptyMap(),
    private val valueTypeNames: Set<String> = emptySet(),
    private val isApplicationSchema: Boolean = false,
) {
    // WinRTTypeWriter.VisitEnumDeclaration owns value__, literal FieldDefs and constants.
    private val fields = runtimeClasses.flatMap { type ->
        type.enumEntries?.let { entries ->
            listOf(Triple(type.runtimeClassName, "value__", null)) +
                entries.mapIndexed { value, name -> Triple(type.runtimeClassName, name, value) }
        }.orEmpty()
    }
    private val constants = fields.mapIndexedNotNull { index, field -> field.third?.let { index + 1 to it } }
    private val properties = runtimeClasses.flatMap { type ->
        applicationMembers[type.runtimeClassName]?.properties.orEmpty().sortedBy { it.name }
            .map { type.runtimeClassName to it }
    }
    private val events = runtimeClasses.flatMap { type ->
        applicationMembers[type.runtimeClassName]?.events.orEmpty().sortedBy { it.name }
            .map { type.runtimeClassName to it }
    }
    // CsWinRT WinRTTypeWriter.GetTypeSpecification also owns closed InterfaceImpl signatures.
    private val implementedInterfaces = (interfaces.flatMap { it.implementedInterfaceNames } +
        runtimeClasses.flatMap { it.interfaceNames }).map(WinRTTypeRef::fromDisplayName)
    private val typeSpecs = (events.map { it.second.handlerType } + implementedInterfaces)
        .filter { it.typeArguments.isNotEmpty() }.distinct()
    private val methods = runtimeClasses.flatMap { type ->
        applicationMembers[type.runtimeClassName]?.methods.orEmpty()
            .sortedWith(compareBy({ it.name }, { it.parameterTypes.joinToString { parameter -> parameter.typeName } }))
            .map { type.runtimeClassName to it }
    }
    private fun hasApplicationConstructor(type: WinmdClass) =
        type.isActivatable && type.runtimeClassName in applicationMembers
    private val accessorCount = properties.sumOf { if (it.second.isReadOnly) 1 else 2 }
    private val methodCount = accessorCount + events.size * 2 + methods.size +
        runtimeClasses.count(::hasApplicationConstructor)
    private val getterIds = buildMap<Pair<String, String>, Int> {
        var next = 1
        runtimeClasses.forEach { type ->
            if (hasApplicationConstructor(type)) next++
            properties.filter { it.first == type.runtimeClassName }.forEach { (owner, property) ->
                put(owner to property.name, next)
                next += if (property.isReadOnly) 1 else 2
            }
            next += events.count { it.first == type.runtimeClassName } * 2
            next += methods.count { it.first == type.runtimeClassName }
        }
    }
    private val eventAdderIds = buildMap<Pair<String, String>, Int> {
        var next = 1
        runtimeClasses.forEach { type ->
            if (hasApplicationConstructor(type)) next++
            next += properties.filter { it.first == type.runtimeClassName }.sumOf { if (it.second.isReadOnly) 1 else 2 }
            events.filter { it.first == type.runtimeClassName }.forEach { (owner, event) ->
                put(owner to event.name, next)
                next += 2
            }
            next += methods.count { it.first == type.runtimeClassName }
        }
    }
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
        val localTypeNames = interfaces.mapTo(mutableSetOf()) { it.interfaceName }
            .apply { addAll(runtimeClasses.map { it.runtimeClassName }) }
        val attributeTypeNames = buildSet {
            if (applicationMembers.values.any { it.contentProperty != null }) add(XAML_CONTENT_PROPERTY)
            if (applicationMembers.values.any { it.createFromStringMethod != null }) add(XAML_CREATE_FROM_STRING)
            if (runtimeClasses.isNotEmpty() || interfaces.isNotEmpty()) {
                add(WINDOWS_FOUNDATION_METADATA_VERSION)
            }
            if (interfaces.isNotEmpty()) {
                add(WINDOWS_FOUNDATION_METADATA_GUID)
            }
            runtimeClasses.forEach { descriptor ->
                if (descriptor.isActivatable) {
                    add(WINDOWS_FOUNDATION_METADATA_ACTIVATABLE)
                }
                if (descriptor.staticFactoryInterfaceNames.isNotEmpty()) {
                    add(WINDOWS_FOUNDATION_METADATA_STATIC)
                }
                if (descriptor.interfaceNames.isNotEmpty()) {
                    add(WINDOWS_FOUNDATION_METADATA_DEFAULT)
                }
                if (descriptor.overridableInterfaceNames.isNotEmpty()) {
                    add(WINDOWS_FOUNDATION_METADATA_OVERRIDABLE)
                }
            }
        }.filterTo(mutableSetOf()) { name ->
            // A compiler-only Kotlin model schema may have no SDK dependency. CLR
            // constructors/members still describe it; component ABI attributes
            // remain mandatory on writeAuthoredWinmd's separate path.
            !isApplicationSchema || externalTypeAssemblies?.containsKey(name) != false ||
                name in setOf(XAML_CONTENT_PROPERTY, XAML_CREATE_FROM_STRING)
        }
        val typeRefs = (
            runtimeClasses.flatMap { descriptor ->
                listOf(descriptor.baseRuntimeClassName ?: "System.Object").filterNot(localTypeNames::contains)
            } +
                implementedInterfaces.flatMap(::signatureReferences).filterNot(localTypeNames::contains) +
                attributeTypeNames + (properties.flatMap { signatureReferences(it.second.type) } +
                    methods.flatMap { (_, method) -> (method.parameterTypes + method.returnType).flatMap(::signatureReferences) } +
                    events.flatMap { signatureReferences(it.second.handlerType) } +
                    if (events.isEmpty()) emptyList() else listOf(EVENT_REGISTRATION_TOKEN)).filterNot(localTypeNames::contains)
            )
            .distinct()
            .map { qualifiedName -> TypeRefRow(qualifiedName) }
        val localTypeDefRowIds = buildMap {
            interfaces.forEachIndexed { index, descriptor -> put(descriptor.interfaceName, index + 2) }
            runtimeClasses.forEachIndexed { index, descriptor -> put(descriptor.runtimeClassName, index + 2 + interfaces.size) }
        }
        val metadataRoot = metadataRoot(
            tables = tablesStream(moduleName, moduleTypeName, assemblyNameIndex, typeRefs, localTypeDefRowIds),
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

    private fun typeDefCount(): Int = 1 + interfaces.size + runtimeClasses.size

    private fun interfaceImplCount(): Int =
        interfaces.sumOf { it.implementedInterfaceNames.size } + runtimeClasses.sumOf { it.interfaceNames.size }

    private fun tablesStream(
        moduleName: Int,
        moduleTypeName: Int,
        assemblyNameIndex: Int,
        typeRefs: List<TypeRefRow>,
        localTypeDefRowIds: Map<String, Int>,
    ): ByteArray {
        val writer = BinaryWriter()
        // CsWinRT WinRTTypeWriter.GetTypeReference owns one AssemblyRef per declaring assembly.
        val typeAssemblies = typeRefs.associate { ref -> ref.qualifiedName to
            if (ref.namespace == "System") "mscorlib" else externalTypeAssemblies?.let {
                requireNotNull(it[ref.qualifiedName] ?: it[ref.qualifiedName.substringBefore('`')]) {
                    "Missing declaring assembly for ${ref.qualifiedName}"
                }
            }
        }
        val assemblyRefs = typeAssemblies.values.filterNotNull().distinct().sorted()
        val attributeMemberRefs = attributeMemberRefs(typeRefs)
        // The sorted table mask below requires HasCustomAttribute parents to be in coded-index order.
        // InterfaceImpl row IDs can sort before later TypeDefs even though their rows are emitted later.
        val customAttributes = (typeDefCustomAttributes(attributeMemberRefs, localTypeDefRowIds) +
            interfaceImplCustomAttributes(attributeMemberRefs, localTypeDefRowIds)).sortedBy { it.parentToken }
        val validMask = (1L shl TABLE_MODULE) or
            (if (typeRefs.isEmpty()) 0L else 1L shl TABLE_TYPE_REF) or
            (1L shl TABLE_TYPE_DEF) or
            (if (fields.isEmpty()) 0L else 1L shl TABLE_FIELD) or
            (if (methodCount == 0) 0L else 1L shl TABLE_METHOD_DEF) or
            (if (interfaceImplCount() > 0) 1L shl TABLE_INTERFACE_IMPL else 0L) or
            (if (attributeMemberRefs.isEmpty()) 0L else 1L shl TABLE_MEMBER_REF) or
            (if (constants.isEmpty()) 0L else 1L shl TABLE_CONSTANT) or
            (if (customAttributes.isEmpty()) 0L else 1L shl TABLE_CUSTOM_ATTRIBUTE) or
            (if (events.isEmpty()) 0L else (1L shl TABLE_EVENT_MAP) or (1L shl TABLE_EVENT)) or
            (if (properties.isEmpty()) 0L else (1L shl TABLE_PROPERTY_MAP) or (1L shl TABLE_PROPERTY)) or
            (if (properties.isEmpty() && events.isEmpty()) 0L else 1L shl TABLE_METHOD_SEMANTICS) or
            (if (typeSpecs.isEmpty()) 0L else 1L shl TABLE_TYPE_SPEC) or
            (1L shl TABLE_ASSEMBLY) or
            (if (assemblyRefs.isEmpty()) 0L else 1L shl TABLE_ASSEMBLY_REF)
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
        writer.int32(typeDefCount())
        if (fields.isNotEmpty()) writer.int32(fields.size)
        if (methodCount > 0) writer.int32(methodCount)
        if (interfaceImplCount() > 0) {
            writer.int32(interfaceImplCount())
        }
        if (attributeMemberRefs.isNotEmpty()) {
            writer.int32(attributeMemberRefs.size)
        }
        if (constants.isNotEmpty()) writer.int32(constants.size)
        if (customAttributes.isNotEmpty()) {
            writer.int32(customAttributes.size)
        }
        if (events.isNotEmpty()) {
            writer.int32(events.map { it.first }.distinct().size)
            writer.int32(events.size)
        }
        if (properties.isNotEmpty()) {
            writer.int32(properties.map { it.first }.distinct().size)
            writer.int32(properties.size)
        }
        if (properties.isNotEmpty() || events.isNotEmpty()) writer.int32(accessorCount + events.size * 2)
        if (typeSpecs.isNotEmpty()) writer.int32(typeSpecs.size)
        writer.int32(1)
        if (assemblyRefs.isNotEmpty()) writer.int32(assemblyRefs.size)
        writer.int16(0)
        writer.index(moduleName)
        writer.index(1)
        writer.index(0)
        writer.index(0)
        typeRefs.forEach { typeRef ->
            val assembly = typeAssemblies[typeRef.qualifiedName]
            writer.index(if (assembly == null) 0 else ((assemblyRefs.indexOf(assembly) + 1) shl 2) or 2)
            writer.index(strings.index(typeRef.name))
            writer.index(strings.index(typeRef.namespace))
        }
        writer.int32(0)
        writer.index(moduleTypeName)
        writer.index(0)
        writer.index(0)
        writer.index(1)
        writer.index(1)
        interfaces.forEach { descriptor ->
            val namespace = descriptor.interfaceName.substringBeforeLast('.', missingDelimiterValue = "")
            val name = descriptor.interfaceName.substringAfterLast('.')
            writer.int32(TYPE_ATTRIBUTES_PUBLIC or TYPE_ATTRIBUTES_WINDOWS_RUNTIME or TYPE_ATTRIBUTES_INTERFACE or TYPE_ATTRIBUTES_ABSTRACT)
            writer.index(strings.index(name))
            writer.index(strings.index(namespace))
            writer.index(0)
            writer.index(1)
            writer.index(1)
        }
        var firstMethod = 1
        var firstField = 1
        runtimeClasses.forEach { descriptor ->
            val namespace = descriptor.runtimeClassName.substringBeforeLast('.', missingDelimiterValue = "")
            val name = descriptor.runtimeClassName.substringAfterLast('.')
            val baseTypeName = descriptor.baseRuntimeClassName ?: "System.Object"
            writer.int32(TYPE_ATTRIBUTES_PUBLIC or TYPE_ATTRIBUTES_WINDOWS_RUNTIME or TYPE_ATTRIBUTES_BEFORE_FIELD_INIT or if (descriptor.isSealed) TYPE_ATTRIBUTES_SEALED else 0)
            writer.index(strings.index(name))
            writer.index(strings.index(namespace))
            writer.index(codedTypeDefOrRef(typeRefs, localTypeDefRowIds, baseTypeName))
            writer.index(firstField)
            writer.index(firstMethod)
            firstField += fields.count { it.first == descriptor.runtimeClassName }
            firstMethod += properties.filter { it.first == descriptor.runtimeClassName }.sumOf { if (it.second.isReadOnly) 1 else 2 } +
                events.count { it.first == descriptor.runtimeClassName } * 2 + methods.count { it.first == descriptor.runtimeClassName } +
                if (hasApplicationConstructor(descriptor)) 1 else 0
        }
        fields.forEach { (owner, name, value) ->
            writer.int16(if (value == null) 0x0601 else 0x8056)
            writer.index(strings.index(name))
            val type = if (value == null) WinRTTypeRef.named("Int32") else WinRTTypeRef.named(owner)
            writer.index(blobs.index(byteArrayOf(0x06) + signature(type, typeRefs, localTypeDefRowIds)))
        }
        // CsWinRT WinRTTypeWriter.AddPropertyDefinition: accessor MethodDefs plus MethodSemantics.
        fun method(name: String, flags: Int, signature: ByteArray) {
            writer.int32(0) // metadata-only, no RVA
            writer.int16(0x1003) // Runtime | InternalCall
            writer.int16(flags)
            writer.index(strings.index(name)); writer.index(blobs.index(signature))
            writer.index(1) // no Param table; signatures own arity
        }
        runtimeClasses.forEach { type ->
            if (hasApplicationConstructor(type)) method(".ctor", 0x1886, byteArrayOf(0x20, 0, 1))
            properties.filter { it.first == type.runtimeClassName }.forEach { (_, property) ->
                val signature = signature(property.type, typeRefs, localTypeDefRowIds)
                val convention = if (property.isStatic) 0 else 0x20
                val flags = 0x0880 or (if (property.isStatic) 0x10 else 0) or
                    (if (property.isPublic) 0x06 else 0x01)
                method("get_${property.name}", flags, byteArrayOf(convention.toByte(), 0) + signature)
                if (!property.isReadOnly) method("put_${property.name}", flags, byteArrayOf(convention.toByte(), 1, 1) + signature)
            }
            // WinRTTypeWriter.AddEventDeclaration owns EventMap/Event/MethodSemantics.
            // These application-only rows describe typed connector calls, not exported component ABI.
            events.filter { it.first == type.runtimeClassName }.forEach { (_, event) ->
                val handler = signature(event.handlerType, typeRefs, localTypeDefRowIds)
                val token = signature(WinRTTypeRef.named(EVENT_REGISTRATION_TOKEN), typeRefs, localTypeDefRowIds)
                method("add_${event.name}", 0x0886, byteArrayOf(0x20, 1) + token + handler)
                method("remove_${event.name}", 0x0886, byteArrayOf(0x20, 1, 1) + token)
            }
            // CsWinRT WinRTTypeWriter.AddMethodDeclaration preserves staticness and typed signatures.
            methods.filter { it.first == type.runtimeClassName }.forEach { (_, declaration) ->
                method(declaration.name, 0x0080 or (if (declaration.isStatic) 0x10 else 0) or
                    (if (declaration.isPublic) 0x06 else 0x01),
                    BinaryWriter().apply {
                        int8(if (declaration.isStatic) 0 else CALL_CONV_HASTHIS)
                        compressedUInt(declaration.parameterTypes.size)
                        bytes(signature(declaration.returnType, typeRefs, localTypeDefRowIds))
                        declaration.parameterTypes.forEach { bytes(signature(it, typeRefs, localTypeDefRowIds)) }
                    }.toByteArray())
            }
        }
        interfaces.forEach { descriptor ->
            val typeDefRowId = requireNotNull(localTypeDefRowIds[descriptor.interfaceName])
            descriptor.implementedInterfaceNames.forEach { interfaceName ->
                writer.index(typeDefRowId)
                writer.index(codedTypeDefOrRef(typeRefs, localTypeDefRowIds, WinRTTypeRef.fromDisplayName(interfaceName)))
            }
        }
        runtimeClasses.forEachIndexed { classIndex, descriptor ->
            val typeDefRowId = classIndex + 2 + interfaces.size
            descriptor.interfaceNames.forEach { interfaceName ->
                writer.index(typeDefRowId)
                writer.index(codedTypeDefOrRef(typeRefs, localTypeDefRowIds, WinRTTypeRef.fromDisplayName(interfaceName)))
            }
        }
        attributeMemberRefs.forEach { memberRef ->
            writer.index((memberRef.typeRefRowId shl CODED_MEMBER_REF_PARENT_TAG_BITS) or CODED_MEMBER_REF_PARENT_TYPE_REF)
            writer.index(strings.index(".ctor"))
            writer.index(memberRef.signatureBlobIndex)
        }
        constants.forEach { (field, value) ->
            writer.int8(0x08); writer.int8(0)
            writer.index(field shl 2) // HasConstant: Field
            writer.index(blobs.index(BinaryWriter().apply { int32(value) }.toByteArray()))
        }
        customAttributes.forEach { attribute ->
            writer.index(attribute.parentToken)
            writer.index((attribute.memberRefRowId shl CODED_CUSTOM_ATTRIBUTE_TYPE_TAG_BITS) or CODED_CUSTOM_ATTRIBUTE_TYPE_MEMBER_REF)
            writer.index(attribute.valueBlobIndex)
        }
        events.forEachIndexed { index, (owner, _) ->
            if (index == 0 || events[index - 1].first != owner) {
                writer.index(localTypeDefRowIds.getValue(owner)); writer.index(index + 1)
            }
        }
        events.forEach { (_, event) ->
            writer.int16(0); writer.index(strings.index(event.name))
            writer.index(codedTypeDefOrRef(typeRefs, localTypeDefRowIds, event.handlerType))
        }
        properties.forEachIndexed { index, (owner, _) ->
            if (index == 0 || properties[index - 1].first != owner) {
                writer.index(localTypeDefRowIds.getValue(owner)); writer.index(index + 1)
            }
        }
        properties.forEach { (_, property) ->
            writer.int16(0); writer.index(strings.index(property.name))
            writer.index(blobs.index(byteArrayOf(if (property.isStatic) 0x08 else 0x28, 0) + signature(property.type, typeRefs, localTypeDefRowIds)))
        }
        data class Semantics(val flags: Int, val method: Int, val association: Int)
        val semantics = mutableListOf<Semantics>()
        properties.forEachIndexed { index, (owner, property) ->
            val accessorId = getterIds.getValue(owner to property.name)
            semantics += Semantics(2, accessorId, ((index + 1) shl 1) or 1)
            if (!property.isReadOnly) {
                semantics += Semantics(1, accessorId + 1, ((index + 1) shl 1) or 1)
            }
        }
        events.forEachIndexed { index, (owner, event) ->
            val adderId = eventAdderIds.getValue(owner to event.name)
            semantics += Semantics(8, adderId, (index + 1) shl 1)
            semantics += Semantics(16, adderId + 1, (index + 1) shl 1)
        }
        semantics.sortedBy { it.association }.forEach { row ->
            writer.int16(row.flags); writer.index(row.method); writer.index(row.association)
        }
        typeSpecs.forEach { type -> writer.index(blobs.index(signature(type, typeRefs, localTypeDefRowIds))) }
        writer.int32(0x00008004)
        writer.int16(1)
        writer.int16(0)
        writer.int16(0)
        writer.int16(0)
        writer.int32(0x00000200)
        writer.index(0)
        writer.index(assemblyNameIndex)
        writer.index(0)
        assemblyRefs.forEach { assembly ->
            repeat(4) { writer.int16(255) }
            writer.int32(if (assembly == "mscorlib") 0 else 0x00000200)
            writer.index(if (assembly == "mscorlib") blobs.index(byteArrayOf(
                0xb7.toByte(), 0x7a, 0x5c, 0x56, 0x19, 0x34, 0xe0.toByte(), 0x89.toByte(),
            )) else 0)
            writer.index(strings.index(assembly))
            writer.index(0)
            writer.index(0)
        }
        return writer.toByteArray()
    }

    private fun attributeMemberRefs(typeRefs: List<TypeRefRow>): List<AttributeMemberRefRow> =
        listOf(
            XAML_CONTENT_PROPERTY to emptyList<Int>(),
            XAML_CREATE_FROM_STRING to emptyList<Int>(),
            WINDOWS_FOUNDATION_METADATA_DEFAULT to emptyList<Int>(),
            WINDOWS_FOUNDATION_METADATA_OVERRIDABLE to emptyList(),
            WINDOWS_FOUNDATION_METADATA_GUID to listOf(ELEMENT_TYPE_STRING),
            WINDOWS_FOUNDATION_METADATA_ACTIVATABLE to listOf(ELEMENT_TYPE_U4),
            WINDOWS_FOUNDATION_METADATA_STATIC to listOf(ELEMENT_TYPE_STRING),
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

    private fun interfaceImplCustomAttributes(
        memberRefs: List<AttributeMemberRefRow>,
        localTypeDefRowIds: Map<String, Int>,
    ): List<CustomAttributeRow> {
        val memberRefRowIds = memberRefs
            .mapIndexed { index, memberRef -> memberRef.attributeTypeName to index + 1 }
            .toMap()
        val rows = mutableListOf<CustomAttributeRow>()
        val emptyAttributeBlobIndex = blobs.index(emptyCustomAttributeBlob())
        var interfaceImplRowId = interfaces.sumOf { it.implementedInterfaceNames.size } + 1
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

    private fun typeDefCustomAttributes(
        memberRefs: List<AttributeMemberRefRow>,
        localTypeDefRowIds: Map<String, Int>,
    ): List<CustomAttributeRow> {
        val memberRefRowIds = memberRefs
            .mapIndexed { index, memberRef -> memberRef.attributeTypeName to index + 1 }
            .toMap()
        val rows = mutableListOf<CustomAttributeRow>()
        interfaces.forEach { descriptor ->
            val typeDefRowId = requireNotNull(localTypeDefRowIds[descriptor.interfaceName])
            memberRefRowIds[WINDOWS_FOUNDATION_METADATA_GUID]?.let { memberRefRowId ->
                rows += CustomAttributeRow(
                    parentToken = hasCustomAttributeToken(
                        rowId = typeDefRowId,
                        tag = CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF,
                    ),
                    memberRefRowId = memberRefRowId,
                    valueBlobIndex = blobs.index(stringCustomAttributeBlob(descriptor.iid)),
                )
            }
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
        }
        runtimeClasses.forEachIndexed { index, descriptor ->
            val typeDefRowId = index + 2 + interfaces.size
            fun stringAttribute(type: String, field: String, content: String) {
                val value = BinaryWriter().apply {
                    int16(1); int16(1) // prolog, one named field
                    int8(0x53); int8(ELEMENT_TYPE_STRING)
                    serializedString(field); serializedString(content)
                }.toByteArray()
                rows += CustomAttributeRow(hasCustomAttributeToken(typeDefRowId, CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF),
                    memberRefRowIds.getValue(type), blobs.index(value))
            }
            applicationMembers[descriptor.runtimeClassName]?.contentProperty?.let { stringAttribute(XAML_CONTENT_PROPERTY, "Name", it) }
            applicationMembers[descriptor.runtimeClassName]?.createFromStringMethod?.let { stringAttribute(XAML_CREATE_FROM_STRING, "MethodName", it) }
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
            descriptor.staticFactoryInterfaceNames.forEach { interfaceName ->
                memberRefRowIds[WINDOWS_FOUNDATION_METADATA_STATIC]?.let { memberRefRowId ->
                    rows += CustomAttributeRow(
                        parentToken = hasCustomAttributeToken(
                            rowId = typeDefRowId,
                            tag = CODED_HAS_CUSTOM_ATTRIBUTE_TYPE_DEF,
                        ),
                        memberRefRowId = memberRefRowId,
                        valueBlobIndex = blobs.index(stringCustomAttributeBlob(interfaceName)),
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

    private fun signatureReferences(type: WinRTTypeRef): List<String> = when (type.kind) {
        WinRTTypeRefKind.Array -> signatureReferences(requireNotNull(type.elementType))
        WinRTTypeRefKind.Named -> {
            val name = requireNotNull(type.qualifiedName)
            (if (winRTFundamentalTypeForName(name) != null || isWinRTObjectTypeName(name) || isWinRTVoidTypeName(name)) emptyList() else listOf(name)) +
                type.typeArguments.flatMap(::signatureReferences)
        }
        else -> error("XAML application property requires a closed type: ${type.typeName}")
    }

    private fun signature(type: WinRTTypeRef, refs: List<TypeRefRow>, locals: Map<String, Int>): ByteArray = BinaryWriter().apply {
        require(!type.isByRef && type.requiredModifiers.isEmpty() && type.optionalModifiers.isEmpty()) {
            "XAML application properties cannot have by-reference or modified signatures: ${type.typeName}"
        }
        if (type.kind == WinRTTypeRefKind.Array) {
            require(type.arrayRank <= 1) { "Only vector XAML properties are supported" }
            int8(0x1d); bytes(signature(requireNotNull(type.elementType), refs, locals))
        } else {
            require(type.kind == WinRTTypeRefKind.Named)
            val name = requireNotNull(type.qualifiedName)
            val primitive = winRTFundamentalTypeForName(name)
            when {
                isWinRTVoidTypeName(name) -> int8(ELEMENT_TYPE_VOID)
                primitive != null -> int8(primitive.cliElementType)
                isWinRTObjectTypeName(name) -> int8(0x1c)
                else -> {
                    if (type.typeArguments.isNotEmpty()) int8(0x15)
                    int8(if (name in valueTypeNames) 0x11 else 0x12)
                    compressedUInt(codedTypeDefOrRef(refs, locals, name))
                    if (type.typeArguments.isNotEmpty()) {
                        compressedUInt(type.typeArguments.size)
                        type.typeArguments.forEach { bytes(signature(it, refs, locals)) }
                    }
                }
            }
        }
    }.toByteArray()

    private fun hasCustomAttributeToken(rowId: Int, tag: Int): Int =
        (rowId shl CODED_HAS_CUSTOM_ATTRIBUTE_TAG_BITS) or tag

    private fun codedTypeRef(typeRefs: List<TypeRefRow>, qualifiedName: String): Int {
        val typeRefRowId = typeRefs.indexOfFirst { typeRef -> typeRef.qualifiedName == qualifiedName } + 1
        require(typeRefRowId > 0) { "WinMD TypeRef '$qualifiedName' was not declared." }
        return (typeRefRowId shl CODED_TYPE_DEF_OR_REF_TAG_BITS) or CODED_TYPE_DEF_OR_REF_TYPE_REF
    }

    private fun codedTypeDefOrRef(
        typeRefs: List<TypeRefRow>,
        localTypeDefRowIds: Map<String, Int>,
        type: WinRTTypeRef,
    ): Int = if (type.typeArguments.isEmpty()) {
        codedTypeDefOrRef(typeRefs, localTypeDefRowIds, requireNotNull(type.qualifiedName))
    } else {
        val rowId = typeSpecs.indexOf(type) + 1
        require(rowId > 0) { "WinMD TypeSpec '${type.typeName}' was not declared." }
        (rowId shl CODED_TYPE_DEF_OR_REF_TAG_BITS) or 2
    }

    private fun codedTypeDefOrRef(
        typeRefs: List<TypeRefRow>,
        localTypeDefRowIds: Map<String, Int>,
        qualifiedName: String,
    ): Int {
        localTypeDefRowIds[qualifiedName]?.let { typeDefRowId ->
            return (typeDefRowId shl CODED_TYPE_DEF_OR_REF_TAG_BITS) or CODED_TYPE_DEF_OR_REF_TYPE_DEF
        }
        return codedTypeRef(typeRefs, qualifiedName)
    }

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

    private fun stringCustomAttributeBlob(value: String): ByteArray {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        require(encoded.size < 0x80) { "WinMD custom attribute string value is too long." }
        return byteArrayOf(
            0x01,
            0x00,
            encoded.size.toByte(),
            *encoded,
            0x00,
            0x00,
        )
    }

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
        // ECMA-335 II.24.2.1: Length includes the padding, not just the UTF-8
        // text and terminator. CsWinRT delegates this layout to MetadataBuilder.
        writer.int32(align(version.size, 4))
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
        const val TABLE_FIELD = 4
        const val TABLE_CONSTANT = 11
        const val TABLE_METHOD_DEF = 6
        const val TABLE_PROPERTY_MAP = 21
        const val TABLE_EVENT_MAP = 18
        const val TABLE_EVENT = 20
        const val TABLE_TYPE_SPEC = 27
        const val EVENT_REGISTRATION_TOKEN = "Windows.Foundation.EventRegistrationToken"
        const val TABLE_PROPERTY = 23
        const val TABLE_METHOD_SEMANTICS = 24
        const val XAML_CONTENT_PROPERTY = "Microsoft.UI.Xaml.Markup.ContentPropertyAttribute"
        // XamlCompiler.Core/KnownStrings.cs resolves this Windows metadata attribute.
        const val XAML_CREATE_FROM_STRING = "Windows.Foundation.Metadata.CreateFromStringAttribute"
        const val TABLE_INTERFACE_IMPL = 9
        const val TABLE_MEMBER_REF = 10
        const val TABLE_CUSTOM_ATTRIBUTE = 12
        const val TABLE_ASSEMBLY = 0x20
        const val TABLE_ASSEMBLY_REF = 0x23
        const val CODED_TYPE_DEF_OR_REF_TAG_BITS = 2
        const val CODED_TYPE_DEF_OR_REF_TYPE_DEF = 0
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
        const val ELEMENT_TYPE_STRING = 0x0e
        const val DEFAULT_VERSION = 1
        const val TYPE_ATTRIBUTES_PUBLIC = 0x00000001
        const val TYPE_ATTRIBUTES_WINDOWS_RUNTIME = 0x00004000
        const val TYPE_ATTRIBUTES_SEALED = 0x00000100
        const val TYPE_ATTRIBUTES_INTERFACE = 0x00000020
        const val TYPE_ATTRIBUTES_ABSTRACT = 0x00000080
        const val TYPE_ATTRIBUTES_BEFORE_FIELD_INIT = 0x00100000
        const val WINDOWS_FOUNDATION_METADATA_DEFAULT = "Windows.Foundation.Metadata.DefaultAttribute"
        const val WINDOWS_FOUNDATION_METADATA_OVERRIDABLE = "Windows.Foundation.Metadata.OverridableAttribute"
        const val WINDOWS_FOUNDATION_METADATA_ACTIVATABLE = "Windows.Foundation.Metadata.ActivatableAttribute"
        const val WINDOWS_FOUNDATION_METADATA_GUID = "Windows.Foundation.Metadata.GuidAttribute"
        const val WINDOWS_FOUNDATION_METADATA_STATIC = "Windows.Foundation.Metadata.StaticAttribute"
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

    fun compressedUInt(value: Int) {
        require(value in 0..0x1fffffff)
        when {
            value < 0x80 -> int8(value)
            value < 0x4000 -> { int8((value ushr 8) or 0x80); int8(value) }
            else -> { int8((value ushr 24) or 0xc0); int8(value ushr 16); int8(value ushr 8); int8(value) }
        }
    }

    fun serializedString(value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        compressedUInt(encoded.size); bytes(encoded)
    }

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
