package io.github.composefluent.winrt.runtime

/** Development-only graph guard. CsWinRT's IWeakReference and IUnknown identity
 * contracts preserve identity without rooting children (and their event owners).
 * WinUI may disconnect weak peers during reference tracking even while the visual
 * survives. Generated components own the strong snapshot, just like x:Name fields;
 * the global registry only holds a weak reference to that component-owned state.
 */
internal class WinUIXamlCollectionBaseline private constructor(private val slots: List<Slot>, private val retainedObjects: List<Any>) {
    private class Slot(val owner: WeakReference<Any>, val member: WinUIXamlMemberDefinition,
        val items: List<WeakReference<Any>?>)

    fun validate(current: List<*>) {
        val expected = slots.firstOrNull { slot -> slot.owner.tryGetTarget()?.let {
            sameObject(slot.member.get(it), current)
        } == true }?.items ?: error("No compiled collection baseline for this path; rebuild or restart.")
        require(current.size == expected.size) {
            "The live collection size changed outside XAML (expected ${expected.size}, actual ${current.size}); rebuild or restart."
        }
        expected.indices.forEach { index ->
            val reference = expected[index]
            val item = current[index]
            require(if (reference == null) item == null else item != null && reference.matchesTarget(item)) {
                "The live collection order or object identity changed outside XAML at item $index; rebuild or restart."
            }
        }
    }

    companion object {
        private fun sameObject(first: Any?, second: Any?): Boolean {
            if (first === second) return true
            if (first == null || second == null) return false
            val left = ComWrappersSupport.tryUnwrapObject(first) ?: return false
            return left.use {
                val right = ComWrappersSupport.tryUnwrapObject(second) ?: return@use false
                right.use { left.sameIdentity(right) }
            }
        }

        fun capture(anchors: List<Any>, previous: WinUIXamlCollectionBaseline? = null,
            changedObjects: List<Any> = emptyList(), changedCollections: List<Any> = emptyList(),
            retainObjects: Boolean = false): WinUIXamlCollectionBaseline {
            val slots = mutableListOf<Slot>()
            val retained = if (retainObjects) mutableListOf<Any>() else null
            val ancestors = mutableListOf<Any>()
            var count = 0
            fun visit(value: Any?) {
                if (value == null || ancestors.size > 64 || ++count > 16384 || ancestors.any { sameObject(it, value) }) return
                retained?.add(value)
                ancestors += value
                try {
                    when (value) {
                        is List<*> -> value.take(4096).forEach(::visit)
                        is Map<*, *> -> value.values.take(4096).forEach(::visit)
                    }
                    // Shared XAML graph roles; the content member comes from IXamlType metadata.
                    (listOfNotNull(WinUIAuthoredTypeMetadata.contentProperty(value)) +
                        WinUIAuthoredTypeMetadata.collectionProperties(value) +
                        listOf("Resources", "MergedDictionaries", "ThemeDictionaries")).distinct().forEach { name ->
                        WinUIAuthoredTypeMetadata.memberDefinition(value, name)?.let { member ->
                            // A getter may be unavailable before template realization. Leave that
                            // path uncaptured (and reject edits to it), not the entire component.
                            val child = runCatching { member.get(value) }.getOrElse { return@let }
                            if (child is List<*> && child.size <= 4096 && slots.none {
                                it.member.name == name && it.owner.matchesTarget(value)
                            }) {
                                val existing = previous?.slots?.firstOrNull {
                                    it.member.name == name && it.owner.matchesTarget(value)
                                }
                                // A successful property write can itself mutate a content collection
                                // (Text -> Inlines). Do not bless mutations in unrelated objects.
                                slots += if (existing != null && changedObjects.none { sameObject(it, value) } &&
                                    changedCollections.none { sameObject(it, child) }) existing
                                else Slot(WeakReference(value), member, child.map { it?.let(::WeakReference) })
                            }
                            visit(child)
                        }
                    }
                } finally { ancestors.removeAt(ancestors.lastIndex) }
            }
            anchors.forEach(::visit)
            return WinUIXamlCollectionBaseline(slots, retained.orEmpty())
        }
    }
}
