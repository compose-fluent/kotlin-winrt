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
import kotlin.io.path.name

abstract class BuildWinRtAuthoringHostTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:Input
    abstract val javaHome: Property<String>

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
        val manifests = (
            authoredHostManifestFiles.files.map(::readHostBuildManifest) +
                dependencyIdentityFiles.files.flatMap(::readAuthoredHostManifestRecords).mapNotNull(::hostBuildManifestFromRecord)
            )
            .distinctBy { it.assemblyName.lowercase() }
        if (manifests.isEmpty()) {
            return
        }
        val moduleDefinition = sourceRoot.resolve("kotlin_winrt_authoring_host.def")
        Files.writeString(
            moduleDefinition,
            """
            EXPORTS
                DllGetActivationFactory   PRIVATE
                DllCanUnloadNow           PRIVATE
            """.trimIndent(),
        )
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            logger.warn("Kotlin/WinRT authoring host native DLL build is Windows-only; generated source files without compiling DLLs.")
            return
        }
        val compiler = findExecutable("clang-cl.exe") ?: findExecutable("cl.exe")
        if (compiler == null) {
            throw IllegalStateException("No clang-cl.exe or cl.exe found. Kotlin/WinRT authoring host DLLs require a Windows C/C++ toolchain.")
        }
        val sdk = findWindowsSdk()
        if (sdk == null) {
            throw IllegalStateException("No Windows SDK installation found. Kotlin/WinRT authoring host DLLs require Windows SDK headers and libraries.")
        }
        manifests.forEach { manifest ->
            val hostSource = sourceRoot.resolve("${manifest.assemblyName.toGeneratedFileStem()}_kotlin_winrt_authoring_host.c")
            Files.writeString(hostSource, authoringHostSource(manifest.hostExportsClass))
            val dll = outputRoot.resolve("${manifest.assemblyName}.dll")
            compileHostDll(
                compiler = compiler,
                sdk = sdk,
                source = hostSource,
                moduleDefinition = moduleDefinition,
                output = dll,
            )
        }
    }

    private fun compileHostDll(
        compiler: Path,
        sdk: WindowsSdkLayout,
        source: Path,
        moduleDefinition: Path,
        output: Path,
    ) {
        val javaHomePath = Path.of(javaHome.get())
        val arguments = mutableListOf(
            compiler.toString(),
            "/nologo",
            "/LD",
            source.toString(),
            "/Fe:${output}",
            "/I${javaHomePath.resolve("include")}",
            "/I${javaHomePath.resolve("include").resolve("win32")}",
            "/I${sdk.includeRoot.resolve("shared")}",
            "/I${sdk.includeRoot.resolve("um")}",
            "/I${sdk.includeRoot.resolve("ucrt")}",
            "/link",
            "/NOLOGO",
            "/DLL",
            "/DEF:${moduleDefinition}",
            "/LIBPATH:${sdk.libRoot.resolve("um").resolve(targetArchitecture())}",
            "/LIBPATH:${sdk.libRoot.resolve("ucrt").resolve(targetArchitecture())}",
            "runtimeobject.lib",
            "kernel32.lib",
            "user32.lib",
        )
        val result = runProcess(arguments, output.parent)
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "Kotlin/WinRT authoring host DLL build failed with exit code ${result.exitCode}.\n${result.output}",
            )
        }
    }

    private fun readHostBuildManifest(source: java.io.File): HostBuildManifest {
        val content = source.takeIf { it.isFile }?.readText().orEmpty()
        val assemblyName = readJsonString(content, "assemblyName")
            ?: throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' is missing assemblyName.")
        if (assemblyName.isBlank()) {
            throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' has blank assemblyName.")
        }
        val classNames = readJsonStringArray(content, "activatableClasses") + readJsonStringMap(content, "activatableClassTargets").keys
        if (classNames.none { it.isNotBlank() }) {
            throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' does not declare any activatable classes.")
        }
        val hostExportsClass = readJsonString(content, "hostExportsClass")
            ?: throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' is missing hostExportsClass.")
        if (hostExportsClass.isBlank()) {
            throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' has blank hostExportsClass.")
        }
        if (!hostExportsClass.matches(JVM_CLASS_NAME_REGEX)) {
            throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' has invalid hostExportsClass '$hostExportsClass'.")
        }
        return HostBuildManifest(assemblyName, hostExportsClass)
    }

    private fun hostBuildManifestFromRecord(record: AuthoredHostManifestRecord): HostBuildManifest? {
        val hostExportsClass = record.hostExportsClass?.takeIf(String::isNotBlank) ?: return null
        if (!record.targetArtifact.endsWith(".jar", ignoreCase = true)) {
            return null
        }
        if ((record.activatableClasses + record.activatableClassTargets.keys).none { it.isNotBlank() }) {
            return null
        }
        if (!hostExportsClass.matches(JVM_CLASS_NAME_REGEX)) {
            throw IllegalArgumentException(
                "Kotlin/WinRT authoring host record for '${record.assemblyName}' has invalid hostExportsClass '$hostExportsClass'.",
            )
        }
        return HostBuildManifest(record.assemblyName, hostExportsClass)
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
                .map { it.trim() }
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

    private fun targetArchitecture(): String =
        windowsSdkArchitecture(runtimeIdentifier.get())

    private fun runProcess(
        arguments: List<String>,
        workingDirectory: Path,
    ): ProcessResult {
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        process.inputStream.copyTo(output)
        val exitCode = process.waitFor()
        return ProcessResult(exitCode, output.toString(Charsets.UTF_8))
    }
}

