package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.runtime.XamlFramework
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test

class UwpApplicationModelTest {
    @Test fun uwp_selects_system_xaml_without_changing_packaging_defaults() {
        val project = ProjectBuilder.builder().build()
        val windows = project.objects.newInstance(WindowsExtension::class.java, project)
        assertEquals(WindowsApplicationModel.Desktop, windows.application.applicationModel.get())
        assertEquals(XamlFramework.WinUi, windows.xaml.framework.get())
        windows.application.applicationModel.set(WindowsApplicationModel.Uwp)
        assertEquals(XamlFramework.Uwp, windows.xaml.framework.get())
        assertEquals(WindowsPackageType.Packaged, windows.application.packageType.get())
        windows.xaml.framework.set(XamlFramework.WinUi)
        assertTrue(runCatching { validateWindowsApplicationModel(WindowsApplicationModel.Uwp,
            WindowsPackageType.Packaged, WindowsAppSdkDeployment.None, windows.xaml.framework.get()) }.isFailure)
    }

    @Test fun uwp_rejects_desktop_deployment_modes() {
        // CsWinRT's UWP build owns AppContainer and system XAML independently of desktop WASDK deployment.
        validateWindowsApplicationModel(WindowsApplicationModel.Uwp, WindowsPackageType.Packaged,
            WindowsAppSdkDeployment.None, XamlFramework.Uwp)
        assertTrue(runCatching { validateWindowsApplicationModel(WindowsApplicationModel.Uwp,
            WindowsPackageType.None, WindowsAppSdkDeployment.None, XamlFramework.Uwp) }.isFailure)
        assertTrue(runCatching { validateWindowsApplicationModel(WindowsApplicationModel.Uwp,
            WindowsPackageType.Packaged, WindowsAppSdkDeployment.SelfContained, XamlFramework.Uwp) }.isFailure)
        assertTrue(runCatching { validateWindowsApplicationModel(WindowsApplicationModel.Uwp,
            WindowsPackageType.Packaged, WindowsAppSdkDeployment.None, XamlFramework.Uwp, WinAppJvmRuntimeMode.External) }.isFailure)
    }

    @Test fun uwp_launcher_resolves_the_bundled_jvm_through_the_package_graph() {
        // CsWinRT's Native AOT stub needs no JVM loader. The JNI host follows the SDK's
        // LoadPackagedLibrary contract: package-relative paths and package-graph dependencies.
        fun source(model: WindowsApplicationModel) = applicationHostSource(
            "sample.MainKt", WindowsPackageType.Packaged.name, WinAppJvmRuntimeMode.Bundled.name, "",
            windowsAppSdkDeployment = WindowsAppSdkDeployment.None, applicationModel = model,
        )
        val uwp = source(WindowsApplicationModel.Uwp)
        assertTrue(uwp.contains("LoadPackagedLibrary(L\"runtime\\\\bin\\\\server\\\\jvm.dll\", 0)"))
        assertTrue(uwp.contains("LoadPackagedLibrary(L\"runtime\\\\jre\\\\bin\\\\server\\\\jvm.dll\", 0)"))
        assertTrue(uwp.contains("LoadPackagedLibrary(L\"runtime\\\\bin\\\\jvm.dll\", 0)"))
        assertFalse(uwp.contains("AddDllDirectory("))
        assertFalse(uwp.contains("SetDefaultDllDirectories("))
        assertFalse(uwp.contains("SetDllDirectoryW("))
        assertFalse(uwp.contains("LoadLibraryW("))
        assertFalse(uwp.contains("LoadLibraryExW("))
        assertFalse(uwp.contains("KOTLIN_WINRT_JAVA_HOME"))

        val desktop = source(WindowsApplicationModel.Desktop)
        assertTrue(desktop.contains("SetDllDirectoryW(directory);"))
        assertTrue(desktop.contains("return LoadLibraryW(path);"))
        assertFalse(desktop.contains("AddDllDirectory("))
        assertFalse(desktop.contains("LoadPackagedLibrary("))
    }

