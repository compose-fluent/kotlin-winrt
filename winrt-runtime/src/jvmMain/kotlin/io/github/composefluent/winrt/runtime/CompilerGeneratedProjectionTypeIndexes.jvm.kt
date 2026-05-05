package io.github.composefluent.winrt.runtime

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
    val baseTypeName = parts.getOrElse(3) { "" }
    if (kotlinTypeName.isBlank() || projectedTypeName.isBlank()) {
        return
    }
    val javaClass = runCatching {
        Class.forName(kotlinTypeName, true, classLoader)
    }.getOrNull() ?: return
    val kClass = javaClass.kotlin
    registerCompilerGeneratedProjectionTypeIndex(
        kClass = kClass,
        projectedTypeName = projectedTypeName,
        kind = kind,
        baseTypeName = baseTypeName,
    )
}
