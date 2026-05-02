package io.github.composefluent.winrt.runtime

sealed interface WinRtTypeSignature {
    fun render(): String

    data class PrimitiveType(
        private val token: String,
    ) : WinRtTypeSignature {
        override fun render(): String = token
    }

    data object ObjectType : WinRtTypeSignature {
        override fun render(): String = "cinterface(IInspectable)"
    }

    data object StringType : WinRtTypeSignature {
        override fun render(): String = "string"
    }

    data class GuidType(
        val value: Guid,
    ) : WinRtTypeSignature {
        override fun render(): String = value.toString().lowercase().let { "{$it}" }
    }

    data class EnumType(
        val qualifiedName: String,
        val underlyingType: WinRtTypeSignature = Int32Type,
    ) : WinRtTypeSignature {
        override fun render(): String = "enum($qualifiedName;${underlyingType.render()})"
    }

    data class StructType(
        val qualifiedName: String,
        val fieldTypes: List<WinRtTypeSignature>,
    ) : WinRtTypeSignature {
        override fun render(): String =
            buildString {
                append("struct(")
                append(qualifiedName)
                fieldTypes.forEach { fieldType ->
                    append(';')
                    append(fieldType.render())
                }
                append(')')
            }
    }

    data class DelegateType(
        val interfaceId: Guid,
    ) : WinRtTypeSignature {
        override fun render(): String = "delegate(${GuidType(interfaceId).render()})"
    }

    data class RuntimeClassType(
        val runtimeClassName: String,
        val defaultInterface: WinRtTypeSignature,
    ) : WinRtTypeSignature {
        override fun render(): String = "rc($runtimeClassName;${defaultInterface.render()})"
    }

    data class ParameterizedInterfaceType(
        val genericInterface: Guid,
        val arguments: List<WinRtTypeSignature>,
    ) : WinRtTypeSignature {
        override fun render(): String =
            buildString {
                append("pinterface(")
                append(GuidType(genericInterface).render())
                arguments.forEach { argument ->
                    append(';')
                    append(argument.render())
                }
                append(')')
            }
    }

    data object Int8Type : WinRtTypeSignature {
        override fun render(): String = "i1"
    }

    data object UInt8Type : WinRtTypeSignature {
        override fun render(): String = "u1"
    }

    data object Int16Type : WinRtTypeSignature {
        override fun render(): String = "i2"
    }

    data object UInt16Type : WinRtTypeSignature {
        override fun render(): String = "u2"
    }

    data object Int32Type : WinRtTypeSignature {
        override fun render(): String = "i4"
    }

    data object UInt32Type : WinRtTypeSignature {
        override fun render(): String = "u4"
    }

    data object Int64Type : WinRtTypeSignature {
        override fun render(): String = "i8"
    }

    data object UInt64Type : WinRtTypeSignature {
        override fun render(): String = "u8"
    }

    data object Float32Type : WinRtTypeSignature {
        override fun render(): String = "f4"
    }

    data object Float64Type : WinRtTypeSignature {
        override fun render(): String = "f8"
    }

    data object BooleanType : WinRtTypeSignature {
        override fun render(): String = "b1"
    }

    data object Char16Type : WinRtTypeSignature {
        override fun render(): String = "c2"
    }

    data object GuidValueType : WinRtTypeSignature {
        override fun render(): String = "g16"
    }

    companion object {
        fun int8(): WinRtTypeSignature = Int8Type

        fun uint8(): WinRtTypeSignature = UInt8Type

        fun int16(): WinRtTypeSignature = Int16Type

        fun uint16(): WinRtTypeSignature = UInt16Type

        fun int32(): WinRtTypeSignature = Int32Type

        fun uint32(): WinRtTypeSignature = UInt32Type

        fun int64(): WinRtTypeSignature = Int64Type

        fun uint64(): WinRtTypeSignature = UInt64Type

        fun float32(): WinRtTypeSignature = Float32Type

        fun float64(): WinRtTypeSignature = Float64Type

        fun boolean(): WinRtTypeSignature = BooleanType

        fun char16(): WinRtTypeSignature = Char16Type

        fun guidValue(): WinRtTypeSignature = GuidValueType

        fun object_(): WinRtTypeSignature = ObjectType

        fun string(): WinRtTypeSignature = StringType

        fun guid(value: String): WinRtTypeSignature = GuidType(Guid(value))

        fun guid(value: Guid): WinRtTypeSignature = GuidType(value)

        fun runtimeClass(
            runtimeClassName: String,
            defaultInterface: WinRtTypeSignature,
        ): WinRtTypeSignature = RuntimeClassType(runtimeClassName, defaultInterface)

        fun enum(
            qualifiedName: String,
            underlyingType: WinRtTypeSignature = Int32Type,
        ): WinRtTypeSignature = EnumType(qualifiedName, underlyingType)

        fun struct(
            qualifiedName: String,
            vararg fieldTypes: WinRtTypeSignature,
        ): WinRtTypeSignature = StructType(qualifiedName, fieldTypes.toList())

        fun delegate(interfaceId: String): WinRtTypeSignature = DelegateType(Guid(interfaceId))

        fun delegate(interfaceId: Guid): WinRtTypeSignature = DelegateType(interfaceId)

        fun parameterizedInterface(
            genericInterface: String,
            vararg arguments: WinRtTypeSignature,
        ): WinRtTypeSignature = ParameterizedInterfaceType(Guid(genericInterface), arguments.toList())

        fun parameterizedInterface(
            genericInterface: Guid,
            vararg arguments: WinRtTypeSignature,
        ): WinRtTypeSignature = ParameterizedInterfaceType(genericInterface, arguments.toList())
    }
}