    @Test fun launcher_reports_java_startup_failures_without_requiring_stderr() {
        val source = applicationHostSource(
            "sample.MainKt", WindowsPackageType.Packaged.name, WinAppJvmRuntimeMode.Bundled.name, "",
            windowsAppSdkDeployment = WindowsAppSdkDeployment.None, applicationModel = WindowsApplicationModel.Uwp,
        )
        val handler = source.substringAfter("static int kotlin_winrt_handle_pending_exception(")
            .substringBefore("static jobject kotlin_winrt_initialize_application_host(")
        val operations = listOf("ExceptionOccurred(env)", "ExceptionDescribe(env)", "ExceptionClear(env)",
            "kotlin_winrt_debug_exception(env, exception)", "DeleteLocalRef(env, exception)", "return 1;")
        operations.forEach { assertTrue(it, handler.contains(it)) }
        assertEquals(operations.map(handler::indexOf).sorted(), operations.map(handler::indexOf))

        val diagnostic = source.substringAfter("static void kotlin_winrt_debug_exception(")
            .substringBefore("static int kotlin_winrt_handle_pending_exception(")
        assertTrue(diagnostic.contains("\"java/io/StringWriter\""))
        assertTrue(diagnostic.contains("\"java/io/PrintWriter\""))
        assertTrue(diagnostic.contains("\"printStackTrace\", \"(Ljava/io/PrintWriter;)V\""))
        assertTrue(diagnostic.contains("GetStringChars(env, stack_trace, NULL)"))
        assertTrue(diagnostic.contains("message[length] = L'\\0';"))
        assertTrue(diagnostic.indexOf("message[length] = L'\\0';") < diagnostic.indexOf("OutputDebugStringW(message);"))
        assertTrue(diagnostic.contains("ReleaseStringChars(env, stack_trace, characters)"))
        assertTrue(diagnostic.contains("if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);"))
        assertTrue(diagnostic.contains("Java stack trace unavailable"))
    }

    @Test fun manifest_declares_appcontainer_and_preserves_entry_class() {
        val manifest = Files.createTempFile("uwp-manifest-", ".xml")
        Files.writeString(manifest, """<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
            <Applications><Application Id="App" Executable="App.exe" EntryPoint="Sample.App" /></Applications>
            </Package>""")
        repeat(2) { AppxManifestPackageSupport.applyApplicationModel(manifest, WindowsApplicationModel.Uwp) }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest.toFile())
        val application = document.getElementsByTagNameNS("*", "Application").item(0) as org.w3c.dom.Element
        val namespace = "http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
        assertEquals("windowsApp", application.getAttributeNS(namespace, "RuntimeBehavior"))
        assertEquals("appContainer", application.getAttributeNS(namespace, "TrustLevel"))
        assertEquals("Sample.App", application.getAttribute("EntryPoint"))
        Files.writeString(manifest, Files.readString(manifest).replace("Sample.App", "Windows.FullTrustApplication"))
        assertTrue(runCatching { AppxManifestPackageSupport.applyApplicationModel(manifest, WindowsApplicationModel.Uwp) }.isFailure)
    }

    @Test fun uwp_compiler_requires_explicit_sdk_compatibility_and_locates_sdk_layout() {
        val root = Files.createTempDirectory("uwp-sdk-")
        val metadata = root.resolve("References/10.0.26100.0/Windows.Foundation.UniversalApiContract/1.0.0.0/Windows.Foundation.UniversalApiContract.winmd")
        Files.createDirectories(metadata.parent)
        Files.write(metadata, byteArrayOf())
        val genXbf = root.resolve("bin/10.0.26100.0/XamlCompiler/x64/GenXbf.dll")
        Files.createDirectories(genXbf.parent)
        Files.write(genXbf, byteArrayOf())
        assertEquals(genXbf.parent.parent.toFile(), findUwpGenXbfDirectory(listOf(metadata.toFile()), "10.0.26100.0"))
        val manifest = Json.parseToJsonElement("""{"compatibility":{"frameworks":["uwp"],"windowsSdkVersions":["10.0.26100.0"],"genXbfHost":"win-x64"}}""").jsonObject
        validateXamlCompilerFramework(manifest, XamlFramework.Uwp, listOf(metadata.toFile()), genXbf.parent.parent.toFile(), null, "10.0.26100.0")
        assertTrue(runCatching { validateXamlCompilerFramework(manifest, XamlFramework.Uwp,
            listOf(metadata.toFile()), genXbf.parent.parent.toFile(), null, "10.0.19041.0") }.isFailure)
        assertTrue(runCatching { validateXamlCompilerFramework(manifest, XamlFramework.WinUi,
            listOf(metadata.toFile()), genXbf.parent.parent.toFile(), null, "10.0.26100.0") }.isFailure)
    }
}
