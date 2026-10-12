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
        return vectorView.getElement(index.toUInt(), elementAdapter)
    }

    override fun indexOf(element: T): Int {
        val (found, index) = if (elementAdapter.valueAbi == null) elementAdapter.withInputAbi(element, vectorView::indexOf)
            else vectorView.indexOfElement(8, element, elementAdapter)
        return if (found) index.toIntChecked("IVectorView.IndexOf") else -1
    }

    override fun contains(element: T): Boolean = indexOf(element) >= 0

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
        return vector.getElement(index.toUInt(), elementAdapter)
    }

    // CsWinRT IListMethods<T> delegates membership to IVector.IndexOf. Comparing
    // projected wrappers instead would make the result depend on RCW caching.
    override fun indexOf(element: T): Int {
        val (found, index) = if (elementAdapter.valueAbi == null) elementMarshaller(element).use { vector.indexOf(it.abi) }
            else vector.indexOfElement(9, element, elementAdapter)
        return if (found) index.toIntChecked("IVector.IndexOf") else -1
    }

    override fun contains(element: T): Boolean = indexOf(element) >= 0

    override fun remove(element: T): Boolean {
        val index = indexOf(element)
        if (index < 0) return false
        vector.removeAt(index.toUInt())
        return true
    }

    override fun set(index: Int, element: T): T {
        val previous = get(index)
        vector.setElement(index.toUInt(), element, elementAdapter)
        return previous
    }

    override fun add(element: T): Boolean {
        vector.appendElement(element, elementAdapter)
        return true
    }

    override fun add(index: Int, element: T) {
        require(index >= 0) { "index must be non-negative." }
        if (index == size) vector.appendElement(element, elementAdapter)
        else vector.setElement(index.toUInt(), element, elementAdapter, insert = true)
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
        get() = object : AbstractSet<Map.Entry<K, V>>() {
            override val size: Int get() = this@WinRTMapViewAdapter.size
            override fun iterator(): Iterator<Map.Entry<K, V>> =
                mapView.asIterable(iterableInterfaceId).elementIterator(iteratorInterfaceId, winRTKeyValuePairAdapter(keyAdapter, valueAdapter))
            override fun contains(element: Map.Entry<K, V>): Boolean =
                containsKey(element.key) && this@WinRTMapViewAdapter[element.key] == element.value
        }

    override fun containsKey(key: K): Boolean =
        if (keyAdapter.valueAbi != null) mapView.hasElementKey(key, keyAdapter) else keyMarshaller(key).use { marshaledKey ->
            mapView.hasKey(marshaledKey.abi)
        }

    override fun get(key: K): V? =
        if (keyAdapter.valueAbi != null || valueAdapter.valueAbi != null) mapView.lookupElement(key, keyAdapter, valueAdapter)
        else keyMarshaller(key).use { marshaledKey ->
            if (mapView.hasKey(marshaledKey.abi)) {
                mapView.lookupProjectedOrNull(marshaledKey.abi, valueAdapter)
            } else null
        }

    override fun close() {
        mapView.close()
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
        get() = WinRTMutableMapEntries(this) {
            map.asIterable(iterableInterfaceId).elementIterator(iteratorInterfaceId, winRTKeyValuePairAdapter(keyAdapter, valueAdapter))
        }

    override fun containsKey(key: K): Boolean =
        if (keyAdapter.valueAbi != null) map.hasElementKey(key, keyAdapter) else keyMarshaller(key).use { marshaledKey ->
            map.hasKey(marshaledKey.abi)
        }

    override fun put(key: K, value: V): V? {
        val previous = get(key)
        map.insertElement(key, value, keyAdapter, valueAdapter)
        return previous
    }

    override fun get(key: K): V? =
        if (keyAdapter.valueAbi != null || valueAdapter.valueAbi != null) map.lookupElement(key, keyAdapter, valueAdapter)
        else keyMarshaller(key).use { marshaledKey ->
            if (map.hasKey(marshaledKey.abi)) {
                map.lookupProjectedOrNull(marshaledKey.abi, valueAdapter)
            } else null
        }

    override fun remove(key: K): V? {
        val previous = get(key)
        if (!containsKey(key)) return null
        map.removeElement(key, keyAdapter)
        return previous
    }

    override fun clear() {
        map.clear()
    }

    override fun close() {
        map.close()
    }

}

internal fun UInt.toIntChecked(operation: String): Int {
    if (this > Int.MAX_VALUE.toUInt()) {
        throw IllegalStateException("$operation exceeded Int.MAX_VALUE.")
    }
    return toInt()
}

private fun <T> WinRTIterableReference.elementIterator(
    iteratorInterfaceId: Guid,
    adapter: WinRTReferenceValueAdapter<T>,
): Iterator<T> = use { iterable ->
    WinRTIteratorProjection.FromAbiHelper(
        iterable.first(iteratorInterfaceId), adapter,
        WinRTTypeHandle("kotlin.collections.Iterator<${adapter.projectedTypeName}>", iteratorInterfaceId),
    )
}

/** Kotlin mutable views over the same native map, alongside CsWinRT IDictionary's views. */
private class WinRTMutableMapEntries<K, V>(
    private val map: MutableMap<K, V>,
    private val iteratorFactory: () -> Iterator<Map.Entry<K, V>>,
) : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
    override val size: Int get() = map.size
    override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean =
        map.containsKey(element.key) && map[element.key] == element.value
    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
        throw UnsupportedOperationException("Add entries through the map")
    override fun clear() = map.clear()
    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
        if (!contains(element)) return false
        map.remove(element.key)
        return true
    }
    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = object : MutableIterator<MutableMap.MutableEntry<K, V>> {
        private var source = iteratorFactory()
        private var last: Map.Entry<K, V>? = null
        private var detachedRemainder = false
        override fun hasNext(): Boolean = source.hasNext()
        override fun next(): MutableMap.MutableEntry<K, V> {
            val entry = source.next()
            last = entry
            return object : MutableMap.MutableEntry<K, V> {
                override val key: K = entry.key
                override var value: V = entry.value
                    private set
                override fun setValue(newValue: V): V {
                    val previous = value
                    preserveRemainder()
                    map[key] = newValue
                    value = newValue
                    return previous
                }
                override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
                override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
                override fun toString(): String = "$key=$value"
            }
        }
        override fun remove() {
            val entry = last ?: throw IllegalStateException("Call next before removing an entry")
            preserveRemainder()
            map.remove(entry.key)
            last = null
        }
        private fun preserveRemainder() {
            // IMap mutations invalidate its native iterator. Materialize only when this
            // iterator itself mutates the map, so ordinary traversal remains streaming.
            if (!detachedRemainder) {
                source = source.asSequence().toList().iterator()
                detachedRemainder = true
            }
        }
    }
}
