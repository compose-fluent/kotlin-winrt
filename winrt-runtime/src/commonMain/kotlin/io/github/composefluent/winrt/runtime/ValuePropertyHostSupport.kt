package io.github.composefluent.winrt.runtime

internal fun createPropertyValueHost(value: Any): WinRTInspectableComObject {
    val propertyType = WinRTValueBoxing.propertyTypeOf(value)
    val definition = cachedValueHostDefinition(
        key = ValueHostShapeKey(
            kind = ValueHostShapeKind.PROPERTY_VALUE,
            defaultInterfaceId = IID.IPropertyValue,
            propertyType = propertyType,
            runtimeClassName = WinRTValueBoxing.boxedRuntimeClassNameForValue(value),
        ),
    ) {
        listOf(createHostPropertyValueInterfaceDefinition(propertyType))
    }
    return createValueHost(value, definition, augmentRuntimeInterfaces = true)
}

internal fun createPropertyValueInterfaceDefinition(
    value: Any,
    propertyType: PropertyType = WinRTValueBoxing.propertyTypeOf(value),
): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = IID.IPropertyValue,
        methods = buildPropertyValueMethods(
            value = value,
            propertyType = propertyType,
            readHostManagedValue = false,
        ),
    )

/**
 * Builds the closed delegate's IPropertyValue surface without capturing one delegate instance.
 * The value is supplied by [WinRTInspectableComObject] at dispatch time.
 */
internal fun createHostPropertyValueInterfaceDefinition(
    propertyType: PropertyType = PropertyType.OtherType,
): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = IID.IPropertyValue,
        methods = buildPropertyValueMethods(
            value = null,
            propertyType = propertyType,
            readHostManagedValue = true,
        ),
    )

private fun buildPropertyValueMethods(
    value: Any?,
    propertyType: PropertyType,
    readHostManagedValue: Boolean,
): List<WinRTInspectableMethodDefinition> {
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
    fun scalarMethod(action: (Any, RawAddress) -> Unit): WinRTInspectableMethodDefinition =
        if (readHostManagedValue) {
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { managedValue, rawArgs ->
                action(requireNotNull(managedValue), rawArgs[0] as RawAddress)
                KnownHResults.S_OK.value
            }
        } else {
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                action(requireNotNull(value), rawArgs[0] as RawAddress)
                KnownHResults.S_OK.value
            }
        }

    fun arrayMethod(action: (Any, RawAddress, RawAddress) -> Unit): WinRTInspectableMethodDefinition =
        if (readHostManagedValue) {
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
            ) { managedValue, rawArgs ->
                action(
                    requireNotNull(managedValue),
                    rawArgs[0] as RawAddress,
                    rawArgs[1] as RawAddress,
                )
                KnownHResults.S_OK.value
            }
        } else {
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
            ) { rawArgs ->
                action(
                    requireNotNull(value),
                    rawArgs[0] as RawAddress,
                    rawArgs[1] as RawAddress,
                )
                KnownHResults.S_OK.value
            }
        }

    return buildList {
        add(
            if (readHostManagedValue) {
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { _, rawArgs ->
                    PlatformAbi.writeInt32(rawArgs[0] as RawAddress, propertyType.code)
                    KnownHResults.S_OK.value
                }
            } else {
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    PlatformAbi.writeInt32(rawArgs[0] as RawAddress, propertyType.code)
                    KnownHResults.S_OK.value
                }
            },
        )
        add(
            scalarMethod { managedValue, resultOut ->
                PlatformAbi.writeInt8(resultOut, if (WinRTValueBoxing.isNumericScalar(managedValue)) 1 else 0)
            },
        )
        scalarGetters.forEach { getterType ->
            add(
                scalarMethod { managedValue, resultOut ->
                    ValueBoxingInterop.writePropertyValue(getterType, managedValue, resultOut)
                },
            )
        }
        arrayGetters.forEach { getterType ->
            add(
                arrayMethod { managedValue, countOut, dataOut ->
                    ValueBoxingInterop.writePropertyValueArray(
                        getterType,
                        managedValue,
                        countOut,
                        dataOut,
                    )
                },
            )
        }
    }
}
