package io.github.kitectlab.winrt.runtime

internal fun createPropertyValueHost(value: Any): WinRtInspectableComObject =
    WinRtInspectableComObject(
        interfaceDefinitions = listOf(
            createPropertyValueInterfaceDefinition(value),
        ),
        runtimeClassName = WinRtValueBoxing.boxedRuntimeClassNameForType(value::class),
        managedValue = value,
    )

internal fun createPropertyValueInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition =
    WinRtInspectableInterfaceDefinition(
        interfaceId = IID.IPropertyValue,
        methods = buildPropertyValueMethods(value),
    )

private fun buildPropertyValueMethods(value: Any): List<WinRtInspectableMethodDefinition> {
    val scalarGetters =
        listOf(
            PropertyType.UInt8,
            PropertyType.Int16,
            PropertyType.UInt16,
            PropertyType.Int32,
            PropertyType.UInt32,
            PropertyType.Int64,
            PropertyType.UInt64,
            PropertyType.Single,
            PropertyType.Double,
            PropertyType.Char16,
            PropertyType.Boolean,
            PropertyType.String,
            PropertyType.Guid,
            PropertyType.DateTime,
            PropertyType.TimeSpan,
            PropertyType.Point,
            PropertyType.Size,
            PropertyType.Rect,
        )
    val arrayGetters =
        listOf(
            PropertyType.UInt8Array,
            PropertyType.Int16Array,
            PropertyType.UInt16Array,
            PropertyType.Int32Array,
            PropertyType.UInt32Array,
            PropertyType.Int64Array,
            PropertyType.UInt64Array,
            PropertyType.SingleArray,
            PropertyType.DoubleArray,
            PropertyType.Char16Array,
            PropertyType.BooleanArray,
            PropertyType.StringArray,
            PropertyType.InspectableArray,
            PropertyType.GuidArray,
            PropertyType.DateTimeArray,
            PropertyType.TimeSpanArray,
            PropertyType.PointArray,
            PropertyType.SizeArray,
            PropertyType.RectArray,
        )
    return buildList {
        add(
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, WinRtValueBoxing.propertyTypeOf(value).code)
                KnownHResults.S_OK.value
            },
        )
        add(
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt8(rawArgs[0] as RawAddress, if (WinRtValueBoxing.isNumericScalar(value)) 1 else 0)
                KnownHResults.S_OK.value
            },
        )
        scalarGetters.forEach { propertyType ->
            add(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    ValueBoxingInterop.writePropertyValue(propertyType, value, rawArgs[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
            )
        }
        arrayGetters.forEach { propertyType ->
            add(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    ValueBoxingInterop.writePropertyValueArray(
                        propertyType,
                        value,
                        rawArgs[0] as RawAddress,
                        rawArgs[1] as RawAddress,
                    )
                    KnownHResults.S_OK.value
                },
            )
        }
    }
}
