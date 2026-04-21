package io.github.kitectlab.winrt.runtime

enum class NativeStructScalarKind(
    val sizeBytes: Long,
) {
    ADDRESS(8),
    INT8(1),
    INT32(4),
    INT64(8),
    DOUBLE(8),
    FLOAT32(4),
    CHAR16(2),
    GUID(Guid.BYTE_SIZE.toLong()),
}

sealed interface NativeStructMemberSpec {
    val name: String
    val sizeBytes: Long
}

data class NativeScalarFieldSpec(
    override val name: String,
    val kind: NativeStructScalarKind,
) : NativeStructMemberSpec {
    override val sizeBytes: Long
        get() = kind.sizeBytes
}

data class NativeNestedStructFieldSpec(
    override val name: String,
    val layout: NativeStructLayout,
) : NativeStructMemberSpec {
    override val sizeBytes: Long
        get() = layout.sizeBytes
}

data class NativeStructField(
    val name: String,
    val offsetBytes: Long,
    val sizeBytes: Long,
)

class NativeStructLayout private constructor(
    val fields: List<NativeStructField>,
    val sizeBytes: Long,
) {
    private val fieldByName: Map<String, NativeStructField> = fields.associateBy(NativeStructField::name)

    fun field(name: String): NativeStructField =
        fieldByName[name] ?: error("Unknown native struct field '$name'.")

    fun slice(
        source: NativePointer,
        fieldName: String,
    ): NativePointer {
        val field = field(fieldName)
        return NativeInterop.slice(source, field.offsetBytes, field.sizeBytes)
    }

    companion object {
        fun sequential(vararg members: NativeStructMemberSpec): NativeStructLayout {
            var offsetBytes = 0L
            val fields =
                members.map { member ->
                    NativeStructField(
                        name = member.name,
                        offsetBytes = offsetBytes,
                        sizeBytes = member.sizeBytes,
                    ).also {
                        offsetBytes += member.sizeBytes
                    }
                }
            return NativeStructLayout(fields = fields, sizeBytes = offsetBytes)
        }
    }
}

interface NativeStructAdapter<T> {
    val layout: NativeStructLayout

    fun read(source: NativePointer): T

    fun write(
        value: T,
        destination: NativePointer,
    )
}

