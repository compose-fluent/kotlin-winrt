package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/**
 * Type identity for code-created authored controls. CsWinRT supplies the CCW identity;
 * the Windows XAML compiler normally supplies IXamlType's FullName/UnderlyingType/BaseType.
 * Without that second contract WinUI MetadataAPI::GetClassInfoFromObject_Helper falls
 * back to the internal Control type for derived WinUI controls such as SelectorBarItem.
 * Generated definitions optionally supply activation and custom member accessors.
 * Metadata generation and markup interpretation remain compiler responsibilities.
 */
internal object WinUiAuthoredTypeMetadata {
    private data class Type(val type: KClass<*>, val name: String, val baseName: String)
    private val types = ConcurrentCacheMap<String, Type>()
    private val definitions = ConcurrentCacheMap<String, WinRTXamlTypeDefinition>()
    private val definitionsByType = ConcurrentCacheMap<KClass<*>, WinRTXamlTypeDefinition>()
    private val valueTypeDefinitions = ConcurrentCacheMap<String, WinRTXamlValueTypeDefinition>()
    private data class EnumType(val type: KClass<*>, val parse: (String) -> Any)
    private val enumTypes = ConcurrentCacheMap<String, EnumType>()

    fun registerEnum(type: KClass<*>, name: String, parse: (String) -> Any) {
        enumTypes.putIfAbsent(name, EnumType(type, parse))
    }

    fun registerDefinition(definition: WinRTXamlTypeDefinition) {
        definitions.putIfAbsent(definition.name, definition)
        definitionsByType.putIfAbsent(definition.type, definition)
        definition.valueTypes.forEach(::registerValueType)
        definition.members.values.forEach { member ->
            member.valueTypes.forEach(::registerValueType)
            member.collection?.let {
                registerValueType(WinRTXamlValueTypeDefinition(member.typeName, it.type,
                    itemTypeName = it.itemTypeName, itemType = it.itemType, addToVector = it.add))
            }
            member.dictionary?.let {
                registerValueType(WinRTXamlValueTypeDefinition(member.typeName, it.type,
                    itemTypeName = it.itemTypeName, itemType = it.itemType,
                    keyTypeName = it.keyTypeName, keyType = it.keyType, addToMap = it.add))
            }
        }
    }

    private fun registerValueType(definition: WinRTXamlValueTypeDefinition) {
        valueTypeDefinitions.putIfAbsent(definition.name, definition)
        TypeNameSupport.registerProjectionType(definition.type, definition.name)
    }

    fun register(type: KClass<*>, name: String, baseName: String) {
        require(name != baseName) { "An authored type cannot derive from itself: $name" }
        types.putIfAbsent(name, Type(type, name, baseName))
    }

    fun clearForTests() {
        types.clear(); definitions.clear(); definitionsByType.clear(); valueTypeDefinitions.clear(); enumTypes.clear()
    }

    /** Reuses generated accessors for CsWinRT's source-generated ICustomProperty path.
     * No reflection or platform-specific property discovery is required. */
    fun customProperty(source: Any, name: String): microsoft.ui.xaml.data.ICustomProperty? {
        var definition = definitionsByType[source::class]
        while (definition != null) {
            val member = definition.members[name]
            if (member != null && !member.isAttachable) return WinRTBindableCustomProperty(
                canRead = true, canWrite = member.set != null, name = name, type = member.type,
                getValueCallback = { member.get(requireNotNull(it)) },
                setValueCallback = member.set?.let { setter -> { target, value -> setter(requireNotNull(target), value) } },
            )
            definition = definitions[definition.baseName]
        }
        return null
    }