private data class HostBuildManifest(
    val assemblyName: String,
    val hostExportsClass: String,
)

private data class ProcessResult(
    val exitCode: Int,
    val output: String,
)

private val JVM_CLASS_NAME_REGEX = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

private fun String.toGeneratedFileStem(): String =
    map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "authoring_host" }

private fun authoringHostSource(hostExportsClass: String): String =
    """
    #define WIN32_LEAN_AND_MEAN
    #include <windows.h>
    #include <winstring.h>
    #include <jni.h>
    #include <stdint.h>

    #define KOTLIN_WINRT_S_OK ((HRESULT)0x00000000)
    #define KOTLIN_WINRT_S_FALSE ((HRESULT)0x00000001)
    #define KOTLIN_WINRT_E_FAIL ((HRESULT)0x80004005)
    #define KOTLIN_WINRT_E_INVALIDARG ((HRESULT)0x80070057)
    #define KOTLIN_WINRT_REGDB_E_READREGDB ((HRESULT)0x80040150)

    typedef jint (JNICALL *kotlin_winrt_get_created_java_vms_fn)(JavaVM **, jsize, jsize *);
    typedef jint (JNICALL *kotlin_winrt_create_java_vm_fn)(JavaVM **, void **, void *);

    static JavaVM *kotlin_winrt_vm = NULL;
    static HMODULE kotlin_winrt_jvm_module = NULL;

    static void kotlin_winrt_host_directory(wchar_t *buffer, DWORD count) {
        HMODULE module = NULL;
        GetModuleHandleExW(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            (LPCWSTR)&kotlin_winrt_host_directory,
            &module);
        GetModuleFileNameW(module, buffer, count);
        for (DWORD i = lstrlenW(buffer); i > 0; --i) {
            if (buffer[i - 1] == L'\\' || buffer[i - 1] == L'/') {
                buffer[i] = L'\0';
                return;
            }
        }
        buffer[0] = L'\0';
    }

    static void kotlin_winrt_append_wide(wchar_t *target, DWORD target_count, const wchar_t *value) {
        if (lstrlenW(target) + lstrlenW(value) + 1 < (int)target_count) {
            lstrcatW(target, value);
        }
    }

    static void kotlin_winrt_append_utf8(char *target, DWORD target_count, const wchar_t *value) {
        int length = WideCharToMultiByte(CP_UTF8, 0, value, -1, NULL, 0, NULL, NULL);
        if (length <= 0) {
            return;
        }
        char converted[MAX_PATH * 4];
        WideCharToMultiByte(CP_UTF8, 0, value, -1, converted, sizeof(converted), NULL, NULL);
        if (lstrlenA(target) + lstrlenA(converted) + 1 < (int)target_count) {
            lstrcatA(target, converted);
        }
    }

    static void kotlin_winrt_append_classpath_jars(char *buffer, DWORD count, const wchar_t *relative_pattern, const wchar_t *relative_prefix) {
        wchar_t directory[MAX_PATH * 2];
        wchar_t pattern[MAX_PATH * 2];
        WIN32_FIND_DATAW data;
        HANDLE find;
        kotlin_winrt_host_directory(directory, ARRAYSIZE(directory));
        lstrcpyW(pattern, directory);
        kotlin_winrt_append_wide(pattern, ARRAYSIZE(pattern), relative_pattern);
        find = FindFirstFileW(pattern, &data);
        if (find == INVALID_HANDLE_VALUE) {
            return;
        }
        do {
            if ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
                wchar_t jar_path[MAX_PATH * 2];
                if (buffer[lstrlenA(buffer) - 1] != '=') {
                    lstrcatA(buffer, ";");
                }
                lstrcpyW(jar_path, directory);
                kotlin_winrt_append_wide(jar_path, ARRAYSIZE(jar_path), relative_prefix);
                kotlin_winrt_append_wide(jar_path, ARRAYSIZE(jar_path), data.cFileName);
                kotlin_winrt_append_utf8(buffer, count, jar_path);
            }
        } while (FindNextFileW(find, &data));
        FindClose(find);
    }

    static void kotlin_winrt_classpath(char *buffer, DWORD count) {
        buffer[0] = '\0';
        lstrcpyA(buffer, "-Djava.class.path=");
        kotlin_winrt_append_classpath_jars(buffer, count, L"*.jar", L"");
        kotlin_winrt_append_classpath_jars(buffer, count, L"lib\\*.jar", L"lib\\");
    }

    static HMODULE kotlin_winrt_load_jvm_module(void) {
        HMODULE module = GetModuleHandleW(L"jvm.dll");
        if (module != NULL) {
            return module;
        }
        module = LoadLibraryW(L"jvm.dll");
        if (module != NULL) {
            return module;
        }
        wchar_t java_home[MAX_PATH * 2];
        DWORD length = GetEnvironmentVariableW(L"JAVA_HOME", java_home, ARRAYSIZE(java_home));
        if (length > 0 && length < ARRAYSIZE(java_home)) {
            kotlin_winrt_append_wide(java_home, ARRAYSIZE(java_home), L"\\bin\\server\\jvm.dll");
            module = LoadLibraryW(java_home);
            if (module != NULL) {
                return module;
            }
        }
        return NULL;
    }

    static HRESULT kotlin_winrt_get_env(JNIEnv **env) {
        jint created = 0;
        kotlin_winrt_get_created_java_vms_fn get_created;
        kotlin_winrt_create_java_vm_fn create_vm;
        JavaVM *vms[1];
        if (kotlin_winrt_vm != NULL) {
            if ((*kotlin_winrt_vm)->GetEnv(kotlin_winrt_vm, (void **)env, JNI_VERSION_1_8) == JNI_OK) {
                return KOTLIN_WINRT_S_OK;
            }
            if ((*kotlin_winrt_vm)->AttachCurrentThread(kotlin_winrt_vm, (void **)env, NULL) == JNI_OK) {
                return KOTLIN_WINRT_S_OK;
            }
            return KOTLIN_WINRT_E_FAIL;
        }
        kotlin_winrt_jvm_module = kotlin_winrt_load_jvm_module();
        if (kotlin_winrt_jvm_module == NULL) {
            return KOTLIN_WINRT_E_FAIL;
        }
        get_created = (kotlin_winrt_get_created_java_vms_fn)GetProcAddress(kotlin_winrt_jvm_module, "JNI_GetCreatedJavaVMs");
        if (get_created != NULL && get_created(vms, 1, &created) == JNI_OK && created > 0) {
            kotlin_winrt_vm = vms[0];
            return kotlin_winrt_get_env(env);
        }
        create_vm = (kotlin_winrt_create_java_vm_fn)GetProcAddress(kotlin_winrt_jvm_module, "JNI_CreateJavaVM");
        if (create_vm == NULL) {
            return KOTLIN_WINRT_E_FAIL;
        }
        char classpath[32768];
        JavaVMOption options[2];
        JavaVMInitArgs args;
        kotlin_winrt_classpath(classpath, sizeof(classpath));
        options[0].optionString = classpath;
        options[1].optionString = "-Djava.awt.headless=true";
        args.version = JNI_VERSION_1_8;
        args.nOptions = 2;
        args.options = options;
        args.ignoreUnrecognized = JNI_TRUE;
        if (create_vm(&kotlin_winrt_vm, (void **)env, &args) != JNI_OK) {
            kotlin_winrt_vm = NULL;
            return KOTLIN_WINRT_E_FAIL;
        }
        return KOTLIN_WINRT_S_OK;
    }

    static HRESULT kotlin_winrt_call_int_method(const char *name, const char *signature, jlong arg0, jlong arg1, jint *result) {
        JNIEnv *env = NULL;
        HRESULT hr = kotlin_winrt_get_env(&env);
        if (FAILED(hr)) {
            return hr;
        }
        jclass exports_class = (*env)->FindClass(env, "${hostExportsClass.replace('.', '/')}");
        if (exports_class == NULL) {
            (*env)->ExceptionClear(env);
            return KOTLIN_WINRT_REGDB_E_READREGDB;
        }
        jmethodID method = (*env)->GetStaticMethodID(env, exports_class, name, signature);
        if (method == NULL) {
            (*env)->ExceptionClear(env);
            return KOTLIN_WINRT_REGDB_E_READREGDB;
        }
        if (signature[1] == ')') {
            *result = (*env)->CallStaticIntMethod(env, exports_class, method);
        } else {
            *result = (*env)->CallStaticIntMethod(env, exports_class, method, arg0, arg1);
        }
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            return KOTLIN_WINRT_E_FAIL;
        }
        return KOTLIN_WINRT_S_OK;
    }

    __declspec(dllexport) HRESULT STDMETHODCALLTYPE DllGetActivationFactory(void *hstr_class_id, void **activation_factory) {
        jint result = 0;
        HRESULT hr;
        if (hstr_class_id == NULL || activation_factory == NULL) {
            return KOTLIN_WINRT_E_INVALIDARG;
        }
        *activation_factory = NULL;
        hr = kotlin_winrt_call_int_method(
            "dllGetActivationFactoryAddress",
            "(JJ)I",
            (jlong)(intptr_t)hstr_class_id,
            (jlong)(intptr_t)activation_factory,
            &result);
        if (FAILED(hr)) {
            return hr;
        }
        return (HRESULT)result;
    }

    __declspec(dllexport) HRESULT STDMETHODCALLTYPE DllCanUnloadNow(void) {
        jint result = 0;
        HRESULT hr = kotlin_winrt_call_int_method("dllCanUnloadNowAddress", "()I", 0, 0, &result);
        if (FAILED(hr)) {
            return KOTLIN_WINRT_S_FALSE;
        }
        return (HRESULT)result;
    }
    """.trimIndent()

private fun readJsonString(content: String, name: String): String? =
    Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(content)
        ?.groupValues
        ?.get(1)
        ?.decodeJsonString()

private fun readJsonStringMap(content: String, name: String): Map<String, String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyMap()
    return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .associate { it.groupValues[1].decodeJsonString() to it.groupValues[2].decodeJsonString() }
}

private fun readJsonStringArray(content: String, name: String): List<String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1].decodeJsonString() }
        .toList()
}

private fun String.decodeJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\")
