package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import windows.foundation.FoundationBuiltInProjectionMappings

internal enum class WinRTTypeKind {
    Primitive,
    Metadata,
    Custom,
}

private data class ManagedTypeName(val name: String, val kind: WinRTTypeKind) {
    val length = winRTStringLength(name)
    // CsWinRT TypeNameCache retains a string that is already UTF-16 and NUL-terminated.
    // Prepare Kotlin/Native's equivalent once per cache entry, not on every input call.
    val pinnedName = winRTPinString(name, length)
}

private val emptyManagedTypeName = ManagedTypeName("", WinRTTypeKind.Primitive)

@OptIn(ExperimentalAtomicApi::class)
private object ManagedTypeNameCache {
    // Do not initialize TypeProjection's ABI layout or an OS-backed map on registration.
    private val entries = AtomicReference<ConcurrentCacheMap<KClass<*>, ManagedTypeName>?>(null)

    fun getOrCreate(): ConcurrentCacheMap<KClass<*>, ManagedTypeName> {
        while (true) {
            entries.load()?.let { return it }
            val candidate = ConcurrentCacheMap<KClass<*>, ManagedTypeName>()
            if (entries.compareAndSet(null, candidate)) return candidate
        }
    }

    fun invalidate() {
        // Replace, rather than clear: a classification already in progress must not
        // repopulate the new generation with metadata from before registration.
        entries.store(null)
    }
}

internal fun invalidateTypeProjectionNameCache() = ManagedTypeNameCache.invalidate()

@WindowsRuntimeType("struct(Windows.UI.Xaml.Interop.TypeName;string;enum(Windows.UI.Xaml.Interop.TypeKind;i4))")
internal object TypeProjection {
    val LAYOUT: NativeStructLayout =
        NativeStructLayout.sequential(
            NativeScalarFieldSpec("name", NativeStructScalarKind.ADDRESS),
            NativeScalarFieldSpec("kind", NativeStructScalarKind.INT32),
        )

    // CsWinRT 3 ABI.System.Type accesses its Name/Kind fields directly. Resolve our
    // immutable layout once, without field-name lookups on each ABI call.
    private val nameOffsetBytes = LAYOUT.field("name").offsetBytes
    private val kindOffsetBytes = LAYOUT.field("kind").offsetBytes

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
        return withManagedTypeName(value) { reference ->
            TypeAbi(
                name = NativeStringMarshaller.fromManaged(reference.name)?.handle ?: PlatformAbi.nullPointer,
                kind = reference.kind.ordinal,
            )
        }
    }

    // Non-null classification follows ABI.System.Type.ToAbi; nullable Kotlin input keeps a cleared TypeAbi.
    private inline fun <R> withManagedTypeName(
        value: KClass<*>?,
        action: (ManagedTypeName) -> R,
    ): R {
        if (value == null) {
            return action(emptyManagedTypeName)
        }
        // CsWinRT 3 ABI.System.TypeMarshaller caches ManagedTypeReference (name/kind).
        // Native scratch frames and the strong string root remain scoped to each ABI call.
        val cache = ManagedTypeNameCache.getOrCreate()
        val reference = cache[value] ?: cache.computeIfAbsent(value, ::computeManagedTypeName)
        if (FeatureSwitches.traceCcw) println("winrt-typename: $value -> ${reference.name} (${reference.kind})")
        return action(reference)
    }

    private fun computeManagedTypeName(value: KClass<*>): ManagedTypeName {
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
        return ManagedTypeName(typeName, kind)
    }

    /** CsWinRT Type.Pinnable/GetAbi(ref) borrow the name only for the synchronous input call. */
    fun createInputMarshaler(value: KClass<*>?): WinRTProjectionMarshaler =
        withManagedTypeName(value) { reference ->
            val length = reference.length
            val pinnedName = reference.pinnedName
            val kind = reference.kind
            val nameFrame = acquireScopedNativeHStringReferenceFrame(pinnedName, length)
            try {
                // CsWinRT TypeReference keeps the value and HSTRING reference in one scope.
                // Reuse this scope's ABI storage instead of borrowing a second scratch frame.
                val pointer = scopedNativeHStringReferenceScratch(nameFrame)
                PlatformAbi.writePointer(
                    pointer,
                    nameOffsetBytes,
                    scopedNativeHStringReferenceHandle(nameFrame, length),
                )
                // The Win64 TypeName has an Int32 kind followed by four padding bytes.
                PlatformAbi.writeInt64(
                    PlatformAbi.slice(pointer, kindOffsetBytes, Long.SIZE_BYTES.toLong()),
                    kind.ordinal.toLong(),
                )
                WinRTProjectionMarshaler(
                    abi = pointer,
                    ownedReference = TypeNameInputScope(nameFrame, pinnedName),
                )
            } catch (error: Throwable) {
                try {
                    releaseInputFrame(nameFrame, pinnedName)
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                throw error
            }
        }

    private class TypeNameInputScope(
        private val nameFrame: RawAddress,
        private var pinnedName: String?,
    ) : AutoCloseable {
        override fun close() {
            val name = pinnedName ?: return
            pinnedName = null
            releaseInputFrame(nameFrame, name)
        }
    }

    private fun releaseInputFrame(nameFrame: RawAddress, pinnedName: String) {
        try {
            winRTKeepAlive(pinnedName)
        } finally {
            releaseScopedNativeHStringReferenceFrame(nameFrame)
        }
    }

    fun copyTo(
        value: KClass<*>?,
        destination: RawAddress,
    ) {
        val abi = fromManaged(value)
        PlatformAbi.writePointer(destination, nameOffsetBytes, abi.name)
        PlatformAbi.writeInt32(destination, kindOffsetBytes, abi.kind)
    }

    /** Generated XAML schema can retain a closed generic identity after KClass erasure. */
    fun copyMetadataNameTo(name: String, destination: RawAddress) {
        PlatformAbi.writePointer(destination, nameOffsetBytes, HString.create(name).handle)
        PlatformAbi.writeInt32(destination, kindOffsetBytes, WinRTTypeKind.Metadata.ordinal)
    }

    fun fromAbi(source: RawAddress): KClass<*>? =
        fromAbi(
            TypeAbi(
                name = PlatformAbi.readPointer(
                    PlatformAbi.slice(source, nameOffsetBytes, NativeStructScalarKind.ADDRESS.sizeBytes),
                ),
                kind = PlatformAbi.readInt32(
                    PlatformAbi.slice(source, kindOffsetBytes, NativeStructScalarKind.INT32.sizeBytes),
                ),
            ),
        )

    fun disposeAbi(source: RawAddress) {
        val name = PlatformAbi.readPointer(
            PlatformAbi.slice(source, nameOffsetBytes, NativeStructScalarKind.ADDRESS.sizeBytes),
        )
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
