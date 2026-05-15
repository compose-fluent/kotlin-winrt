package io.github.composefluent.winrt.runtime

private const val WINRT_PROJECTION_REGISTRAR_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTProjectionRegistrar"

private const val WINRT_INTERFACE_PROJECTION_REGISTRY_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTInterfaceProjectionRegistry"

private const val WINRT_AUTHORING_TYPE_DETAILS_REGISTRAR_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar"

private const val WINRT_EVENT_PROJECTION_REGISTRY_CLASS: String =
    "io.github.composefluent.winrt.projections.support.WinRTEventProjectionRegistry"

internal actual fun registerCompilerGeneratedProjectionTypeIndexes() {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: WinRtTypeRegistry::class.java.classLoader
        ?: return
    registerGeneratedProjectionRegistry(classLoader, WINRT_INTERFACE_PROJECTION_REGISTRY_CLASS)
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

internal actual fun registerCompilerGeneratedEventSources() {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: WinRtTypeRegistry::class.java.classLoader
        ?: return
    registerGeneratedProjectionRegistry(classLoader, WINRT_EVENT_PROJECTION_REGISTRY_CLASS)
    val resources = classLoader.getResources(WINRT_EVENT_SOURCE_RESOURCE).toList()
    resources.forEach { resource ->
        resource.openStream().bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank)
                .filterNot { line -> line.startsWith("eventType\t") }
                .forEach(::registerEventSourceLine)
        }
    }
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

private fun registerEventSourceLine(line: String) {
    val parts = line.split('\t', limit = 10)
    val eventType = parts.getOrElse(0) { "" }
    val ownerType = parts.getOrElse(1) { "" }
    if (eventType.isBlank() || ownerType.isBlank()) {
        return
    }
    val descriptor = WinRtEventSourceDescriptor(
        eventType = eventType,
        ownerType = ownerType,
        sourceClass = parts.getOrElse(2) { "" },
        abiEventType = parts.getOrElse(3) { "" },
        genericArguments = parts.getOrElse(4) { "" }.splitListField(),
        usesSharedEventHandlerSource = parts.getOrElse(5) { "" }.toBooleanStrictOrNull() ?: false,
        interfaceId = parts.getOrElse(6) { "" }.takeIf(String::isNotBlank)?.let(::Guid),
        parameterKinds = parts.getOrElse(7) { "" }.splitListField().mapNotNull(::delegateValueKindOrNull),
        returnKind = delegateValueKindOrNull(parts.getOrElse(8) { "" }) ?: WinRtDelegateValueKind.UNIT,
        parameterTypeNames = parts.getOrElse(9) { "" }.splitListField(),
    )
    val factory = WinRtGeneratedEventSourceRuntime.createEventSourceFactory(descriptor)
    WinRtEventSourceRuntime.registerEventSource(descriptor.copy(eventSourceFactory = factory))
}

private fun String.splitListField(): List<String> =
    split(',').filter(String::isNotBlank)

private fun delegateValueKindOrNull(name: String): WinRtDelegateValueKind? =
    runCatching { WinRtDelegateValueKind.valueOf(name) }.getOrNull()
