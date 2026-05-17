package io.github.composefluent.winrt.gradle

import java.nio.file.Path

internal data class ApplicationPackageItem(
    val kind: ApplicationPackageItemKind,
    val source: Path,
    val target: Path,
    val targetKey: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ApplicationPackageItem && targetKey == other.targetKey

    override fun hashCode(): Int =
        targetKey.hashCode()
}

internal enum class ApplicationPackageItemKind {
    ComponentPri,
    PriResource,
    Layout,
    ExcludedLayout,
    Content,
    Embed,
    ;

    val isPackagePayload: Boolean
        get() = this == Layout || this == Content
}

internal fun applicationPackageItem(kind: ApplicationPackageItemKind, source: Path, target: Path): ApplicationPackageItem =
    ApplicationPackageItem(
        kind = kind,
        source = source.toAbsolutePath().normalize(),
        target = target.toAbsolutePath().normalize(),
        targetKey = target.toNormalizedPackagePathKey(),
    )

internal fun Path.toNormalizedPackagePathKey(): String =
    toAbsolutePath().normalize().toString().lowercase()
