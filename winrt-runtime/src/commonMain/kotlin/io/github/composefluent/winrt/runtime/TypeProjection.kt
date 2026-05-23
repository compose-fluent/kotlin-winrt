package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal enum class WinRtTypeKind {
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
                value == KClass::class -> WinRtTypeKind.Metadata
                isPrimitiveWinRtType(value) -> WinRtTypeKind.Primitive
                Projections.isTypeWindowsRuntimeType(value) -> WinRtTypeKind.Metadata
                else -> WinRtTypeKind.Custom
            }
        val typeName =
            if (kind == WinRtTypeKind.Custom) {
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
            WinRtPlatformApi.windowsDeleteStringRaw(name)
        }
    }

    data class TypeAbi(
        val name: RawAddress = PlatformAbi.nullPointer,
        val kind: Int = 0,
    )
}

internal object WinRtBuiltInProjectionMappings {
    fun register() {
        registerAlwaysOnMappings()
        if (!FeatureSwitches.enableDefaultCustomTypeMappings) {
            return
        }

        CommonWinRtBuiltInProjectionMappings.register()
        XamlSystemProjectionMappings.register()
        registerUriProjection()
    }

    private fun registerAlwaysOnMappings() {
        Projections.registerCustomAbiTypeMapping(
            publicType = KClass::class,
            helperType = TypeProjection::class,
            abiTypeName = "Windows.UI.Xaml.Interop.TypeName",
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = KClass::class,
            projectedTypeName = "Windows.UI.Xaml.Interop.TypeName",
            helperType = TypeProjection::class,
            signature = "struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))",
            boxedName = WinRtReferenceTypeNames.boxedReference("Windows.UI.Xaml.Interop.TypeName"),
            isWindowsRuntimeType = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerReferenceArrayType(
            elementType = KClass::class,
            arrayType = emptyArray<KClass<*>>()::class,
        )
    }

    private fun registerUriProjection() {
        Projections.registerCustomAbiTypeMapping(
            publicType = WinRtUri::class,
            helperType = UriProjection::class,
            abiTypeName = "Windows.Foundation.Uri",
            isRuntimeClass = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = WinRtUri::class,
            projectedTypeName = "Windows.Foundation.Uri",
            helperType = UriProjection::class,
            signature = "rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})",
            runtimeClassName = "Windows.Foundation.Uri",
            defaultInterface = IUriRuntimeClassProjection::class,
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        Projections.registerDefaultInterfaceType(
            runtimeClass = WinRtUri::class,
            defaultInterface = IUriRuntimeClassProjection::class,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = IUriRuntimeClassProjection::class,
            projectedTypeName = "Windows.Foundation.IUriRuntimeClass",
            guid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            iid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            isWindowsRuntimeType = true,
        )
    }
}