    /** Returns an owned IXamlType pointer, or null when this is not an authored type. */
    fun tryCreate(name: String, resolveType: (String) -> RawAddress): RawAddress {
        enumTypes[name]?.let { return createSystemType(name, it.type, it.parse) }
        // XBF refers to member types by name before asking IXamlMember.Type.
        // Generated XamlTypeInfo registers closed collection types in the same
        // lookup table as authored classes, preserving their typed Add helper.
        valueTypeDefinitions[name]?.let { return createValueType(it, resolveType) }
        val type = types[name] ?: return PlatformAbi.nullPointer
        val definition = definitions[name]
        val baseName = definition?.baseName ?: type.baseName
        if (FeatureSwitches.traceCcw) {
            println("winrt-xaml-metadata: authored type=$name definition=${definition != null}")
        }
        fun resolveBase(includeSystemStub: Boolean = true): RawAddress {
            val authored = tryCreate(baseName, resolveType)
            if (!PlatformAbi.isNull(authored)) return authored
            val sdkType = resolveType(baseName)
            if (!PlatformAbi.isNull(sdkType)) return sdkType
            return if (includeSystemStub) {
                definition?.baseType?.let { createSystemType(baseName, it) } ?: PlatformAbi.nullPointer
            } else PlatformAbi.nullPointer
        }
        // Generated XamlTypeInfo includes system-type entries when the SDK provider omits them.
        val base = resolveBase()
        if (PlatformAbi.isNull(base)) return PlatformAbi.nullPointer
        WinRTPlatformApi.releaseRaw(base)

        fun output(write: (RawAddress) -> Unit) =
            WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
                write(args[0] as RawAddress)
                KnownHResults.S_OK.value
            }

