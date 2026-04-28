package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiInventory
import io.github.kitectlab.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.kitectlab.winrt.metadata.WinRtMetadataParameterCategory
import io.github.kitectlab.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeRef
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtMetadataValidationOptions
import io.github.kitectlab.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.kitectlab.winrt.metadata.requireValidForProjection
import io.github.kitectlab.winrt.metadata.semanticHelpers
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.Marshaler
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.ParameterizedInterfaceId
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.NativeNestedStructFieldSpec
import io.github.kitectlab.winrt.runtime.NativeScalarFieldSpec
import io.github.kitectlab.winrt.runtime.NativeStructLayout
import io.github.kitectlab.winrt.runtime.NativeStructScalarKind
import io.github.kitectlab.winrt.runtime.WinRtBindableIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.kitectlab.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.kitectlab.winrt.runtime.WinRtDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtListProjection
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyListProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceArrayProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceValueAdapter
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtTypeSignature
import io.github.kitectlab.winrt.runtime.WinRtTypeHandle
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

internal fun KotlinProjectionRenderer.readOnlyCollectionProjectedType(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): TypeName = when (binding.kind) {
    KotlinProjectionReadOnlyCollectionKind.Iterable ->
        Iterable::class.asClassName().parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
    KotlinProjectionReadOnlyCollectionKind.VectorView ->
        List::class.asClassName().parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
    KotlinProjectionReadOnlyCollectionKind.MapView ->
        Map::class.asClassName().parameterizedBy(
            resolveTypeName(requireNotNull(binding.keyBinding).typeName),
            resolveTypeName(requireNotNull(binding.valueBinding).typeName),
        )
}

internal fun KotlinProjectionRenderer.mutableCollectionProjectedType(
    binding: KotlinProjectionMutableCollectionBinding,
): TypeName = when (binding.kind) {
    KotlinProjectionMutableCollectionKind.Vector ->
        MUTABLE_LIST_CLASS_NAME.parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
    KotlinProjectionMutableCollectionKind.Map ->
        MUTABLE_MAP_CLASS_NAME.parameterizedBy(
            resolveTypeName(requireNotNull(binding.keyBinding).typeName),
            resolveTypeName(requireNotNull(binding.valueBinding).typeName),
        )
}

internal fun KotlinProjectionRenderer.renderMutableCollectionDelegateProperty(
    binding: KotlinProjectionMutableCollectionBinding,
): PropertySpec =
    PropertySpec.builder(binding.delegatePropertyName, mutableCollectionProjectedType(binding))
        .addModifiers(KModifier.PRIVATE)
        .delegate(
            CodeBlock.of(
                "lazy(%T.PUBLICATION) {\n%L}\n",
                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                renderMutableCollectionDelegateInitializer(binding),
            ),
        )
        .build()

internal fun KotlinProjectionRenderer.renderMutableCollectionDelegateInitializer(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock = when (binding.kind) {
    KotlinProjectionMutableCollectionKind.Vector ->
        bindableCollectionDelegateInitializer(binding.slotInterfaceQualifiedName, binding.ownerCachePropertyName)
            ?: renderVectorCollectionDelegateInitializer(binding)
    KotlinProjectionMutableCollectionKind.Map -> renderMapCollectionDelegateInitializer(binding)
}

internal fun KotlinProjectionRenderer.renderVectorCollectionDelegateInitializer(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock {
    val elementBinding = requireNotNull(binding.elementBinding)
    val elementType = resolveTypeName(elementBinding.typeName)
    val projectedType = mutableCollectionProjectedType(binding)
    val abstractMutableListType = ABSTRACT_MUTABLE_LIST_CLASS_NAME.parameterizedBy(elementType)
    return CodeBlock.of(
        """
        object : %T(), %T, %T {
            override val nativeObject: %T
                get() = %L

            override val size: Int
                get() = __readSize().toInt()

            override fun get(index: Int): %T {
                require(index >= 0) { %S }
                %L
            }

            override fun set(index: Int, element: %T): %T {
                require(index >= 0) { %S }
                val __previous = get(index)
                %L
                return __previous
            }

            override fun add(index: Int, element: %T) {
                require(index >= 0) { %S }
                %L
            }

            override fun add(element: %T): Boolean {
                %L
                return true
            }

            override fun removeAt(index: Int): %T {
                require(index >= 0) { %S }
                val __previous = get(index)
                %L
                return __previous
            }

            override fun clear() {
                %L
            }

            private fun __readSize(): %T {
                %L
            }
        }
        """.trimIndent() + "\n",
        abstractMutableListType,
        projectedType,
        IWINRT_OBJECT_CLASS_NAME,
        COM_OBJECT_REFERENCE_CLASS_NAME,
        binding.ownerCachePropertyName,
        elementType,
        "index must be non-negative.",
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "GETAT_SLOT",
            returnBinding = elementBinding,
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding(
                    name = "index",
                    typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                ),
            ),
        ).toString(),
        elementType,
        elementType,
        "index must be non-negative.",
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "SETAT_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding(
                    name = "index",
                    typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                ),
                KotlinProjectionAbiParameterBinding("element", elementBinding),
            ),
        ).toString(),
        elementType,
        "index must be non-negative.",
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "INSERTAT_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding(
                    name = "index",
                    typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                ),
                KotlinProjectionAbiParameterBinding("element", elementBinding),
            ),
        ).toString(),
        elementType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "APPEND_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("element", elementBinding)),
        ).toString(),
        elementType,
        "index must be non-negative.",
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "REMOVEAT_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding(
                    name = "index",
                    typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                ),
            ),
        ).toString(),
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "CLEAR_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
        ).toString(),
        KOTLIN_UINT_CLASS_NAME,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "SIZE_GETTER_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
        ).toString(),
    )
}

