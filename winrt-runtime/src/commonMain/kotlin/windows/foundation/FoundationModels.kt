package windows.foundation

import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.IInspectableReference
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructAdapter
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WindowsRuntimeType

typealias EventHandler<TArgs> = (Any?, TArgs) -> Unit

typealias TypedEventHandler<TSender, TResult> = (TSender, TResult) -> Unit

interface IStringable {
    override fun toString(): String
}

data class Uri(
    val raw: String,
) {
    override fun toString(): String = raw

    object Metadata {
        val DEFAULT_INTERFACE_IID: Guid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC")

        fun wrap(instance: IInspectableReference): Uri =
            UriAbiProjection.fromInspectable(instance)
    }
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

        fun copyTo(
            value: EventRegistrationToken,
            destination: RawAddress,
        ) {
            write(value, destination)
        }
    }
}
