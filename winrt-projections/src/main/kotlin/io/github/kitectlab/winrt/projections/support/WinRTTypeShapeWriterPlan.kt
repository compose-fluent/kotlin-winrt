package io.github.kitectlab.winrt.projections.support

// Deterministic generator handoff for .cswinrt WinRTTypeShapeWriterPlan writer parity.

internal data class TypeShapeEntry(
    val typeName: String,
    val kind: String,
    val mappedMembers: List<String>,
    val factoryMembers: List<String>,
    val moduleActivationEntries: List<String>,
)

internal object WinRTTypeShapeWriterPlan {
    val HELPER_OUTPUTS: List<String> = listOf("WinRTEventHelpers.cs", "WinRTGenericTypeInstantiations.cs")
    val BASE_TYPE_MAPPINGS: List<String> = emptyList()
    val AUTHORING_METADATA_MAPPINGS: List<String> = emptyList()
    val TYPES: List<TypeShapeEntry> = listOf(
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncActionCompletedHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncActionProgressHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncActionWithProgressCompletedHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncOperationCompletedHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncOperationProgressHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncOperationWithProgressCompletedHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.AsyncStatus",
            kind = "Enum",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.CollectionChange",
            kind = "Enum",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IIterable",
            kind = "Interface",
            mappedMembers = listOf("First", "IEnumerable`1"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IIterator",
            kind = "Interface",
            mappedMembers = listOf("Current", "GetMany", "HasCurrent", "IEnumerator`1", "MoveNext"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IKeyValuePair",
            kind = "Interface",
            mappedMembers = listOf("Key", "Value"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IMap",
            kind = "Interface",
            mappedMembers = listOf("Clear", "GetView", "HasKey", "IDictionary`2", "Insert", "Lookup", "Remove", "Size"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IMapChangedEventArgs",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IMapView",
            kind = "Interface",
            mappedMembers = listOf("HasKey", "IReadOnlyDictionary`2", "Lookup", "Size", "Split"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IObservableMap",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IObservableVector",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IPropertySet",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IVector",
            kind = "Interface",
            mappedMembers = listOf("Append", "Clear", "GetAt", "GetMany", "GetView", "IList`1", "IndexOf", "InsertAt", "RemoveAt", "RemoveAtEnd", "ReplaceAll", "SetAt", "Size"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IVectorChangedEventArgs",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.IVectorView",
            kind = "Interface",
            mappedMembers = listOf("GetAt", "GetMany", "IReadOnlyList`1", "IndexOf", "Size"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.MapChangedEventHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.PropertySet",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.FoundationContract"),
            moduleActivationEntries = listOf("Windows.Foundation.Collections.PropertySet"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.StringMap",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.FoundationContract"),
            moduleActivationEntries = listOf("Windows.Foundation.Collections.StringMap"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.ValueSet",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.FoundationContract"),
            moduleActivationEntries = listOf("Windows.Foundation.Collections.ValueSet"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Collections.VectorChangedEventHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.DateTime",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Deferral",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.IDeferralFactory"),
            moduleActivationEntries = listOf("Windows.Foundation.Deferral"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.DeferralCompletedHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.EventHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.EventRegistrationToken",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.FoundationContract",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.GuidHelper",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.IGuidHelperStatics"),
            moduleActivationEntries = listOf("Windows.Foundation.GuidHelper"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.HResult",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IAsyncAction",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IAsyncActionWithProgress",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IAsyncInfo",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IAsyncOperation",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IAsyncOperationWithProgress",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IClosable",
            kind = "Interface",
            mappedMembers = listOf("Close", "IDisposable"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IDeferral",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IDeferralFactory",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IGetActivationFactory",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IGuidHelperStatics",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IMemoryBuffer",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IMemoryBufferFactory",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IMemoryBufferReference",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IPropertyValue",
            kind = "Interface",
            mappedMembers = listOf("GetBoolean", "GetBooleanArray", "GetChar16", "GetChar16Array", "GetDateTime", "GetDateTimeArray", "GetDouble", "GetDoubleArray", "GetGuid", "GetGuidArray", "GetInspectableArray", "GetInt16", "GetInt16Array", "GetInt32", "GetInt32Array", "GetInt64", "GetInt64Array", "GetPoint", "GetPointArray", "GetRect", "GetRectArray", "GetSingle", "GetSingleArray", "GetSize", "GetSizeArray", "GetString", "GetStringArray", "GetTimeSpan", "GetTimeSpanArray", "GetUInt16", "GetUInt16Array", "GetUInt32", "GetUInt32Array", "GetUInt64", "GetUInt64Array", "GetUInt8", "GetUInt8Array", "IsNumericScalar", "Type"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IPropertyValueStatics",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IReference",
            kind = "Interface",
            mappedMembers = listOf("Value"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IReferenceArray",
            kind = "Interface",
            mappedMembers = listOf("Value"),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IStringable",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IUriEscapeStatics",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IUriRuntimeClass",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IUriRuntimeClassFactory",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IUriRuntimeClassWithAbsoluteCanonicalUri",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IWwwFormUrlDecoderEntry",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IWwwFormUrlDecoderRuntimeClass",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.IWwwFormUrlDecoderRuntimeClassFactory",
            kind = "Interface",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.MemoryBuffer",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.IMemoryBufferFactory"),
            moduleActivationEntries = listOf("Windows.Foundation.MemoryBuffer"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Point",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.PropertyType",
            kind = "Enum",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.PropertyValue",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.IPropertyValueStatics"),
            moduleActivationEntries = listOf("Windows.Foundation.PropertyValue"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Rect",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Size",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.TimeSpan",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.TypedEventHandler",
            kind = "Delegate",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.UniversalApiContract",
            kind = "Struct",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.Uri",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.IUriRuntimeClassFactory", "Windows.Foundation.IUriEscapeStatics"),
            moduleActivationEntries = listOf("Windows.Foundation.Uri"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.WwwFormUrlDecoder",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = listOf("Windows.Foundation.IWwwFormUrlDecoderRuntimeClassFactory"),
            moduleActivationEntries = listOf("Windows.Foundation.WwwFormUrlDecoder"),
        ),
        TypeShapeEntry(
            typeName = "Windows.Foundation.WwwFormUrlDecoderEntry",
            kind = "RuntimeClass",
            mappedMembers = emptyList(),
            factoryMembers = emptyList(),
            moduleActivationEntries = emptyList(),
        ),
    )
    val TYPES_BY_NAME: Map<String, TypeShapeEntry> = TYPES.associateBy { it.typeName }
    val BASE_TYPE_MAPPING_TABLE: Map<String, String> = BASE_TYPE_MAPPINGS.toArrowMap()
    val AUTHORING_METADATA_MAPPING_TABLE: Map<String, String> = AUTHORING_METADATA_MAPPINGS.toArrowMap()

    fun typeShape(typeName: String): TypeShapeEntry? = TYPES_BY_NAME[typeName]

    fun registerBaseTypeMappings(register: (Map<String, String>) -> Unit) {
        if (BASE_TYPE_MAPPING_TABLE.isNotEmpty()) {
            register(BASE_TYPE_MAPPING_TABLE)
        }
    }

    fun registerAuthoringMetadataMappings(register: (Map<String, String>) -> Unit) {
        if (AUTHORING_METADATA_MAPPING_TABLE.isNotEmpty()) {
            register(AUTHORING_METADATA_MAPPING_TABLE)
        }
    }

    fun installModuleActivationFactories(install: (typeName: String, factoryMember: String) -> Unit) {
        TYPES.forEach { type ->
            type.moduleActivationEntries.forEach { factoryMember ->
                install(type.typeName, factoryMember)
            }
        }
    }

    private fun List<String>.toArrowMap(): Map<String, String> =
        mapNotNull { entry ->
            val separator = entry.indexOf("->")
            if (separator < 0) null else entry.substring(0, separator) to entry.substring(separator + 2)
        }.toMap()
}