internal fun KotlinProjectionRenderer.renderMapCollectionDelegateInitializer(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock {
    val keyBinding = requireNotNull(binding.keyBinding)
    val valueBinding = requireNotNull(binding.valueBinding)
    val keyType = resolveTypeName(keyBinding.typeName)
    val valueType = resolveTypeName(valueBinding.typeName)
    val projectedType = mutableCollectionProjectedType(binding)
    val abstractMutableMapType = ABSTRACT_MUTABLE_MAP_CLASS_NAME.parameterizedBy(keyType, valueType)
    val entryType = MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType)
    return CodeBlock.of(
        """
        object : %T(), %T, %T {
            override val nativeObject: %T
                get() = %L

            override val entries: MutableSet<%T>
                get() {
                    val __map = this
                    return object : %T<%T>() {
                        override val size: Int
                            get() = __map.size

                        override fun add(element: %T): Boolean {
                            val __replaced = __map.containsKey(element.key)
                            __map.put(element.key, element.value)
                            return !__replaced
                        }

                        override fun iterator(): MutableIterator<%T> {
                            val __iterator = __createEntryIterator()
                            return object : MutableIterator<%T> {
                                private var __lastReturned: %T? = null

                                override fun hasNext(): Boolean = __iterator.hasNext()

                                override fun next(): %T {
                                    val __entry = __iterator.next()
                                    val __mutableEntry = object : %T {
                                        override val key: %T = __entry.key
                                        private var __currentValue: %T = __entry.value
                                        override val value: %T
                                            get() = __currentValue

                                        override fun setValue(newValue: %T): %T {
                                            val __previous = __currentValue
                                            __map.put(key, newValue)
                                            __currentValue = newValue
                                            return __previous
                                        }
                                    }
                                    __lastReturned = __mutableEntry
                                    return __mutableEntry
                                }

                                override fun remove() {
                                    val __entry = __lastReturned ?: throw %T(%S)
                                    __map.remove(__entry.key)
                                    __lastReturned = null
                                }
                            }
                        }
                    }
                }

            override val size: Int
                get() = __readSize().toInt()

            override fun containsKey(key: %T): Boolean {
                %L
            }

            override fun get(key: %T): %T? {
                return if (containsKey(key)) {
                    %L
                } else {
                    null
                }
            }

            override fun put(key: %T, value: %T): %T? {
                val __previous = get(key)
                %L
                return __previous
            }

            override fun remove(key: %T): %T? {
                val __previous = get(key) ?: return null
                %L
                return __previous
            }

            override fun clear() {
                %L
            }

            private fun __createEntryIterator(): Iterator<Map.Entry<%T, %T>> {
                %L
            }

            private fun __readSize(): %T {
                %L
            }
        }
        """.trimIndent() + "\n",
        abstractMutableMapType,
        projectedType,
        IWINRT_OBJECT_CLASS_NAME,
        COM_OBJECT_REFERENCE_CLASS_NAME,
        binding.ownerCachePropertyName,
        entryType,
        ABSTRACT_MUTABLE_SET_CLASS_NAME,
        entryType,
        entryType,
        entryType,
        entryType,
        entryType,
        entryType,
        MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType),
        keyType,
        valueType,
        valueType,
        valueType,
        valueType,
        ILLEGAL_STATE_EXCEPTION_CLASS_NAME,
        "remove() before next() is not allowed for mutable map entry iteration.",
        keyType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "HASKEY_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
        ).toString(),
        keyType,
        valueType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "LOOKUP_SLOT",
            returnBinding = valueBinding,
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
        ).toString(),
        keyType,
        valueType,
        valueType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "INSERT_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding("key", keyBinding),
                KotlinProjectionAbiParameterBinding("value", valueBinding),
            ),
        ).toString(),
        keyType,
        valueType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "REMOVE_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
        ).toString(),
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "CLEAR_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
        ).toString(),
        keyType,
        valueType,
        renderMappedIteratorCreationCode(
            ownerExpression = binding.ownerCachePropertyName,
            iterableSlotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterable",
            elementBinding = null,
            entryBinding = binding.asReadOnlyEntryBinding(),
        ),
        KOTLIN_UINT_CLASS_NAME,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "SIZE_GETTER_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
        ).toString(),
    )
}

