@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.benchmarks

import io.github.composefluent.winrt.runtime.RuntimeScope
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlin.concurrent.Volatile
import platform.windows.CloseHandle
import platform.windows.CoWaitForMultipleHandles
import platform.windows.CreateEventW
import platform.windows.CreateThread
import platform.windows.FALSE
import platform.windows.INFINITE
import platform.windows.ResetEvent
import platform.windows.SetEvent
import platform.windows.WAIT_OBJECT_0
import platform.windows.WaitForSingleObject
import windows.ui.popups.PopupMenu

internal actual fun createNonAgileObjectPerf(): NonAgileObjectPerf = NativeNonAgileObjectPerf()

private class NativeNonAgileObjectPerf : NonAgileObjectPerf {
    private val createObject = checkNotNull(CreateEventW(null, FALSE, FALSE, null))
    private val exitThread = checkNotNull(CreateEventW(null, FALSE, FALSE, null))
    private val objectCreated = checkNotNull(CreateEventW(null, FALSE, FALSE, null))
    private val stableRef = StableRef.create(this)
    private val staThread = checkNotNull(
        CreateThread(
            null,
            0u,
            staticCFunction(::nonAgileObjectThreadMain),
            stableRef.asCPointer(),
            0u,
            null,
        ),
    )

    @Volatile
    private var nonAgileObject: PopupMenu? = null

    @Volatile
    private var threadFailure: Throwable? = null

    override fun constructAndQueryNonAgileObject() {
        constructNonAgileObject()
        callObject()
    }

    override fun constructNonAgileObject() {
        check(SetEvent(createObject) != FALSE)
        check(WaitForSingleObject(objectCreated, INFINITE) == WAIT_OBJECT_0)
        ResetEvent(objectCreated)
        threadFailure?.let { failure ->
            throw IllegalStateException("The non-agile object STA worker failed.", failure)
        }
    }

    override fun close() {
        check(SetEvent(exitThread) != FALSE)
        check(SetEvent(createObject) != FALSE)
        check(WaitForSingleObject(staThread, INFINITE) == WAIT_OBJECT_0)
        CloseHandle(staThread)
        CloseHandle(objectCreated)
        CloseHandle(exitThread)
        CloseHandle(createObject)
        stableRef.dispose()
    }

    fun objectAllocationLoop() {
        try {
            RuntimeScope.initializeSingleThreaded().use {
                memScoped {
                    val handles = allocArray<COpaquePointerVar>(1)
                    handles[0] = createObject
                    val signaledIndex = alloc<UIntVar>()
                    try {
                        while (true) {
                            val result = CoWaitForMultipleHandles(
                                COM_AWARE_WAIT_FLAGS,
                                INFINITE,
                                1u,
                                handles,
                                signaledIndex.ptr,
                            )
                            check(result >= 0) {
                                "CoWaitForMultipleHandles failed with HRESULT 0x${result.toUInt().toString(16)}."
                            }
                            check(signaledIndex.value == 0u) {
                                "CoWaitForMultipleHandles returned an unexpected handle index."
                            }
                            if (WaitForSingleObject(exitThread, 1u) == WAIT_OBJECT_0) {
                                return
                            }
                            ResetEvent(createObject)
                            nonAgileObject = PopupMenu()
                            callObject()
                            check(SetEvent(objectCreated) != FALSE)
                        }
                    } finally {
                        nonAgileObject?.nativeObject?.close()
                        nonAgileObject = null
                    }
                }
            }
        } catch (failure: Throwable) {
            threadFailure = failure
            runCatching { SetEvent(objectCreated) }
        }
    }

    private fun callObject(): Int = requireNotNull(nonAgileObject).commands.size
}

private const val COM_AWARE_WAIT_FLAGS: UInt = 0x0000000Au

private fun nonAgileObjectThreadMain(parameter: COpaquePointer?): UInt {
    checkNotNull(parameter).asStableRef<NativeNonAgileObjectPerf>().get().objectAllocationLoop()
    return 0u
}
