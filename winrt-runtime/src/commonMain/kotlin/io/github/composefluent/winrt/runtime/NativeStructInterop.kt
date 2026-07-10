package io.github.composefluent.winrt.runtime

// ---------------------------------------------------------------------------
// NativeAbiLayout — platform-agnostic ABI memory layout descriptor.
// Replaces direct references to JVM java.lang.foreign.MemoryLayout so that
// value-boxing logic can live in commonMain.
// ---------------------------------------------------------------------------

data class NativeAbiLayout(
    val byteSize: Long,
    val byteAlignment: Long = byteSize,
) {
    companion object {
        /** 8-byte pointer (WinRT only runs on 64-bit Windows). */
        val ADDRESS: NativeAbiLayout = NativeAbiLayout(byteSize = 8, byteAlignment = 8)

        val INT8: NativeAbiLayout = NativeAbiLayout(byteSize = 1)
        val INT16: NativeAbiLayout = NativeAbiLayout(byteSize = 2)
        val INT32: NativeAbiLayout = NativeAbiLayout(byteSize = 4)
        val INT64: NativeAbiLayout = NativeAbiLayout(byteSize = 8)
        val FLOAT32: NativeAbiLayout = NativeAbiLayout(byteSize = 4)
        val FLOAT64: NativeAbiLayout = NativeAbiLayout(byteSize = 8)

        /** UTF-16 char (little-endian, unaligned). */
        val CHAR16: NativeAbiLayout = NativeAbiLayout(byteSize = 2, byteAlignment = 1)

        /** 16-byte GUID aligned to its Windows ABI boundary. */
        val GUID: NativeAbiLayout = NativeAbiLayout(byteSize = Guid.BYTE_SIZE.toLong(), byteAlignment = 4)

        /**
         * Windows `TypeName` struct: HSTRING name (ADDRESS) + INT32 kind.
         * Native struct size is rounded to the largest member alignment.
         */
        val TYPE_NAME: NativeAbiLayout = NativeAbiLayout(byteSize = 16, byteAlignment = 8)
    }
}

/** Returns a [NativeAbiLayout] covering this [NativeStructLayout]'s flat binary footprint. */
val NativeStructLayout.abiLayout: NativeAbiLayout
    get() = NativeAbiLayout(byteSize = sizeBytes, byteAlignment = alignmentBytes)

// ---------------------------------------------------------------------------
// NativeStructScalarKind — scalar element sizes for struct field descriptors.
// ---------------------------------------------------------------------------

enum class NativeStructScalarKind(
    val sizeBytes: Long,
    val alignmentBytes: Long = sizeBytes,
) {
    ADDRESS(8),
    INT8(1),
    INT16(2),
    INT32(4),
    INT64(8),
    DOUBLE(8),
    FLOAT32(4),
    CHAR16(2),
    GUID(Guid.BYTE_SIZE.toLong(), 4),
}

// ---------------------------------------------------------------------------
// NativeStructLayout — sequential flat struct layout with named fields.
// ---------------------------------------------------------------------------

sealed interface NativeStructMemberSpec {
    val name: String
    val sizeBytes: Long
    val alignmentBytes: Long
}

data class NativeScalarFieldSpec(
    override val name: String,
    val kind: NativeStructScalarKind,
) : NativeStructMemberSpec {
    override val sizeBytes: Long get() = kind.sizeBytes
    override val alignmentBytes: Long get() = kind.alignmentBytes
}

data class NativeNestedStructFieldSpec(
    override val name: String,
    val layout: NativeStructLayout,
) : NativeStructMemberSpec {
    override val sizeBytes: Long get() = layout.sizeBytes
    override val alignmentBytes: Long get() = layout.alignmentBytes
}

data class NativeStructField(
    val name: String,
    val offsetBytes: Long,
    val sizeBytes: Long,
)

class NativeStructLayout private constructor(
    val fields: List<NativeStructField>,
    val sizeBytes: Long,
    val alignmentBytes: Long,
) {
    private val fieldByName: Map<String, NativeStructField> = fields.associateBy(NativeStructField::name)

    fun field(name: String): NativeStructField =
        fieldByName[name] ?: error("Unknown native struct field '$name'.")

    fun slice(source: RawAddress, fieldName: String): RawAddress {
        val field = field(fieldName)
        return PlatformAbi.slice(source, field.offsetBytes, field.sizeBytes)
    }

    companion object {
        fun sequential(vararg members: NativeStructMemberSpec): NativeStructLayout {
            var offsetBytes = 0L
            var maxAlignmentBytes = 1L
            val fields = members.map { member ->
                maxAlignmentBytes = maxOf(maxAlignmentBytes, member.alignmentBytes)
                offsetBytes = alignTo(offsetBytes, member.alignmentBytes)
                NativeStructField(
                    name = member.name,
                    offsetBytes = offsetBytes,
                    sizeBytes = member.sizeBytes,
                ).also { offsetBytes += member.sizeBytes }
            }
            return NativeStructLayout(
                fields = fields,
                sizeBytes = alignTo(offsetBytes, maxAlignmentBytes),
                alignmentBytes = maxAlignmentBytes,
            )
        }

        private fun alignTo(value: Long, alignment: Long): Long {
            if (alignment <= 1L) {
                return value
            }
            val remainder = value % alignment
            return if (remainder == 0L) value else value + alignment - remainder
        }
    }
}

interface NativeStructAdapter<T> {
    val layout: NativeStructLayout

    fun read(source: RawAddress): T

    fun write(value: T, destination: RawAddress)

    fun disposeAbi(source: RawAddress) {}
}
