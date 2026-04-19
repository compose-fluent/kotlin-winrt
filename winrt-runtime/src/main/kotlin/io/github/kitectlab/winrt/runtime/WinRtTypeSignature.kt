package io.github.kitectlab.winrt.runtime

sealed interface WinRtTypeSignature {
    fun render(): String

    data object ObjectType : WinRtTypeSignature {
        override fun render(): String = "cinterface(IInspectable)"
    }

    data class GuidType(
        val value: Guid,
    ) : WinRtTypeSignature {
        override fun render(): String = value.toString().lowercase().let { "{$it}" }
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

    companion object {
        fun object_(): WinRtTypeSignature = ObjectType

        fun guid(value: String): WinRtTypeSignature = GuidType(Guid(value))

        fun guid(value: Guid): WinRtTypeSignature = GuidType(value)

        fun runtimeClass(runtimeClassName: String, defaultInterface: WinRtTypeSignature): WinRtTypeSignature =
            RuntimeClassType(runtimeClassName, defaultInterface)

        fun parameterizedInterface(genericInterface: String, vararg arguments: WinRtTypeSignature): WinRtTypeSignature =
            ParameterizedInterfaceType(Guid(genericInterface), arguments.toList())

        fun parameterizedInterface(genericInterface: Guid, vararg arguments: WinRtTypeSignature): WinRtTypeSignature =
            ParameterizedInterfaceType(genericInterface, arguments.toList())
    }
}
