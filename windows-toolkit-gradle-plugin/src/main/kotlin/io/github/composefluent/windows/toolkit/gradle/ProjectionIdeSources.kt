package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/** The same sync signal used by KGP's IsInIdeaSyncValueSource for supported IDE versions. */
internal fun projectionIdeImport(project: Project): Provider<Boolean> {
    val importTaskRequested = project.gradle.startParameter.taskNames.any {
        it.substringAfterLast(':') in setOf("prepareKotlinIdeaImport", "ideaModule", "idea")
    }
    return project.providers.systemProperty("idea.sync.active").map(String::toBoolean)
        .orElse(false).map { it || importTaskRequested }
}

internal fun projectionIdeSourceDirectory(
    project: Project,
    implementation: Provider<Directory>,
    reference: Provider<Directory>,
): Provider<Directory> = projectionIdeImport(project).flatMap { if (it) reference else implementation }

/** Prebuilt SDK business/metadata compilations consume contracts, like CsWinRT's
 * ReferenceAssemblyPaths. Only the isolated projection compiler owns implementation sources.
 * Keeping those directories out of business source roots also avoids Gradle treating an
 * excluded, empty implementation tree as an undeclared producer input during publication. */
internal fun projectionBusinessSourceDirectory(
    project: Project,
    implementation: Provider<Directory>,
    reference: Provider<Directory>,
): Provider<Directory> = if (project.plugins.hasPlugin("winrt.prebuilt-projection")) {
    reference
} else {
    projectionIdeSourceDirectory(project, implementation, reference)
}
