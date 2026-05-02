package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

internal object WinRtWindowsActivationContext {
    private const val appxNamespace = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
    private const val envVarName = "MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY"
    private val linker: Linker = Linker.nativeLinker()
    private val kernel32: SymbolLookup = SymbolLookup.libraryLookup("kernel32", Arena.global())
    private val actCtxLayout: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("cbSize"),
        ValueLayout.JAVA_INT.withName("dwFlags"),
        ValueLayout.ADDRESS.withName("lpSource"),
        ValueLayout.JAVA_SHORT.withName("wProcessorArchitecture"),
        ValueLayout.JAVA_SHORT.withName("wLangId"),
        MemoryLayout.paddingLayout(4),
        ValueLayout.ADDRESS.withName("lpAssemblyDirectory"),
        ValueLayout.ADDRESS.withName("lpResourceName"),
        ValueLayout.ADDRESS.withName("lpApplicationName"),
        ValueLayout.ADDRESS.withName("hModule"),
    )

    class Scope internal constructor(
        internal val handle: MemorySegment,
        internal val cookie: MemorySegment,
        val manifestPath: Path,
    ) : AutoCloseable {
        override fun close() {
            deactivate(this)
        }
    }

    fun activate(root: Path): Scope? {
        if (!PlatformRuntime.isWindows || !root.isDirectory()) {
            return null
        }
        val fragments = Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.name.equals("LiftedWinRTClassRegistrations.xml", ignoreCase = true) }
                .sorted()
                .toList()
        }
        if (fragments.isEmpty()) {
            return null
        }
        val frameworkFileNames = Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .map { root.relativize(it).toString().replace('/', '\\') }
                .filter { it.endsWith(".dll", ignoreCase = true) || it.endsWith(".pri", ignoreCase = true) }
                .distinct()
                .sorted()
                .toList()
        }
        val manifestPath = root.resolve("WindowsAppSDK-SelfContained.manifest")
        Files.writeString(manifestPath, buildManifest(fragments, frameworkFileNames))
        setEnvironmentVariable(envVarName, root.toAbsolutePath().toString().let { if (it.endsWith("\\") || it.endsWith("/")) it else "$it\\" })
        return Arena.ofConfined().use { arena ->
            val manifestSegment = allocateWideString(arena, manifestPath.toAbsolutePath().toString())
            val actCtx = arena.allocate(actCtxLayout)
            actCtx.set(ValueLayout.JAVA_INT, 0L, actCtxLayout.byteSize().toInt())
            actCtx.set(ValueLayout.JAVA_INT, 4L, 0)
            actCtx.set(ValueLayout.ADDRESS, 8L, manifestSegment)
            actCtx.set(ValueLayout.JAVA_SHORT, 16L, 0)
            actCtx.set(ValueLayout.JAVA_SHORT, 18L, 0)
            actCtx.set(ValueLayout.ADDRESS, 24L, MemorySegment.NULL)
            actCtx.set(ValueLayout.ADDRESS, 32L, MemorySegment.NULL)
            actCtx.set(ValueLayout.ADDRESS, 40L, MemorySegment.NULL)
            actCtx.set(ValueLayout.ADDRESS, 48L, MemorySegment.NULL)
            val createActCtx = downcall("CreateActCtxW", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
            val handle = createActCtx.invokeWithArguments(actCtx) as MemorySegment
            if (handle.address() == -1L) {
                error("CreateActCtxW failed with GetLastError=${getLastError()} for $manifestPath")
            }
            val cookieOut = arena.allocate(ValueLayout.ADDRESS)
            val activateActCtx = downcall(
                "ActivateActCtx",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
            val activated = activateActCtx.invokeWithArguments(handle, cookieOut) as Int
            if (activated == 0) {
                releaseActCtx(handle)
                error("ActivateActCtx failed with GetLastError=${getLastError()} for $manifestPath")
            }
            Scope(handle, cookieOut.get(ValueLayout.ADDRESS, 0L), manifestPath)
        }
    }

    private fun deactivate(scope: Scope) {
        runCatching {
            downcall(
                "DeactivateActCtx",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
            ).invokeWithArguments(0, scope.cookie)
        }
        releaseActCtx(scope.handle)
    }

    private fun buildManifest(fragmentPaths: List<Path>, frameworkFileNames: List<String>): String {
        val documentBuilder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        val entryByFileName = linkedMapOf<String, String>()
        val remainingFileNames = linkedMapOf<String, String>()
        frameworkFileNames.forEach { fileName -> remainingFileNames.putIfAbsent(fileName.lowercase(), fileName) }
        fragmentPaths.forEach { fragmentPath ->
            val document = documentBuilder.parse(fragmentPath.toFile())
            val inProcessServers = document.getElementsByTagNameNS(appxNamespace, "InProcessServer")
            for (index in 0 until inProcessServers.length) {
                val server = inProcessServers.item(index) as? org.w3c.dom.Element ?: continue
                val path = firstChildText(server, "Path") ?: continue
                val activatableClasses = server.getElementsByTagNameNS(appxNamespace, "ActivatableClass")
                val body = buildString {
                    for (classIndex in 0 until activatableClasses.length) {
                        val activatableClass = activatableClasses.item(classIndex) as? org.w3c.dom.Element ?: continue
                        val classId = activatableClass.getAttribute("ActivatableClassId").trim()
                        if (classId.isNotEmpty()) {
                            append("        <winrtv1:activatableClass name='")
                            append(escapeXml(classId))
                            append("' threadingModel='both'/>\n")
                        }
                    }
                }
                if (body.isNotEmpty()) {
                    entryByFileName.putIfAbsent(path.lowercase(), manifestFileEntry(path, body))
                    remainingFileNames.remove(path.lowercase())
                }
            }
        }
        return buildString {
            appendLine("<?xml version='1.0' encoding='utf-8' standalone='yes'?>")
            appendLine("<assembly manifestVersion='1.0'")
            appendLine("    xmlns:asmv3='urn:schemas-microsoft-com:asm.v3'")
            appendLine("    xmlns:winrtv1='urn:schemas-microsoft-com:winrt.v1'")
            appendLine("    xmlns='urn:schemas-microsoft-com:asm.v1'>")
            appendLine("    <assemblyIdentity type='win32' name='io.github.composefluent.winrt.windowsappsdk' version='1.0.0.0' processorArchitecture='*'/>")
            entryByFileName.values.forEach(::append)
            remainingFileNames.values.forEach { fileName -> append(manifestFileEntry(fileName, "")) }
            appendLine("</assembly>")
        }
    }

    private fun manifestFileEntry(path: String, body: String): String =
        buildString {
            append("    <asmv3:file name='")
            append(escapeXml(path))
            append("' loadFrom='%")
            append(envVarName)
            append("%")
            append(escapeXml(path))
            appendLine("'>")
            append(body)
            appendLine("    </asmv3:file>")
        }

    private fun firstChildText(element: org.w3c.dom.Element, localName: String): String? {
        val children = element.getElementsByTagNameNS(appxNamespace, localName)
        return children.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun setEnvironmentVariable(name: String, value: String) {
        Arena.ofConfined().use { arena ->
            val result = downcall(
                "SetEnvironmentVariableW",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            ).invokeWithArguments(allocateWideString(arena, name), allocateWideString(arena, value)) as Int
            if (result == 0) {
                error("SetEnvironmentVariableW failed with GetLastError=${getLastError()} for $name")
            }
        }
    }

    private fun getLastError(): Int =
        downcall("GetLastError", FunctionDescriptor.of(ValueLayout.JAVA_INT)).invokeWithArguments() as Int

    private fun releaseActCtx(handle: MemorySegment) {
        downcall("ReleaseActCtx", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invokeWithArguments(handle)
    }

    private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(kernel32.find(name).orElseThrow(), descriptor)

    private fun allocateWideString(arena: Arena, value: String): MemorySegment {
        val bytes = (value + '\u0000').toByteArray(StandardCharsets.UTF_16LE)
        return arena.allocate(bytes.size.toLong(), 2).copyFrom(MemorySegment.ofArray(bytes))
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;")
}
