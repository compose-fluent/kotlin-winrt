package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

private const val APPLICATION_MODULES_EXTENSION = "kotlinWinRTApplicationModules"
private const val APPLICATION_MODULE_DISCOVERY = "kotlinWinRTApplicationModuleDiscovery"

// CsWinRT's WinUIDesktopSample.csproj leaves output naming to the build system.
// Keep the Gradle equivalent here, outside projection/runtime naming contracts.
internal fun winAppApplicationModules(project: Project): SetProperty<String> = synchronized(project.rootProject) {
    val root = project.rootProject
    @Suppress("UNCHECKED_CAST")
    val existing = root.extensions.findByName(APPLICATION_MODULES_EXTENSION) as? SetProperty<String>
    if (existing != null) return@synchronized existing
    root.objects.setProperty(String::class.java).convention(emptySet()).also {
        root.extensions.add(APPLICATION_MODULES_EXTENSION, it)
    }
}

internal fun registerWinAppApplicationModule(project: Project) = synchronized(project.rootProject) {
    val root = project.rootProject
    winAppApplicationModules(root).add(project.path)
    val extra = root.extensions.extraProperties
    if (project.gradle.startParameter.isConfigureOnDemand && !extra.has(APPLICATION_MODULE_DISCOVERY)) {
        extra.set(APPLICATION_MODULE_DISCOVERY, true)
        // Default naming needs the build-wide app count even when only one module's task was
        // requested. Discover remaining modules before task inputs/cache state are captured.
        project.gradle.projectsEvaluated {
            root.allprojects.forEach { candidate ->
                if (!candidate.state.executed) root.evaluationDependsOn(candidate.path)
            }
        }
    }
}

internal fun defaultWinAppExecutableBaseName(project: Project): Provider<String> {
    val rootName = project.rootProject.name
    val moduleName = project.name
    // Capture only Gradle property values, so task inputs do not traverse Projects on cache replay.
    return winAppApplicationModules(project).map { modules ->
        if (modules.size > 1) "$rootName-$moduleName" else rootName
    }
}

internal fun validateWinAppExecutableBaseName(name: String): String {
    require(name.isNotBlank() && name.none { it < ' ' || it in "<>:\"/\\|?*" } &&
        !name.endsWith('.') && !name.endsWith(' ') && !name.endsWith(".exe", ignoreCase = true)
    ) {
        "windows.application.executableBaseName must be a Windows filename without a path or the .exe suffix: '$name'."
    }
    val stem = name.substringBefore('.').uppercase(java.util.Locale.ROOT)
    require(stem !in setOf("CON", "PRN", "AUX", "NUL") && !Regex("(?:COM|LPT)[1-9¹²³]").matches(stem)) {
        "windows.application.executableBaseName cannot use the reserved Windows filename '$name'."
    }
    return name
}
