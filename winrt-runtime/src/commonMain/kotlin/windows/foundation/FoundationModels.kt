package windows.foundation

import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructAdapter
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateHandle
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTDelegateType
import io.github.composefluent.winrt.runtime.WinRTProjectedDelegate
import io.github.composefluent.winrt.runtime.WindowsRuntimeType

/**
 * Generic WinRT event handler declaration. The compiler plugin supplies the closed
 * descriptor for managed lambdas; the default body keeps handwritten/runtime-only
 * consumers from accidentally constructing an open generic ABI shape.
 */
@WinRTDelegateType(
    genericInterfaceIid = "9DE1C535-6AE1-11E0-84E1-18A905BCC53F",
    adapterFunction = "io.github.composefluent.winrt.runtime.adaptWinRTEventHandler",
)
fun interface EventHandler<TArgs> : WinRTProjectedDelegate {
    operator fun invoke(sender: Any?, args: TArgs)

    override fun createWinRTDelegateHandle(): WinRTDelegateHandle =
        error("EventHandler lambda must be lowered by the WinRT compiler plugin.")
}

/** Generic two-argument WinRT event handler declaration. */
@WinRTDelegateType(
    genericInterfaceIid = "9DE1C534-6AE1-11E0-84E1-18A905BCC53F",
    adapterFunction = "io.github.composefluent.winrt.runtime.adaptWinRTTypedEventHandler",
)
fun interface TypedEventHandler<TSender, TResult> : WinRTProjectedDelegate {
    operator fun invoke(sender: TSender, args: TResult)

    override fun createWinRTDelegateHandle(): WinRTDelegateHandle =
        error("TypedEventHandler lambda must be lowered by the WinRT compiler plugin.")
}

interface IStringable {
    override fun toString(): String
}

@WindowsRuntimeType("struct(Windows.Foundation.EventRegistrationToken;i8)")
data class EventRegistrationToken(
    val value: Long = 0L,
) {
    companion object Metadata : NativeStructAdapter<EventRegistrationToken> {
        const val BYTE_SIZE: Int = Long.SIZE_BYTES

        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("value", NativeStructScalarKind.INT64),
            )

        override fun read(source: RawAddress): EventRegistrationToken =
            EventRegistrationToken(
                value = PlatformAbi.readInt64(layout.slice(source, "value")),
            )

        override fun write(
            value: EventRegistrationToken,
            destination: RawAddress,
        ) {
            PlatformAbi.writeInt64(layout.slice(destination, "value"), value.value)
        }

        fun fromAbi(source: RawAddress): EventRegistrationToken = read(source)

        fun fromAbiValue(value: Long): EventRegistrationToken = EventRegistrationToken(value)

        fun toAbi(value: EventRegistrationToken): Long = value.value

        fun copyTo(
            value: EventRegistrationToken,
            destination: RawAddress,
        ) {
            write(value, destination)
        }
    }
}
