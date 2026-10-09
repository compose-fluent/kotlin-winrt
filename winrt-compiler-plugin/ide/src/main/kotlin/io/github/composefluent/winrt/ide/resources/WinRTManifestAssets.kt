package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

internal object WinRTManifestAssets {
    fun qualified(value: String, scale: Int): String {
        if (value.isBlank()) return ""
        val dot = value.lastIndexOf('.')
        if (dot < 0) return "$value.scale-$scale.png"
        val stem = value.substring(0, dot).replace(Regex("\\.scale-\\d+(?:_[^.]+)?$"), "")
        return "$stem.scale-$scale${value.substring(dot)}"
    }

    fun generate(root: Path, request: WinRTManifestAssetRequest, fields: Map<WinRTManifestAssetKind, WinRTXmlField>): Pair<Map<String, ByteArray>, Map<WinRTXmlField, String>> {
        val target = root.resolve(request.target.replace('\\', '/')).normalize()
        require(target.startsWith(root) && target != root && !request.target.contains("..")) { "Choose an asset directory inside the package." }
        require(request.scales.isNotEmpty() && request.scales.all { it in listOf(100, 125, 150, 200, 400) })
        val source = readImage(Path.of(request.source))
        val files = linkedMapOf<String, ByteArray>()
        val edits = linkedMapOf<WinRTXmlField, String>()
        fields.filterKeys { it in request.kinds }.forEach { (kind, field) ->
            val name = field.value.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.').takeIf { it.isNotBlank() }
                ?.replace(Regex("\\.scale-\\d+.*$"), "") ?: kind.basename
            val base = root.relativize(target.resolve("$name.png")).toString().replace('\\', '/')
            edits[field] = base.replace('/', '\\')
            request.scales.sorted().forEach { scale ->
                val bitmap = resize(source, (kind.width * scale / 100f).roundToInt(), (kind.height * scale / 100f).roundToInt(), request.interpolation,
                    request.padding && kind != WinRTManifestAssetKind.Splash)
                files[qualified(base, scale)] = png(bitmap)
                if (request.lightTheme && kind == WinRTManifestAssetKind.AppIcon) {
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        val pixel = bitmap.getRGB(x, y)
                        bitmap.setRGB(x, y, (pixel and -0x1000000) or ((pixel xor 0x00ffffff) and 0x00ffffff))
                    }
                    files["${base.substringBeforeLast('.')}.scale-${scale}_altform-lightunplated.png"] = png(bitmap)
                }
            }
        }
        require(files.isNotEmpty()) { "Select at least one asset." }
        if (!request.overwrite) require(files.keys.none { Files.exists(root.resolve(it)) }) { "Some assets already exist. Select Replace existing generated assets to regenerate them." }
        return files to edits
    }

    fun write(project: Project, manifest: VirtualFile, files: Map<String, ByteArray>, overwrite: Boolean) {
        val root = manifest.parent.toNioPath().toAbsolutePath().normalize()
        // Check the complete batch before writing so conflicts cannot leave a
        // partially generated asset family.
        files.keys.forEach { name ->
            val path = root.resolve(name.replace('\\', '/')).normalize()
            require(path.startsWith(root) && path != root && !name.contains("..")) { "Asset paths must stay inside the package." }
            require(overwrite || !Files.exists(path)) { "Asset already exists: $name" }
        }
        WriteCommandAction.runWriteCommandAction(project, "Generate manifest assets", null, Runnable {
            files.forEach { (name, bytes) ->
                val parts = name.replace('\\', '/').split('/')
                var directory = manifest.parent
                parts.dropLast(1).forEach { part -> directory = directory.findChild(part) ?: directory.createChildDirectory(this, part) }
                val file = directory.findChild(parts.last()) ?: directory.createChildData(this, parts.last())
                file.setBinaryContent(bytes)
            }
        })
    }

    fun importVariant(project: Project, manifest: VirtualFile, source: VirtualFile, field: WinRTXmlField, scale: Int): String {
        val base = field.value.takeIf { it.isNotBlank() } ?: "Assets\\${field.attribute ?: field.path.last().name}.png"
        val bytes = png(readImage(source.toNioPath()))
        val target = qualified(base, scale).substringBeforeLast('.') + ".png"
        write(project, manifest, mapOf(target to bytes), true)
        return base.substringBeforeLast('.') + ".png"
    }

    fun removeVariant(project: Project, manifest: VirtualFile, field: WinRTXmlField, scale: Int) {
        val root = manifest.parent.toNioPath().toAbsolutePath().normalize()
        val path = root.resolve(qualified(field.value, scale).replace('\\', '/')).normalize()
        require(field.value.isNotBlank() && path.startsWith(root) && path != root)
        val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return
        WriteCommandAction.runWriteCommandAction(project, "Remove manifest scale asset", null, Runnable { file.delete(this) })
    }

    private fun readImage(path: Path): BufferedImage {
        require(Files.size(path) in 1..16_777_216) { "Choose a source image smaller than 16 MB." }
        ImageIO.createImageInputStream(path.toFile()).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: error("Unsupported image format.")
            try {
                reader.input = stream
                val width = reader.getWidth(0); val height = reader.getHeight(0)
                require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 16_777_216) { "The source image is too large." }
                return reader.read(0)
            } finally { reader.dispose() }
        }
    }

    private fun resize(source: BufferedImage, width: Int, height: Int, interpolation: String, padding: Boolean): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics = target.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, when (interpolation) {
                    "Nearest neighbor" -> RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    "Bilinear" -> RenderingHints.VALUE_INTERPOLATION_BILINEAR
                    else -> RenderingHints.VALUE_INTERPOLATION_BICUBIC
                })
                val inset = if (padding) .16f else 0f
                val ratio = min(width * (1 - 2 * inset) / source.width, height * (1 - 2 * inset) / source.height)
                val scaledWidth = (source.width * ratio).roundToInt().coerceAtLeast(1)
                val scaledHeight = (source.height * ratio).roundToInt().coerceAtLeast(1)
                graphics.drawImage(source, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight, null)
            } finally { graphics.dispose() }
        }

    private fun png(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { output -> ImageIO.write(image, "png", output); output.toByteArray() }
    fun choose(project: Project, manifest: VirtualFile, onChosen: (VirtualFile) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle("Choose manifest image")
                    .withFileFilter { it.extension?.lowercase() in listOf("png", "jpg", "jpeg") },
                project, manifest.parent, onChosen,
            )
        }
    }

    fun import(project: Project, manifest: VirtualFile, source: VirtualFile): String {
        val root = manifest.parent
        val rootPath = root.toNioPath()
        if (source.toNioPath().startsWith(rootPath)) return rootPath.relativize(source.toNioPath()).toString().replace('/', '\\')
        var imported: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Import manifest image", null, Runnable {
            val assets = root.findChild("Assets") ?: root.createChildDirectory(this, "Assets")
            require(assets.findChild(source.name) == null) { "Assets/${source.name} already exists. Choose that image or rename the new file." }
            imported = source.copy(this, assets, source.name)
        })
        return rootPath.relativize(requireNotNull(imported).toNioPath()).toString().replace('/', '\\')
    }

    /** Base package paths can refer to a scale-qualified asset instead of a literal file. */
    fun resolve(root: Path, value: String): Path? {
        val path = runCatching { root.resolve(value.replace('\\', '/')).normalize() }.getOrNull() ?: return null
        if (value.isBlank() || !path.startsWith(root)) return null
        if (Files.isRegularFile(path)) return path
        val parent = path.parent ?: return null
        if (!Files.isDirectory(parent)) return null
        val name = path.fileName.toString()
        val stem = name.substringBeforeLast('.')
        val suffix = name.substringAfterLast('.')
        return Files.list(parent).use { files -> files.filter { candidate ->
            Files.isRegularFile(candidate) && candidate.fileName.toString().let { it.startsWith("$stem.scale-") || it.startsWith("$stem.targetsize-") } && candidate.fileName.toString().endsWith(".$suffix", true)
        }.sorted().findFirst().orElse(null) }
    }
}
