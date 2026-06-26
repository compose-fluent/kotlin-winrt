package io.github.composefluent.winrt.runtime

import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.collections.AbstractMutableList
import kotlin.collections.AbstractMutableMap

class WinRTVectorViewListAdapter<T>(
    private val vectorView: WinRTVectorViewReference,
    private val elementAdapter: WinRTReferenceValueAdapter<T>,
) : AbstractList<T>(), AutoCloseable {
    override val size: Int
        get() = vectorView.size().toIntChecked("IVectorView.Size")

    override fun get(index: Int): T {
        require(index >= 0) { "index must be non-negative." }
        return projectOwned(vectorView.getAtAbiOrNull(index.toUInt()), elementAdapter)
    }

    override fun close() {
        vectorView.close()
    }
}

class WinRTVectorListAdapter<T>(
    private val vector: WinRTVectorReference,
    private val elementAdapter: WinRTReferenceValueAdapter<T>,
    private val elementMarshaller: (T) -> WinRTObjectMarshaler,
) : AbstractMutableList<T>(), AutoCloseable {
    override val size: Int
        get() = vector.size().toIntChecked("IVector.Size")

    override fun get(index: Int): T {
        require(index >= 0) { "index must be non-negative." }
        return projectOwned(vector.getAtAbiOrNull(index.toUInt()), elementAdapter)
    }

    override fun set(index: Int, element: T): T {
        val previous = get(index)
        elementMarshaller(element).use { marshaler ->
            vector.setAt(index.toUInt(), marshaler.abi)
        }
        return previous
    }

    override fun add(index: Int, element: T) {
        require(index >= 0) { "index must be non-negative." }
        elementMarshaller(element).use { marshaler ->
            if (index == size) {
                vector.append(marshaler.abi)
            } else {
                vector.insertAt(index.toUInt(), marshaler.abi)
            }
        }
    }

    override fun removeAt(index: Int): T {
        val previous = get(index)
        vector.removeAt(index.toUInt())
        return previous
    }

    override fun clear() {
        vector.clear()
    }

    override fun close() {
        vector.close()
    }
}

class WinRTMapViewAdapter<K, V>(
    private val mapView: WinRTMapViewReference,
    private val iterableInterfaceId: Guid,
    private val iteratorInterfaceId: Guid,
    private val keyValuePairInterfaceId: Guid,
    private val keyAdapter: WinRTReferenceValueAdapter<K>,
    private val valueAdapter: WinRTReferenceValueAdapter<V>,
    private val keyMarshaller: (K) -> WinRTObjectMarshaler,
) : AbstractMap<K, V>(), AutoCloseable {
    override val size: Int
        get() = mapView.size().toIntChecked("IMapView.Size")

    override val entries: Set<Map.Entry<K, V>>
        get() = buildEntries().toSet()

    override fun containsKey(key: K): Boolean =
        keyMarshaller(key).use { marshaledKey ->
            mapView.hasKey(marshaledKey.abi)
        }

    override fun get(key: K): V? =
        keyMarshaller(key).use { marshaledKey ->
            if (!mapView.hasKey(marshaledKey.abi)) {
                null
            } else {
                projectOwned(mapView.lookupAbiOrNull(marshaledKey.abi), valueAdapter)
            }
        }

    override fun close() {
        mapView.close()
    }

    private fun buildEntries(): List<Map.Entry<K, V>> {
        return mapView.asIterable(iterableInterfaceId).useAndCollect(
            iteratorInterfaceId,
            winRTKeyValuePairAdapter(keyAdapter, valueAdapter),
        )
    }
}

