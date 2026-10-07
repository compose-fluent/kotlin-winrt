package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

abstract class BuildWinAppHostTask : DefaultTask() {
    @get:Inject
    protected abstract val providers: ProviderFactory

    @get:Input
    @get:Optional
    val nativeToolchain: Provider<WindowsNativeToolchain> = providers.of(WindowsNativeToolchainValueSource::class.java) {
        it.parameters.forAuthoring.set(false)
        it.parameters.runtimeIdentifier.set(runtimeIdentifier)
        it.parameters.windowsSdkVersion.set(windowsSdkVersion)
        it.parameters.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
    }

    init {
        packageType.convention(WindowsPackageType.Packaged.name)
        applicationVariant.convention("jvm:main")
        jvmRuntimeMode.convention(WinAppJvmRuntimeMode.Bundled.name)
        externalJvmHome.convention("")
        expectedJavaMajor.convention(25)
        console.convention(false)
        windowsAppSdkDeployment.convention(WindowsAppSdkDeployment.FrameworkDependent)
        windowsSdkVersion.convention("")
        windowsSdkRegistryRoots.convention(emptyList())
        launcherOnly.convention(false)
    }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val applicationVariant: Property<String>

    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty

    /** Produces only the launcher source and executable when enabled. */
    @get:Input
    abstract val launcherOnly: Property<Boolean>

