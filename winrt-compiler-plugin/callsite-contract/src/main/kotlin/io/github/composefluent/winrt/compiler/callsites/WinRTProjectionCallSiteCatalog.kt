package io.github.composefluent.winrt.compiler.callsites

import java.lang.classfile.Annotation
import java.lang.classfile.AnnotationValue
import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile

data class WinRTProjectionCallSiteDeclaration(
    val ownerFqName: String,
    val functionName: String,
    val jvmMethodName: String,
    val key: WinRTProjectionCallSiteCatalogKey,
) {
    val jvmMethodDescriptor: String
        get() = key.jvmMethodDescriptor

    val metadata: WinRTProjectionCallSiteMetadata
        get() = key.metadata
}

class WinRTProjectionCallSiteCatalog private constructor(
    declarations: List<WinRTProjectionCallSiteDeclaration>,
) {
    val declarations: List<WinRTProjectionCallSiteDeclaration> =
        declarations.sortedWith(
            compareBy<WinRTProjectionCallSiteDeclaration>(
                { declaration -> declaration.key.jvmMethodDescriptor },
                { declaration -> declaration.key.metadata.toString() },
                WinRTProjectionCallSiteDeclaration::ownerFqName,
                WinRTProjectionCallSiteDeclaration::functionName,
            ),
        )

    private val declarationsByKey: Map<WinRTProjectionCallSiteCatalogKey, WinRTProjectionCallSiteDeclaration>

    init {
        val duplicateCallable = this.declarations
            .groupBy { declaration -> declaration.ownerFqName to declaration.functionName }
            .entries
            .firstOrNull { (_, matches) -> matches.size > 1 }
        require(duplicateCallable == null) {
            val (owner, function) = requireNotNull(duplicateCallable).key
            "Duplicate WinRT projection call-site declaration $owner.$function."
        }

        val groupedByKey = this.declarations.groupBy(WinRTProjectionCallSiteDeclaration::key)
        val duplicateKey = groupedByKey.entries.firstOrNull { (_, matches) -> matches.size > 1 }
        require(duplicateKey == null) {
            val matches = requireNotNull(duplicateKey).value
            "WinRT runtime-owned call-site key '${matches.first().key}' is owned by " +
                matches.joinToString { declaration ->
                    "${declaration.ownerFqName}.${declaration.functionName}"
                } + "."
        }
        declarationsByKey = groupedByKey.mapValues { (_, matches) -> matches.single() }
    }

    operator fun get(key: WinRTProjectionCallSiteCatalogKey): WinRTProjectionCallSiteDeclaration? =
        declarationsByKey[key]

    companion object {
        fun fromJvmClass(
            owner: Class<*>,
            annotationFqName: String = WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME,
        ): WinRTProjectionCallSiteCatalog {
            val resourceName = "/${owner.name.replace('.', '/')}.class"
            val bytes = requireNotNull(owner.getResourceAsStream(resourceName)) {
                "Cannot read JVM class file $resourceName for WinRT projection call-site catalog generation."
            }.use { input -> input.readAllBytes() }
            return fromJvmClassFile(bytes, annotationFqName)
        }

        fun fromJvmClassFile(
            classFileBytes: ByteArray,
            annotationFqName: String = WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME,
        ): WinRTProjectionCallSiteCatalog {
            val classModel = ClassFile.of().parse(classFileBytes)
            val ownerFqName = classModel.thisClass().asInternalName().replace('/', '.')
            val callSiteAnnotationDescriptor = "L${annotationFqName.replace('.', '/')};"
            val parameterAnnotationDescriptor =
                "L${WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME.replace('.', '/')};"
            val declarations = classModel.methods().mapNotNull { method ->
                val callSiteAnnotations = method.declarationAnnotations()
                    .filter { annotation -> annotation.className().equalsString(callSiteAnnotationDescriptor) }
                if (callSiteAnnotations.isEmpty()) return@mapNotNull null
                require(callSiteAnnotations.size == 1) {
                    "JVM method $ownerFqName.${method.methodName().stringValue()} has multiple " +
                        "@$annotationFqName annotations."
                }

                val parameterAnnotations = method.parameterDeclarationAnnotations()
                require(parameterAnnotations.size >= 2) {
                    "JVM method $ownerFqName.${method.methodName().stringValue()} must expose receiver and slot parameters."
                }
                val parameters = parameterAnnotations.drop(2).mapIndexed { index, annotations ->
                    val matches = annotations.filter { annotation ->
                        annotation.className().equalsString(parameterAnnotationDescriptor)
                    }
                    require(matches.size <= 1) {
                        "JVM method $ownerFqName.${method.methodName().stringValue()} parameter ${index + 2} " +
                            "has multiple @$WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME annotations."
                    }
                    matches.singleOrNull()?.parameterMetadata()
                        ?: WinRTProjectionParameterMetadata()
                }
                val metadata = callSiteAnnotations.single().callSiteMetadata(parameters)
                val jvmMethodName = method.methodName().stringValue()
                WinRTProjectionCallSiteDeclaration(
                    ownerFqName = ownerFqName,
                    functionName = jvmMethodName.kotlinSourceFunctionName(),
                    jvmMethodName = jvmMethodName,
                    key = WinRTProjectionCallSiteCatalogKey(
                        metadata = metadata,
                        jvmMethodDescriptor = method.methodType().stringValue(),
                    ),
                )
            }
            return WinRTProjectionCallSiteCatalog(declarations)
        }
    }
}

