package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** openProject(isNewProject = true) clears existing IDE configuration. Preserve
 * the prepared fixture's import, SDK and editor state for desktop acceptance. */
internal fun preserveWinRTIntegrationProjectMetadata(root: Path): () -> Unit {
    val metadata = root.resolve(".idea").toAbsolutePath().normalize()
    require(!Files.isSymbolicLink(metadata))
    val saved = if (!Files.isDirectory(metadata, NOFOLLOW_LINKS)) emptyMap() else
        Files.walk(metadata).use { paths -> paths.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
            .toList().associate { metadata.relativize(it) to Files.readAllBytes(it) } }
    return {
        require(!Files.isSymbolicLink(metadata))
        saved.forEach { (relative, bytes) ->
            val target = metadata.resolve(relative).normalize()
            require(target.startsWith(metadata))
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
    }
}
