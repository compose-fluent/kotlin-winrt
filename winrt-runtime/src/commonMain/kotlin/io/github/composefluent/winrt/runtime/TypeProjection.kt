package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import windows.foundation.FoundationBuiltInProjectionMappings

internal enum class WinRTTypeKind {
    Primitive,
    Metadata,
    Custom,
}

@WindowsRuntimeType("struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))")
internal object TypeProjection {
    val LAYOUT: NativeStructLayout =
        NativeStructLayout.sequential(
            NativeScalarFieldSpec("name", NativeStructScalarKind.ADDRESS),
            NativeScalarFieldSpec("kind", NativeStructScalarKind.INT32),
        )

    fun fromAbi(value: TypeAbi): KClass<*>? {
        val name = NativeStringMarshaller.fromAbi(value.name)
        if (name.isBlank()) {
            return null
        }
        return TypeNameSupport.findKClassByNameCached(name)
    }

    fun fromManaged(value: KClass<*>?): TypeAbi {
        if (value == null) {
            return TypeAbi()
        }
        val kind =
            when {
                value == KClass::class -> WinRTTypeKind.Metadata
                isPrimitiveWinRTType(value) -> WinRTTypeKind.Primitive
                Projections.isTypeWindowsRuntimeType(value) -> WinRTTypeKind.Metadata
                else -> WinRTTypeKind.Custom
            }
        val typeName =
            if (kind == WinRTTypeKind.Custom) {
                value.qualifiedName ?: value.simpleName ?: "<anonymous>"
            } else {
                TypeNameSupport.getNameForType(value)
            }
        return TypeAbi(
            name = NativeStringMarshaller.fromManaged(typeName)?.handle ?: PlatformAbi.nullPointer,
            kind = kind.ordinal,
        )
    }

    fun copyTo(
        value: KClass<*>?,
        destination: RawAddress,
    ) {
        val abi = fromManaged(value)
        PlatformAbi.writePointer(destination, LAYOUT.field("name").offsetBytes, abi.name)
        PlatformAbi.writeInt32(destination, LAYOUT.field("kind").offsetBytes, abi.kind)
    }

    fun fromAbi(source: RawAddress): KClass<*>? =
        fromAbi(
            TypeAbi(
                name = PlatformAbi.readPointer(LAYOUT.slice(source, "name")),
                kind = PlatformAbi.readInt32(LAYOUT.slice(source, "kind")),
            ),
        )

    fun disposeAbi(source: RawAddress) {
        val name = PlatformAbi.readPointer(LAYOUT.slice(source, "name"))
        if (!PlatformAbi.isNull(name)) {
            WinRTPlatformApi.windowsDeleteStringRaw(name)
        }
    }

    data class TypeAbi(
        val name: RawAddress = PlatformAbi.nullPointer,
        val kind: Int = 0,
    )
}

internal object WinRTBuiltInProjectionMappings {
    fun register() {
        registerAlwaysOnMappings()
        if (!FeatureSwitches.enableDefaultCustomTypeMappings) {
            return
        }

        CommonWinRTBuiltInProjectionMappings.register()
        FoundationBuiltInProjectionMappings.register()
        XamlSystemProjectionMappings.register()
    }

    private fun registerAlwaysOnMappings() {
        Projections.registerCustomAbiTypeMapping(
            publicType = KClass::class,
            helperType = TypeProjection::class,
            abiTypeName = "Windows.UI.Xaml.Interop.TypeName",
        )
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = KClass::class,
            projectedTypeName = "Windows.UI.Xaml.Interop.TypeName",
            helperType = TypeProjection::class,
            signature = "struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))",
            boxedName = WinRTReferenceTypeNames.boxedReference("Windows.UI.Xaml.Interop.TypeName"),
            isWindowsRuntimeType = true,
        )
        CommonWinRTBuiltInProjectionMappings.registerReferenceArrayType(
            elementType = KClass::class,
            arrayType = emptyArray<KClass<*>>()::class,
        )
    }

}
