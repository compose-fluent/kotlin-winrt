package io.github.composefluent.winrt.runtime

/**
 * CsWinRT's IListMethods<T,TAbi>/IDictionaryMethods<K,KAbi,V,VAbi> use the element's
 * actual ABI. Reference elements keep their existing pointer fast path; value elements
 * share the registered value marshaler with IReference<T>, without boxing the element.
 */
internal val WinRTReferenceValueAdapter<*>.elementAbiKind: ComAbiValueKind
    get() = valueAbi?.abiKind ?: ComAbiValueKind.Pointer

internal val WinRTReferenceValueAdapter<*>.elementAbiLayout: NativeAbiLayout
    get() = valueAbi?.abiLayout ?: NativeAbiLayout.ADDRESS

internal fun WinRTReferenceValueAdapter<*>.elementAt(data: RawAddress, index: Int): RawAddress =
    PlatformAbi.slice(data, index.toLong() * elementAbiLayout.byteSize, elementAbiLayout.byteSize)

@Suppress("UNCHECKED_CAST")
internal fun <T> WinRTReferenceValueAdapter<T>.readElement(source: RawAddress): T =
    valueAbi.let { value -> if (value != null) value.readValue(source) as T else projectAbi(PlatformAbi.readPointer(source)) }

internal fun WinRTReferenceValueAdapter<*>.disposeElement(source: RawAddress) {
    val value = valueAbi
    if (value != null) value.disposeValue(source) else disposeAbi(PlatformAbi.readPointer(source))
}

internal fun <T> WinRTReferenceValueAdapter<T>.writeElement(value: T, destination: RawAddress) {
    val nativeValue = valueAbi
    if (nativeValue != null) {
        PlatformAbi.zeroBytes(destination, nativeValue.abiLayout.byteSize)
        try {
            nativeValue.writeValue(requireNotNull(value), destination)
        } catch (error: Throwable) {
            try { nativeValue.disposeValue(destination) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            PlatformAbi.zeroBytes(destination, nativeValue.abiLayout.byteSize)
            throw error
        }
        return
    }
    val marshaler = createOutputMarshaler(value)
    val abi = marshaler.abi
    try {
        marshaler.use { PlatformAbi.writePointer(destination, abi) }
    } catch (error: Throwable) {
        try { disposeAbi(abi) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
        throw error
    }
}

internal fun <T, R> WinRTReferenceValueAdapter<T>.withElementWord(value: T, action: (Long) -> R): R {
    val nativeValue = valueAbi ?: return withInputAbi(value) { action(it.value) }
    return PlatformAbi.confinedScope().use { scope ->
        val storage = PlatformAbi.allocateBytes(scope, nativeValue.abiLayout.byteSize,
            maxOf(16L, nativeValue.abiLayout.byteAlignment))
        PlatformAbi.zeroBytes(storage, nativeValue.abiLayout.byteSize)
        try {
            nativeValue.writeValue(requireNotNull(value), storage)
            action(nativeValue.abiKind.readArgumentWord(storage))
        } finally {
            nativeValue.disposeValue(storage)
        }
    }
}

internal fun <T> WinRTReferenceValueAdapter<T>.readElementResult(invoke: (RawAddress) -> Int): T =
    PlatformAbi.confinedScope().use { scope ->
        val layout = elementAbiLayout
        val result = PlatformAbi.allocateBytes(scope, layout.byteSize, layout.byteAlignment)
        PlatformAbi.zeroBytes(result, layout.byteSize)
        try {
            WinRTPlatformApi.checkSucceededRaw(invoke(result))
            readElement(result)
        } finally {
            disposeElement(result)
        }
    }

@Suppress("UNCHECKED_CAST")
internal fun <T> WinRTReferenceValueAdapter<T>.readElementArgument(argument: Any?): T {
    val value = valueAbi ?: return projectAbi(argument as RawAddress)
    if (value.abiKind is ComAbiValueKind.Struct) return value.readValue(argument as RawAddress) as T
    return PlatformAbi.confinedScope().use { scope ->
        val storage = PlatformAbi.allocateBytes(scope, value.abiLayout.byteSize, value.abiLayout.byteAlignment)
        value.abiKind.writeArgument(argument, storage)
        value.readValue(storage) as T
    }
}

// These conversions concern ABI carriers only; semantic type classification remains in
// the shared value adapters. Struct words carry the address of their by-value storage.
private fun ComAbiValueKind.readArgumentWord(source: RawAddress): Long = when (this) {
    ComAbiValueKind.Pointer -> PlatformAbi.readPointer(source).value
    ComAbiValueKind.Int8 -> PlatformAbi.readInt8(source).toLong()
    ComAbiValueKind.Int16 -> PlatformAbi.readInt16(source).toLong()
    ComAbiValueKind.Int32, ComAbiValueKind.Float -> PlatformAbi.readInt32(source).toLong()
    ComAbiValueKind.Int64, ComAbiValueKind.Double -> PlatformAbi.readInt64(source)
    is ComAbiValueKind.Struct -> source.value
}

private fun ComAbiValueKind.writeArgument(value: Any?, destination: RawAddress) = when (this) {
    ComAbiValueKind.Pointer -> PlatformAbi.writePointer(destination, value as RawAddress)
    ComAbiValueKind.Int8 -> PlatformAbi.writeInt8(destination, value as Byte)
    ComAbiValueKind.Int16 -> PlatformAbi.writeInt16(destination, value as Short)
    ComAbiValueKind.Int32 -> PlatformAbi.writeInt32(destination, value as Int)
    ComAbiValueKind.Int64 -> PlatformAbi.writeInt64(destination, value as Long)
    ComAbiValueKind.Float -> PlatformAbi.writeFloat(destination, value as Float)
    ComAbiValueKind.Double -> PlatformAbi.writeDouble(destination, value as Double)
    is ComAbiValueKind.Struct -> error("Struct arguments already carry their ABI storage")
}

private fun WinRTCollectionReferenceBase.invokeElements(slot: Int, kinds: List<ComAbiValueKind>, vararg words: Long): Int =
    ComVtableInvoker.invokeGeneric(comPtr.checkedPointer(), slot, ComMethodSignature(kinds), words)

internal fun <T> WinRTVectorReference.getElement(index: UInt, adapter: WinRTReferenceValueAdapter<T>): T =
    if (adapter.valueAbi == null) adapter.projectOwnedAbi(getAtAbiOrNull(index) ?: RawAddress.Null)
    else adapter.readElementResult { ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 6, index, it) }

internal fun <T> WinRTVectorViewReference.getElement(index: UInt, adapter: WinRTReferenceValueAdapter<T>): T =
    if (adapter.valueAbi == null) adapter.projectOwnedAbi(getAtAbiOrNull(index) ?: RawAddress.Null)
    else adapter.readElementResult { ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 6, index, it) }