        fun inherited(slot: Int, arguments: Int) = WinRTInspectableMethodDefinition(
            ComMethodSignature.of(*Array(arguments) { ComAbiValueKind.Pointer }),
        ) { args ->
            val pointer = resolveBase()
            if (PlatformAbi.isNull(pointer)) {
                KnownHResults.E_NOINTERFACE.value
            } else {
                IUnknownReference(pointer.asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { reference ->
                    when (arguments) {
                        0 -> ComVtableInvoker.invoke(reference.pointer, slot)
                        1 -> ComVtableInvoker.invokeArgs(reference.pointer, slot, args[0] as RawAddress)
                        2 -> ComVtableInvoker.invokeArgs(reference.pointer, slot, args[0] as RawAddress, args[1] as RawAddress)
                        else -> ComVtableInvoker.invokeArgs(reference.pointer, slot, args[0] as RawAddress, args[1] as RawAddress, args[2] as RawAddress)
                    }
                }
            }
        }

        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
                interfaceId = WinUiXamlInterfaceIds.IXamlType,
                methods = listOf(
                    output { PlatformAbi.writePointer(it, resolveBase()) }, // BaseType
                    definition?.contentProperty?.let { memberName -> output { result ->
                        val member = definition.members[memberName]
                        if (member != null) PlatformAbi.writePointer(result, createMember(definition, member, resolveType))
                        else IUnknownReference(resolveBase().asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { base ->
                            HString.create(memberName).use { member ->
                                HResult(ComVtableInvoker.invokeArgs(base.pointer, 21, member.handle, result)).requireSuccess()
                            }
                        }
                    } } ?: inherited(7, 1), // ContentProperty may be declared on an application base.
                    output { PlatformAbi.writePointer(it, HString.create(type.name).handle) },
                    definition?.shape?.let { shape -> output { PlatformAbi.writeInt8(it, if (shape.isArray) 1 else 0) } } ?: inherited(9, 1),
                    definition?.shape?.let { shape -> output { PlatformAbi.writeInt8(it, if (shape.addToVector != null) 1 else 0) } } ?: inherited(10, 1),
                    output { PlatformAbi.writeInt8(it, if (definition?.activate != null) 1 else 0) },
                    definition?.shape?.let { shape -> output { PlatformAbi.writeInt8(it, if (shape.addToMap != null) 1 else 0) } } ?: inherited(12, 1),
                    inherited(13, 1), // IsMarkupExtension
                    if (definition?.isBindable == true) output { PlatformAbi.writeInt8(it, 1) } else inherited(14, 1),
                    definition?.shape?.let { shape -> output { PlatformAbi.writePointer(it,
                        shape.itemTypeName?.let { name -> resolve(name, shape.itemType, resolveType) } ?: PlatformAbi.nullPointer) } } ?: inherited(15, 1),
                    definition?.shape?.let { shape -> output { PlatformAbi.writePointer(it,
                        shape.keyTypeName?.let { name -> resolve(name, shape.keyType, resolveType) } ?: PlatformAbi.nullPointer) } } ?: inherited(16, 1),
                    // CSharpTypeInfoPass2 XamlUserType owns its BoxedType. A
                    // normal authored reference type is not a box of its SDK base.
                    output { PlatformAbi.writePointer(it, PlatformAbi.nullPointer) }, // BoxedType
                    output { TypeProjection.copyTo(type.type, it) },
                    WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
                        PlatformAbi.writePointer(args[0] as RawAddress, PlatformAbi.nullPointer)
                        val activate = definition?.activate
                        if (activate == null) KnownHResults.E_NOTIMPL.value else {
                            if (FeatureSwitches.traceCcw) println("winrt-xaml-metadata: activate $name")
                            PlatformAbi.writePointer(args[0] as RawAddress, publishInstance(activate()))
                            KnownHResults.S_OK.value
                        }
                    }, // Never substitute native base activation for an authored constructor.
                    WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                        PlatformAbi.writePointer(args[1] as RawAddress, PlatformAbi.nullPointer)
                        val parser = definition?.createFromString
                        if (parser == null) KnownHResults.E_NOTIMPL.value else {
                            val input = HString.fromHandle(args[0] as RawAddress, owner = false).use { it.toKString() }
                            PlatformAbi.writePointer(args[1] as RawAddress, publishInstance(parser(input)))
                            KnownHResults.S_OK.value
                        }
                    }, // CreateFromString
                    WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                        PlatformAbi.writePointer(args[1] as RawAddress, PlatformAbi.nullPointer)
                        val memberName = HString.fromHandle(args[0] as RawAddress, owner = false).use { it.toKString() }
                        val member = definition?.members?.get(memberName)
                        if (FeatureSwitches.traceCcw) {
                            println("winrt-xaml-metadata: member $name.$memberName found=${member != null}")
                        }
                        if (definition != null && member != null) {
                            PlatformAbi.writePointer(args[1] as RawAddress, createMember(definition, member, resolveType))
                            KnownHResults.S_OK.value
                        } else {
                            // CSharpTypeInfoPass2's XamlUserType returns null for a
                            // missing member. Its system base describes type identity
                            // only; asking that stub for members would return E_NOTIMPL.
                            val pointer = resolveBase(includeSystemStub = false)
                            if (PlatformAbi.isNull(pointer)) {
                                KnownHResults.S_OK.value
                            } else IUnknownReference(pointer.asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use {
                                ComVtableInvoker.invokeArgs(it.pointer, 21, args[0] as RawAddress, args[1] as RawAddress)
                            }
                        }
                    },
                    definition?.shape?.addToVector?.let { add -> WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) {
                        add(requireNotNull(MarshalInspectable.any().fromAbi(it[0])), MarshalInspectable.any().fromAbi(it[1]))
                        KnownHResults.S_OK.value
                    } } ?: inherited(22, 2),
                    definition?.shape?.addToMap?.let { add -> WinRTInspectableMethodDefinition(ComMethodSignature.of(
                        ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer)) {
                        val marshaler = MarshalInspectable.any()
                        add(requireNotNull(marshaler.fromAbi(it[0])), marshaler.fromAbi(it[1]), marshaler.fromAbi(it[2]))
                        KnownHResults.S_OK.value
                    } } ?: inherited(23, 3),
                    if (definition != null) WinRTInspectableMethodDefinition(ComMethodSignature.of()) {
                        definition.initializer?.invoke(); KnownHResults.S_OK.value
                    } else inherited(24, 0), // XamlUserType invokes only its own initializer.
                ),
            )),
            defaultInterfaceId = WinUiXamlInterfaceIds.IXamlType,
        )
        return host.detachReference(WinUiXamlInterfaceIds.IXamlType)
    }

    private fun publishInstance(instance: Any): RawAddress {
        if (instance is WinRTXamlComponent) initializeWinRTXamlComponent(instance)
        return WinRTObjectMarshaller.fromManaged(instance)
    }

    private fun createMember(owner: WinRTXamlTypeDefinition, member: WinRTXamlMemberDefinition,
        resolveType: (String) -> RawAddress): RawAddress {
        fun output(write: (RawAddress) -> Unit) = WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) {
            write(it[0] as RawAddress); KnownHResults.S_OK.value
        }
        val marshaler = MarshalInspectable.any()
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
                interfaceId = WinUiXamlInterfaceIds.IXamlMember,
                methods = listOf(
                    output { PlatformAbi.writeInt8(it, if (member.isAttachable) 1 else 0) }, // IsAttachable
                    output { PlatformAbi.writeInt8(it, if (member.isDependencyProperty) 1 else 0) },
                    output { PlatformAbi.writeInt8(it, if (member.set == null) 1 else 0) },
                    output { PlatformAbi.writePointer(it, HString.create(member.name).handle) },
                    output { PlatformAbi.writePointer(it, resolve(member.targetTypeName ?: owner.name,
                        member.targetType ?: owner.type, resolveType)) },
                    output { PlatformAbi.writePointer(it, resolve(member.typeName, member.type, resolveType)) },
                    WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                        val output = args[1] as RawAddress
                        PlatformAbi.writePointer(output, PlatformAbi.nullPointer)
                        val instance = requireNotNull(marshaler.fromAbi(args[0]))
                        marshaler.copyManaged(member.get(instance), output)
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                        val setter = member.set
                        if (setter == null) KnownHResults.E_NOTIMPL.value else {
                            if (FeatureSwitches.traceCcw) println("winrt-xaml-metadata: set ${owner.name}.${member.name}")
                            setter(requireNotNull(marshaler.fromAbi(args[0])), marshaler.fromAbi(args[1]))
                            KnownHResults.S_OK.value
                        }
                    },
                ),
            )), defaultInterfaceId = WinUiXamlInterfaceIds.IXamlMember,
        )
        return host.detachReference(WinUiXamlInterfaceIds.IXamlMember)
    }

    private fun resolve(name: String, fallbackType: KClass<*>?, resolveType: (String) -> RawAddress): RawAddress {
        val authored = tryCreate(name, resolveType)
        if (!PlatformAbi.isNull(authored)) return authored
        val sdkType = resolveType(name)
        if (!PlatformAbi.isNull(sdkType)) return sdkType
        return fallbackType?.let { createSystemType(name, it,
            WinRTTypeClassifier.classify(it)?.xamlLiteralParser) } ?: PlatformAbi.nullPointer
    }

    private fun createValueType(value: WinRTXamlValueTypeDefinition,
        resolveType: (String) -> RawAddress): RawAddress {
        val name = value.name
        val addToVector = value.addToVector
        val addToMap = value.addToMap
        fun output(write: (RawAddress) -> Unit) = WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) {
            write(it[0] as RawAddress); KnownHResults.S_OK.value
        }
        fun pointer(value: () -> RawAddress) = output { PlatformAbi.writePointer(it, value()) }
        fun boolean(value: Boolean = false) = output { PlatformAbi.writeInt8(it, if (value) 1 else 0) }
        fun unavailable(signature: ComMethodSignature) = WinRTInspectableMethodDefinition(signature) { KnownHResults.E_NOTIMPL.value }
        val marshaler = MarshalInspectable.any()
        val host = WinRTInspectableComObject(interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
            interfaceId = WinUiXamlInterfaceIds.IXamlType,
            methods = listOf(
                pointer { PlatformAbi.nullPointer }, // BaseType
                pointer { PlatformAbi.nullPointer }, // ContentProperty
                pointer { HString.create(name).handle },
                boolean(value.isArray), boolean(addToVector != null), boolean(), boolean(addToMap != null), boolean(), boolean(),
                pointer { value.itemTypeName?.let { resolve(it, value.itemType, resolveType) } ?: PlatformAbi.nullPointer },
                pointer { value.keyTypeName?.let { resolve(it, value.keyType, resolveType) } ?: PlatformAbi.nullPointer },
                pointer { value.boxedTypeName?.let { resolve(it, value.boxedType, resolveType) } ?: PlatformAbi.nullPointer },
                output { TypeProjection.copyMetadataNameTo(name, it) },
                unavailable(ComMethodSignatures.HResult_Ptr), // ActivateInstance
                value.boxedTypeName?.let { boxedName -> WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                    // Nullable<T> has the same boxed inspectable as T, as in CsWinRT Marshaler<T?>.
                    PlatformAbi.writePointer(args[1] as RawAddress, PlatformAbi.nullPointer)
                    val boxed = resolve(boxedName, value.boxedType, resolveType)
                    if (PlatformAbi.isNull(boxed)) KnownHResults.E_NOINTERFACE.value else
                        IUnknownReference(boxed.asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use {
                            ComVtableInvoker.invokeArgs(it.pointer, 20, args[0] as RawAddress, args[1] as RawAddress)
                        }
                } } ?: unavailable(ComMethodSignatures.HResult_Ptr_Ptr),
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) {
                    PlatformAbi.writePointer(it[1] as RawAddress, PlatformAbi.nullPointer); KnownHResults.S_OK.value
                }, // No members on a closed array, nullable or collection type.
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                    if (addToVector == null) KnownHResults.E_NOTIMPL.value else {
                        addToVector(requireNotNull(marshaler.fromAbi(args[0])), marshaler.fromAbi(args[1]))
                        KnownHResults.S_OK.value
                    }
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer)) { args ->
                    if (addToMap == null) KnownHResults.E_NOTIMPL.value else {
                        addToMap(requireNotNull(marshaler.fromAbi(args[0])), marshaler.fromAbi(args[1]), marshaler.fromAbi(args[2]))
                        KnownHResults.S_OK.value
                    }
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of()) { KnownHResults.S_OK.value },
            ),
        )), defaultInterfaceId = WinUiXamlInterfaceIds.IXamlType)
        return host.detachReference(WinUiXamlInterfaceIds.IXamlType)
    }

    /** XamlCompiler's XamlSystemBaseType for a projected type absent from SDK metadata providers. */
    private fun createSystemType(name: String, type: KClass<*>, parse: ((String) -> Any)? = null): RawAddress {
        fun pointer(value: () -> RawAddress) = WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) {
            PlatformAbi.writePointer(it[0] as RawAddress, value()); KnownHResults.S_OK.value
        }
        fun boolean(value: Boolean = false) = WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) {
            PlatformAbi.writeInt8(it[0] as RawAddress, if (value) 1 else 0); KnownHResults.S_OK.value
        }
        fun unavailable(signature: ComMethodSignature) = WinRTInspectableMethodDefinition(signature) {
            KnownHResults.E_NOTIMPL.value
        }
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
                interfaceId = WinUiXamlInterfaceIds.IXamlType,
                methods = listOf(
                    pointer { PlatformAbi.nullPointer }, // BaseType
                    pointer { PlatformAbi.nullPointer }, // ContentProperty
                    pointer { HString.create(name).handle }, // FullName
                    boolean(), // IsArray
                    boolean(), // IsCollection
                    boolean(), // IsConstructible
                    boolean(), // IsDictionary
                    boolean(), // IsMarkupExtension
                    boolean(), // IsBindable
                    pointer { PlatformAbi.nullPointer }, // ItemType
                    pointer { PlatformAbi.nullPointer }, // KeyType
                    pointer { PlatformAbi.nullPointer }, // BoxedType
                    WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) {
                        TypeProjection.copyTo(type, it[0] as RawAddress); KnownHResults.S_OK.value
                    }, // UnderlyingType
                    unavailable(ComMethodSignatures.HResult_Ptr), // ActivateInstance
                    parse?.let { parser -> WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { args ->
                        val input = HString.fromHandle(args[0] as RawAddress, owner = false).use { it.toKString() }
                        PlatformAbi.writePointer(args[1] as RawAddress, WinRTObjectMarshaller.fromManaged(parser(input)))
                        KnownHResults.S_OK.value
                    } } ?: unavailable(ComMethodSignatures.HResult_Ptr_Ptr), // CreateFromString
                    unavailable(ComMethodSignatures.HResult_Ptr_Ptr), // GetMember
                    unavailable(ComMethodSignatures.HResult_Ptr_Ptr), // AddToVector
                    unavailable(ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer)), // AddToMap
                    unavailable(ComMethodSignature.of()), // RunInitializer
                ),
            )),
            defaultInterfaceId = WinUiXamlInterfaceIds.IXamlType,
        )
        return host.detachReference(WinUiXamlInterfaceIds.IXamlType)
    }
}
