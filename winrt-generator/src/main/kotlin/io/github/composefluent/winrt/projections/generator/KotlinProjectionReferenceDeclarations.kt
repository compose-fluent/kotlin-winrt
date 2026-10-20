@file:OptIn(com.squareup.kotlinpoet.ExperimentalKotlinPoetApi::class)

package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.*
import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_INBOUND_CALL_SITE_ANNOTATION_FQ_NAME

/**
 * Reference declarations are derived from the very same KotlinPoet declaration tree as the
 * implementation (cswinrt/code_writers.h: reference_projection). There is no second WinRT type
 * classifier or member planner. Inline bodies and constant/default expressions are part of
 * Kotlin's compilation contract and must survive this pass.
 */
fun FileSpec.referenceDeclarations(): FileSpec = toBuilder().apply {
    members.clear()
    this@referenceDeclarations.members.forEach { member ->
        when (member) {
            is TypeSpec -> addType(member.referenceDeclaration())
            is FunSpec -> addFunction(member.referenceDeclaration())
            is PropertySpec -> addProperty(member.referenceDeclaration(emptySet(), false))
            is TypeAliasSpec -> addTypeAlias(member)
            else -> error("Unsupported reference declaration: ${member::class}")
        }
    }
}.build()

private val referenceBody = CodeBlock.of("throw %T(%S)", ClassName("kotlin", "UnsupportedOperationException"), "WinRT reference projection")

private fun FunSpec.referenceDeclaration(): FunSpec {
    // Inbound recipes contain compiler protocol placeholders, not executable Kotlin bodies.
    // Preserve them until the compiler has lowered their ABI-facing signatures.
    if (annotations.any { it.typeName.toString() == WINRT_PROJECTION_INBOUND_CALL_SITE_ANNOTATION_FQ_NAME }) return this
    if (KModifier.INLINE in modifiers || KModifier.EXPECT in modifiers ||
        KModifier.ABSTRACT in modifiers || KModifier.EXTERNAL in modifiers || body.isEmpty()) return this
    return toBuilder().clearBody().addStatement("%L", referenceBody).build()
}

private fun PropertySpec.referenceDeclaration(constructorParameters: Set<String>, inInterface: Boolean): PropertySpec {
    // Preserve primary-constructor property promotion, data/value classes and constants.
    // Delegation is encoded in Kotlin metadata; retaining only accessor signatures is insufficient.
    if (delegated || KModifier.CONST in modifiers || KModifier.INLINE in modifiers ||
        getter?.modifiers?.contains(KModifier.INLINE) == true ||
        setter?.modifiers?.contains(KModifier.INLINE) == true ||
        initializer?.toString()?.let {
            name in constructorParameters && (it == name || it == CodeBlock.of("%N", name).toString())
        } == true) return this
    val declaration = PropertySpec.builder(name, type, modifiers)
        .mutable(mutable)
        .addAnnotations(annotations)
        .addTypeVariables(typeVariables)
        .addKdoc(kdoc)
    receiverType?.let(declaration::receiver)
    declaration.contextReceiverTypes.addAll(contextReceiverTypes)
    getter?.let { declaration.getter(it.referenceDeclaration()) }
    setter?.let { declaration.setter(it.referenceDeclaration()) }
    if (initializer != null || (getter == null && !inInterface &&
            modifiers.none { it == KModifier.ABSTRACT || it == KModifier.EXPECT || it == KModifier.LATEINIT })) {
        declaration.initializer(referenceBody)
    }
    return declaration.build()
}

private fun TypeSpec.referenceDeclaration(): TypeSpec {
    val declaration = when {
        isAnonymousClass -> TypeSpec.anonymousClassBuilder()
        isCompanion -> TypeSpec.companionObjectBuilder(name)
        isEnum -> TypeSpec.enumBuilder(requireNotNull(name))
        isAnnotation -> TypeSpec.annotationBuilder(requireNotNull(name))
        kind == TypeSpec.Kind.INTERFACE -> TypeSpec.interfaceBuilder(requireNotNull(name))
        kind == TypeSpec.Kind.OBJECT -> TypeSpec.objectBuilder(requireNotNull(name))
        else -> TypeSpec.classBuilder(requireNotNull(name))
    }.addModifiers(modifiers).addAnnotations(annotations).addTypeVariables(typeVariables).addKdoc(kdoc)
    declaration.contextReceiverTypes.addAll(contextReceiverTypes)
    primaryConstructor?.let(declaration::primaryConstructor)
    if (superclass != ANY) declaration.superclass(superclass)
    superclassConstructorParameters.forEach(declaration::addSuperclassConstructorParameter)
    superinterfaces.forEach { (type, delegate) ->
        if (delegate == null) declaration.addSuperinterface(type) else declaration.addSuperinterface(type, delegate)
    }
    enumConstants.forEach { (name, type) -> declaration.addEnumConstant(name, type.referenceDeclaration()) }
    val parameters = primaryConstructor?.parameters.orEmpty().mapTo(mutableSetOf()) { it.name }
    propertySpecs.forEach { declaration.addProperty(it.referenceDeclaration(parameters, kind == TypeSpec.Kind.INTERFACE)) }
    funSpecs.forEach { declaration.addFunction(it.referenceDeclaration()) }
    typeSpecs.forEach { declaration.addType(it.referenceDeclaration()) }
    return declaration.build()
}