internal fun KotlinProjectionRenderer.renderReadOnlyCollectionDelegateProperty(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): PropertySpec =
    PropertySpec.builder(binding.delegatePropertyName, readOnlyCollectionProjectedType(binding))
        .addModifiers(KModifier.PRIVATE)
        .delegate(
            CodeBlock.of(
                "lazy(%T.PUBLICATION) {\n%L}\n",
                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                renderReadOnlyCollectionDelegateInitializer(binding),
            ),
        )
        .build()

internal fun KotlinProjectionRenderer.renderReadOnlyCollectionDelegateInitializer(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock = when (binding.kind) {
    KotlinProjectionReadOnlyCollectionKind.Iterable ->
        bindableCollectionDelegateInitializer(binding.slotInterfaceQualifiedName, binding.ownerCachePropertyName)
            ?: renderIterableCollectionDelegateInitializer(binding)
    KotlinProjectionReadOnlyCollectionKind.VectorView ->
        bindableCollectionDelegateInitializer(binding.slotInterfaceQualifiedName, binding.ownerCachePropertyName)
            ?: renderVectorViewCollectionDelegateInitializer(binding)
    KotlinProjectionReadOnlyCollectionKind.MapView -> renderMapViewCollectionDelegateInitializer(binding)
}

private fun bindableCollectionDelegateInitializer(
    slotInterfaceQualifiedName: String,
    ownerCachePropertyName: String,
): CodeBlock? =
    when (mappedTypeByAbiName(slotInterfaceQualifiedName)?.abiValueKind) {
        KotlinProjectionAbiValueKind.MappedBindableIterable ->
            CodeBlock.of("%T.fromAbi(%L)\n", WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME, ownerCachePropertyName)
        KotlinProjectionAbiValueKind.MappedBindableVectorView ->
            CodeBlock.of("%T.fromAbi(%L)\n", WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME, ownerCachePropertyName)
        KotlinProjectionAbiValueKind.MappedBindableVector ->
            CodeBlock.of("%T.fromAbi(%L)\n", WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME, ownerCachePropertyName)
        else -> null
    }

internal fun KotlinProjectionRenderer.renderIterableCollectionDelegateInitializer(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock {
    val elementBinding = requireNotNull(binding.elementBinding)
    val elementType = resolveTypeName(elementBinding.typeName)
    val projectedType = readOnlyCollectionProjectedType(binding)
    val iteratorType = Iterator::class.asClassName().parameterizedBy(elementType)
    return CodeBlock.of(
        """
        object : %T, %T {
            override val nativeObject: %T
                get() = %L

            override fun iterator(): %T {
                val __owner = %L
                %L
            }
        }
        """.trimIndent() + "\n",
        projectedType,
        IWINRT_OBJECT_CLASS_NAME,
        COM_OBJECT_REFERENCE_CLASS_NAME,
        binding.ownerCachePropertyName,
        iteratorType,
        binding.ownerCachePropertyName,
        renderMappedIteratorCreationCode(
            ownerExpression = "__owner",
            iterableSlotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            elementBinding = elementBinding,
            entryBinding = null,
        ),
    )
}

internal fun KotlinProjectionRenderer.renderVectorViewCollectionDelegateInitializer(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock {
    val elementBinding = requireNotNull(binding.elementBinding)
    val elementType = resolveTypeName(elementBinding.typeName)
    val projectedType = readOnlyCollectionProjectedType(binding)
    val abstractListType = ABSTRACT_LIST_CLASS_NAME.parameterizedBy(elementType)
    return CodeBlock.of(
        """
        object : %T(), %T, %T {
            override val nativeObject: %T
                get() = %L

            override val size: Int
                get() = __readSize().toInt()

            override fun get(index: Int): %T {
                require(index >= 0) { %S }
                %L
            }

            private fun __readSize(): %T {
                %L
            }
        }
        """.trimIndent() + "\n",
        abstractListType,
        projectedType,
        IWINRT_OBJECT_CLASS_NAME,
        COM_OBJECT_REFERENCE_CLASS_NAME,
        binding.ownerCachePropertyName,
        elementType,
        "index must be non-negative.",
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "GETAT_SLOT",
            returnBinding = elementBinding,
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding(
                    name = "index",
                    typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                ),
            ),
        ).toString(),
        KOTLIN_UINT_CLASS_NAME,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "SIZE_GETTER_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
        ).toString(),
    )
}