private fun java.lang.classfile.MethodModel.declarationAnnotations(): List<Annotation> = buildList {
    findAttributes(Attributes.runtimeInvisibleAnnotations())
        .forEach { attribute -> addAll(attribute.annotations()) }
    findAttributes(Attributes.runtimeVisibleAnnotations())
        .forEach { attribute -> addAll(attribute.annotations()) }
}

private fun java.lang.classfile.MethodModel.parameterDeclarationAnnotations(): List<List<Annotation>> {
    val invisible = findAttributes(Attributes.runtimeInvisibleParameterAnnotations())
        .flatMap { attribute -> attribute.parameterAnnotations() }
    val visible = findAttributes(Attributes.runtimeVisibleParameterAnnotations())
        .flatMap { attribute -> attribute.parameterAnnotations() }
    val count = maxOf(invisible.size, visible.size, methodTypeSymbol().parameterCount())
    return List(count) { index -> invisible.getOrNull(index).orEmpty() + visible.getOrNull(index).orEmpty() }
}

private fun Annotation.callSiteMetadata(
    parameters: List<WinRTProjectionParameterMetadata>,
): WinRTProjectionCallSiteMetadata {
    val values = elements().associate { element -> element.name().stringValue() to element.value() }
    val known = setOf("hResult", "result", "returnAbiType")
    require(values.keys.all(known::contains)) {
        "Malformed @$WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME annotation elements ${values.keys - known}."
    }
    return WinRTProjectionCallSiteMetadata(
        hResultPolicy = values["hResult"].enumValueOrDefault(WinRTProjectionCallSiteHResultPolicy.CHECK),
        resultKind = values["result"].enumValueOrDefault(WinRTProjectionCallSiteResultKind.INFER),
        returnAbiType = values["returnAbiType"].stringValueOrDefault(),
        parameters = parameters,
    )
}

private fun Annotation.parameterMetadata(): WinRTProjectionParameterMetadata {
    val values = elements().associate { element -> element.name().stringValue() to element.value() }
    val known = setOf("direction", "abiType")
    require(values.keys.all(known::contains)) {
        "Malformed @$WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME annotation elements ${values.keys - known}."
    }
    return WinRTProjectionParameterMetadata(
        direction = values["direction"].enumValueOrDefault(WinRTProjectionCallSiteParameterDirection.IN),
        abiType = values["abiType"].stringValueOrDefault(),
    )
}

private inline fun <reified T : Enum<T>> AnnotationValue?.enumValueOrDefault(default: T): T {
    if (this == null) return default
    require(this is AnnotationValue.OfEnum) { "Expected an enum annotation value, got $this." }
    val name = constantName().stringValue()
    return enumValues<T>().singleOrNull { value -> value.name == name }
        ?: error("Unknown ${T::class.simpleName} annotation value '$name'.")
}

private fun AnnotationValue?.stringValueOrDefault(): String {
    if (this == null) return ""
    require(this is AnnotationValue.OfString) { "Expected a string annotation value, got $this." }
    return stringValue()
}

private val kotlinJvmMangledSuffix = Regex("-[A-Za-z0-9_]{7}$")

private fun String.kotlinSourceFunctionName(): String = replace(kotlinJvmMangledSuffix, "")