internal fun <T> WinRTIteratorReference.currentElement(adapter: WinRTReferenceValueAdapter<T>): T =
    if (adapter.valueAbi == null) adapter.projectOwnedAbi(currentAbiOrNull() ?: RawAddress.Null)
    else adapter.readElementResult { ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 6, it) }

internal fun <T> WinRTKeyValuePairReference.keyElement(adapter: WinRTReferenceValueAdapter<T>): T =
    if (adapter.valueAbi == null) adapter.projectOwnedAbi(keyAbiOrNull() ?: RawAddress.Null)
    else adapter.readElementResult { ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 6, it) }

internal fun <T> WinRTKeyValuePairReference.valueElement(adapter: WinRTReferenceValueAdapter<T>): T =
    if (adapter.valueAbi == null) adapter.projectOwnedAbi(valueAbiOrNull() ?: RawAddress.Null)
    else adapter.readElementResult { ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 7, it) }

internal fun <T> WinRTCollectionReferenceBase.indexOfElement(slot: Int, value: T, adapter: WinRTReferenceValueAdapter<T>): Pair<Boolean, UInt> =
    adapter.withElementWord(value) { word ->
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeElements(slot, listOf(adapter.elementAbiKind, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                word, indexOut.value, foundOut.value)
        }
    }

internal fun <T> WinRTVectorReference.setElement(index: UInt, value: T, adapter: WinRTReferenceValueAdapter<T>, insert: Boolean = false) {
    if (adapter.valueAbi == null) {
        adapter.withInputAbi(value) { if (insert) insertAt(index, it) else setAt(index, it) }
        return
    }
    adapter.withElementWord(value) { word ->
        WinRTPlatformApi.checkSucceededRaw(invokeElements(if (insert) 11 else 10,
            listOf(ComAbiValueKind.Int32, adapter.elementAbiKind), index.toLong(), word))
    }
}

internal fun <T> WinRTVectorReference.appendElement(value: T, adapter: WinRTReferenceValueAdapter<T>) {
    if (adapter.valueAbi == null) { adapter.withInputAbi(value, ::append); return }
    adapter.withElementWord(value) { word ->
        WinRTPlatformApi.checkSucceededRaw(invokeElements(13, listOf(adapter.elementAbiKind), word))
    }
}