    /** Executable produced by the independent launcher task for aggregate staging. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val launcherExecutable: RegularFileProperty

    /** Compiled Win32 icon resource, shared with the Kotlin/Native link path. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherIconResource: RegularFileProperty

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val executableBaseName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetsDirectory: ConfigurableFileCollection

    @get:Input
    abstract val packageType: Property<String>

    @get:Input
    abstract val windowsAppSdkDeployment: Property<WindowsAppSdkDeployment>

    @get:Input
    abstract val console: Property<Boolean>

    @get:Input
    abstract val javaHome: Property<String>

    @get:Input
    abstract val expectedJavaMajor: Property<Int>

    @get:Input
    abstract val jvmRuntimeMode: Property<String>

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeImageDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val externalJvmHome: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkRegistryRoots: ListProperty<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    @get:Internal
    abstract val commandWorkingDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        val sourceRoot = generatedSourceDirectory.get().asFile.toPath()
        val mainClassValue = mainClass.orNull?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("WinApp host requires an application mainClass.")
        val runtimeMode = jvmRuntimeMode.get()
        val externalHome = externalJvmHome.orNull?.trim().orEmpty()
        if (runtimeMode == WinAppJvmRuntimeMode.External.name && externalHome.isBlank()) {
            throw IllegalStateException(
                "External JVM runtime mode requires application.externalJvmHome to point to a JVM home.",
            )
        }
        if (runtimeMode == WinAppJvmRuntimeMode.External.name) {
            validateExternalJvmHome(Path.of(externalHome))
        }
        val configuredRuntimeImage = if (runtimeMode == WinAppJvmRuntimeMode.Bundled.name) {
            runtimeImageDirectory.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        } else {
            null
        }
        if (configuredRuntimeImage != null) {
            if (!configuredRuntimeImage.isDirectory()) {
                throw IllegalStateException("Bundled JVM runtime image is not a directory: $configuredRuntimeImage")
            }
            runtimeImageOverlapError(configuredRuntimeImage, outputRoot, "Bundled JVM runtime image")?.let { message ->
                throw IllegalStateException(message)
            }
        }
        if (launcherOnly.get()) {
            GradleFileOperations.cleanDirectory(outputRoot)
            Files.createDirectories(outputRoot)
            Files.createDirectories(sourceRoot)
            val source = sourceRoot.resolve("kotlin_winrt_application_host.c")
            Files.writeString(
                source,
                applicationHostSource(
                    mainClass = mainClassValue,
                    packageType = packageType.get(),
                    runtimeMode = runtimeMode,
                    externalJvmHome = externalHome,
                    windowsAppSdkDeployment = windowsAppSdkDeployment.get(),
                ),
            )
            if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                logger.warn("WinApp launcher compilation is Windows-only; generated source without compiling EXE.")
                return
            }
            val toolchain = nativeToolchain.get()
            logger.info("Kotlin/WinRT JVM application launcher: {} ({}; Windows SDK {})", toolchain.compiler, runtimeIdentifier.get(), toolchain.sdkVersion)
            compileHostExe(toolchain, source, outputRoot.resolve("${executableBaseName.get()}.exe"))
            return
        }
        // Validate the image/output relationship before cleaning the host output. A configured
        // image inside that output would otherwise be deleted before it can be staged.
        GradleFileOperations.cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        Files.createDirectories(sourceRoot)
        val source = sourceRoot.resolve("kotlin_winrt_application_host.c")
        // Host compilation is Windows-only. On other hosts this task still emits the source
        // used by TestKit and cross-platform configuration checks, but it cannot consume a
        // Windows JVM image or compile the native launcher.
        if (runtimeMode == WinAppJvmRuntimeMode.Bundled.name && isWindowsHost()) {
            stageRuntimeImage(outputRoot)
        }
        Files.writeString(
            source,
            applicationHostSource(
                mainClass = mainClassValue,
                packageType = packageType.get(),
                runtimeMode = runtimeMode,
                externalJvmHome = externalHome,
                windowsAppSdkDeployment = windowsAppSdkDeployment.get(),
            ),
        )
        stageRuntimeClasspath(outputRoot)
        stageRuntimeAssets(outputRoot)
        WinAppManifestGenerator.writeApplicationManifest(
            outputRoot,
            executableBaseName.get(),
            windowsManifestProcessorArchitecture(runtimeIdentifier.get()),
            redirectDlls = packageType.get() != WindowsPackageType.Packaged.name,
        )
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            logger.warn("WinApp host native EXE build is Windows-only; generated source without compiling EXE.")
            return
        }
        val toolchain = nativeToolchain.get()
        logger.info("Kotlin/WinRT JVM application host: {} ({}; Windows SDK {})", toolchain.compiler, runtimeIdentifier.get(), toolchain.sdkVersion)
        val launcher = launcherExecutable.orNull?.asFile?.toPath()
        if (launcher != null) {
            require(launcher.isRegularFile()) {
                "Configured Kotlin/WinRT launcher executable is missing: $launcher"
            }
            Files.copy(
                launcher,
                outputRoot.resolve("${executableBaseName.get()}.exe"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } else {
            compileHostExe(toolchain, source, outputRoot.resolve("${executableBaseName.get()}.exe"))
        }
    }

    private fun stageRuntimeClasspath(outputRoot: Path) {
        val libRoot = outputRoot.resolve("lib")
        GradleFileOperations.cleanDirectory(libRoot)
        Files.createDirectories(libRoot)
        stagedRuntimeJarNames(runtimeClasspath.files.filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) })
            .forEach { (jar, name) ->
                val target = libRoot.resolve(name)
                // JARs with one file name and one content share a staged file.
                if (!Files.exists(target)) {
                    Files.copy(jar.toPath(), target)
                }
            }
    }

    private fun stageRuntimeImage(outputRoot: Path) {
        val source = runtimeImageDirectory.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
            ?: throw IllegalStateException(
                "Bundled JVM runtime image is missing. Configure application.jvmRuntimeImage or ensure the " +
                    "prepareWinAppJvmRuntimeImage task is wired before building the application host.",
            )
        if (!source.isDirectory()) {
            throw IllegalStateException("Bundled JVM runtime image is not a directory: $source")
        }
        val target = outputRoot.resolve("runtime").toAbsolutePath().normalize()
        runtimeImageOverlapError(source, outputRoot, "Bundled JVM runtime image")?.let { message ->
            throw IllegalStateException(message)
        }
        copyDirectory(source, target)
        if (isWindowsHost() && !hasWindowsJvmLibrary(target)) {
            throw IllegalStateException(
                "Bundled JVM runtime image at $source does not contain a Windows JVM library " +
                "(bin/server/jvm.dll, jre/bin/server/jvm.dll, or bin/jvm.dll).",
            )
        }
        if (isWindowsHost()) {
            runCatching {
                validateJvmRuntime(target, expectedJavaMajor.get(), runtimeIdentifier.get(), "Bundled JVM runtime image")
            }.getOrElse { error ->
                throw IllegalStateException(error.message, error)
            }
        }
    }

    private fun validateExternalJvmHome(home: Path) {
        val normalized = home.toAbsolutePath().normalize()
        if (!normalized.isDirectory()) {
            throw IllegalStateException("External JVM runtime home does not exist or is not a directory: $normalized")
        }
        if (isWindowsHost() && !hasWindowsJvmLibrary(normalized)) {
            throw IllegalStateException(
                "External JVM runtime home at $normalized does not contain a Windows JVM library " +
                "(bin/server/jvm.dll, jre/bin/server/jvm.dll, or bin/jvm.dll).",
            )
        }
        if (isWindowsHost()) {
            runCatching {
                validateJvmRuntime(normalized, expectedJavaMajor.get(), runtimeIdentifier.get(), "External JVM runtime")
            }.getOrElse { error ->
                throw IllegalStateException(error.message, error)
            }
        }
    }

    private fun hasWindowsJvmLibrary(root: Path): Boolean = listOf(
        root.resolve("bin").resolve("server").resolve("jvm.dll"),
        root.resolve("jre").resolve("bin").resolve("server").resolve("jvm.dll"),
        root.resolve("bin").resolve("jvm.dll"),
    ).any(Path::isRegularFile)

    private fun stageRuntimeAssets(outputRoot: Path) {
        runtimeAssetsDirectory.files
            .filter { it.exists() }
            .filterNot { it.toPath().toAbsolutePath().normalize() == outputRoot.toAbsolutePath().normalize() }
            .forEach { source ->
                if (source.isDirectory) {
                    copyRuntimeAssetDirectory(source.toPath(), outputRoot)
                } else if (source.isFile) {
                    Files.createDirectories(outputRoot)
                    rejectReservedRuntimeAsset(Path.of(source.name))
                    Files.copy(
                        source.toPath(),
                        outputRoot.resolve(source.name),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            }
    }

    private fun copyRuntimeAssetDirectory(sourceRoot: Path, targetRoot: Path) {
        Files.walk(sourceRoot).use { stream ->
            stream.filter(Files::isRegularFile).forEach { source ->
                val relative = sourceRoot.relativize(source)
                rejectReservedRuntimeAsset(relative)
                val target = targetRoot.resolve(relative.toString()).normalize()
                Files.createDirectories(target.parent)
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun rejectReservedRuntimeAsset(relative: Path) {
        val firstSegment = relative.iterator().asSequence().firstOrNull()?.toString().orEmpty()
        if (firstSegment.equals("runtime", ignoreCase = true) || firstSegment.equals("lib", ignoreCase = true)) {
            throw IllegalStateException(
                "Runtime asset '${relative.toString().replace('\\', '/')}' targets a reserved JVM host directory.",
            )
        }
    }

    private fun copyDirectory(sourceRoot: Path, targetRoot: Path) {
        Files.walk(sourceRoot).use { stream ->
            stream.forEach { source ->
                val target = targetRoot.resolve(sourceRoot.relativize(source).toString())
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else if (Files.isRegularFile(source)) {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun compileHostExe(
        toolchain: WindowsNativeToolchain,
        source: Path,
        output: Path,
    ) {
        val jniHeaders = resolveJvmNativeHeaderDirectories(javaHome.orNull.orEmpty())
        val sdk = toolchain.sdk
        val architecture = windowsSdkArchitecture(runtimeIdentifier.get())
        val arguments = buildList {
            add(toolchain.compiler)
            addAll(toolchain.compilerArguments)
            launcherIconResource.orNull?.let { add(it.asFile.absolutePath) }
            addAll(
                listOf(
                    "/nologo",
                    source.toString(),
                    "/Fe:${output}",
                    "/I",
                    jniHeaders.includeDirectory.toString(),
                    "/I",
                    jniHeaders.platformIncludeDirectory.toString(),
                    "/I${sdk.includeRoot.resolve("shared")}",
                    "/I${sdk.includeRoot.resolve("um")}",
                    "/I${sdk.includeRoot.resolve("ucrt")}",
                    "/link",
                    "/NOLOGO",
                ),
            )
            if (console.get()) {
                add("/SUBSYSTEM:CONSOLE")
            } else {
                add("/SUBSYSTEM:WINDOWS")
                add("/ENTRY:wmainCRTStartup")
            }
            addAll(
                listOf(
                    "/LIBPATH:${sdk.libRoot.resolve("um").resolve(architecture)}",
                    "/LIBPATH:${sdk.libRoot.resolve("ucrt").resolve(architecture)}",
                    "kernel32.lib",
                    "shell32.lib",
                    "user32.lib",
                ),
            )
        }
        val result = toolchain.compile(arguments, output.parent)
        if (result.exitCode != 0) {
            throw IllegalStateException("WinApp host build failed with exit code ${result.exitCode}.\n${result.output}")
        }
    }
}

/**
 * File names for the runtime JARs of a JVM application host, which keeps them in one directory
 * and puts every JAR in it on the class path.
 *
 * Different modules can publish a JAR under one file name: a JetBrains redirect artifact is
 * named like the androidx JAR it points to. Such JARs get a digest of their content in their
 * name, so the result depends neither on where the files are nor on their order.
 */
internal fun stagedRuntimeJarNames(jars: Iterable<java.io.File>): Map<java.io.File, String> {
    val distinctJars = jars.distinctBy { jar -> jar.toPath().toAbsolutePath().normalize() }
    return distinctJars.groupBy { jar -> jar.name.lowercase() }.values.flatMap { sameName ->
        sameName.map { jar ->
            jar to if (sameName.size == 1) {
                jar.name
            } else {
                "${jar.nameWithoutExtension}-${runtimeJarDigest(jar).take(8)}.${jar.extension}"
            }
        }
    }.toMap()
}

private fun runtimeJarDigest(jar: java.io.File): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(jar.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }

internal fun applicationHostSource(
    mainClass: String,
    packageType: String,
    runtimeMode: String,
    externalJvmHome: String,
    windowsAppSdkDeployment: WindowsAppSdkDeployment = WindowsAppSdkDeployment.FrameworkDependent,
): String {
    require(windowsAppSdkDeployment != WindowsAppSdkDeployment.Auto) {
        "Generated application hosts require a concrete Windows App SDK deployment mode."
    }
    val mainClassPath = mainClass.replace('.', '/')
    val unpackaged = packageType == WindowsPackageType.None.name
    val packageIdentity = if (unpackaged) "Unpackaged" else "Packaged"
    val externalJvmHomePath = externalJvmHome
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val bundledRuntime = runtimeMode == WinAppJvmRuntimeMode.Bundled.name
    val bundledRuntimeCondition = if (bundledRuntime) "1" else "0"
    return """
    #define WIN32_LEAN_AND_MEAN
    #include <windows.h>
    #include <jni.h>
    #include <process.h>
    #include <stdint.h>
    #include <wchar.h>

    typedef jint (JNICALL *kotlin_winrt_create_java_vm_fn)(JavaVM **, void **, void *);

    static HMODULE kotlin_winrt_jvm_module = NULL;
    static JavaVM *kotlin_winrt_vm = NULL;

    static void kotlin_winrt_append_wide(wchar_t *target, DWORD target_count, const wchar_t *value) {
        if (lstrlenW(target) + lstrlenW(value) + 1 < (int)target_count) {
            lstrcatW(target, value);
        }
    }

    static void kotlin_winrt_append_utf8(char *target, DWORD target_count, const wchar_t *value) {
        char converted[MAX_PATH * 4];
        int length = WideCharToMultiByte(CP_UTF8, 0, value, -1, converted, sizeof(converted), NULL, NULL);
        if (length > 0 && lstrlenA(target) + lstrlenA(converted) + 1 < (int)target_count) {
            lstrcatA(target, converted);
        }
    }

    static void kotlin_winrt_host_directory(wchar_t *buffer, DWORD count) {
        GetModuleFileNameW(NULL, buffer, count);
        for (DWORD i = lstrlenW(buffer); i > 0; --i) {
            if (buffer[i - 1] == L'\\' || buffer[i - 1] == L'/') {
                buffer[i] = L'\0';
                return;
            }
        }
        buffer[0] = L'\0';
    }

    static void kotlin_winrt_classpath(char *buffer, DWORD count) {
        wchar_t directory[MAX_PATH * 2];
        wchar_t pattern[MAX_PATH * 2];
        WIN32_FIND_DATAW data;
        HANDLE find;
        buffer[0] = '\0';
        lstrcpyA(buffer, "-Djava.class.path=");
        kotlin_winrt_host_directory(directory, ARRAYSIZE(directory));
        lstrcpyW(pattern, directory);
        kotlin_winrt_append_wide(pattern, ARRAYSIZE(pattern), L"*.jar");
        find = FindFirstFileW(pattern, &data);
        if (find != INVALID_HANDLE_VALUE) {
            do {
                if ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
                    wchar_t jar_path[MAX_PATH * 2];
                    if (buffer[lstrlenA(buffer) - 1] != '=') {
                        lstrcatA(buffer, ";");
                    }
                    lstrcpyW(jar_path, directory);
                    kotlin_winrt_append_wide(jar_path, ARRAYSIZE(jar_path), data.cFileName);
                    kotlin_winrt_append_utf8(buffer, count, jar_path);
                }
            } while (FindNextFileW(find, &data));
            FindClose(find);
        }
        lstrcpyW(pattern, directory);
        kotlin_winrt_append_wide(pattern, ARRAYSIZE(pattern), L"lib\\*.jar");
        find = FindFirstFileW(pattern, &data);
        if (find != INVALID_HANDLE_VALUE) {
            do {
                if ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
                    wchar_t jar_path[MAX_PATH * 2];
                    if (buffer[lstrlenA(buffer) - 1] != '=') {
                        lstrcatA(buffer, ";");
                    }
                    lstrcpyW(jar_path, directory);
                    kotlin_winrt_append_wide(jar_path, ARRAYSIZE(jar_path), L"lib\\");
                    kotlin_winrt_append_wide(jar_path, ARRAYSIZE(jar_path), data.cFileName);
                    kotlin_winrt_append_utf8(buffer, count, jar_path);
                }
            } while (FindNextFileW(find, &data));
            FindClose(find);
        }
    }

    static void kotlin_winrt_remove_last_path_component(wchar_t *path) {
        for (int i = lstrlenW(path); i > 0; --i) {
            if (path[i - 1] == L'\\' || path[i - 1] == L'/') {
                path[i - 1] = L'\0';
                return;
            }
        }
        path[0] = L'\0';
    }

    // The libraries of a Java runtime import each other by name: zip.dll needs java.dll.
    // java.exe finds them because they are next to it. This host is not, and the default
    // search continues on PATH, where the java.dll of another JDK answers for the one that
    // belongs to this jvm.dll. Search the runtime's own bin directory first, as java.exe does.
    static void kotlin_winrt_use_runtime_library_directory(const wchar_t *jvm_path) {
        wchar_t directory[MAX_PATH * 4];
        int length;
        lstrcpynW(directory, jvm_path, ARRAYSIZE(directory));
        // <home>\bin\server\jvm.dll and <home>\bin\jvm.dll both belong to <home>\bin.
        kotlin_winrt_remove_last_path_component(directory);
        length = lstrlenW(directory);
        if (length > 7 && (directory[length - 7] == L'\\' || directory[length - 7] == L'/') &&
            lstrcmpiW(directory + length - 6, L"server") == 0) {
            directory[length - 7] = L'\0';
        }
        if (directory[0] != L'\0') {
            SetDllDirectoryW(directory);
        }
    }

    static HMODULE kotlin_winrt_load_jvm_at(const wchar_t *home, const wchar_t *suffix) {
        wchar_t path[MAX_PATH * 4];
        if (home == NULL || home[0] == L'\0') {
            return NULL;
        }
        lstrcpynW(path, home, ARRAYSIZE(path));
        kotlin_winrt_append_wide(path, ARRAYSIZE(path), suffix);
        if (GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES) {
            return NULL;
        }
        kotlin_winrt_use_runtime_library_directory(path);
        return LoadLibraryW(path);
    }

    static HMODULE kotlin_winrt_load_jvm_module(void) {
        wchar_t host_directory[MAX_PATH * 4];
        kotlin_winrt_host_directory(host_directory, ARRAYSIZE(host_directory));
        HMODULE module = NULL;
        if ($bundledRuntimeCondition) {
            module = kotlin_winrt_load_jvm_at(host_directory, L"\\runtime\\bin\\server\\jvm.dll");
            if (module == NULL) {
                module = kotlin_winrt_load_jvm_at(host_directory, L"\\runtime\\jre\\bin\\server\\jvm.dll");
            }
            if (module == NULL) {
                module = kotlin_winrt_load_jvm_at(host_directory, L"\\runtime\\bin\\jvm.dll");
            }
        } else {
            module = kotlin_winrt_load_jvm_at(L"$externalJvmHomePath", L"\\bin\\server\\jvm.dll");
            if (module == NULL) {
                module = kotlin_winrt_load_jvm_at(L"$externalJvmHomePath", L"\\jre\\bin\\server\\jvm.dll");
            }
            if (module == NULL) {
                module = kotlin_winrt_load_jvm_at(L"$externalJvmHomePath", L"\\bin\\jvm.dll");
            }
            wchar_t configured_home[MAX_PATH * 4];
            DWORD length = GetEnvironmentVariableW(L"KOTLIN_WINRT_JAVA_HOME", configured_home, ARRAYSIZE(configured_home));
            if (module == NULL && length > 0 && length < ARRAYSIZE(configured_home)) {
                module = kotlin_winrt_load_jvm_at(configured_home, L"\\bin\\server\\jvm.dll");
            }
            length = GetEnvironmentVariableW(L"JAVA_HOME", configured_home, ARRAYSIZE(configured_home));
            if (module == NULL && length > 0 && length < ARRAYSIZE(configured_home)) {
                module = kotlin_winrt_load_jvm_at(configured_home, L"\\bin\\server\\jvm.dll");
            }
            if (module == NULL) {
                module = LoadLibraryW(L"jvm.dll");
            }
        }
        return module;
    }

    static int kotlin_winrt_add_environment_options(JavaVMOption *options, int option_count, int option_capacity, char *environment_options, DWORD environment_options_count) {
        DWORD length = GetEnvironmentVariableA("KOTLIN_WINRT_JVM_OPTIONS", environment_options, environment_options_count);
        if (length == 0 || length >= environment_options_count) {
            return option_count;
        }
        char *start = environment_options;
        for (DWORD i = 0; i <= length && option_count < option_capacity; ++i) {
            if (environment_options[i] == ';' || environment_options[i] == '\n' || environment_options[i] == '\0') {
                environment_options[i] = '\0';
                if (*start != '\0') {
                    options[option_count++].optionString = start;
                }
                start = environment_options + i + 1;
            }
        }
        return option_count;
    }

    static int kotlin_winrt_create_vm(JNIEnv **env) {
        char classpath[32768];
        char environment_options[32768];
        wchar_t host_directory[MAX_PATH * 2];
        wchar_t host_module[MAX_PATH * 2];
        char runtime_assets_root[MAX_PATH * 4 + 64];
        char application_manifest_name[MAX_PATH * 4 + 64];
        JavaVMOption options[64];
        int option_count = 0;
        JavaVMInitArgs args;
        kotlin_winrt_create_java_vm_fn create_vm;
        kotlin_winrt_jvm_module = kotlin_winrt_load_jvm_module();
        if (kotlin_winrt_jvm_module == NULL) {
            return 1;
        }
        create_vm = (kotlin_winrt_create_java_vm_fn)GetProcAddress(kotlin_winrt_jvm_module, "JNI_CreateJavaVM");
        if (create_vm == NULL) {
            return 1;
        }
        kotlin_winrt_classpath(classpath, sizeof(classpath));
        options[option_count++].optionString = classpath;
        options[option_count++].optionString = "--enable-native-access=ALL-UNNAMED";
        kotlin_winrt_host_directory(host_directory, ARRAYSIZE(host_directory));
        lstrcpyA(runtime_assets_root, "-Dkotlin.winrt.runtimeAssetsRoot=");
        kotlin_winrt_append_utf8(runtime_assets_root, sizeof(runtime_assets_root), host_directory);
        options[option_count++].optionString = runtime_assets_root;
        GetModuleFileNameW(NULL, host_module, ARRAYSIZE(host_module));
        for (DWORD i = lstrlenW(host_module); i > 0; --i) {
            if (host_module[i - 1] == L'\\' || host_module[i - 1] == L'/') {
                MoveMemory(host_module, host_module + i, (lstrlenW(host_module + i) + 1) * sizeof(wchar_t));
                break;
            }
        }
        lstrcpyA(application_manifest_name, "-Dkotlin.winrt.applicationManifestName=");
        kotlin_winrt_append_utf8(application_manifest_name, sizeof(application_manifest_name), host_module);
        lstrcatA(application_manifest_name, ".manifest");
        options[option_count++].optionString = application_manifest_name;
        option_count = kotlin_winrt_add_environment_options(options, option_count, ARRAYSIZE(options), environment_options, sizeof(environment_options));
        args.version = JNI_VERSION_1_8;
        args.nOptions = option_count;
        args.options = options;
        args.ignoreUnrecognized = JNI_TRUE;
        if (create_vm(&kotlin_winrt_vm, (void **)env, &args) != JNI_OK) {
            kotlin_winrt_vm = NULL;
            return 1;
        }
        return 0;
    }

    static int kotlin_winrt_handle_pending_exception(JNIEnv *env) {
        if (!(*env)->ExceptionCheck(env)) {
            return 0;
        }
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 1;
    }

    static jobject kotlin_winrt_initialize_application_host(JNIEnv *env) {
        jclass support_class = (*env)->FindClass(env, "io/github/composefluent/winrt/runtime/WindowsAppSdkLauncherSupport");
        jmethodID initialize;
        jstring package_identity;
        jstring deployment_mode;
        jstring runtime_assets_root;
        wchar_t host_directory[MAX_PATH * 4];
        char host_directory_utf8[MAX_PATH * 4];
        jobject result;
        if (support_class == NULL) {
            return NULL;
        }
        initialize = (*env)->GetStaticMethodID(env, support_class, "initializeApplicationHost", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/AutoCloseable;");
        if (initialize == NULL) {
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        package_identity = (*env)->NewStringUTF(env, "$packageIdentity");
        if (package_identity == NULL) {
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        deployment_mode = (*env)->NewStringUTF(env, "${windowsAppSdkDeployment.name}");
        if (deployment_mode == NULL) {
            (*env)->DeleteLocalRef(env, package_identity);
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        kotlin_winrt_host_directory(host_directory, ARRAYSIZE(host_directory));
        host_directory_utf8[0] = '\0';
        kotlin_winrt_append_utf8(host_directory_utf8, sizeof(host_directory_utf8), host_directory);
        runtime_assets_root = (*env)->NewStringUTF(env, host_directory_utf8);
        if (runtime_assets_root == NULL) {
            (*env)->DeleteLocalRef(env, package_identity);
            (*env)->DeleteLocalRef(env, deployment_mode);
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        result = (*env)->CallStaticObjectMethod(env, support_class, initialize, package_identity, deployment_mode, runtime_assets_root);
        (*env)->DeleteLocalRef(env, package_identity);
        (*env)->DeleteLocalRef(env, deployment_mode);
        (*env)->DeleteLocalRef(env, runtime_assets_root);
        (*env)->DeleteLocalRef(env, support_class);
        return result;
    }

    static int kotlin_winrt_close_application_host(JNIEnv *env, jobject application_host) {
        jclass support_class;
        jmethodID close;
        int failed = 0;
        if (application_host == NULL) {
            return 0;
        }
        failed |= kotlin_winrt_handle_pending_exception(env);
        support_class = (*env)->FindClass(env, "io/github/composefluent/winrt/runtime/WindowsAppSdkLauncherSupport");
        if (support_class == NULL) {
            failed |= kotlin_winrt_handle_pending_exception(env);
            return 1;
        }
        close = (*env)->GetStaticMethodID(env, support_class, "close", "(Ljava/lang/AutoCloseable;)V");
        if (close == NULL) {
            failed |= kotlin_winrt_handle_pending_exception(env);
            (*env)->DeleteLocalRef(env, support_class);
            return 1;
        }
        (*env)->CallStaticVoidMethod(env, support_class, close, application_host);
        failed |= kotlin_winrt_handle_pending_exception(env);
        (*env)->DeleteLocalRef(env, support_class);
        return failed;
    }

    // Package activation forwards arguments, not the launching IDE's environment.
    // Consume the development opt-in before JVM creation; user main sees only its own arguments.
    static int kotlin_winrt_preview_requested = 0;
    static int kotlin_winrt_development_arguments(int *argc, wchar_t **wargv) {
        const wchar_t *prefix = L"$WINAPP_HOT_RELOAD_ARGUMENT";
        const size_t prefix_length = wcslen(prefix);
        int next = 1;
        int enabled = 0;
        for (int i = 1; i < *argc; ++i) {
            if (wcsncmp(wargv[i], prefix, prefix_length) == 0) {
                const wchar_t *directory = wargv[i] + prefix_length;
                if (enabled || directory[0] == L'\0' ||
                    !SetEnvironmentVariableW(L"${io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol.SESSION_DIRECTORY}", directory)) {
                    return 1;
                }
                enabled = 1;
            } else if (wcscmp(wargv[i], L"${io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol.PREVIEW_ARGUMENT}") == 0) {
                kotlin_winrt_preview_requested = 1;
            } else {
                wargv[next++] = wargv[i];
            }
        }
        *argc = next;
        wargv[next] = NULL;
        return 0;
    }

    static int kotlin_winrt_run_application(int argc, wchar_t **wargv) {
        JNIEnv *env = NULL;
        jobject application_host = NULL;
        jclass main_class;
        jmethodID main_method;
        jclass string_class;
        jobjectArray args;
        int exit_code = 0;
        if (kotlin_winrt_development_arguments(&argc, wargv) != 0) {
            return 1;
        }
        if (kotlin_winrt_create_vm(&env) != 0 || env == NULL) {
            return 1;
        }
        application_host = kotlin_winrt_initialize_application_host(env);
        if (application_host == NULL) {
            kotlin_winrt_handle_pending_exception(env);
            return 1;
        }
        if (kotlin_winrt_handle_pending_exception(env)) {
            kotlin_winrt_close_application_host(env, application_host);
            return 1;
        }
        main_class = (*env)->FindClass(env, kotlin_winrt_preview_requested ?
            "io/github/composefluent/winrt/generated/xaml/KotlinWinRTXamlPreviewHost" : "$mainClassPath");
        if (main_class == NULL) {
            exit_code = 1;
            kotlin_winrt_handle_pending_exception(env);
            goto cleanup;
        }
        main_method = (*env)->GetStaticMethodID(env, main_class, "main", "([Ljava/lang/String;)V");
        if (main_method == NULL) {
            exit_code = 1;
            kotlin_winrt_handle_pending_exception(env);
            goto cleanup;
        }
        string_class = (*env)->FindClass(env, "java/lang/String");
        if (string_class == NULL) {
            exit_code = 1;
            kotlin_winrt_handle_pending_exception(env);
            goto cleanup;
        }
        args = (*env)->NewObjectArray(env, argc > 1 ? argc - 1 : 0, string_class, NULL);
        if (args == NULL) {
            exit_code = 1;
            kotlin_winrt_handle_pending_exception(env);
            goto cleanup;
        }
        for (int i = 1; i < argc; ++i) {
            int length = WideCharToMultiByte(CP_UTF8, 0, wargv[i], -1, NULL, 0, NULL, NULL);
            if (length <= 0) {
                exit_code = 1;
                goto cleanup;
            }
            char *utf8 = (char *)HeapAlloc(GetProcessHeap(), 0, length);
            if (utf8 == NULL) {
                exit_code = 1;
                goto cleanup;
            }
            WideCharToMultiByte(CP_UTF8, 0, wargv[i], -1, utf8, length, NULL, NULL);
            jstring value = (*env)->NewStringUTF(env, utf8);
            HeapFree(GetProcessHeap(), 0, utf8);
            if (value == NULL) {
                exit_code = 1;
                kotlin_winrt_handle_pending_exception(env);
                goto cleanup;
            }
            (*env)->SetObjectArrayElement(env, args, i - 1, value);
            (*env)->DeleteLocalRef(env, value);
            if (kotlin_winrt_handle_pending_exception(env)) {
                exit_code = 1;
                goto cleanup;
            }
        }
        (*env)->CallStaticVoidMethod(env, main_class, main_method, args);
        if (kotlin_winrt_handle_pending_exception(env)) {
            exit_code = 1;
        }
    cleanup:
        if (kotlin_winrt_close_application_host(env, application_host) != 0) {
            exit_code = 1;
        }
        return exit_code;
    }

    typedef struct {
        int argc;
        wchar_t **argv;
    } kotlin_winrt_application_args;

    static unsigned __stdcall kotlin_winrt_application_thread(void *context) {
        kotlin_winrt_application_args *args = (kotlin_winrt_application_args *)context;
        int exit_code = kotlin_winrt_run_application(args->argc, args->argv);
        if (kotlin_winrt_vm != NULL && (*kotlin_winrt_vm)->DetachCurrentThread(kotlin_winrt_vm) != JNI_OK) {
            exit_code = 1;
        }
        // WinUI and native components can keep thread-owned HWNDs until DLL
        // static teardown. Run normal CRT/process shutdown on their UI thread;
        // returning here would destroy its windows before the main thread unloads
        // those DLLs. The application host and JNI attachment are already closed.
        exit(exit_code);
    }

    int wmain(int argc, wchar_t **wargv) {
        kotlin_winrt_application_args args = { argc, wargv };
        DWORD exit_code = 1;
        // JNI recommends creating the VM on a fresh thread rather than the primordial
        // process thread. Commit enough native stack for nested WinUI/FFM callbacks;
        // Java -Xss does not set the stack of an existing JNI invocation thread.
        // https://docs.oracle.com/en/java/javase/25/docs/specs/jni/invocation.html
        HANDLE thread = (HANDLE)_beginthreadex(
            NULL, 4 * 1024 * 1024, kotlin_winrt_application_thread, &args,
            0, NULL);
        if (thread == NULL) {
            return 1;
        }
        if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0 ||
            !GetExitCodeThread(thread, &exit_code)) {
            exit_code = 1;
        }
        CloseHandle(thread);
        return (int)exit_code;
    }
    """.trimIndent()
}
