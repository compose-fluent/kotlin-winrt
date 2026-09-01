package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultKind
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinProjectionCallSiteDescriptorsTest {
    @Test
    fun caller_owned_composable_outputs_are_derived_by_lowering_not_generator_slots() {
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val invocation = renderer.composeTypedProjectionCallSite(
            callPlan = renderer.requireAbiCallPlan(
                bindingName = "sample.Factory.CreateInstance",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        "__baseInterface",
                        KotlinProjectionAbiTypeBinding(
                            KotlinProjectionAbiValueKind.RawAddress,
                            "io.github.composefluent.winrt.runtime.RawAddress",
                        ),
                    ),
                ),
            ),
            callSiteSupport = support,
            callerOwnedResultType = ClassName("sample", "ComposableResult"),
        )

        assertEquals(1, invocation.plan.parameters.size)
        assertEquals(1, invocation.arguments.size)
        assertEquals(listOf(WinRTProjectionCallSiteSlotDirection.IN), invocation.plan.descriptor.slots.map { it.direction })
        assertEquals(1, invocation.plan.descriptor.functionParameterCount)
        assertEquals(ClassName("sample", "ComposableResult"), invocation.plan.returnType)
        assertEquals(WinRTProjectionCallSiteResultKind.CALLER_OWNED, invocation.plan.resultKind)
        assertEquals(WinRTProjectionCallSiteResultKind.CALLER_OWNED, invocation.plan.metadata.resultKind)
    }

    @Test
    fun runtime_catalog_preserves_the_decided_fourteen_structured_call_sites() {
        val expectedNames = setOf(
            "getString",
            "getBoolean",
            "getNoExceptionBoolean",
            "getInt32",
            "getUInt32",
            "getInt64",
            "getUInt64",
            "getFloat",
            "getDouble",
            "setString",
            "setInt32",
            "setUInt32",
            "setInt64",
            "setUInt64",
        )
        val declarations = KotlinRuntimeOwnedProjectionCallSites.catalog.declarations

        assertEquals(expectedNames, declarations.map { it.functionName }.toSet())
        assertEquals(expectedNames.size, declarations.size)
        declarations.forEach { declaration -> assertEquals(declaration.metadata, declaration.key.metadata) }
    }

    @Test
    fun runtime_owned_selection_is_an_exact_complete_plan_match() {
        val declaration = KotlinRuntimeOwnedProjectionCallSites.catalog.declarations.single { it.functionName == "getInt32" }
        val renderer = KotlinProjectionRenderer()
        val matching = renderer.composeTypedProjectionCallSite(
            callPlan = renderer.requireAbiCallPlan(
                bindingName = "sample.RuntimeOwned.getInt32",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int32"),
                parameterBindings = emptyList(),
            ),
        ).plan
        val different = matching.copy(
            descriptor = matching.descriptor.copy(
                hResultPolicy = WinRTProjectionCallSiteHResultPolicy.IGNORE,
            ),
        )

        assertEquals(declaration, KotlinRuntimeOwnedProjectionCallSites.declarationFor(matching))
        assertEquals(null, KotlinRuntimeOwnedProjectionCallSites.declarationFor(different))
    }

    @Test
    fun runtime_owned_string_selection_uses_the_jvm_mapped_string_descriptor() {
        val renderer = KotlinProjectionRenderer()
        val string = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String")
        val unit = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit")
        val getter = renderer.composeTypedProjectionCallSite(
            renderer.requireAbiCallPlan("sample.get", string, emptyList()),
        ).plan
        val setter = renderer.composeTypedProjectionCallSite(
            renderer.requireAbiCallPlan(
                "sample.set",
                unit,
                listOf(KotlinProjectionAbiParameterBinding("value", string)),
            ),
        ).plan

        assertEquals("getString", KotlinRuntimeOwnedProjectionCallSites.declarationFor(getter)?.functionName)
        assertEquals("setString", KotlinRuntimeOwnedProjectionCallSites.declarationFor(setter)?.functionName)
    }

    @Test
    fun runtime_owned_unsigned_selection_survives_jvm_inline_type_erasure() {
        val renderer = KotlinProjectionRenderer()
        val uint32 = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt32")
        val uint64 = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt64, "UInt64")
        val unit = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit")

        fun getter(binding: KotlinProjectionAbiTypeBinding) =
            renderer.composeTypedProjectionCallSite(
                renderer.requireAbiCallPlan("sample.get", binding, emptyList()),
            ).plan

        fun setter(binding: KotlinProjectionAbiTypeBinding) =
            renderer.composeTypedProjectionCallSite(
                renderer.requireAbiCallPlan(
                    "sample.set",
                    unit,
                    listOf(KotlinProjectionAbiParameterBinding("value", binding)),
                ),
            ).plan

        assertEquals("getUInt32", KotlinRuntimeOwnedProjectionCallSites.declarationFor(getter(uint32))?.functionName)
        assertEquals("getUInt64", KotlinRuntimeOwnedProjectionCallSites.declarationFor(getter(uint64))?.functionName)
        assertEquals("setUInt32", KotlinRuntimeOwnedProjectionCallSites.declarationFor(setter(uint32))?.functionName)
        assertEquals("setUInt64", KotlinRuntimeOwnedProjectionCallSites.declarationFor(setter(uint64))?.functionName)
    }

    @Test
    fun mapped_call_site_adapter_resolution_is_exact_and_ambiguous_kind_indexes_fail_closed() {
        val renderer = KotlinProjectionRenderer()
        val intBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int32")
        val exactBinding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedIterable,
            typeName = "Windows.Foundation.Collections.IIterable<Int32>",
            resolvedTypeName = "Windows.Foundation.Collections.IIterable<Int32>",
            typeArguments = listOf(intBinding),
        )

        val exactPlan = renderer.buildCallSiteRecipe(exactBinding)
        val spoofedFailure = runCatching {
            renderer.buildCallSiteRecipe(
                exactBinding.copy(
                    typeName = "Sample.Collections.IFakeIterable<Int32>",
                    resolvedTypeName = "Sample.Collections.IFakeIterable<Int32>",
                ),
            )
        }.exceptionOrNull()

        assertNotNull(exactPlan.inputFactory)
        assertNotNull(exactPlan.inputValueAdapter)
        assertNotNull(spoofedFailure)
        assertTrue(spoofedFailure!!.message.orEmpty().contains("has no central adapter description"))
        assertTrue(AMBIGUOUS_MAPPED_TYPE_ABI_KINDS.contains(KotlinProjectionAbiValueKind.MappedBindableIterable))
        assertNull(mappedTypeByAbiKind(KotlinProjectionAbiValueKind.MappedBindableIterable))
    }

    @Test
    fun object_aliases_normalize_to_the_exact_system_object_adapter_identity() {
        val planner = KotlinProjectionPlanner()
        val renderer = KotlinProjectionRenderer()

        listOf("Object", "Any", "System.Object").forEach { alias ->
            val binding = planner.classifyAbiTypeBinding(alias, "sample", emptyMap())
            val recipePlan = renderer.buildCallSiteRecipe(binding)

            assertEquals(KotlinProjectionAbiValueKind.Object, binding.kind)
            assertEquals("System.Object", binding.resolvedTypeName)
            assertNotNull(recipePlan.inputFactory)
            assertNotNull(recipePlan.recipe.callables?.fromAbi)
        }
        assertEquals(
            KotlinProjectionAbiValueKind.Object,
            mappedTypeByAbiName("System.Object")?.abiValueKind,
        )
    }

    @Test
    fun direct_inbound_descriptor_composition_accepts_borrowed_scalars_and_rejects_consuming_mapped_outputs() {
        val planner = KotlinProjectionPlanner()
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val objectBinding = planner.classifyAbiTypeBinding("System.Object", "Sample.Foundation", emptyMap())
        val intBinding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.Int32,
            typeName = "Int",
            resolvedTypeName = "Int",
        )
        val vectorViewBinding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedVectorView,
            typeName = "Windows.Foundation.Collections.IVectorView<Int>",
            resolvedTypeName = "Windows.Foundation.Collections.IVectorView<Int>",
            typeArguments = listOf(intBinding),
        )

        val objectParameter = renderer.composeDirectInboundCallSiteParameter(objectBinding)
        val intParameter = renderer.composeDirectInboundCallSiteParameter(intBinding)
        renderer.composeTypedProjectionCallSite(
            renderer.requireAbiCallPlan("sample.getObject", objectBinding, emptyList()),
            support,
        )
        val supportContents = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
            .joinToString("\n") { file -> file.contents }

        assertNotNull(objectParameter)
        assertEquals("System.Object", objectParameter?.abiType)
        assertNotNull(intParameter)
        assertEquals("kotlin.Int", intParameter?.abiType)
        assertNull(renderer.composeDirectInboundCallSiteParameter(vectorViewBinding))
        assertTrue(supportContents.contains("role = WinRTProjectionAbiCodecRole.FROM_BORROWED_ABI"))
        assertTrue(supportContents.contains("WinRTObjectMarshaller.fromAbi(__abi)"))
        assertTrue(supportContents.contains("role = WinRTProjectionAbiCodecRole.FROM_ABI"))
        assertTrue(supportContents.contains("WinRTObjectMarshaller.fromOwnedAbi(__abi)"))
    }

    @Test
    fun nullable_custom_object_factory_uses_nullable_return_and_descriptor_contract() {
        val renderer = KotlinProjectionRenderer()
        val binding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.ProjectedInterface,
            typeName = "Windows.UI.Xaml.Data.INotifyPropertyChanged?",
            resolvedTypeName = "Windows.UI.Xaml.Data.INotifyPropertyChanged?",
        )
        val recipePlan = renderer.buildCallSiteRecipe(binding)
        val factory = requireNotNull(recipePlan.inputFactory)
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val invocation = renderer.composeTypedProjectionCallSite(
            renderer.buildAbiCallPlan(
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", binding)),
            ) ?: error("Expected nullable custom-object call plan."),
            support,
        )
        val generatedSource = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
            .joinToString("\n") { file -> file.contents }

        assertTrue(factory.returnType.isNullable)
        assertTrue(factory.nullable)
        assertTrue(factory.body.toString().contains("createObjectReferenceOrNull"))
        assertTrue(invocation.plan.descriptor.slots.single().recipe.nullable)
        assertTrue(invocation.plan.descriptor.slots.single().recipe.callables?.createMarshaler.orEmpty().isNotBlank())
        assertTrue(generatedSource.contains("ComObjectReference?"))
        assertTrue(generatedSource.contains("createObjectReferenceOrNull"))
    }

    @Test
    fun nullable_projected_object_input_omits_module_codec_for_long_generated_signatures() {
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val binding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
            typeName = "Windows.Web.Http.HttpRequestMessage?",
            resolvedTypeName = "Windows.Web.Http.HttpRequestMessage?",
        )

        val invocation = renderer.composeTypedProjectionCallSite(
            renderer.buildAbiCallPlan(
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("request", binding)),
            ) ?: error("Expected nullable projected-object call plan."),
            support,
        )
        val generatedFiles = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
        assertTrue(invocation.plan.descriptor.slots.single().recipe.callables?.toAbi.orEmpty().isBlank())
        assertEquals(
            "windows.web.http.HttpRequestMessage?",
            invocation.plan.descriptor.slots.single().recipe.projectedKotlinTypeName,
        )
        assertTrue(generatedFiles.isEmpty())
    }

    @Test
    fun mapped_recipe_signature_uses_the_composed_kotlin_surface_type() {
        val renderer = KotlinProjectionRenderer()
        val intBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int32")
        val binding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedVectorView,
            typeName = "Windows.Foundation.Collections.IVectorView<Int32>",
            resolvedTypeName = "Windows.Foundation.Collections.IVectorView<Int32>",
            typeArguments = listOf(intBinding),
        )

        val recipe = renderer.buildCallSiteRecipe(binding, category = null).recipe

        assertEquals("kotlin.collections.List<kotlin.Int>", recipe.projectedKotlinTypeName)
        assertTrue(recipe.typeSignature.contains("Windows.Foundation.Collections.IVectorView<Int32>"))
    }

    @Test
    fun mapped_reference_recipe_uses_projected_nullability() {
        val renderer = KotlinProjectionRenderer()
        val intBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int32")
        val binding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.Reference,
            typeName = "Windows.Foundation.IReference<Int32>",
            resolvedTypeName = "Windows.Foundation.IReference<Int32>",
            interfaceId = Guid("61C17706-2D65-11E0-9AE8-D48564015472"),
            typeArguments = listOf(intBinding),
        )

        val recipe = renderer.buildCallSiteRecipe(binding, category = null).recipe

        assertTrue(recipe.nullable)
        assertEquals("kotlin.Int?", recipe.projectedKotlinTypeName)
    }

    @Test
    fun mapped_reference_call_sites_keep_the_closed_winmd_abi_identity() {
        val planner = KotlinProjectionPlanner()
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val reference = planner.classifyAbiTypeBinding(
            typeName = "Windows.Foundation.IReference<Int>",
            currentNamespace = "sample",
            typesByQualifiedName = emptyMap(),
        )
        val unit = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit")

        val getter = renderer.composeTypedProjectionCallSite(
            renderer.requireAbiCallPlan("sample.get", reference, emptyList()),
            support,
        ).plan
        val setter = renderer.composeTypedProjectionCallSite(
            renderer.requireAbiCallPlan(
                "sample.set",
                unit,
                listOf(KotlinProjectionAbiParameterBinding("value", reference)),
            ),
            support,
        ).plan

        assertEquals("Windows.Foundation.IReference<Int>", reference.explicitCallSiteAbiTypeName())
        assertEquals("Windows.Foundation.IReference<Int>", getter.metadata.returnAbiType)
        assertEquals(
            "Windows.Foundation.IReference<Int>",
            setter.metadata.parameters.single().abiType,
        )
        assertNull(KotlinRuntimeOwnedProjectionCallSites.declarationFor(getter))
        assertNull(KotlinRuntimeOwnedProjectionCallSites.declarationFor(setter))
    }

    @Test
    fun mapped_call_site_abi_identity_recursively_composes_multiple_winmd_type_arguments() {
        val binding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
            typeName = "Windows.Foundation.IAsyncOperationWithProgress<Int, String>",
            resolvedTypeName = "Windows.Foundation.IAsyncOperationWithProgress<Int, String>",
            typeArguments = listOf(
                KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int"),
                KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String"),
            ),
        )

        assertEquals(
            "Windows.Foundation.IAsyncOperationWithProgress<Int,String>",
            binding.explicitCallSiteAbiTypeName(),
        )
    }

    @Test
    fun enclosing_array_call_site_identity_preserves_a_mapped_winmd_element() {
        val entry = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedKeyValuePair,
            typeName = "Windows.Foundation.Collections.IKeyValuePair<String, Int>",
            resolvedTypeName = "Windows.Foundation.Collections.IKeyValuePair<String, Int>",
            typeArguments = listOf(
                KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String"),
                KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int"),
            ),
        )
        val array = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.Array,
            typeName = "Array<Windows.Foundation.Collections.IKeyValuePair<String, Int>>",
            resolvedTypeName = "Array<Windows.Foundation.Collections.IKeyValuePair<String, Int>>",
            typeArguments = listOf(entry),
        )

        assertEquals(
            "Array<Windows.Foundation.Collections.IKeyValuePair<String,Int>>",
            array.explicitCallSiteAbiTypeName(),
        )
    }

    @Test
    fun metadata_composed_struct_recipes_use_win64_size_classification() {
        fun structBinding(sizeBytes: Int, fieldBindings: List<KotlinProjectionAbiTypeBinding>) =
            KotlinProjectionAbiTypeBinding(
                kind = KotlinProjectionAbiValueKind.Struct,
                typeName = "Sample.Foundation.Value$sizeBytes",
                resolvedTypeName = "Sample.Foundation.Value$sizeBytes",
                abiSize = sizeBytes,
                abiAlignment = fieldBindings.maxOf { binding -> binding.abiSize ?: sizeBytes },
                structFieldBindings = fieldBindings,
                structFieldOffsets = fieldBindings.runningFold(0) { offset, binding ->
                    offset + requireNotNull(binding.abiSize)
                }.dropLast(1),
            )
        val int32 = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.Int32,
            typeName = "Int32",
            abiSize = Int.SIZE_BYTES,
            abiAlignment = Int.SIZE_BYTES,
        )

        val fourByteRecipe = KotlinProjectionRenderer().buildCallSiteRecipe(
            structBinding(Int.SIZE_BYTES, listOf(int32)),
        ).recipe
        val eightByteRecipe = KotlinProjectionRenderer().buildCallSiteRecipe(
            structBinding(Long.SIZE_BYTES, listOf(int32, int32)),
        ).recipe
        val twelveByteRecipe = KotlinProjectionRenderer().buildCallSiteRecipe(
            structBinding(12, listOf(int32, int32, int32)),
        ).recipe

        assertEquals(listOf(WinRTProjectionCallSiteAbiCarrier.INT32), fourByteRecipe.abiCarriers)
        assertEquals(listOf(WinRTProjectionCallSiteAbiCarrier.INT64), eightByteRecipe.abiCarriers)
        assertEquals(listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS), twelveByteRecipe.abiCarriers)
        listOf(fourByteRecipe, eightByteRecipe, twelveByteRecipe).forEach { recipe ->
            assertEquals("copyTo", recipe.callables?.copyToAbi)
            assertEquals("fromAbi", recipe.callables?.fromAbi)
        }
    }

    @Test
    fun mapped_and_reference_array_factories_fold_closed_child_adapters_in_both_placements() {
        val intBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int32")
        val mappedElement = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedIterable,
            typeName = "Windows.Foundation.Collections.IIterable<Int32>",
            resolvedTypeName = "Windows.Foundation.Collections.IIterable<Int32>",
            typeArguments = listOf(intBinding),
        )
        val referenceElement = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
            typeName = "Sample.Foundation.Widget",
            resolvedTypeName = "Sample.Foundation.Widget",
        )

        listOf(mappedElement, referenceElement).forEach { elementBinding ->
            val arrayBinding = KotlinProjectionAbiTypeBinding(
                kind = KotlinProjectionAbiValueKind.Array,
                typeName = "Array<${elementBinding.typeName}>",
                resolvedTypeName = "Array<${elementBinding.resolvedTypeName}>",
                typeArguments = listOf(elementBinding),
            )
            val renderer = KotlinProjectionRenderer()
            val recipePlan = renderer.buildCallSiteRecipe(arrayBinding)
            val childAdapter = requireNotNull(recipePlan.childInputValueAdapters.single())
            val factoryBody = requireNotNull(recipePlan.inputFactory).body.toString()

            assertTrue(factoryBody.contains(childAdapter.toString()))
            assertTrue(factoryBody.contains("referenceValueAdapter"))

            val collectingSupport = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
            val collectingRenderer = KotlinProjectionRenderer(modulePlatformAbiCalls = collectingSupport)
            val invocation = collectingRenderer.composeTypedProjectionCallSite(
                collectingRenderer.buildAbiCallPlan(
                    returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                    parameterBindings = listOf(
                        KotlinProjectionAbiParameterBinding(
                            name = "values",
                            typeBinding = arrayBinding,
                            category = WinRTMetadataParameterCategory.In,
                        ),
                    ),
                ) ?: error("Expected closed array call plan."),
                collectingSupport,
            )
            collectingSupport.typedInvocation("instance", CodeBlock.of("slot"), invocation)
            collectingSupport.typedInvocation("instance", CodeBlock.of("slot"), invocation)
            val enabledCalls = collectingSupport.plannedCalls()
            val selectedSupport = KotlinModulePlatformAbiCallSupport(
                className = ClassName("sample", "ModulePlatformAbi"),
                enabledCalls = enabledCalls,
            )
            val selectedRenderer = KotlinProjectionRenderer(modulePlatformAbiCalls = selectedSupport)
            val selectedInvocation = selectedRenderer.composeTypedProjectionCallSite(
                selectedRenderer.buildAbiCallPlan(
                    returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                    parameterBindings = listOf(KotlinProjectionAbiParameterBinding("values", arrayBinding)),
                ) ?: error("Expected selected closed array call plan."),
                selectedSupport,
            )
            selectedSupport.typedInvocation("instance", CodeBlock.of("slot"), selectedInvocation)
            val moduleSource = selectedSupport.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
                .joinToString("\n") { file -> file.contents }

            val inlineSupport = KotlinModulePlatformAbiCallSupport(
                className = ClassName("sample", "InlinePlatformAbi"),
                enabledCalls = emptySet(),
            )
            val inlineRenderer = KotlinProjectionRenderer(modulePlatformAbiCalls = inlineSupport)
            val inlineInvocation = inlineRenderer.composeTypedProjectionCallSite(
                inlineRenderer.buildAbiCallPlan(
                    returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                    parameterBindings = listOf(KotlinProjectionAbiParameterBinding("values", arrayBinding)),
                ) ?: error("Expected inline closed array call plan."),
                inlineSupport,
            )
            val inlineExpression = inlineSupport
                .typedInvocation("instance", CodeBlock.of("slot"), inlineInvocation)
                .toString()
            val inlineSource = inlineSupport.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
                .joinToString("\n") { file -> file.contents }

            assertTrue(enabledCalls.contains(invocation.plan))
            assertTrue(moduleSource.contains("fun ${selectedInvocation.plan.functionName}("))
            assertTrue(moduleSource.contains("referenceValueAdapter"))
            assertTrue(inlineSource.contains("referenceValueAdapter"))
            assertTrue(inlineExpression.contains("__winrtCallSiteResult"))
            assertFalse(inlineExpression.contains("callSite_"))
        }
    }

    @Test
    fun ordered_slot_composition_is_the_merge_key_without_a_result_family() {
        val first = modulePlan("sample.First")
        val second = modulePlan("sample.Second")

        assertNotEquals(first, second)
        assertNotEquals(first.functionName, second.functionName)
        assertEquals(
            WinRTProjectionCallSiteRecipeKind.PROJECTION,
            first.descriptor.returnSlot?.recipe?.kind,
        )
        assertEquals(2, first.descriptor.slots.size)
    }

    @Test
    fun repeated_complete_plan_emits_one_annotated_module_helper() {
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        val plan = modulePlan("sample.Result")
        val invocation = KotlinTypedProjectionCallSiteInvocation(plan, listOf(CodeBlock.of("value")))

        support.typedInvocation("instance", CodeBlock.of("slot"), invocation)
        support.typedInvocation("instance", CodeBlock.of("slot"), invocation)
        val enabled = support.plannedCalls()
        val rendered = KotlinModulePlatformAbiCallSupport(
            className = ClassName("sample", "ModulePlatformAbi"),
            enabledCalls = enabled,
        ).also { selected ->
            selected.typedInvocation("instance", CodeBlock.of("slot"), invocation)
        }.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).single().contents

        assertEquals(setOf(plan), enabled)
        assertTrue(rendered.contains("@WinRTProjectionCallSite"))
        assertTrue(rendered.contains("@PublishedApi"))
        assertTrue(rendered.contains("internal fun ${plan.functionName}("))
        assertTrue(rendered.contains("fun ${plan.functionName}("))
        assertFalse(rendered.contains("WinRTProjectionIntrinsic"))
    }

    @Test
    fun module_helper_merge_uses_emitted_call_site_identity_not_private_recipe() {
        val first = modulePlan("sample.Result")
        val second = first.copy(
            descriptor = first.descriptor.copy(
                slots = first.descriptor.slots.dropLast(1) + first.descriptor.slots.last().copy(
                    ownership = WinRTProjectionCallSiteOwnership.NONE,
                ),
            ),
        )
        assertNotEquals(first, second)
        assertEquals(first.functionName, second.functionName)

        val collector = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))
        listOf(first, second).forEach { plan ->
            collector.typedInvocation(
                "instance",
                CodeBlock.of("slot"),
                KotlinTypedProjectionCallSiteInvocation(plan, listOf(CodeBlock.of("value"))),
            )
        }
        val enabled = collector.plannedCalls()
        assertEquals(1, enabled.size)

        val selected = KotlinModulePlatformAbiCallSupport(
            className = ClassName("sample", "ModulePlatformAbi"),
            enabledCalls = enabled,
        )
        listOf(first, second).forEach { plan ->
            selected.typedInvocation(
                "instance",
                CodeBlock.of("slot"),
                KotlinTypedProjectionCallSiteInvocation(plan, listOf(CodeBlock.of("value"))),
            )
        }
        val rendered = selected.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).single().contents

        assertEquals(1, Regex("fun ${first.functionName}\\(").findAll(rendered).count())
    }

    @Test
    fun singleton_plan_is_annotated_at_the_real_call_point() {
        val support = KotlinModulePlatformAbiCallSupport(
            className = ClassName("sample", "ModulePlatformAbi"),
            enabledCalls = emptySet(),
        )
        val plan = modulePlan("sample.Result")
        val expression = support.typedInvocation(
            "instance",
            CodeBlock.of("slot"),
            KotlinTypedProjectionCallSiteInvocation(plan, listOf(CodeBlock.of("value"))),
        ).toString()

        assertTrue(expression.contains("WinRTProjectionCallSite"))
        assertFalse(expression.contains("v4|"))
        assertFalse(expression.contains("Base64"))
        assertTrue(expression.contains("__winrtCallSiteResult"))
        assertFalse(expression.contains("callSite_"))
    }

    @Test
    fun module_codec_merge_uses_the_plugin_visible_identity_not_the_private_recipe() {
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "ModulePlatformAbi"))

        fun registerCodec(
            signature: String,
            consumesOwnedAbi: Boolean = true,
        ): String = support.registerCodec(
            operation = "fromAbi",
            role = KotlinProjectionAbiCodecRole.FROM_ABI,
            abiTypeName = "System.Object",
            signature = signature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__abi", INT)),
            returnType = INT,
            body = CodeBlock.of("return __abi\n"),
            consumesOwnedAbi = consumesOwnedAbi,
        )

        val first = registerCodec("private-recipe-a")
        val second = registerCodec("private-recipe-b")
        val rendered = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
            .joinToString("\n", transform = KotlinProjectionFile::contents)

        assertEquals(first, second)
        assertEquals(1, Regex("fun $first\\(").findAll(rendered).count())
        assertEquals(1, Regex("role = WinRTProjectionAbiCodecRole.FROM_ABI").findAll(rendered).count())
        assertEquals(1, Regex("consumesOwnedAbi = true").findAll(rendered).count())
        val conflict = runCatching {
            registerCodec("private-recipe-c", consumesOwnedAbi = false)
        }.exceptionOrNull()
        assertNotNull(conflict)
        assertTrue(conflict?.message.orEmpty().contains("Conflicting generated ABI codec implementations"))
    }

    @Test
    fun large_module_support_stably_shards_call_sites_with_abi_metadata() {
        val support = KotlinModulePlatformAbiCallSupport(
            className = ClassName("sample", "ModulePlatformAbi"),
            abiSupportShardCount = 4,
        )
        val abiTypeNames = (0..64).map { index -> "sample.Type$index" }
        val firstAbiType = abiTypeNames.first()
        val firstOwner = support.codecOwnerFqName(firstAbiType)
        val secondAbiType = abiTypeNames.first { candidate ->
            support.codecOwnerFqName(candidate) != firstOwner
        }
        val secondOwner = support.codecOwnerFqName(secondAbiType)

        fun registerCodec(operation: String, abiTypeName: String): String = support.registerCodec(
            operation = operation,
            role = KotlinProjectionAbiCodecRole.TO_ABI,
            abiTypeName = abiTypeName,
            signature = "$operation|$abiTypeName",
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__value", INT)),
            returnType = INT,
            body = CodeBlock.of("return __value\n"),
        )

        val firstToAbi = registerCodec("toAbi", firstAbiType)
        val firstCopyToAbi = registerCodec("copyToAbi", firstAbiType)
        val secondToAbi = registerCodec("toAbi", secondAbiType)
        listOf(firstAbiType, secondAbiType).forEach { abiTypeName ->
            support.registerAbiType(
                KotlinProjectionAbiTypeMetadata(
                    abiTypeName = abiTypeName,
                    kind = KotlinProjectionAbiTypeKind.COM_REFERENCE,
                ),
            )
        }
        val callSitePlan = modulePlan("sample.Result")
        val invocation = support.typedInvocation(
            "instance",
            CodeBlock.of("slot"),
            KotlinTypedProjectionCallSiteInvocation(callSitePlan, listOf(CodeBlock.of("value"))),
        )

        val files = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
        val callSiteOwner = support.callSiteOwnerFqName(callSitePlan)
        val callSiteFile = files.single { file -> file.contents.contains("fun ${callSitePlan.functionName}(") }
        val firstShard = files.single { file -> file.contents.contains("fun $firstToAbi(") }
        val secondShard = files.single { file -> file.contents.contains("fun $secondToAbi(") }

        assertEquals(setOf(firstOwner, secondOwner, callSiteOwner).size, files.size)
        assertTrue(
            invocation.toString().contains(
                "${callSiteOwner.substringAfterLast('.')}.${callSitePlan.functionName}("
            ),
        )
        assertTrue(callSiteFile.relativePath.endsWith("/${callSiteOwner.substringAfterLast('.')}.kt"))
        assertNotEquals(firstShard.relativePath, secondShard.relativePath)
        assertTrue(firstShard.relativePath.endsWith("/${firstOwner.substringAfterLast('.')}.kt"))
        assertTrue(secondShard.relativePath.endsWith("/${secondOwner.substringAfterLast('.')}.kt"))
        assertTrue(firstShard.contents.contains("fun $firstCopyToAbi("))
        assertTrue(firstShard.contents.contains("name = \"$firstAbiType\""))
        assertTrue(secondShard.contents.contains("name = \"$secondAbiType\""))
    }

    private fun modulePlan(projectedResultName: String): KotlinTypedProjectionCallSitePlan {
        val typeSignature =
            "RuntimeClass($projectedResultName|$projectedResultName|-|0:0|-|-)"
        val descriptor = WinRTProjectionCallSiteDescriptor(
            hResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
            slots = listOf(
                WinRTProjectionCallSiteSlot(
                    direction = WinRTProjectionCallSiteSlotDirection.IN,
                    recipe = WinRTProjectionCallSiteRecipe(
                        kind = WinRTProjectionCallSiteRecipeKind.VALUE,
                        abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.INT32),
                        valueCarrier = WinRTProjectionCallSiteAbiCarrier.INT32,
                        typeSignature = "Int32",
                    ),
                ),
                WinRTProjectionCallSiteSlot(
                    direction = WinRTProjectionCallSiteSlotDirection.RETURN,
                    ownership = WinRTProjectionCallSiteOwnership.OWNED,
                    recipe = WinRTProjectionCallSiteRecipe(
                        kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
                        abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
                        valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                        callables = WinRTProjectionCallSiteCallables(
                            ownerFqName = "$projectedResultName.Metadata",
                            fromAbi = "wrap",
                        ),
                        children = listOf(
                            WinRTProjectionCallSiteRecipe(
                                kind = WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
                                abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
                                valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                                referenceAccess = WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT,
                                typeSignature = typeSignature,
                            ),
                        ),
                        typeSignature = typeSignature,
                    ),
                ),
            ),
        )
        return KotlinTypedProjectionCallSitePlan(
            descriptor = descriptor,
            returnType = ClassName.bestGuess(projectedResultName),
            parameters = listOf(KotlinTypedProjectionCallSiteParameter(INT)),
        )
    }
}
