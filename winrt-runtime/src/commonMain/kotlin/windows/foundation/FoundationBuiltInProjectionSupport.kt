package windows.foundation

import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.CommonWinRTBuiltInProjectionMappings
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IInspectableReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.Projections
import io.github.composefluent.winrt.runtime.WinRTGuid
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTUnsupportedOperationException

private val closableTypeHandle = WinRTTypeHandle("Windows.Foundation.IClosable", IID.IDisposable)

@WinRTGuid("30D5A829-7FA4-4026-83BB-D75BAE4EA99E")
internal interface IClosableProjection

internal object FoundationBuiltInProjectionMappings {
    fun register() {
        registerClosableProjection()
        CommonWinRTBuiltInProjectionMappings.registerStruct(EventRegistrationToken::class)
    }

    private fun registerClosableProjection() {
        Projections.registerCustomAbiTypeMapping(
            publicType = AutoCloseable::class,
            helperType = IClosableProjection::class,
            abiTypeName = "Windows.Foundation.IClosable",
        )
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = AutoCloseable::class,
            projectedTypeName = "Windows.Foundation.IClosable",
            helperType = IClosableProjection::class,
            guid = IID.IDisposable,
            iid = IID.IDisposable,
            isWindowsRuntimeType = true,
        )
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = IClosableProjection::class,
            projectedTypeName = "Windows.Foundation.IClosable",
            guid = IID.IDisposable,
            iid = IID.IDisposable,
            isWindowsRuntimeType = true,
        )
    }
}

internal object FoundationBuiltInProjectionRuntimeHooks {
    fun ensureRegistered() {
        ComWrappersSupport.registerTypedRcwFactory(closableTypeHandle) { inspectable ->
            WinRTClosableObject(inspectable)
        }
    }

    fun tryCreateProjectedReference(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? =
        null
}

class WinRTClosableObject(
    private val inspectable: IInspectableReference,
) : AutoCloseable, IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = inspectable

    override fun close() {
        inspectable.tryQueryInterface(IID.IDisposable)?.use { closable ->
            val hr = ComVtableInvoker.invoke(closable.pointer, slot = 6)
            WinRTPlatformApi.checkSucceededRaw(hr)
            return
        }
        throw WinRTUnsupportedOperationException(
            "Object does not implement Windows.Foundation.IClosable.",
            KnownHResults.E_NOINTERFACE,
        )
    }
}
