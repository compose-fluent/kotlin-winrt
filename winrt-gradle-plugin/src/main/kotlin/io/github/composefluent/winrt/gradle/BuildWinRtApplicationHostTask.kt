package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

abstract class BuildWinRtApplicationHostTask : DefaultTask() {
    init {
        packageMode.convention(WinRtApplicationPackageMode.Unpackaged.name)
        console.convention(false)
        windowsSdkVersion.convention("")
    }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

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
    abstract val packageMode: Property<String>

    @get:Input
    abstract val console: Property<Boolean>

    @get:Input
    abstract val javaHome: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    @get:Internal
    abstract val commandWorkingDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        val sourceRoot = generatedSourceDirectory.get().asFile.toPath()
        Files.createDirectories(outputRoot)
        Files.createDirectories(sourceRoot)
        val source = sourceRoot.resolve("kotlin_winrt_application_host.c")
        val mainClassValue = mainClass.orNull?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Kotlin/WinRT application host requires an application mainClass.")
        Files.writeString(source, applicationHostSource(mainClassValue, packageMode.get(), Path.of(javaHome.get())))
        stageRuntimeClasspath(outputRoot)
        stageRuntimeAssets(outputRoot)
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            logger.warn("Kotlin/WinRT application host native EXE build is Windows-only; generated source without compiling EXE.")
            return
        }
        val compiler = findExecutable("clang-cl.exe") ?: findExecutable("cl.exe")
        if (compiler == null) {
            throw IllegalStateException("No clang-cl.exe or cl.exe found. Kotlin/WinRT application host requires a Windows C/C++ toolchain.")
        }
        val sdk = findWindowsSdk(windowsSdkVersion.get().takeIf(String::isNotBlank))
            ?: throw IllegalStateException("No Windows SDK installation found. Kotlin/WinRT application host requires Windows SDK headers and libraries.")
        compileHostExe(compiler, sdk, source, outputRoot.resolve("${executableBaseName.get()}.exe"))
    }

    private fun stageRuntimeClasspath(outputRoot: Path) {
        val libRoot = outputRoot.resolve("lib")
        GradleFileOperations.cleanDirectory(libRoot)
        Files.createDirectories(libRoot)
        runtimeClasspath.files
            .filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }
            .forEach { jar ->
                Files.copy(jar.toPath(), libRoot.resolve(jar.name), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
    }

    private fun stageRuntimeAssets(outputRoot: Path) {
        val runtimeAssetsRoot = outputRoot.resolve("kotlin-winrt-runtime-assets")
        GradleFileOperations.cleanDirectory(runtimeAssetsRoot)
        runtimeAssetsDirectory.files
            .filter { it.exists() }
            .forEach { source ->
                if (source.isDirectory) {
                    copyDirectory(source.toPath(), runtimeAssetsRoot)
                } else if (source.isFile) {
                    Files.createDirectories(runtimeAssetsRoot)
                    Files.copy(
                        source.toPath(),
                        runtimeAssetsRoot.resolve(source.name),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                }
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
        compiler: Path,
        sdk: WindowsSdkLayout,
        source: Path,
        output: Path,
    ) {
        val javaHomePath = Path.of(javaHome.get())
        val architecture = windowsSdkArchitecture(runtimeIdentifier.get())
        val useLlvmLinker = compiler.fileName.toString().equals("clang-cl.exe", ignoreCase = true) &&
            findExecutable("lld-link.exe") != null
        val arguments = buildList {
            add(compiler.toString())
            if (useLlvmLinker) {
                add("-fuse-ld=lld")
            }
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
        val result = runProcess(arguments, output.parent)
        if (result.exitCode != 0) {
            throw IllegalStateException("Kotlin/WinRT application host build failed with exit code ${result.exitCode}.\n${result.output}")
        }
    }

    private fun findExecutable(name: String): Path? {
        val path = System.getenv("PATH").orEmpty()
            .split(java.io.File.pathSeparatorChar)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { Path.of(it).resolve(name) }
            .firstOrNull { it.isRegularFile() }
        if (path != null) {
            return path
        }
        return runCatching {
            val result = runProcess(listOf("cmd.exe", "/c", "where", name), commandWorkingDirectory.get().asFile.toPath())
            result.output
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.endsWith(name, ignoreCase = true) }
                ?.let(Path::of)
        }.getOrNull()
            ?: standardWindowsToolchainCandidates(name).firstOrNull { it.isRegularFile() }
    }

    private fun standardWindowsToolchainCandidates(name: String): Sequence<Path> = sequence {
        System.getenv("ProgramFiles")?.takeIf { it.isNotBlank() }?.let { programFiles ->
            yield(Path.of(programFiles).resolve("LLVM").resolve("bin").resolve(name))
        }
        System.getenv("ProgramFiles(x86)")?.takeIf { it.isNotBlank() }?.let { programFilesX86 ->
            yield(Path.of(programFilesX86).resolve("LLVM").resolve("bin").resolve(name))
        }
    }

    private fun runProcess(
        arguments: List<String>,
        workingDirectory: Path,
    ): HostProcessResult {
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        process.inputStream.copyTo(output)
        val exitCode = process.waitFor()
        return HostProcessResult(exitCode, output.toString(Charsets.UTF_8))
    }
}

private data class HostProcessResult(
    val exitCode: Int,
    val output: String,
)

private fun applicationHostSource(
    mainClass: String,
    packageMode: String,
    javaHome: Path,
): String {
    val mainClassPath = mainClass.replace('.', '/')
    val unpackaged = packageMode == WinRtApplicationPackageMode.Unpackaged.name
    val javaHomeJvmPath = javaHome.resolve("bin").resolve("server").resolve("jvm.dll")
        .toString()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
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

    static HMODULE kotlin_winrt_load_jvm_module(void) {
        HMODULE configured_module = LoadLibraryW(L"$javaHomeJvmPath");
        if (configured_module != NULL) {
            return configured_module;
        }
        HMODULE module = LoadLibraryW(L"jvm.dll");
        if (module != NULL) {
            return module;
        }
        wchar_t java_home[MAX_PATH * 2];
        DWORD length = GetEnvironmentVariableW(L"JAVA_HOME", java_home, ARRAYSIZE(java_home));
        if (length > 0 && length < ARRAYSIZE(java_home)) {
            kotlin_winrt_append_wide(java_home, ARRAYSIZE(java_home), L"\\bin\\server\\jvm.dll");
            return LoadLibraryW(java_home);
        }
        return NULL;
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
        option_count = kotlin_winrt_add_environment_options(options, option_count, ARRAYSIZE(options), environment_options, sizeof(environment_options));
        args.version = JNI_VERSION_1_8;
        args.nOptions = option_count;
        args.options = options;
        args.ignoreUnrecognized = JNI_TRUE;
        return create_vm(&kotlin_winrt_vm, (void **)env, &args) == JNI_OK ? 0 : 1;
    }

    static jobject kotlin_winrt_initialize_deployment(JNIEnv *env) {
        ${if (unpackaged) """
        jclass support_class = (*env)->FindClass(env, "io/github/composefluent/winrt/runtime/WinRtWindowsAppSdkLauncherSupport");
        if (support_class == NULL) {
            return NULL;
        }
        jmethodID initialize = (*env)->GetStaticMethodID(env, support_class, "initializeForUnpackagedApp", "()Ljava/lang/AutoCloseable;");
        if (initialize == NULL) {
            return NULL;
        }
        return (*env)->CallStaticObjectMethod(env, support_class, initialize);
        """.trimIndent() else "return NULL;"}
    }

    static void kotlin_winrt_close_deployment(JNIEnv *env, jobject deployment) {
        ${if (unpackaged) """
        jclass support_class;
        jmethodID close;
        if (deployment == NULL) {
            return;
        }
        support_class = (*env)->FindClass(env, "io/github/composefluent/winrt/runtime/WinRtWindowsAppSdkLauncherSupport");
        if (support_class == NULL) {
            return;
        }
        close = (*env)->GetStaticMethodID(env, support_class, "close", "(Ljava/lang/AutoCloseable;)V");
        if (close != NULL) {
            (*env)->CallStaticVoidMethod(env, support_class, close, deployment);
        }
        """.trimIndent() else "(void)env; (void)deployment;"}
    }

    int wmain(int argc, wchar_t **wargv) {
        JNIEnv *env = NULL;
        jobject deployment = NULL;
        jclass main_class;
        jmethodID main_method;
        jobjectArray args;
        int exit_code = 0;
        if (kotlin_winrt_create_vm(&env) != 0 || env == NULL) {
            return 1;
        }
        deployment = kotlin_winrt_initialize_deployment(env);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            return 1;
        }
        main_class = (*env)->FindClass(env, "$mainClassPath");
        if (main_class == NULL) {
            (*env)->ExceptionDescribe(env);
            kotlin_winrt_close_deployment(env, deployment);
            return 1;
        }
        main_method = (*env)->GetStaticMethodID(env, main_class, "main", "([Ljava/lang/String;)V");
        if (main_method == NULL) {
            (*env)->ExceptionDescribe(env);
            kotlin_winrt_close_deployment(env, deployment);
            return 1;
        }
        jclass string_class = (*env)->FindClass(env, "java/lang/String");
        args = (*env)->NewObjectArray(env, argc > 1 ? argc - 1 : 0, string_class, NULL);
        for (int i = 1; i < argc; ++i) {
            int length = WideCharToMultiByte(CP_UTF8, 0, wargv[i], -1, NULL, 0, NULL, NULL);
            char *utf8 = (char *)HeapAlloc(GetProcessHeap(), 0, length);
            WideCharToMultiByte(CP_UTF8, 0, wargv[i], -1, utf8, length, NULL, NULL);
            jstring value = (*env)->NewStringUTF(env, utf8);
            HeapFree(GetProcessHeap(), 0, utf8);
            (*env)->SetObjectArrayElement(env, args, i - 1, value);
            (*env)->DeleteLocalRef(env, value);
        }
        (*env)->CallStaticVoidMethod(env, main_class, main_method, args);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            exit_code = 1;
        }
        kotlin_winrt_close_deployment(env, deployment);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            exit_code = 1;
        }
        return exit_code;
    }
    """.trimIndent()
}
