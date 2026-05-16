package io.github.composefluent.winrt.runtime

private const val WINRT_PROJECTION_REGISTRAR_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTProjectionRegistrar"

private const val WINRT_AUTHORING_TYPE_DETAILS_REGISTRAR_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar"

private const val WINRT_EVENT_PROJECTION_REGISTRY_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTEventProjectionRegistry"

internal actual fun registerCompilerGeneratedProjectionTypeIndexes() {
    val classLoaders = compilerGeneratedRegistryClassLoaders()
    classLoaders.forEach { classLoader ->
        registerGeneratedProjectionRegistry(classLoader, WINRT_AUTHORING_TYPE_DETAILS_REGISTRAR_CLASS)
        registerGeneratedProjectionRegistry(classLoader, WINRT_PROJECTION_REGISTRAR_CLASS)
    }
    // A fixed registrar class can only represent one classpath entry; resources preserve dependency indexes.
    classLoaders.forEach { classLoader ->
        val resources = classLoader.getResources(WINRT_PROJECTION_TYPE_INDEX_RESOURCE).toList()
        resources.forEach { resource ->
            resource.openStream().bufferedReader().useLines { lines ->
                lines.filter(String::isNotBlank)
                    .forEach { line -> registerProjectionTypeIndexLine(classLoader, line) }
            }
        }
    }
}

private fun compilerGeneratedRegistryClassLoaders(): List<ClassLoader> {
    val classLoaders = linkedSetOf<ClassLoader>()
    Thread.currentThread().contextClassLoader?.let(classLoaders::add)
    stackClassLoaders().forEach(classLoaders::add)
    WinRtTypeRegistry::class.java.classLoader?.let(classLoaders::add)
    return classLoaders.toList()
}

private fun stackClassLoaders(): List<ClassLoader> =
    runCatching {
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk { frames ->
                frames
                    .map { frame -> frame.declaringClass.classLoader }
                    .filter { classLoader -> classLoader != null }
                    .distinct()
                    .toList()
            }
    }.getOrDefault(emptyList())

internal fun registerCompilerGeneratedProjectionTypeIndexesForClassLoader(classLoader: ClassLoader) {
    registerGeneratedProjectionRegistry(classLoader, WINRT_AUTHORING_TYPE_DETAILS_REGISTRAR_CLASS)
    registerGeneratedProjectionRegistry(classLoader, WINRT_PROJECTION_REGISTRAR_CLASS)
    // A fixed registrar class can only represent one classpath entry; resources preserve dependency indexes.
    val resources = classLoader.getResources(WINRT_PROJECTION_TYPE_INDEX_RESOURCE).toList()
    resources.forEach { resource ->
        resource.openStream().bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank)
                .forEach { line -> registerProjectionTypeIndexLine(classLoader, line) }
        }
    }
}

internal actual fun registerCompilerGeneratedProjectionRegistry(registryClassName: String) {
    if (registryClassName.isBlank()) {
        return
    }
    compilerGeneratedRegistryClassLoaders().forEach { classLoader ->
        if (registerGeneratedProjectionRegistry(classLoader, registryClassName)) {
            return
        }
    }
}

internal actual fun registerCompilerGeneratedEventSources() {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: WinRtTypeRegistry::class.java.classLoader
        ?: return
    registerGeneratedProjectionRegistry(classLoader, WINRT_EVENT_PROJECTION_REGISTRY_CLASS)
}

private fun registerGeneratedProjectionRegistry(
    classLoader: ClassLoader,
    className: String,
): Boolean {
    val registrarClass = runCatching {
        Class.forName(className, true, classLoader)
    }.getOrNull() ?: return false
    val register = runCatching {
        registrarClass.getDeclaredMethod("register")
    }.getOrNull() ?: return false
    if (runCatching {
        register.isAccessible = true
        register.invoke(null)
    }.isSuccess) {
        return true
    }
    val instance = runCatching {
        registrarClass.getDeclaredField("INSTANCE").also { it.isAccessible = true }.get(null)
    }.getOrNull() ?: return false
    return runCatching {
        register.invoke(instance)
    }.isSuccess
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
