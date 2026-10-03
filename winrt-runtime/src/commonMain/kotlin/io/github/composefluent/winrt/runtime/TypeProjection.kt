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
        return withManagedTypeName(value) { typeName, kind ->
            TypeAbi(
                name = NativeStringMarshaller.fromManaged(typeName)?.handle ?: PlatformAbi.nullPointer,
                kind = kind.ordinal,
            )
        }
    }

    // Non-null classification follows ABI.System.Type.ToAbi; nullable Kotlin input keeps a cleared TypeAbi.
    private inline fun <R> withManagedTypeName(
        value: KClass<*>?,
        action: (String, WinRTTypeKind) -> R,
    ): R {
        if (value == null) {
            return action("", WinRTTypeKind.Primitive)
        }
        val intrinsic = WinRTTypeClassifier.classify(value)
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
                intrinsic?.canonicalRuntimeName ?: TypeNameSupport.getNameForType(value)
            }
        if (FeatureSwitches.traceCcw) println("winrt-typename: $value -> $typeName ($kind)")
        return action(typeName, kind)
    }

    /** CsWinRT Type.Pinnable/GetAbi(ref) borrow the name only for the synchronous input call. */
    fun createInputMarshaler(value: KClass<*>?): WinRTProjectionMarshaler =
        withManagedTypeName(value) { name, kind ->
            val length = winRTStringLength(name)
            val pinnedName = winRTPinString(name, length)
            val nameFrame = acquireScopedNativeHStringReferenceFrame(pinnedName, length)
            var structFrame: NativeStructScratchFrame? = null
            try {
                val frame = acquireNativeStructScratchFrame(LAYOUT.sizeBytes, LAYOUT.alignmentBytes, clear = true)
                structFrame = frame
                val pointer = frame.pointer
                PlatformAbi.writePointer(
                    pointer,
                    LAYOUT.field("name").offsetBytes,
                    scopedNativeHStringReferenceHandle(nameFrame, length),
                )
                PlatformAbi.writeInt32(pointer, LAYOUT.field("kind").offsetBytes, kind.ordinal)
                WinRTProjectionMarshaler(
                    abi = pointer,
                    ownedReference = TypeNameInputScope(frame, nameFrame, pinnedName),
                )
            } catch (error: Throwable) {
                try {
                    releaseInputFrames(structFrame, nameFrame, pinnedName)
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                throw error
            }
        }

    private class TypeNameInputScope(
        private val structFrame: NativeStructScratchFrame,
        private val nameFrame: RawAddress,
        private var pinnedName: String?,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            val name = requireNotNull(pinnedName)
            pinnedName = null
            releaseInputFrames(structFrame, nameFrame, name)
        }
    }

    private fun releaseInputFrames(
        structFrame: NativeStructScratchFrame?,
        nameFrame: RawAddress,
        pinnedName: String,
    ) {
        var failure: Throwable? = null
        try {
            structFrame?.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            try {
                winRTKeepAlive(pinnedName)
            } finally {
                releaseScopedNativeHStringReferenceFrame(nameFrame)
            }
        } catch (error: Throwable) {
            val original = failure
            if (original == null) failure = error else if (original !== error) original.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    fun copyTo(
        value: KClass<*>?,
        destination: RawAddress,
    ) {
        val abi = fromManaged(value)
        PlatformAbi.writePointer(destination, LAYOUT.field("name").offsetBytes, abi.name)
        PlatformAbi.writeInt32(destination, LAYOUT.field("kind").offsetBytes, abi.kind)
    }

    /** Generated XAML schema can retain a closed generic identity after KClass erasure. */
    fun copyMetadataNameTo(name: String, destination: RawAddress) {
        PlatformAbi.writePointer(destination, LAYOUT.field("name").offsetBytes, HString.create(name).handle)
        PlatformAbi.writeInt32(destination, LAYOUT.field("kind").offsetBytes, WinRTTypeKind.Metadata.ordinal)
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
