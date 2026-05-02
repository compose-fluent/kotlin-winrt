package io.github.composefluent.winrt.runtime

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
