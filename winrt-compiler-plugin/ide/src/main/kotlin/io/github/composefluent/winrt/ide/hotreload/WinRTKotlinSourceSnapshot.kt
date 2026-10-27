package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Compare source contents, not IDE refresh events or filesystem timestamps. */
internal fun winRTKotlinSourceSnapshot(projects: List<Path>, roots: List<Path>,
    text: (Path) -> String = Files::readString): Map<Path, String> {
    val result = linkedMapOf<Path, String>()
    roots.map(::winRTSourcePath).distinct().filter(Files::isDirectory).forEach { root ->
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) =
                if (isWinRTUserKotlinSource(dir, projects, roots)) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file.toString().endsWith(".kt", true) && isWinRTUserKotlinSource(file, projects, roots)) {
                    result.getOrPut(file) { winRTKotlinSourceHash(text(file)) }
                }
                return FileVisitResult.CONTINUE
            }
        })
    }
    return result
}

internal fun winRTKotlinSourceHash(text: String): String =
    java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