internal fun KotlinProjectionRenderer.renderMapViewCollectionDelegateInitializer(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock {
    val keyBinding = requireNotNull(binding.keyBinding)
    val valueBinding = requireNotNull(binding.valueBinding)
    val keyType = resolveTypeName(keyBinding.typeName)
    val valueType = resolveTypeName(valueBinding.typeName)
    val projectedType = readOnlyCollectionProjectedType(binding)
    val abstractMapType = ABSTRACT_MAP_CLASS_NAME.parameterizedBy(keyType, valueType)
    val entryType = Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)
    return CodeBlock.of(
        """
        object : %T(), %T, %T {
            override val nativeObject: %T
                get() = %L

            override val entries: Set<%T>
                get() {
                    val __entries = linkedSetOf<%T>()
                    val __iterator = __createEntryIterator()
                    while (__iterator.hasNext()) {
                        __entries += __iterator.next()
                    }
                    return __entries
                }

            override fun containsKey(key: %T): Boolean {
                %L
            }

            override fun get(key: %T): %T? {
                return if (containsKey(key)) {
                    %L
                } else {
                    null
                }
            }

            private fun __createEntryIterator(): %T {
                %L
            }
        }
        """.trimIndent() + "\n",
        abstractMapType,
        projectedType,
        IWINRT_OBJECT_CLASS_NAME,
        COM_OBJECT_REFERENCE_CLASS_NAME,
        binding.ownerCachePropertyName,
        entryType,
        entryType,
        keyType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "HASKEY_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
        ).toString(),
        keyType,
        valueType,
        renderCollectionInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotConstantName = "LOOKUP_SLOT",
            returnBinding = valueBinding,
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
        ).toString(),
        Iterator::class.asClassName().parameterizedBy(entryType),
        renderMappedIteratorCreationCode(
            ownerExpression = binding.ownerCachePropertyName,
            iterableSlotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterable",
            elementBinding = null,
            entryBinding = binding,
        ),
    )
}

