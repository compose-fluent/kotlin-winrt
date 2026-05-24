package io.github.composefluent.winrt.runtime

import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.collections.AbstractMutableList
import kotlin.collections.AbstractMutableMap

class WinRtVectorViewListAdapter<T>(
    private val vectorView: WinRtVectorViewReference,
    private val elementProjector: (IUnknownReference?) -> T,
) : AbstractList<T>(), AutoCloseable {
    override val size: Int
        get() = vectorView.size().toIntChecked("IVectorView.Size")

    override fun get(index: Int): T {
        require(index >= 0) { "index must be non-negative." }
        return projectAndClose(vectorView.getAtOrNull(index.toUInt()), elementProjector)
    }

    override fun close() {
        vectorView.close()
    }
}

class WinRtVectorListAdapter<T>(
    private val vector: WinRtVectorReference,
    private val elementProjector: (IUnknownReference?) -> T,
    private val elementMarshaller: (T) -> WinRtObjectMarshaler,
) : AbstractMutableList<T>(), AutoCloseable {
    override val size: Int
        get() = vector.size().toIntChecked("IVector.Size")

    override fun get(index: Int): T {
        require(index >= 0) { "index must be non-negative." }
        return projectAndClose(vector.getAtOrNull(index.toUInt()), elementProjector)
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

class WinRtMapViewAdapter<K, V>(
    private val mapView: WinRtMapViewReference,
    private val iterableInterfaceId: Guid,
    private val iteratorInterfaceId: Guid,
    private val keyValuePairInterfaceId: Guid,
    private val keyProjector: (IUnknownReference?) -> K,
    private val valueProjector: (IUnknownReference?) -> V,
    private val keyMarshaller: (K) -> WinRtObjectMarshaler,
) : AbstractMap<K, V>(), AutoCloseable {
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
                projectAndClose(mapView.lookupOrNull(marshaledKey.abi), valueProjector)
            }
        }

    override fun close() {
        mapView.close()
    }

    private fun buildEntries(): List<Map.Entry<K, V>> {
        return mapView.asIterable(iterableInterfaceId).useAndCollect(iteratorInterfaceId) { current ->
            when (current) {
                null -> error("IKeyValuePair reference cannot be null.")
                is WinRtKeyValuePairReference -> current.projectPair(keyProjector, valueProjector)
                else -> WinRtKeyValuePairReference(
                    current.pointer.asRawAddress(),
                    keyValuePairInterfaceId,
                    preventReleaseOnDispose = true,
                ).usePair(keyProjector, valueProjector)
            }
        }
    }
}

class WinRtMapAdapter<K, V>(
    private val map: WinRtMapReference,
    private val mapViewInterfaceId: Guid,
    private val iterableInterfaceId: Guid,
    private val iteratorInterfaceId: Guid,
    private val keyValuePairInterfaceId: Guid,
    private val keyProjector: (IUnknownReference?) -> K,
    private val valueProjector: (IUnknownReference?) -> V,
    private val keyMarshaller: (K) -> WinRtObjectMarshaler,
    private val valueMarshaller: (V) -> WinRtObjectMarshaler,
) : AbstractMutableMap<K, V>(), AutoCloseable {
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = buildEntries().toMutableSet()

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
                projectAndClose(map.lookupOrNull(marshaledKey.abi), valueProjector)
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
        val adapter = WinRtMapViewAdapter(
            mapView = view,
            iterableInterfaceId = iterableInterfaceId,
            iteratorInterfaceId = iteratorInterfaceId,
            keyValuePairInterfaceId = keyValuePairInterfaceId,
            keyProjector = keyProjector,
            valueProjector = valueProjector,
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

private fun <T> projectAndClose(
    value: IUnknownReference?,
    projector: (IUnknownReference?) -> T,
): T = try {
    projector(value)
} finally {
    value?.close()
}

private fun <T> WinRtIterableReference.useAndCollect(
    iteratorInterfaceId: Guid,
    projector: (IUnknownReference?) -> T,
): List<T> {
    return use { iterable ->
        iterable.first(iteratorInterfaceId).use { iterator ->
            val values = mutableListOf<T>()
            while (iterator.hasCurrent()) {
                values += projectAndClose(iterator.currentOrNull(), projector)
                if (!iterator.moveNext()) {
                    break
                }
            }
            values
        }
    }
}

private fun <K, V> WinRtKeyValuePairReference?.usePair(
    keyProjector: (IUnknownReference?) -> K,
    valueProjector: (IUnknownReference?) -> V,
): Map.Entry<K, V> {
    require(this != null) { "IKeyValuePair reference cannot be null." }
    return use { pair -> pair.projectPair(keyProjector, valueProjector) }
}

private fun <K, V> WinRtKeyValuePairReference.projectPair(
    keyProjector: (IUnknownReference?) -> K,
    valueProjector: (IUnknownReference?) -> V,
): Map.Entry<K, V> {
    val key = projectAndClose(key(), keyProjector)
    val value = projectAndClose(value(), valueProjector)
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
