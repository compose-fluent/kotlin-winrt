package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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

abstract class BuildWinRTApplicationHostTask : DefaultTask() {
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
        jvmRuntimeMode.convention(WinRTJvmRuntimeMode.Bundled.name)
        externalJvmHome.convention("")
        expectedJavaMajor.convention(25)
        console.convention(false)
        windowsAppSdkDeployment.convention(WinRTWindowsAppSdkDeployment.FrameworkDependent.name)
        windowsSdkVersion.convention("")
        windowsSdkRegistryRoots.convention(emptyList())
    }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val applicationVariant: Property<String>

    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty

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
    abstract val windowsAppSdkDeployment: Property<String>

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
            ?: throw IllegalStateException("Kotlin/WinRT application host requires an application mainClass.")
        val runtimeMode = jvmRuntimeMode.get()
        val externalHome = externalJvmHome.orNull?.trim().orEmpty()
        if (runtimeMode == WinRTJvmRuntimeMode.External.name && externalHome.isBlank()) {
            throw IllegalStateException(
                "External JVM runtime mode requires application.externalJvmHome to point to a JVM home.",
            )
        }
        if (runtimeMode == WinRTJvmRuntimeMode.External.name) {
            validateExternalJvmHome(Path.of(externalHome))
        }
        val configuredRuntimeImage = if (runtimeMode == WinRTJvmRuntimeMode.Bundled.name) {
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
        // Validate the image/output relationship before cleaning the host output. A configured
        // image inside that output would otherwise be deleted before it can be staged.
        GradleFileOperations.cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        Files.createDirectories(sourceRoot)
        val source = sourceRoot.resolve("kotlin_winrt_application_host.c")
        // Host compilation is Windows-only. On other hosts this task still emits the source
        // used by TestKit and cross-platform configuration checks, but it cannot consume a
        // Windows JVM image or compile the native launcher.
        if (runtimeMode == WinRTJvmRuntimeMode.Bundled.name && isWindowsHost()) {
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
        WinRTApplicationManifestGenerator.writeApplicationManifest(
            outputRoot,
            executableBaseName.get(),
            winRTManifestProcessorArchitecture(runtimeIdentifier.get()),
            redirectDlls = packageType.get() != WindowsPackageType.Packaged.name,
        )
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            logger.warn("Kotlin/WinRT application host native EXE build is Windows-only; generated source without compiling EXE.")
            return
        }
        val toolchain = nativeToolchain.get()
        logger.info("Kotlin/WinRT JVM application host: {} ({}; Windows SDK {})", toolchain.compiler, runtimeIdentifier.get(), toolchain.sdkVersion)
        compileHostExe(toolchain, source, outputRoot.resolve("${executableBaseName.get()}.exe"))
    }

    private fun stageRuntimeClasspath(outputRoot: Path) {
        val libRoot = outputRoot.resolve("lib")
        GradleFileOperations.cleanDirectory(libRoot)
        Files.createDirectories(libRoot)
        val staged = linkedMapOf<String, Path>()
        runtimeClasspath.files
            .filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }
            .sortedBy { it.absolutePath.lowercase() }
            .forEach { jar ->
                val key = jar.name.lowercase()
                val previous = staged[key]
                if (previous != null && previous != jar.toPath().toAbsolutePath().normalize()) {
                    throw IllegalStateException(
                        "JVM application host cannot stage two runtime JARs with the same file name '${jar.name}': " +
                            "$previous and ${jar.toPath().toAbsolutePath().normalize()}",
                    )
                }
                if (previous == null) {
                    staged[key] = jar.toPath().toAbsolutePath().normalize()
                    Files.copy(jar.toPath(), libRoot.resolve(jar.name))
                }
            }
    }

    private fun stageRuntimeImage(outputRoot: Path) {
        val source = runtimeImageDirectory.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
            ?: throw IllegalStateException(
                "Bundled JVM runtime image is missing. Configure application.jvmRuntimeImage or ensure the " +
                    "prepareWinRTJvmRuntimeImage task is wired before building the application host.",
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
        val javaHomeValue = javaHome.orNull?.trim().orEmpty()
        if (javaHomeValue.isBlank()) {
            throw IllegalStateException(
                "Kotlin/WinRT application host requires a resolved Java toolchain home for JNI headers.",
            )
        }
        val javaHomePath = Path.of(javaHomeValue)
        val sdk = toolchain.sdk
        val architecture = windowsSdkArchitecture(runtimeIdentifier.get())
        val arguments = buildList {
            add(toolchain.compiler)
            addAll(toolchain.compilerArguments)
            addAll(
                listOf(
                    "/nologo",
                    source.toString(),
                    "/Fe:${output}",
                    "/I${javaHomePath.resolve("include")}",
                    "/I${javaHomePath.resolve("include").resolve("win32")}",
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
            throw IllegalStateException("Kotlin/WinRT application host build failed with exit code ${result.exitCode}.\n${result.output}")
        }
    }
}

internal fun applicationHostSource(
    mainClass: String,
    packageType: String,
    runtimeMode: String,
    externalJvmHome: String,
    windowsAppSdkDeployment: String = WinRTWindowsAppSdkDeployment.FrameworkDependent.name,
): String {
    val mainClassPath = mainClass.replace('.', '/')
    val unpackaged = packageType == WindowsPackageType.None.name
    val packageIdentity = if (unpackaged) "Unpackaged" else "Packaged"
    val externalJvmHomePath = externalJvmHome
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val bundledRuntime = runtimeMode == WinRTJvmRuntimeMode.Bundled.name
    val bundledRuntimeCondition = if (bundledRuntime) "1" else "0"
    return """
    #define WIN32_LEAN_AND_MEAN
    #include <windows.h>
    #include <jni.h>
    #include <stdint.h>

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

    static HMODULE kotlin_winrt_load_jvm_at(const wchar_t *home, const wchar_t *suffix) {
        wchar_t path[MAX_PATH * 4];
        if (home == NULL || home[0] == L'\0') {
            return NULL;
        }
        lstrcpynW(path, home, ARRAYSIZE(path));
        kotlin_winrt_append_wide(path, ARRAYSIZE(path), suffix);
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
        return create_vm(&kotlin_winrt_vm, (void **)env, &args) == JNI_OK ? 0 : 1;
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
        jclass support_class = (*env)->FindClass(env, "io/github/composefluent/winrt/runtime/WinRTWindowsAppSdkLauncherSupport");
        jmethodID initialize;
        jstring package_identity;
        jstring deployment_mode;
        jobject result;
        if (support_class == NULL) {
            return NULL;
        }
        initialize = (*env)->GetStaticMethodID(env, support_class, "initializeApplicationHost", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/AutoCloseable;");
        if (initialize == NULL) {
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        package_identity = (*env)->NewStringUTF(env, "$packageIdentity");
        if (package_identity == NULL) {
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        deployment_mode = (*env)->NewStringUTF(env, "$windowsAppSdkDeployment");
        if (deployment_mode == NULL) {
            (*env)->DeleteLocalRef(env, package_identity);
            (*env)->DeleteLocalRef(env, support_class);
            return NULL;
        }
        result = (*env)->CallStaticObjectMethod(env, support_class, initialize, package_identity, deployment_mode);
        (*env)->DeleteLocalRef(env, package_identity);
        (*env)->DeleteLocalRef(env, deployment_mode);
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
        support_class = (*env)->FindClass(env, "io/github/composefluent/winrt/runtime/WinRTWindowsAppSdkLauncherSupport");
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

    int wmain(int argc, wchar_t **wargv) {
        JNIEnv *env = NULL;
        jobject application_host = NULL;
        jclass main_class;
        jmethodID main_method;
        jclass string_class;
        jobjectArray args;
        int exit_code = 0;
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
        main_class = (*env)->FindClass(env, "$mainClassPath");
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
    """.trimIndent()
}