internal fun <K> WinRTCollectionReferenceBase.hasElementKey(key: K, adapter: WinRTReferenceValueAdapter<K>): Boolean =
    adapter.withElementWord(key) { word ->
        RawAbiResultSupport.booleanResult { result ->
            if (adapter.valueAbi == null) ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 8, RawAddress(word), result)
            else invokeElements(8, listOf(adapter.elementAbiKind, ComAbiValueKind.Pointer), word, result.value)
        }
    }

internal fun <K, V> WinRTCollectionReferenceBase.lookupElement(key: K, keyAdapter: WinRTReferenceValueAdapter<K>, valueAdapter: WinRTReferenceValueAdapter<V>): V? {
    if (!hasElementKey(key, keyAdapter)) return null
    return keyAdapter.withElementWord(key) { word ->
        valueAdapter.readElementResult { result ->
            if (keyAdapter.valueAbi == null) ComVtableInvoker.invokeArgs(comPtr.checkedPointer(), 6, RawAddress(word), result)
            else invokeElements(6, listOf(keyAdapter.elementAbiKind, ComAbiValueKind.Pointer), word, result.value)
        }
    }
}

internal fun <K, V> WinRTMapReference.insertElement(key: K, value: V, keyAdapter: WinRTReferenceValueAdapter<K>, valueAdapter: WinRTReferenceValueAdapter<V>) {
    keyAdapter.withElementWord(key) { keyWord ->
        valueAdapter.withElementWord(value) { valueWord ->
            if (keyAdapter.valueAbi == null && valueAdapter.valueAbi == null) insert(RawAddress(keyWord), RawAddress(valueWord))
            else RawAbiResultSupport.booleanResult { result ->
                invokeElements(10, listOf(keyAdapter.elementAbiKind, valueAdapter.elementAbiKind, ComAbiValueKind.Pointer),
                    keyWord, valueWord, result.value)
            }
        }
    }
}

internal fun <K> WinRTMapReference.removeElement(key: K, adapter: WinRTReferenceValueAdapter<K>) {
    adapter.withElementWord(key) { word ->
        if (adapter.valueAbi == null) remove(RawAddress(word))
        else WinRTPlatformApi.checkSucceededRaw(invokeElements(11, listOf(adapter.elementAbiKind), word))
    }
}

/** CsWinRT Marshaler<T> uses contiguous TAbi arrays for enums and structs too. */
@Suppress("UNCHECKED_CAST")
internal fun <T> valueElementMarshaler(adapter: WinRTReferenceValueAdapter<T>): Marshaler<T> {
    val value = requireNotNull(adapter.valueAbi)
    fun createArray(values: Array<out T?>?): WinRTAbiArray? = values?.let {
        val (length, data) = value.createTransferredArray(it)
        WinRTAbiArray(length, data) { value.disposeOwnedArray(length, data) }
    }
    fun read(abi: Any?): T? = when (abi) {
        null -> null
        is RawAddress -> adapter.readElement(abi)
        else -> adapter.readElementArgument(abi)
    }
    fun write(item: T?, destination: RawAddress) {
        if (item == null) PlatformAbi.zeroBytes(destination, value.abiLayout.byteSize)
        else adapter.writeElement(item, destination)
    }
    return Marshaler(
        abiCategory = WinRTAbiCategory.VALUE,
        createMarshalerImpl = { item -> createArray(arrayOf<Any?>(item) as Array<T?>) },
        getAbiImpl = { carrier -> (carrier as? WinRTAbiArray)?.data ?: carrier },
        fromAbiImpl = ::read,
        fromManagedImpl = { item -> value.createTransferredArray(arrayOf<Any?>(item)).second },
        copyAbiImpl = { abi, destination -> write(read(abi), destination) },
        copyManagedImpl = ::write,
        disposeMarshalerImpl = { (it as? WinRTAbiArray)?.close() },
        disposeAbiImpl = { if (it is RawAddress) value.disposeOwnedArray(1, it) },
        createMarshalerArrayImpl = ::createArray,
        fromAbiArrayImpl = { length, data -> value.readOwnedArray(length, data)?.toList() as List<T?>? },
        fromManagedArrayImpl = ::createArray,
        copyManagedArrayImpl = { values, destination ->
            var written = 0
            try {
                values?.forEachIndexed { index, item -> write(item, adapter.elementAt(destination, index)); written++ }
            } catch (error: Throwable) {
                repeat(written) { index ->
                    try { adapter.disposeElement(adapter.elementAt(destination, index)) }
                    catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                }
                throw error
            }
        },
        disposeMarshalerArrayImpl = { it?.close() },
        disposeAbiArrayImpl = value::disposeOwnedArray,
    )
}
