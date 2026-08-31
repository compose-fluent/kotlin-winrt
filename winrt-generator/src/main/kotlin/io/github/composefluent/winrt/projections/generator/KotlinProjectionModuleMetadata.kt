package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

/**
 * Hoists an immutable expression which was composed from closed WinMD facts into the generated
 * module helper. The module support object owns the cache; CallSite annotations remain semantic
 * metadata only and never carry this expression or its implementation recipe.
 */
internal fun KotlinProjectionRenderer.hoistModuleMetadata(
    identity: String,
    type: TypeName,
    initializer: CodeBlock,
    deferredInitialization: Boolean = false,
): CodeBlock = modulePlatformAbiCalls?.registerMetadataExpression(
    identity = identity,
    type = type,
    initializer = initializer,
    deferredInitialization = deferredInitialization,
) ?: initializer

internal fun KotlinProjectionAbiTypeBinding.moduleMetadataIdentity(prefix: String): String =
    "$prefix|${canonicalCallSiteTypeSignature()}"