internal fun KotlinProjectionRenderer.renderMappedIteratorCreationCode(
    ownerExpression: String,
    iterableSlotInterfaceQualifiedName: String,
    elementBinding: KotlinProjectionAbiTypeBinding?,
    entryBinding: KotlinProjectionReadOnlyCollectionBinding?,
): CodeBlock {
    val effectiveEntryBinding = entryBinding ?: elementBinding
        ?.takeIf { it.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair && it.typeArguments.size == 2 }
        ?.let {
            KotlinProjectionReadOnlyCollectionBinding(
                kind = KotlinProjectionReadOnlyCollectionKind.MapView,
                ownerInterfaceQualifiedName = it.resolvedTypeName,
                ownerCachePropertyName = "",
                slotInterfaceQualifiedName = it.resolvedTypeName,
                delegatePropertyName = "",
                keyBinding = it.typeArguments[0],
                valueBinding = it.typeArguments[1],
            )
        }
    val returnType = when {
        effectiveEntryBinding != null -> Map.Entry::class.asClassName().parameterizedBy(
            resolveTypeName(requireNotNull(effectiveEntryBinding.keyBinding).typeName),
            resolveTypeName(requireNotNull(effectiveEntryBinding.valueBinding).typeName),
        )
        else -> resolveTypeName(requireNotNull(elementBinding).typeName)
    }
    return CodeBlock.of(
        """
        fun __createIteratorReference(): %T {
            %L
        }
        val __iterator = __createIteratorReference()
        return object : %T {
            private var __hasNext = __iteratorHasCurrent(__iterator)

            override fun hasNext(): Boolean = __hasNext

            override fun next(): %T {
                if (!__hasNext) {
                    throw %T()
                }
                val __current = __readCurrent(__iterator)
                __hasNext = __iteratorMoveNext(__iterator)
                return __current
            }

            private fun __readCurrent(__iteratorRef: %T): %T {
                %L
            }

            private fun __iteratorHasCurrent(__iteratorRef: %T): Boolean {
                %L
            }

            private fun __iteratorMoveNext(__iteratorRef: %T): Boolean {
                %L
            }
        }
        """.trimIndent() + "\n",
        IUNKNOWN_REFERENCE_CLASS_NAME,
        renderCollectionInvocation(
            invokeTargetExpression = ownerExpression,
            slotInterfaceQualifiedName = iterableSlotInterfaceQualifiedName,
            slotConstantName = "FIRST_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UnknownReference, IUNKNOWN_REFERENCE_CLASS_NAME.simpleName),
        ).toString(),
        Iterator::class.asClassName().parameterizedBy(returnType),
        returnType,
        NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME,
        IUNKNOWN_REFERENCE_CLASS_NAME,
        returnType,
        renderMappedIteratorCurrentCode(elementBinding, effectiveEntryBinding, "__iteratorRef").toString(),
        IUNKNOWN_REFERENCE_CLASS_NAME,
        renderCollectionInvocation(
            invokeTargetExpression = "__iteratorRef",
            slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
            slotConstantName = "HASCURRENT_GETTER_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
        ).toString(),
        IUNKNOWN_REFERENCE_CLASS_NAME,
        renderCollectionInvocation(
            invokeTargetExpression = "__iteratorRef",
            slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
            slotConstantName = "MOVENEXT_SLOT",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
        ).toString(),
    )
}

internal fun KotlinProjectionRenderer.renderMappedIteratorCurrentCode(
    elementBinding: KotlinProjectionAbiTypeBinding?,
    entryBinding: KotlinProjectionReadOnlyCollectionBinding?,
    iteratorExpression: String,
): CodeBlock {
    if (entryBinding != null) {
        val keyBinding = requireNotNull(entryBinding.keyBinding)
        val valueBinding = requireNotNull(entryBinding.valueBinding)
        val keyType = resolveTypeName(keyBinding.typeName)
        val valueType = resolveTypeName(valueBinding.typeName)
        return CodeBlock.of(
            """
            fun __readPairReference(): %T {
                %L
            }
            fun __readKey(__pairRef: %T): %T {
                %L
            }
            fun __readValue(__pairRef: %T): %T {
                %L
            }
            val __pair = __readPairReference()
            val __key = __readKey(__pair)
            val __value = __readValue(__pair)
            return object : %T {
                override val key: %T = __key
                override val value: %T = __value
            }
            """.trimIndent(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = iteratorExpression,
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                slotConstantName = "CURRENT_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UnknownReference, IUNKNOWN_REFERENCE_CLASS_NAME.simpleName),
            ).toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            keyType,
            renderCollectionInvocation(
                invokeTargetExpression = "__pairRef",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                slotConstantName = "KEY_GETTER_SLOT",
                returnBinding = keyBinding,
            ).toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = "__pairRef",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                slotConstantName = "VALUE_GETTER_SLOT",
                returnBinding = valueBinding,
            ).toString(),
            Map.Entry::class.asClassName().parameterizedBy(keyType, valueType),
            keyType,
            valueType,
        )
    }
    return renderCollectionInvocation(
        invokeTargetExpression = iteratorExpression,
        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
        slotConstantName = "CURRENT_GETTER_SLOT",
        returnBinding = requireNotNull(elementBinding),
    ).toString().let { CodeBlock.of("%L", it) }
}

internal fun KotlinProjectionRenderer.renderCollectionInvocation(
    invokeTargetExpression: String,
    slotInterfaceQualifiedName: String,
    slotConstantName: String,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
): CodeBlock {
    val callPlan = requireAbiCallPlan(
        bindingName = "${slotInterfaceQualifiedName.substringAfterLast('.')}_$slotConstantName",
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
    )
    return renderInlineAbiInvocation(
        invokeTargetExpression = invokeTargetExpression,
        slotExpression = CodeBlock.of("%T.Metadata.%L", projectionClassName(slotInterfaceQualifiedName), slotConstantName),
        callPlan = callPlan,
    ) ?: error("Generator read-only collection parity failed to emit $slotInterfaceQualifiedName.$slotConstantName")
}
