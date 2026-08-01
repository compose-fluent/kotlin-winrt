package io.github.composefluent.winrt.compiler.callsites

import java.lang.classfile.Annotation
import java.lang.classfile.AnnotationValue
import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile

data class WinRTProjectionCallSiteDeclaration(
    val ownerFqName: String,
    val functionName: String,
    val jvmMethodName: String,
    val jvmMethodDescriptor: String,
    val descriptor: WinRTProjectionCallSiteDescriptor,
)

class WinRTProjectionCallSiteCatalog private constructor(
    declarations: List<WinRTProjectionCallSiteDeclaration>,
) {
    val declarations: List<WinRTProjectionCallSiteDeclaration> =
        declarations.sortedWith(
            compareBy<WinRTProjectionCallSiteDeclaration>(
                { declaration -> declaration.descriptor.encode() },
                WinRTProjectionCallSiteDeclaration::ownerFqName,
                WinRTProjectionCallSiteDeclaration::functionName,
            ),
        )

    private val declarationsByDescriptor: Map<WinRTProjectionCallSiteDescriptor, WinRTProjectionCallSiteDeclaration>

    init {
        val duplicateCallable = this.declarations
            .groupBy { declaration -> declaration.ownerFqName to declaration.functionName }
            .entries
            .firstOrNull { (_, matches) -> matches.size > 1 }
        require(duplicateCallable == null) {
            val (owner, function) = requireNotNull(duplicateCallable).key
            "Duplicate WinRT projection call-site declaration $owner.$function."
        }

        val groupedByDescriptor = this.declarations.groupBy(WinRTProjectionCallSiteDeclaration::descriptor)
        val duplicateDescriptor = groupedByDescriptor.entries.firstOrNull { (_, matches) -> matches.size > 1 }
        require(duplicateDescriptor == null) {
            val matches = requireNotNull(duplicateDescriptor).value
            "WinRT projection call-site descriptor '${matches.first().descriptor.encode()}' is owned by " +
                matches.joinToString { declaration ->
                    "${declaration.ownerFqName}.${declaration.functionName}"
                } + "."
        }
        declarationsByDescriptor = groupedByDescriptor.mapValues { (_, matches) -> matches.single() }
    }

    operator fun get(descriptor: WinRTProjectionCallSiteDescriptor): WinRTProjectionCallSiteDeclaration? =
        declarationsByDescriptor[descriptor]

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
            val annotationDescriptor = "L${annotationFqName.replace('.', '/')};"
            val declarations = classModel.methods().mapNotNull { method ->
                val annotations = buildList {
                    method.findAttributes(Attributes.runtimeInvisibleAnnotations())
                        .forEach { attribute -> addAll(attribute.annotations()) }
                    method.findAttributes(Attributes.runtimeVisibleAnnotations())
                        .forEach { attribute -> addAll(attribute.annotations()) }
                }
                val callSiteAnnotations = annotations.filter { annotation ->
                    annotation.className().equalsString(annotationDescriptor)
                }
                if (callSiteAnnotations.isEmpty()) {
                    return@mapNotNull null
                }
                require(callSiteAnnotations.size == 1) {
                    "JVM method $ownerFqName.${method.methodName().stringValue()} has multiple " +
                        "@$annotationFqName annotations."
                }
                val descriptorText = callSiteAnnotations.single().descriptorText(
                    ownerFqName = ownerFqName,
                    methodName = method.methodName().stringValue(),
                    annotationFqName = annotationFqName,
                )
                val descriptor = WinRTProjectionCallSiteDescriptor.parse(descriptorText)
                require(descriptor.encode() == descriptorText) {
                    "JVM method $ownerFqName.${method.methodName().stringValue()} uses a non-canonical " +
                        "WinRT projection call-site descriptor '$descriptorText'."
                }
                val jvmMethodName = method.methodName().stringValue()
                WinRTProjectionCallSiteDeclaration(
                    ownerFqName = ownerFqName,
                    functionName = jvmMethodName.kotlinSourceFunctionName(),
                    jvmMethodName = jvmMethodName,
                    jvmMethodDescriptor = method.methodType().stringValue(),
                    descriptor = descriptor,
                )
            }
            return WinRTProjectionCallSiteCatalog(declarations)
        }
    }
}

private fun Annotation.descriptorText(
    ownerFqName: String,
    methodName: String,
    annotationFqName: String,
): String {
    val descriptorElements = elements().filter { element -> element.name().equalsString("descriptor") }
    require(descriptorElements.size == 1 && elements().size == 1) {
        "JVM method $ownerFqName.$methodName has a malformed @$annotationFqName annotation."
    }
    val value = descriptorElements.single().value()
    require(value is AnnotationValue.OfString) {
        "JVM method $ownerFqName.$methodName has a non-string @$annotationFqName descriptor."
    }
    return value.stringValue()
}

private val kotlinJvmMangledSuffix = Regex("-[A-Za-z0-9_]{7}$")

private fun String.kotlinSourceFunctionName(): String =
    replace(kotlinJvmMangledSuffix, "")
