package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import java.nio.file.Path

internal object AppPackageFileSupport {
    fun validatePackageExtension(path: Path, action: String) {
        val fileName = path.fileName?.toString().orEmpty()
        if (!fileName.endsWith(".appx", ignoreCase = true) && !fileName.endsWith(".msix", ignoreCase = true)) {
            throw GradleException("Cannot $action appx/msix package because package file must end with .appx or .msix: $path.")
        }
    }
}
