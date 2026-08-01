package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteDescriptor
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteOwnership
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameter
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterRole
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteReceiver
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultStrategy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteValue
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteValueKind

internal fun KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.canonicalCallSiteDescriptorOrNull():
    WinRTProjectionCallSiteDescriptor? =
    when (this) {
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.Simple -> simpleCallSiteDescriptor(helperFunction)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.DescriptorUnit ->
            unitCallSiteDescriptor(arguments)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.DescriptorBoolean ->
            scalarCallSiteDescriptor("Boolean", arguments)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.DescriptorScalar ->
            scalarCallSiteDescriptor(returnShape, arguments)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.ProjectedReferenceGetter ->
            referenceCallSiteDescriptor(helperFunction, emptyList())
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.DescriptorProjectedReference ->
            referenceCallSiteDescriptor(helperFunction, arguments)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.StructGetter ->
            structGetterCallSiteDescriptor(struct)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.StructSetter ->
            structSetterCallSiteDescriptor(struct)
        is KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall.DescriptorStruct ->
            descriptorStructCallSiteDescriptor(resultStruct, arguments)
    }

private fun simpleCallSiteDescriptor(helperFunction: String): WinRTProjectionCallSiteDescriptor? =
    when (helperFunction) {
        "getString" -> scalarCallSiteDescriptor("String", emptyList())
        "getBoolean" -> scalarCallSiteDescriptor("Boolean", emptyList())
        "getNoExceptionBoolean" -> scalarCallSiteDescriptor(
            resultShape = "Boolean",
            argumentShapes = emptyList(),
            hResultPolicy = WinRTProjectionCallSiteHResultPolicy.IGNORE,
        )
        "getInt32" -> scalarCallSiteDescriptor("Int32", emptyList())
        "getUInt32" -> scalarCallSiteDescriptor("UInt32", emptyList())
        "getInt64" -> scalarCallSiteDescriptor("Int64", emptyList())
        "getUInt64" -> scalarCallSiteDescriptor("UInt64", emptyList())
        "getFloat" -> scalarCallSiteDescriptor("Float", emptyList())
        "getDouble" -> scalarCallSiteDescriptor("Double", emptyList())
        "setString" -> unitCallSiteDescriptor(listOf("String"))
        "setBoolean" -> unitCallSiteDescriptor(listOf("Boolean"))
        "setInt32" -> unitCallSiteDescriptor(listOf("Int32"))
        "setUInt32" -> unitCallSiteDescriptor(listOf("UInt32"))
        "setInt64" -> unitCallSiteDescriptor(listOf("Int64"))
        "setUInt64" -> unitCallSiteDescriptor(listOf("UInt64"))
        "setFloat" -> unitCallSiteDescriptor(listOf("Float"))
        "setDouble" -> unitCallSiteDescriptor(listOf("Double"))
        else -> null
    }

private fun unitCallSiteDescriptor(argumentShapes: List<String>): WinRTProjectionCallSiteDescriptor? =
    WinRTProjectionCallSiteDescriptor(
        receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
        resultStrategy = WinRTProjectionCallSiteResultStrategy.UNIT,
        result = WinRTProjectionCallSiteValue.VOID,
        parameters = argumentShapes.callSiteParametersOrNull() ?: return null,
    )

private fun scalarCallSiteDescriptor(
    resultShape: String,
    argumentShapes: List<String>,
    hResultPolicy: WinRTProjectionCallSiteHResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
): WinRTProjectionCallSiteDescriptor? {
    val resultKind = resultShape.callSiteValueKindOrNull() ?: return null
    val isString = resultKind == WinRTProjectionCallSiteValueKind.STRING
    return WinRTProjectionCallSiteDescriptor(
        receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
        hResultPolicy = hResultPolicy,
        resultStrategy = if (isString) {
            WinRTProjectionCallSiteResultStrategy.STRING_OUT
        } else {
            WinRTProjectionCallSiteResultStrategy.SCALAR_OUT
        },
        result = WinRTProjectionCallSiteValue(
            kind = resultKind,
            ownership = if (isString) {
                WinRTProjectionCallSiteOwnership.OWNED
            } else {
                WinRTProjectionCallSiteOwnership.NONE
            },
        ),
        parameters = argumentShapes.callSiteParametersOrNull() ?: return null,
    )
}

private fun referenceCallSiteDescriptor(
    helperFunction: String,
    argumentShapes: List<String>,
): WinRTProjectionCallSiteDescriptor? {
    val isRuntimeClass = helperFunction == "getProjectedRuntimeClass" ||
        helperFunction == "getNullableProjectedRuntimeClass" ||
        helperFunction == "callProjectedRuntimeClass"
    val isInterface = helperFunction == "getProjectedInterface" ||
        helperFunction == "getNullableProjectedInterface" ||
        helperFunction == "callProjectedInterface"
    if (!isRuntimeClass && !isInterface) return null
    return WinRTProjectionCallSiteDescriptor(
        receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
        resultStrategy = WinRTProjectionCallSiteResultStrategy.REFERENCE_OUT,
        result = WinRTProjectionCallSiteValue(
            kind = if (isRuntimeClass) {
                WinRTProjectionCallSiteValueKind.INSPECTABLE_REFERENCE
            } else {
                WinRTProjectionCallSiteValueKind.UNKNOWN_REFERENCE
            },
            ownership = WinRTProjectionCallSiteOwnership.OWNED,
            nullable = helperFunction.startsWith("getNullable"),
        ),
        parameters = argumentShapes.callSiteParametersOrNull() ?: return null,
    )
}

