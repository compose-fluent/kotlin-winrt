package io.github.kitectlab.winrt.projections.support

// Deterministic generator handoff for .cswinrt WinRTGenericTypeInstantiations writer parity.

internal data class GenericTypeInstantiationEntry(
    val className: String,
    val sourceType: String,
    val isDelegate: Boolean,
    val rcwFunctions: List<String>,
    val vtableFunctions: List<String>,
    val propertyAccessors: List<String>,
    val dependencies: List<String>,
)

internal object WinRTGenericTypeInstantiations {
    val ENTRIES: List<GenericTypeInstantiationEntry> = listOf(
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_AsyncActionProgressHandler_T0_",
            sourceType = "Windows.Foundation.AsyncActionProgressHandler<T0>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_AsyncActionWithProgressCompletedHandler_T0_",
            sourceType = "Windows.Foundation.AsyncActionWithProgressCompletedHandler<T0>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_AsyncOperationCompletedHandler_T0_",
            sourceType = "Windows.Foundation.AsyncOperationCompletedHandler<T0>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_AsyncOperationProgressHandler_T0__T1_",
            sourceType = "Windows.Foundation.AsyncOperationProgressHandler<T0, T1>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_AsyncOperationWithProgressCompletedHandler_T0__T1_",
            sourceType = "Windows.Foundation.AsyncOperationWithProgressCompletedHandler<T0, T1>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IIterable_T0_",
            sourceType = "Windows.Foundation.Collections.IIterable<T0>",
            isDelegate = false,
            rcwFunctions = listOf("First"),
            vtableFunctions = listOf("First"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IIterable_Windows_Foundation_Collections_IKeyValuePair_String__System_Object__",
            sourceType = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<String, System.Object>>",
            isDelegate = false,
            rcwFunctions = listOf("First"),
            vtableFunctions = listOf("First"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IIterable_Windows_Foundation_Collections_IKeyValuePair_T0__T1__",
            sourceType = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>",
            isDelegate = false,
            rcwFunctions = listOf("First"),
            vtableFunctions = listOf("First"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IIterable_Windows_Foundation_IWwwFormUrlDecoderEntry_",
            sourceType = "Windows.Foundation.Collections.IIterable<Windows.Foundation.IWwwFormUrlDecoderEntry>",
            isDelegate = false,
            rcwFunctions = listOf("First"),
            vtableFunctions = listOf("First"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IIterator_T0_",
            sourceType = "Windows.Foundation.Collections.IIterator<T0>",
            isDelegate = false,
            rcwFunctions = listOf("GetMany"),
            vtableFunctions = listOf("GetMany"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IKeyValuePair_String__System_Object_",
            sourceType = "Windows.Foundation.Collections.IKeyValuePair<String, System.Object>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IKeyValuePair_T0__T1_",
            sourceType = "Windows.Foundation.Collections.IKeyValuePair<T0, T1>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IMapChangedEventArgs_T0_",
            sourceType = "Windows.Foundation.Collections.IMapChangedEventArgs<T0>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IMapView_T0__T1_",
            sourceType = "Windows.Foundation.Collections.IMapView<T0, T1>",
            isDelegate = false,
            rcwFunctions = listOf("Lookup", "HasKey", "Split"),
            vtableFunctions = listOf("Lookup", "HasKey", "Split"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IMap_String__System_Object_",
            sourceType = "Windows.Foundation.Collections.IMap<String, System.Object>",
            isDelegate = false,
            rcwFunctions = listOf("Lookup", "HasKey", "GetView", "Insert", "Remove"),
            vtableFunctions = listOf("Lookup", "HasKey", "GetView", "Insert", "Remove"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IMap_T0__T1_",
            sourceType = "Windows.Foundation.Collections.IMap<T0, T1>",
            isDelegate = false,
            rcwFunctions = listOf("Lookup", "HasKey", "GetView", "Insert", "Remove"),
            vtableFunctions = listOf("Lookup", "HasKey", "GetView", "Insert", "Remove"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IObservableMap_String__System_Object_",
            sourceType = "Windows.Foundation.Collections.IObservableMap<String, System.Object>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IMap<T0, T1>", "Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IObservableMap_T0__T1_",
            sourceType = "Windows.Foundation.Collections.IObservableMap<T0, T1>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IMap<T0, T1>", "Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IObservableVector_T0_",
            sourceType = "Windows.Foundation.Collections.IObservableVector<T0>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IVector<T0>", "Windows.Foundation.Collections.VectorChangedEventHandler<T0>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IVectorView_T0_",
            sourceType = "Windows.Foundation.Collections.IVectorView<T0>",
            isDelegate = false,
            rcwFunctions = listOf("GetAt", "IndexOf", "GetMany"),
            vtableFunctions = listOf("GetAt", "IndexOf", "GetMany"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IIterable<T0>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IVectorView_Windows_Foundation_IWwwFormUrlDecoderEntry_",
            sourceType = "Windows.Foundation.Collections.IVectorView<Windows.Foundation.IWwwFormUrlDecoderEntry>",
            isDelegate = false,
            rcwFunctions = listOf("GetAt", "IndexOf", "GetMany"),
            vtableFunctions = listOf("GetAt", "IndexOf", "GetMany"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IIterable<T0>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_IVector_T0_",
            sourceType = "Windows.Foundation.Collections.IVector<T0>",
            isDelegate = false,
            rcwFunctions = listOf("GetAt", "GetView", "IndexOf", "SetAt", "InsertAt", "Append", "GetMany", "ReplaceAll"),
            vtableFunctions = listOf("GetAt", "GetView", "IndexOf", "SetAt", "InsertAt", "Append", "GetMany", "ReplaceAll"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.Collections.IIterable<T0>"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_MapChangedEventHandler_T0__T1_",
            sourceType = "Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_Collections_VectorChangedEventHandler_T0_",
            sourceType = "Windows.Foundation.Collections.VectorChangedEventHandler<T0>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_IAsyncActionWithProgress_T0_",
            sourceType = "Windows.Foundation.IAsyncActionWithProgress<T0>",
            isDelegate = false,
            rcwFunctions = emptyList(),
            vtableFunctions = emptyList(),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.IAsyncInfo"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_IAsyncOperationWithProgress_T0__T1_",
            sourceType = "Windows.Foundation.IAsyncOperationWithProgress<T0, T1>",
            isDelegate = false,
            rcwFunctions = listOf("GetResults"),
            vtableFunctions = listOf("GetResults"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.IAsyncInfo"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_IAsyncOperation_T0_",
            sourceType = "Windows.Foundation.IAsyncOperation<T0>",
            isDelegate = false,
            rcwFunctions = listOf("GetResults"),
            vtableFunctions = listOf("GetResults"),
            propertyAccessors = emptyList(),
            dependencies = listOf("Windows.Foundation.IAsyncInfo"),
        ),
        GenericTypeInstantiationEntry(
            className = "Windows_Foundation_TypedEventHandler_Windows_Foundation_IMemoryBufferReference__System_Object_",
            sourceType = "Windows.Foundation.TypedEventHandler<Windows.Foundation.IMemoryBufferReference, System.Object>",
            isDelegate = true,
            rcwFunctions = listOf("Invoke"),
            vtableFunctions = listOf("Invoke"),
            propertyAccessors = emptyList(),
            dependencies = emptyList(),
        ),
    )
    val ENTRIES_BY_CLASS_NAME: Map<String, GenericTypeInstantiationEntry> = ENTRIES.associateBy { it.className }
    val ENTRIES_BY_SOURCE_TYPE: Map<String, GenericTypeInstantiationEntry> = ENTRIES.associateBy { it.sourceType }
    private val INITIALIZED_CLASS_NAMES: MutableSet<String> = linkedSetOf()

    fun entryForClassName(className: String): GenericTypeInstantiationEntry? =
        ENTRIES_BY_CLASS_NAME[className]

    fun entryForSourceType(sourceType: String): GenericTypeInstantiationEntry? =
        ENTRIES_BY_SOURCE_TYPE[sourceType]

    fun isInitialized(entry: GenericTypeInstantiationEntry): Boolean =
        entry.className in INITIALIZED_CLASS_NAMES

    fun initializeAll() {
        val visited = linkedSetOf<String>()
        ENTRIES.forEach { initializeWithDependencies(it, visited) }
    }

    fun initializeBySourceType(sourceType: String) {
        entryForSourceType(sourceType)?.let(::initializeEntry)
    }

    fun initializeEntry(entry: GenericTypeInstantiationEntry) {
        initializeWithDependencies(entry, linkedSetOf())
    }

    fun initializeDependencies(
        entry: GenericTypeInstantiationEntry,
        initialize: (GenericTypeInstantiationEntry) -> Unit,
    ) {
        val visited = linkedSetOf(entry.className)
        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)
            .forEach { initializeWithDependencies(it, visited, initialize) }
    }

    fun initializeDependencies(entry: GenericTypeInstantiationEntry) {
        val visited = linkedSetOf(entry.className)
        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)
            .forEach { initializeWithDependencies(it, visited) }
    }

    private fun initializeWithDependencies(
        entry: GenericTypeInstantiationEntry,
        visited: MutableSet<String>,
        initialize: (GenericTypeInstantiationEntry) -> Unit,
    ) {
        if (!visited.add(entry.className)) return
        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)
            .forEach { initializeWithDependencies(it, visited, initialize) }
        initialize(entry)
    }

    private fun initializeWithDependencies(
        entry: GenericTypeInstantiationEntry,
        visited: MutableSet<String>,
    ) {
        if (!visited.add(entry.className)) return
        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)
            .forEach { initializeWithDependencies(it, visited) }
        registerGenericInstantiation(entry)
    }

    private fun registerGenericInstantiation(entry: GenericTypeInstantiationEntry) {
        INITIALIZED_CLASS_NAMES.add(entry.className)
    }
}
