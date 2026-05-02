package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal actual fun registerCompilerGeneratedProjectionTypeIndexes() {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: WinRtTypeRegistry::class.java.classLoader
        ?: return
    val resources = classLoader.getResources(WINRT_PROJECTION_TYPE_INDEX_RESOURCE).toList()
    resources.forEach { resource ->
        resource.openStream().bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank)
                .forEach { line -> registerProjectionTypeIndexLine(classLoader, line) }
        }
    }
}

private fun registerProjectionTypeIndexLine(
    classLoader: ClassLoader,
    line: String,
) {
    val parts = line.split('\t')
    val kotlinTypeName = parts.getOrElse(0) { "" }
    val projectedTypeName = parts.getOrElse(1) { "" }
    val kind = parts.getOrElse(2) { "" }
    if (kotlinTypeName.isBlank() || projectedTypeName.isBlank()) {
        return
    }
    val kClass = runCatching {
        Class.forName(kotlinTypeName, false, classLoader).kotlin
    }.getOrNull() ?: return
    registerProjectionTypeIndex(
        kClass = kClass,
        projectedTypeName = projectedTypeName,
        kind = kind,
    )
}

@Suppress("UNCHECKED_CAST")
private fun registerProjectionTypeIndex(
    kClass: KClass<*>,
    projectedTypeName: String,
    kind: String,
) {
    val typedClass = kClass as KClass<Any>
    val isRuntimeClass = kind == "RuntimeClass"
    WinRtTypeRegistry.update(typedClass) { existing ->
        WinRtTypeId(
            kClass = typedClass,
            projectedTypeName = projectedTypeName,
            guid = existing?.guid,
            iid = existing?.iid,
            signature = existing?.signature,
            enumAbiValue = existing?.enumAbiValue,
            enumEntries = existing?.enumEntries,
            isExceptionType = existing?.isExceptionType == true,
            helperType = existing?.helperType,
            defaultInterface = existing?.defaultInterface,
            boxedName = existing?.boxedName,
            runtimeClassName = if (isRuntimeClass) projectedTypeName else existing?.runtimeClassName,
            vftblType = existing?.vftblType,
            isDelegate = kind == "Delegate" || existing?.isDelegate == true,
            isRuntimeClass = isRuntimeClass || existing?.isRuntimeClass == true,
            isWindowsRuntimeType = true,
            aliases = existing?.aliases.orEmpty() + projectedTypeName,
        )
    }
    TypeNameSupport.registerProjectionType(
        type = kClass,
        runtimeClassName = projectedTypeName.takeIf { isRuntimeClass },
    )
}