private fun structGetterCallSiteDescriptor(
    struct: KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct,
): WinRTProjectionCallSiteDescriptor =
    WinRTProjectionCallSiteDescriptor(
        receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
        resultStrategy = WinRTProjectionCallSiteResultStrategy.STRUCT_OUT,
        result = struct.callSiteValue(WinRTProjectionCallSiteOwnership.OWNED),
        parameters = listOf(struct.callSiteParameter(WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER)),
    )

private fun structSetterCallSiteDescriptor(
    struct: KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct,
): WinRTProjectionCallSiteDescriptor =
    WinRTProjectionCallSiteDescriptor(
        receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
        resultStrategy = WinRTProjectionCallSiteResultStrategy.UNIT,
        result = WinRTProjectionCallSiteValue.VOID,
        parameters = listOf(
            struct.callSiteParameter(
                role = WinRTProjectionCallSiteParameterRole.STRUCT_VALUE,
                ownership = WinRTProjectionCallSiteOwnership.BORROWED,
            ),
            struct.callSiteParameter(WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER),
        ),
    )

private fun descriptorStructCallSiteDescriptor(
    resultStruct: KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct,
    argumentShapes: List<String>,
): WinRTProjectionCallSiteDescriptor? =
    WinRTProjectionCallSiteDescriptor(
        receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
        resultStrategy = WinRTProjectionCallSiteResultStrategy.STRUCT_OUT,
        result = resultStruct.callSiteValue(WinRTProjectionCallSiteOwnership.OWNED),
        parameters = listOf(
            resultStruct.callSiteParameter(WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER),
        ) + (argumentShapes.callSiteParametersOrNull() ?: return null),
    )

private fun KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct.callSiteParameter(
    role: WinRTProjectionCallSiteParameterRole,
    ownership: WinRTProjectionCallSiteOwnership = WinRTProjectionCallSiteOwnership.NONE,
): WinRTProjectionCallSiteParameter =
    WinRTProjectionCallSiteParameter(
        role = role,
        value = callSiteValue(ownership),
    )

private fun KotlinModulePlatformAbiCallSupport.ModulePlatformAbiStruct.callSiteValue(
    ownership: WinRTProjectionCallSiteOwnership,
): WinRTProjectionCallSiteValue =
    WinRTProjectionCallSiteValue(
        kind = WinRTProjectionCallSiteValueKind.STRUCT,
        ownership = ownership,
        sizeBytes = sizeBytes,
        alignmentBytes = alignmentBytes,
    )

private fun List<String>.callSiteParametersOrNull(): List<WinRTProjectionCallSiteParameter>? {
    val parameters = ArrayList<WinRTProjectionCallSiteParameter>(size)
    for (shape in this) {
        val kind = shape.callSiteValueKindOrNull() ?: return null
        parameters += WinRTProjectionCallSiteParameter(
            role = WinRTProjectionCallSiteParameterRole.ABI_ARGUMENT,
            value = WinRTProjectionCallSiteValue(
                kind = kind,
                ownership = when (kind) {
                    WinRTProjectionCallSiteValueKind.STRING,
                    WinRTProjectionCallSiteValueKind.RAW_ADDRESS,
                    WinRTProjectionCallSiteValueKind.RAW_COM_PTR,
                    WinRTProjectionCallSiteValueKind.OBJECT -> WinRTProjectionCallSiteOwnership.BORROWED
                    else -> WinRTProjectionCallSiteOwnership.NONE
                },
            ),
        )
    }
    return parameters
}

private fun String.callSiteValueKindOrNull(): WinRTProjectionCallSiteValueKind? =
    when (this) {
        "Boolean" -> WinRTProjectionCallSiteValueKind.BOOLEAN
        "Int8", "Byte" -> WinRTProjectionCallSiteValueKind.INT8
        "UInt8" -> WinRTProjectionCallSiteValueKind.UINT8
        "Int16" -> WinRTProjectionCallSiteValueKind.INT16
        "UInt16" -> WinRTProjectionCallSiteValueKind.UINT16
        "Int32" -> WinRTProjectionCallSiteValueKind.INT32
        "UInt32" -> WinRTProjectionCallSiteValueKind.UINT32
        "Int64" -> WinRTProjectionCallSiteValueKind.INT64
        "UInt64" -> WinRTProjectionCallSiteValueKind.UINT64
        "Float" -> WinRTProjectionCallSiteValueKind.FLOAT
        "Double" -> WinRTProjectionCallSiteValueKind.DOUBLE
        "String" -> WinRTProjectionCallSiteValueKind.STRING
        "RawAddress" -> WinRTProjectionCallSiteValueKind.RAW_ADDRESS
        "RawComPtr" -> WinRTProjectionCallSiteValueKind.RAW_COM_PTR
        "Object" -> WinRTProjectionCallSiteValueKind.OBJECT
        else -> null
    }
