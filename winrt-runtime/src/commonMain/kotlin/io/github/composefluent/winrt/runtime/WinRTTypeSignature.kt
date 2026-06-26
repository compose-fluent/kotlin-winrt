package io.github.composefluent.winrt.runtime

sealed interface WinRTTypeSignature {
    fun render(): String

    data class PrimitiveType(
        private val token: String,
    ) : WinRTTypeSignature {
        override fun render(): String = token
    }

    data object ObjectType : WinRTTypeSignature {
        override fun render(): String = "cinterface(IInspectable)"
    }

    data object StringType : WinRTTypeSignature {
        override fun render(): String = "string"
    }

    data class GuidType(
        val value: Guid,
    ) : WinRTTypeSignature {
        override fun render(): String = value.toString().lowercase().let { "{$it}" }
    }

    data class EnumType(
        val qualifiedName: String,
        val underlyingType: WinRTTypeSignature = Int32Type,
    ) : WinRTTypeSignature {
        override fun render(): String = "enum($qualifiedName;${underlyingType.render()})"
    }

    data class StructType(
        val qualifiedName: String,
        val fieldTypes: List<WinRTTypeSignature>,
    ) : WinRTTypeSignature {
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
    ) : WinRTTypeSignature {
        override fun render(): String = "delegate(${GuidType(interfaceId).render()})"
    }

    data class RuntimeClassType(
        val runtimeClassName: String,
        val defaultInterface: WinRTTypeSignature,
    ) : WinRTTypeSignature {
        override fun render(): String = "rc($runtimeClassName;${defaultInterface.render()})"
    }

    data class ParameterizedInterfaceType(
        val genericInterface: Guid,
        val arguments: List<WinRTTypeSignature>,
    ) : WinRTTypeSignature {
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

    data object Int8Type : WinRTTypeSignature {
        override fun render(): String = "i1"
    }

    data object UInt8Type : WinRTTypeSignature {
        override fun render(): String = "u1"
    }

    data object Int16Type : WinRTTypeSignature {
        override fun render(): String = "i2"
    }

    data object UInt16Type : WinRTTypeSignature {
        override fun render(): String = "u2"
    }

    data object Int32Type : WinRTTypeSignature {
        override fun render(): String = "i4"
    }

    data object UInt32Type : WinRTTypeSignature {
        override fun render(): String = "u4"
    }

    data object Int64Type : WinRTTypeSignature {
        override fun render(): String = "i8"
    }

    data object UInt64Type : WinRTTypeSignature {
        override fun render(): String = "u8"
    }

    data object Float32Type : WinRTTypeSignature {
        override fun render(): String = "f4"
    }

    data object Float64Type : WinRTTypeSignature {
        override fun render(): String = "f8"
    }

    data object BooleanType : WinRTTypeSignature {
        override fun render(): String = "b1"
    }

    data object Char16Type : WinRTTypeSignature {
        override fun render(): String = "c2"
    }

    data object GuidValueType : WinRTTypeSignature {
        override fun render(): String = "g16"
    }

    companion object {
        fun int8(): WinRTTypeSignature = Int8Type

        fun uint8(): WinRTTypeSignature = UInt8Type

        fun int16(): WinRTTypeSignature = Int16Type

        fun uint16(): WinRTTypeSignature = UInt16Type

        fun int32(): WinRTTypeSignature = Int32Type

        fun uint32(): WinRTTypeSignature = UInt32Type

        fun int64(): WinRTTypeSignature = Int64Type

        fun uint64(): WinRTTypeSignature = UInt64Type

        fun float32(): WinRTTypeSignature = Float32Type

        fun float64(): WinRTTypeSignature = Float64Type

        fun boolean(): WinRTTypeSignature = BooleanType

        fun char16(): WinRTTypeSignature = Char16Type

        fun guidValue(): WinRTTypeSignature = GuidValueType

        fun object_(): WinRTTypeSignature = ObjectType

        fun string(): WinRTTypeSignature = StringType

        fun guid(value: String): WinRTTypeSignature = GuidType(Guid(value))

        fun guid(value: Guid): WinRTTypeSignature = GuidType(value)

        fun runtimeClass(
            runtimeClassName: String,
            defaultInterface: WinRTTypeSignature,
        ): WinRTTypeSignature = RuntimeClassType(runtimeClassName, defaultInterface)

        fun enum(
            qualifiedName: String,
            underlyingType: WinRTTypeSignature = Int32Type,
        ): WinRTTypeSignature = EnumType(qualifiedName, underlyingType)

        fun struct(
            qualifiedName: String,
            vararg fieldTypes: WinRTTypeSignature,
        ): WinRTTypeSignature = StructType(qualifiedName, fieldTypes.toList())

        fun delegate(interfaceId: String): WinRTTypeSignature = DelegateType(Guid(interfaceId))

        fun delegate(interfaceId: Guid): WinRTTypeSignature = DelegateType(interfaceId)

        fun parameterizedInterface(
            genericInterface: String,
            vararg arguments: WinRTTypeSignature,
        ): WinRTTypeSignature = ParameterizedInterfaceType(Guid(genericInterface), arguments.toList())

        fun parameterizedInterface(
            genericInterface: Guid,
            vararg arguments: WinRTTypeSignature,
        ): WinRTTypeSignature = ParameterizedInterfaceType(genericInterface, arguments.toList())
    }
}