class WinRTMapAdapter<K, V>(
    private val map: WinRTMapReference,
    private val mapViewInterfaceId: Guid,
    private val iterableInterfaceId: Guid,
    private val iteratorInterfaceId: Guid,
    private val keyValuePairInterfaceId: Guid,
    private val keyAdapter: WinRTReferenceValueAdapter<K>,
    private val valueAdapter: WinRTReferenceValueAdapter<V>,
    private val keyMarshaller: (K) -> WinRTObjectMarshaler,
    private val valueMarshaller: (V) -> WinRTObjectMarshaler,
) : AbstractMutableMap<K, V>(), AutoCloseable {
    override val size: Int
        get() = map.size().toIntChecked("IMap.Size")

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = buildEntries().toMutableSet()

    override fun containsKey(key: K): Boolean =
        keyMarshaller(key).use { marshaledKey ->
            map.hasKey(marshaledKey.abi)
        }

    override fun put(key: K, value: V): V? {
        val previous = get(key)
        keyMarshaller(key).use { marshaledKey ->
            valueMarshaller(value).use { marshaledValue ->
                map.insert(marshaledKey.abi, marshaledValue.abi)
            }
        }
        return previous
    }

    override fun get(key: K): V? =
        keyMarshaller(key).use { marshaledKey ->
            if (!map.hasKey(marshaledKey.abi)) {
                null
            } else {
                projectOwned(map.lookupAbiOrNull(marshaledKey.abi), valueAdapter)
            }
        }

    override fun remove(key: K): V? {
        val previous = get(key)
        keyMarshaller(key).use { marshaledKey ->
            if (!map.hasKey(marshaledKey.abi)) {
                return null
            }
            map.remove(marshaledKey.abi)
        }
        return previous
    }

    override fun clear() {
        map.clear()
    }

    override fun close() {
        map.close()
    }

    private fun buildEntries(): List<MutableMap.MutableEntry<K, V>> {
        val view = map.getView(mapViewInterfaceId)
        val adapter = WinRTMapViewAdapter(
            mapView = view,
            iterableInterfaceId = iterableInterfaceId,
            iteratorInterfaceId = iteratorInterfaceId,
            keyValuePairInterfaceId = keyValuePairInterfaceId,
            keyAdapter = keyAdapter,
            valueAdapter = valueAdapter,
            keyMarshaller = keyMarshaller,
        )
        return adapter.use { readOnly ->
            readOnly.entries.map { entry -> MutableEntrySnapshot(entry.key, entry.value) }
        }
    }
}

private fun UInt.toIntChecked(operation: String): Int {
    if (this > Int.MAX_VALUE.toUInt()) {
        throw IllegalStateException("$operation exceeded Int.MAX_VALUE.")
    }
    return toInt()
}

private fun <T> projectOwned(
    value: RawAddress?,
    adapter: WinRTReferenceValueAdapter<T>,
): T = adapter.projectOwnedAbi(value ?: PlatformAbi.nullPointer)

private fun <T> WinRTIterableReference.useAndCollect(
    iteratorInterfaceId: Guid,
    adapter: WinRTReferenceValueAdapter<T>,
): List<T> {
    return use { iterable ->
        iterable.first(iteratorInterfaceId).use { iterator ->
            val values = mutableListOf<T>()
            while (iterator.hasCurrent()) {
                values += projectOwned(iterator.currentAbiOrNull(), adapter)
                if (!iterator.moveNext()) {
                    break
                }
            }
            values
        }
    }
}

private fun <K, V> WinRTKeyValuePairReference?.usePair(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
): Map.Entry<K, V> {
    require(this != null) { "IKeyValuePair reference cannot be null." }
    return use { pair -> pair.projectPair(keyAdapter, valueAdapter) }
}

private fun <K, V> WinRTKeyValuePairReference.projectPair(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
): Map.Entry<K, V> {
    val key = projectOwned(keyAbiOrNull(), keyAdapter)
    val value = projectOwned(valueAbiOrNull(), valueAdapter)
    return EntrySnapshot(key, value)
}

private data class EntrySnapshot<K, V>(
    override val key: K,
    override val value: V,
) : Map.Entry<K, V>

private data class MutableEntrySnapshot<K, V>(
    override val key: K,
    private var backingValue: V,
) : MutableMap.MutableEntry<K, V> {
    override val value: V
        get() = backingValue

    override fun setValue(newValue: V): V {
        val previous = backingValue
        backingValue = newValue
        return previous
    }
}
