package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.github.composefluent.winrt.metadata.*

/** Local implementation slicing, never a published contract transformation.
 * CsWinRT code_writers.h separates projected member forwarding from vtable/CCW
 * emission; InteropTypeDiscovery separately roots exposed interface hierarchies.
 * Kotlin/Native's DLL roots require dropping unused forwarding declarations before
 * compilation. Keep ABI plans intact and use one decision for interfaces and overrides.
 */
class NativeInstanceMemberDemand private constructor(
    val omittedMembers: Map<String, Set<String>>,
    private val omittedSurfaces: Set<String>,
    internal val ccwInputTypes: Set<String>,
    private val constructionTypes: Set<String>?,
) {
    internal fun retainsFactories(owner: String): Boolean = constructionTypes?.contains(owner) != false
    internal fun retain(owner: String, name: String): Boolean = name !in omittedMembers[owner].orEmpty()

    // Forwarding proxy caches only: fast-ABI object-reference caches may be shared
    // with other retained interfaces and must not be removed by this predicate.
    internal fun requiresInstanceSurface(owner: String): Boolean = owner !in omittedSurfaces

    internal fun apply(type: TypeSpec, plan: KotlinTypeProjectionPlan): TypeSpec {
        val omitted = omittedMembers[plan.type.qualifiedName].orEmpty()
        fun prune(type: TypeSpec): TypeSpec = type.toBuilder().apply {
            funSpecs.removeAll { !it.isConstructor && it.name in omitted }
            propertySpecs.removeAll { it.name in omitted }
            // Metadata/factory objects contain ABI slots and static entry points, not
            // instance dispatch. Only the RCW nested class shares this member surface.
            typeSpecs.replaceAll { if (it.name == "NativeProjection") prune(it) else it }
        }.build()
        return prune(type)
    }

    companion object {
        fun create(model: WinRTMetadataModel, members: Set<String>, exposedTypes: Set<String>,
            constructionTypes: Set<String>? = null, staticMembers: Set<String>? = null,
            abiOnlyInterfaces: Set<String> = emptySet()): NativeInstanceMemberDemand {
            val plans = KotlinProjectionPlanner().plan(model)
            val byName = plans.associateBy { it.type.qualifiedName }
            // Null is the complete-contract/legacy caller mode. Only a complete v4
            // business manifest authorizes removing unreferenced construction bodies.
            val factoryTypes = constructionTypes?.let { roots ->
                val result = mutableSetOf<String>()
                fun keep(name: String) {
                    val canonical = name.substringBefore('<').removeSuffix("?")
                    if (!result.add(canonical)) return
                    byName[canonical]?.type?.baseTypeName?.let(::keep)
                }
                (roots + exposedTypes + members.filter { it.substringAfter('\t') == "*" }
                    .map { it.substringBefore('\t') }).forEach(::keep)
                result
            }
            data class Member(val owner: String, val name: String)
            val edges = mutableMapOf<Member, MutableSet<Member>>()
            fun connect(left: Member, right: Member) {
                edges.getOrPut(left) { mutableSetOf() } += right
                edges.getOrPut(right) { mutableSetOf() } += left
            }
            val namesByType = mutableMapOf<String, Set<String>>()
            val bindingMembers = mutableMapOf<Member, String>()
            val staticBindingMembers = mutableMapOf<Member, Set<String>>()
            val retained = mutableSetOf<Member>()
            plans.forEach { plan ->
                val owner = plan.type.qualifiedName
                mergedStaticMethods(plan, plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedStaticMethod))
                    .forEach { method ->
                        staticBindingMembers[Member(owner, staticMethodBindingName(plan, method))] = setOf(method.projectedMethodName())
                    }
                mergedStaticProperties(plan, plan.type.properties.filter { it.isStatic }).forEach { property ->
                    for (accessor in listOf("GETTER", "SETTER")) {
                        staticBindingMembers[Member(owner, "STATIC_${property.name.uppercase()}_${accessor}_SLOT")] =
                            setOf(property.name.replaceFirstChar(Char::lowercase))
                    }
                }
                (plan.type.events.filter { it.isStatic } + plan.staticInterfaceNames
                    .mapNotNull(plan.typesByQualifiedName::get).flatMap { it.events }).forEach { event ->
                    for (accessor in listOf("ADD", "REMOVE")) {
                        staticBindingMembers[Member(owner, "STATIC_${event.name.uppercase()}_${accessor}_SLOT")] =
                            setOf(event.name.replaceFirstChar(Char::lowercase), "add${event.name}", "remove${event.name}")
                    }
                }
                val local = buildList<Set<String>> {
                    plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                        bindingMembers[Member(owner, method.abiSlotConstantName(plan.type.methods))] = method.projectedMethodName()
                        val names = mutableSetOf(method.projectedMethodName(), authoringInvokeBridgeName(method))
                        if (plan.type.kind == WinRTTypeKind.RuntimeClass) {
                            val binding = plan.instanceMemberBindings.firstOrNull {
                                it.bindingName == method.abiSlotConstantName(plan.type.methods)
                            }
                            if (binding != null) {
                                val modifiers = runtimeClassMemberModifiers(plan, binding)
                                names += method.projectedRuntimeClassMethodName(plan, modifiers)
                                // Older callers do not describe construction/exposure fully.
                                // With a complete manifest, keepHierarchy below roots the
                                // callback family only for actual exposed implementations.
                                if (constructionTypes == null && KModifier.PROTECTED in modifiers)
                                    retained += names.map { Member(owner, it) }
                            }
                        }
                        add(names)
                    }
                    plan.type.properties.filterNot { it.isStatic }.forEach {
                        for (accessor in listOf("GETTER", "SETTER")) {
                            bindingMembers[Member(owner, "${it.name.uppercase()}_${accessor}_SLOT")] = it.name.replaceFirstChar(Char::lowercase)
                        }
                        add(setOf(it.name.replaceFirstChar(Char::lowercase)))
                    }
                    plan.type.events.filterNot { it.isStatic }.forEach {
                        for (accessor in listOf("ADD", "REMOVE")) {
                            bindingMembers[Member(owner, "${it.name.uppercase()}_${accessor}_SLOT")] = it.name.replaceFirstChar(Char::lowercase)
                        }
                        add(setOf(it.name.replaceFirstChar(Char::lowercase), "add${it.name}", "remove${it.name}"))
                    }
                }
                local.forEach { family ->
                    val first = Member(owner, family.first())
                    family.forEach { connect(first, Member(owner, it)) }
                }
                namesByType[owner] = local.flatten().toSet()
            }
            fun parents(owner: String): List<String> = byName[owner]?.type?.let { type ->
                (listOfNotNull(type.baseTypeName, type.defaultInterfaceName) +
                    type.implementedInterfaces.map { it.interfaceName })
                    .map { it.substringBefore('<').removeSuffix("?") }.distinct()
            }.orEmpty()
            // CsWinRT write_class_members forwards members of each implemented interface.
            // Include inherited surfaces even when metadata does not repeat declarations.
            val surfaces = mutableMapOf<String, Set<String>>()
            fun surface(owner: String, visiting: Set<String> = emptySet()): Set<String> {
                surfaces[owner]?.let { return it }
                check(owner !in visiting) { "Cyclic projection inheritance: $owner" }
                return (namesByType[owner].orEmpty() + parents(owner).flatMap {
                    surface(it, visiting + owner)
                }).also { surfaces[owner] = it }
            }
            plans.forEach { plan ->
                val owner = plan.type.qualifiedName
                surface(owner)
                parents(owner).forEach { parent ->
                    surface(parent).forEach { name -> connect(Member(owner, name), Member(parent, name)) }
                }
                surface(owner).filter { it in setOf("equals", "hashCode", "toString") }
                    .forEach { retained += Member(owner, it) }
            }
            val fullTypes = exposedTypes.toMutableSet()
            members.forEach { edge ->
                val owner = edge.substringBefore('\t')
                val name = edge.substringAfter('\t')
                if (name == "*") fullTypes += owner else retained += Member(owner, name)
            }
            // The existing generator owns callback and mapped/generic obligations.
            // Preserve their complete member sets rather than duplicating ABI categories.
            plans.forEach { plan ->
                val type = plan.type
                if (type.genericParameterCount != 0 || type.kind !in setOf(WinRTTypeKind.Interface, WinRTTypeKind.RuntimeClass) ||
                    KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds ||
                    isRuntimeOwnedMappedTypeName(type.qualifiedName) || plan.readOnlyCollectionBindings.isNotEmpty() ||
                    plan.mutableCollectionBindings.isNotEmpty() ||
                    plan.requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().isNotEmpty())
                    fullTypes += type.qualifiedName
                if (constructionTypes == null)
                    fullTypes += type.implementedInterfaces.filter { it.isOverridable || it.isProtected }.map { it.interfaceName }
                // Raw factory calls use the original ABI plan, not a projected static
                // interface. Only an ordinary interface use requires its full surface.
                fullTypes += type.activation.staticInterfaceNames.filterNot(abiOnlyInterfaces::contains)
                fullTypes += type.activation.factories.filter { it.kind == WinRTAttributedFactoryKind.Static }
                    .map { it.interfaceName }.filterNot(abiOnlyInterfaces::contains)
                if (factoryTypes == null || type.qualifiedName in factoryTypes) {
                    fullTypes += type.activation.factories.map { it.interfaceName }.filterNot(abiOnlyInterfaces::contains)
                    type.activation.activatableFactoryInterfaceName?.let(fullTypes::add)
                }
            }
            val seen = mutableSetOf<String>()
            fun keepHierarchy(name: String) {
                val canonical = name.substringBefore('<').removeSuffix("?")
                if (!seen.add(canonical)) return
                retained += surface(canonical).map { Member(canonical, it) }
                val type = byName[canonical]?.type ?: return
                type.baseTypeName?.let(::keepHierarchy)
                type.defaultInterfaceName?.let(::keepHierarchy)
                type.implementedInterfaces.forEach { keepHierarchy(it.interfaceName) }
            }
            fullTypes.forEach(::keepHierarchy)
            // Bidirectional dispatch obligations: a retained interface requires its
            // implementations, and a retained override requires its base declaration.
            // Unrelated homonyms have no edge. Overloads/accessors remain one local family.
            val ccwInputTypes = mutableSetOf<String>()
            // CsWinRT InteropTypeDiscovery recursively discovers dependencies of exposed
            // implementations. A newly required CCW keeps its full ABI surface, whose
            // parameters can require further CCWs; converge before emitting any bodies.
            do {
                val pending = ArrayDeque(retained)
                while (pending.isNotEmpty()) {
                    edges[pending.removeFirst()].orEmpty().forEach { if (retained.add(it)) pending.addLast(it) }
                }
                val discovered = plans.projectedInterfaceCcwInputPlans(retainStaticBinding = { plan, binding ->
                    val owner = plan.type.qualifiedName
                    val name = staticBindingMembers[Member(owner, binding.bindingName)]
                    // Unknown bindings remain conservative. Known static members use
                    // exactly the same family/wildcard demand as their emitted body.
                    name == null || retainsNativeStaticMember(staticMembers, owner, *name.toTypedArray())
                }) { plan, binding ->
                    val owner = plan.type.qualifiedName
                    val name = bindingMembers[Member(owner, binding.bindingName)]
                    owner !in abiOnlyInterfaces && (name == null || Member(owner, name) in retained)
                }.map { it.type.qualifiedName }.filter { it !in ccwInputTypes }
                ccwInputTypes += discovered
                discovered.forEach(::keepHierarchy)
            } while (discovered.isNotEmpty())
            val omitted = surfaces.mapValues { (owner, names) ->
                names.filterTo(sortedSetOf()) { Member(owner, it) !in retained }
            }.toSortedMap()
            return NativeInstanceMemberDemand(omitted, surfaces.filter { (owner, names) ->
                names.isNotEmpty() && omitted.getValue(owner).containsAll(names)
            }.keys, ccwInputTypes, factoryTypes)
        }
    }
}

internal fun retainsNativeStaticMember(demand: Set<String>?, owner: String, vararg names: String): Boolean =
    demand == null || "$owner\t*" in demand || names.any { "$owner\t$it" in demand }
