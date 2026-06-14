package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class WinRtCollectionReferenceVtableTest {
    @Test
    fun vector_replace_all_invokes_size_and_items_vtable_shape() {
        ReplaceAllVectorComObject.create().use { host ->
            val first = ComObjectReference(RawComPtr(0x1010), IID.IUnknown, preventReleaseOnDispose = true)
            val second = ComObjectReference(RawComPtr(0x2020), IID.IUnknown, preventReleaseOnDispose = true)

            host.reference.replaceAll(listOf(first, second))

            assertEquals(listOf(PlatformAbi.pointerKey(first.pointer), PlatformAbi.pointerKey(second.pointer)), host.capturedItems)
        }
    }

    private class ReplaceAllVectorComObject private constructor(
        private val scope: NativeScope,
        private val callback: NativeCallbackHandle,
        val reference: WinRtVectorReference,
    ) : AutoCloseable {
        var capturedItems: List<Long> = emptyList()
            private set

        override fun close() {
            callback.close()
            scope.close()
        }

        companion object {
            fun create(): ReplaceAllVectorComObject {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: ReplaceAllVectorComObject
                val callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                ) { args ->
                    val size = args[1] as Int
                    val items = args[2] as RawAddress
                    host.capturedItems = List(size) { index ->
                        PlatformAbi.pointerKey(PlatformAbi.readPointerAt(items, index))
                    }
                    KnownHResults.S_OK.value
                }
                val vtable = PlatformAbi.allocatePointerArray(scope, 18)
                PlatformAbi.writePointerAt(vtable, 17, callback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, vtable)
                val reference = WinRtVectorReference(
                    pointer = objectMemory,
                    interfaceId = IID.IUnknown,
                    preventReleaseOnDispose = true,
                )
                host = ReplaceAllVectorComObject(scope, callback, reference)
                return host
            }
        }
    }
}
