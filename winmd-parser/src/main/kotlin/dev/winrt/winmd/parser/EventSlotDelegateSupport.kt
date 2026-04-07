package dev.winrt.winmd.parser

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import dev.winrt.core.WinRtDelegateValueKind

internal data class EventSlotDelegatePlan(
    val delegateType: TypeName,
    val lambdaType: LambdaTypeName,
    val delegateGuid: String,
    val parameterPlans: List<EventSlotLambdaParameterPlan>,
) {
    fun argumentKindsLiteral(): String {
        return parameterPlans.joinToString(
            prefix = "listOf(",
            postfix = ")",
        ) { plan -> "${PoetSymbols.winRtDelegateValueKindClass.canonicalName}.${plan.argumentKind.name}" }
    }

    fun callbackInvocation(callbackName: String): CodeBlock {
        val builder = CodeBlock.builder().add("%N(", callbackName)
        parameterPlans.forEachIndexed { index, plan ->
            if (index > 0) {
                builder.add(", ")
            }
            when (plan.decodeMode) {
                EventSlotDecodeMode.DIRECT -> builder.add("args[%L] as %T", index, plan.lambdaType)
                EventSlotDecodeMode.PROJECTION -> builder.add("%T(args[%L] as %T)", plan.lambdaType, index, PoetSymbols.comPtrClass)
            }
        }
        return builder.add(")").build()
    }
}

internal data class EventSlotLambdaParameterPlan(
    val lambdaType: TypeName,
    val argumentKind: WinRtDelegateValueKind,
    val decodeMode: EventSlotDecodeMode,
)

internal enum class EventSlotDecodeMode {
    DIRECT,
    PROJECTION,
}

internal class EventSlotDelegatePlanResolver(
    private val typeNameMapper: TypeNameMapper,
    private val typeRegistry: TypeRegistry,
) {
    fun resolve(
        delegateTypeName: String,
        currentNamespace: String,
        genericParameters: Set<String> = emptySet(),
    ): EventSlotDelegatePlan? {
        val rawType = delegateTypeName.substringBefore('<').substringBefore('`')
        val genericArguments = parseGenericArguments(delegateTypeName) ?: return null
        val parameterPlans = when {
            rawType == "Windows.Foundation.EventHandler" && genericArguments.size == 1 -> listOf(
                EventSlotLambdaParameterPlan(
                    lambdaType = PoetSymbols.comPtrClass,
                    argumentKind = WinRtDelegateValueKind.OBJECT,
                    decodeMode = EventSlotDecodeMode.DIRECT,
                ),
                resolveParameterPlan(genericArguments.single(), currentNamespace, genericParameters) ?: return null,
            )
            rawType == "Windows.Foundation.TypedEventHandler" && genericArguments.size == 2 -> genericArguments.map { argument ->
                resolveParameterPlan(argument, currentNamespace, genericParameters) ?: return null
            }
            else -> return null
        }
        val lambdaType = LambdaTypeName.get(
            parameters = parameterPlans.map { it.lambdaType }.toTypedArray(),
            returnType = Unit::class.asTypeName(),
        )
        return EventSlotDelegatePlan(
            delegateType = typeNameMapper.mapTypeName(delegateTypeName, currentNamespace, genericParameters),
            lambdaType = lambdaType,
            delegateGuid = typeRegistry.findType(delegateTypeName, currentNamespace)?.guid ?: "00000000-0000-0000-0000-000000000000",
            parameterPlans = parameterPlans,
        )
    }

    private fun resolveParameterPlan(
        typeName: String,
        currentNamespace: String,
        genericParameters: Set<String>,
    ): EventSlotLambdaParameterPlan? {
        return when (typeName) {
            "Object" -> EventSlotLambdaParameterPlan(
                lambdaType = PoetSymbols.comPtrClass,
                argumentKind = WinRtDelegateValueKind.OBJECT,
                decodeMode = EventSlotDecodeMode.DIRECT,
            )
            "Int32" -> scalarPlan(Int::class.asTypeName(), WinRtDelegateValueKind.INT32)
            "UInt32" -> scalarPlan(UInt::class.asTypeName(), WinRtDelegateValueKind.UINT32)
            "Boolean" -> scalarPlan(Boolean::class.asTypeName(), WinRtDelegateValueKind.BOOLEAN)
            "Int64" -> scalarPlan(Long::class.asTypeName(), WinRtDelegateValueKind.INT64)
            "UInt64" -> scalarPlan(ULong::class.asTypeName(), WinRtDelegateValueKind.UINT64)
            "Float32" -> scalarPlan(Float::class.asTypeName(), WinRtDelegateValueKind.FLOAT32)
            "Float64" -> scalarPlan(Double::class.asTypeName(), WinRtDelegateValueKind.FLOAT64)
            "String" -> scalarPlan(String::class.asTypeName(), WinRtDelegateValueKind.STRING)
            else -> {
                if (!supportsProjectedObjectType(typeName)) {
                    null
                } else {
                    EventSlotLambdaParameterPlan(
                        lambdaType = typeNameMapper.mapTypeName(typeName, currentNamespace, genericParameters),
                        argumentKind = WinRtDelegateValueKind.OBJECT,
                        decodeMode = EventSlotDecodeMode.PROJECTION,
                    )
                }
            }
        }
    }

    private fun scalarPlan(typeName: TypeName, kind: WinRtDelegateValueKind): EventSlotLambdaParameterPlan {
        return EventSlotLambdaParameterPlan(
            lambdaType = typeName,
            argumentKind = kind,
            decodeMode = EventSlotDecodeMode.DIRECT,
        )
    }

    private fun supportsProjectedObjectType(typeName: String): Boolean {
        return (typeName.contains('.') || typeName in setOf("T", "TSender", "TResult")) &&
            !typeName.contains('`') &&
            !typeName.contains('<') &&
            !typeName.endsWith("[]")
    }

    private fun parseGenericArguments(typeName: String): List<String>? {
        if ('<' !in typeName || !typeName.endsWith(">")) {
            return null
        }
        return splitGenericArguments(typeName.substringAfter('<').substringBeforeLast('>'))
    }
}
