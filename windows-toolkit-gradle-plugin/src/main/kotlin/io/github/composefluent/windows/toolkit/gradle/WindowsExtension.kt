package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import javax.inject.Inject

typealias NamedNuGetPackageContainer = NamedDomainObjectContainer<KotlinWinRTNuGetPackage>

interface PackageReferencesConfiguration {
    val includeNamespaces: ListProperty<String>
    val includeTypes: ListProperty<String>
    val excludeNamespaces: ListProperty<String>
    val excludeTypes: ListProperty<String>
    val additionExcludeNamespaces: ListProperty<String>
    val metadataInputs: ListProperty<String>
    val windowsSdkDeclared: Property<Boolean>
    val windowsSdkVersion: Property<String>
    val includeWindowsSdkExtensions: Property<Boolean>
    val generateWindowsSdkProjection: Property<Boolean>
    val nugetExecutable: Property<String>
    val nugetCliVersion: Property<String>
    val restoreNuGetPackages: Property<Boolean>
    val useNuGetCliGlobalPackages: Property<Boolean>
    val nugetGlobalPackagesRoots: ListProperty<String>
    val nugetConfigFile: RegularFileProperty
    val nugetConfigDirectory: DirectoryProperty
    val nugetPackages: NamedNuGetPackageContainer

    /** Uses NuGet's normal hierarchy from the selected file's directory and its ancestors. */
    fun nugetConfig(input: Any)

    fun namespace(name: String)

    fun type(name: String)

    fun excludeNamespace(name: String)

    fun excludeType(name: String)

    fun excludeAdditionNamespace(name: String)

    fun winmd(input: Any)

    fun windowsSdk(
        version: String? = null,
        includeExtensions: Boolean = false,
        generateProjection: Boolean = false,
    )

    fun nugetPackage(packageId: String, version: String)

    fun nugetPackage(packageId: String, version: String, action: Action<in KotlinWinRTNuGetPackage>)

    fun nugetPackage(packageId: String, action: Action<in KotlinWinRTNuGetPackage>)
}

abstract class PackageReferencesConfigurationSupport @Inject constructor(
    objects: ObjectFactory,
    private val project: Project,
) : PackageReferencesConfiguration {
    override val includeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val includeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val excludeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val excludeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val additionExcludeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val metadataInputs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val windowsSdkDeclared: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    override val windowsSdkVersion: Property<String> = objects.property(String::class.java)
    override val includeWindowsSdkExtensions: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    override val generateWindowsSdkProjection: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
    override val nugetExecutable: Property<String> = objects.property(String::class.java).convention("nuget")
    override val nugetCliVersion: Property<String> = objects.property(String::class.java).convention("7.3.1")
    override val restoreNuGetPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    override val useNuGetCliGlobalPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    override val nugetGlobalPackagesRoots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val nugetConfigFile: RegularFileProperty = objects.fileProperty()
    override val nugetConfigDirectory: DirectoryProperty = objects.directoryProperty()

    @get:Nested
    override val nugetPackages: NamedNuGetPackageContainer =
        objects.domainObjectContainer(KotlinWinRTNuGetPackage::class.java) { name ->
            objects.newInstance(KotlinWinRTNuGetPackage::class.java, name).also { nugetPackage ->
                nugetPackage.generateProjectionProperty.convention(true)
            }
        }

    override fun namespace(name: String) {
        includeNamespaces.add(name)
    }

    override fun type(name: String) {
        includeTypes.add(name)
    }

    override fun excludeNamespace(name: String) {
        excludeNamespaces.add(name)
    }

    override fun excludeType(name: String) {
        excludeTypes.add(name)
    }

    override fun excludeAdditionNamespace(name: String) {
        additionExcludeNamespaces.add(name)
    }

    override fun winmd(input: Any) {
        metadataInputs.add(input.toString())
    }

    override fun nugetConfig(input: Any) {
        nugetConfigFile.set(project.file(input))
        nugetConfigDirectory.set(project.layout.dir(nugetConfigFile.map { file -> file.asFile.parentFile }))
    }

    override fun windowsSdk(
        version: String?,
        includeExtensions: Boolean,
        generateProjection: Boolean,
    ) {
        windowsSdkDeclared.set(true)
        version?.let(windowsSdkVersion::set)
        includeWindowsSdkExtensions.set(includeExtensions)
        generateWindowsSdkProjection.set(generateProjection)
    }

    override fun nugetPackage(packageId: String, version: String) {
        val versionValue = version
        nugetPackages.create(packageId, Action<KotlinWinRTNuGetPackage> { nugetPackage ->
            nugetPackage.version.set(versionValue)
        })
    }

    override fun nugetPackage(
        packageId: String,
        version: String,
        action: Action<in KotlinWinRTNuGetPackage>,
    ) {
        val versionValue = version
        nugetPackages.create(packageId, Action<KotlinWinRTNuGetPackage> { nugetPackage ->
            nugetPackage.version.set(versionValue)
            action.execute(nugetPackage)
        })
    }

    override fun nugetPackage(packageId: String, action: Action<in KotlinWinRTNuGetPackage>) {
        nugetPackages.create(packageId, action)
    }

}

abstract class WindowsExtension @Inject constructor(
    objects: ObjectFactory,
    private val project: Project,
) {
    /** WinMD, Windows SDK metadata, projection filters and NuGet restore inputs. */
    @get:Nested
    val packageReferences: PackageReferencesConfigurationSupport =
        objects.newInstance(PackageReferencesConfigurationSupport::class.java, project)

    /** Shared CLI control used by package restore and Windows application packaging. */
    val winAppCliExecutable: Property<String> = objects.property(String::class.java).convention("winapp")
    /** NuGet SDK toolchain revision, independent of the Windows API and OS version numbers. */
    val windowsSdkToolsVersion: Property<String> = objects.property(String::class.java)
        .convention(WinAppConfigurationDefaults.WINDOWS_SDK_TOOLS_VERSION)

    val applicationEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    /** Stable namespace owned by this module's generated AppX resource accessor. */
    val appxResourcePackageName: Property<String> = objects.property(String::class.java).convention(
        defaultAppxResourcePackageName(project.name),
    )
    private val applicationConfiguredActions = mutableListOf<() -> Unit>()

    @get:Nested
    val application: WinAppConfiguration = objects.newInstance(WinAppConfiguration::class.java, project)

    fun packageReferences(action: Action<in PackageReferencesConfigurationSupport>) {
        action.execute(packageReferences)
    }

    fun application(action: Action<in WinAppConfiguration>) {
        applicationEnabled.set(true)
        action.execute(application)
        applicationConfiguredActions.forEach { it() }
        applicationConfiguredActions.clear()
    }

    internal fun whenApplicationConfigured(action: () -> Unit) {
        if (applicationEnabled.get()) {
            action()
        } else {
            applicationConfiguredActions += action
        }
    }

    internal val includeNamespaces get() = packageReferences.includeNamespaces
    internal val includeTypes get() = packageReferences.includeTypes
    internal val excludeNamespaces get() = packageReferences.excludeNamespaces
    internal val excludeTypes get() = packageReferences.excludeTypes
    internal val additionExcludeNamespaces get() = packageReferences.additionExcludeNamespaces
    internal val metadataInputs get() = packageReferences.metadataInputs
    internal val windowsSdkDeclared get() = packageReferences.windowsSdkDeclared
    internal val windowsSdkVersion get() = packageReferences.windowsSdkVersion
    internal val includeWindowsSdkExtensions get() = packageReferences.includeWindowsSdkExtensions
    internal val generateWindowsSdkProjection get() = packageReferences.generateWindowsSdkProjection
    internal val nugetExecutable get() = packageReferences.nugetExecutable
    internal val nugetCliVersion get() = packageReferences.nugetCliVersion
    internal val restoreNuGetPackages get() = packageReferences.restoreNuGetPackages
    internal val useNuGetCliGlobalPackages get() = packageReferences.useNuGetCliGlobalPackages
    internal val nugetGlobalPackagesRoots get() = packageReferences.nugetGlobalPackagesRoots
    internal val nugetConfigFile get() = packageReferences.nugetConfigFile
    internal val nugetConfigDirectory get() = packageReferences.nugetConfigDirectory
    internal val nugetPackages get() = packageReferences.nugetPackages
    internal val runtimeAssets get() = application.runtimeAssets
}

abstract class WinAppConfiguration @Inject constructor(
    objects: ObjectFactory,
    project: Project,
) : WinAppOptions(objects, project) {
    @get:Nested
    val variants: NamedDomainObjectContainer<NamedWinAppOptions> =
        objects.domainObjectContainer(NamedWinAppOptions::class.java) { name ->
            objects.newInstance(NamedWinAppOptions::class.java, name, project).also { variant ->
                variant.inheritFrom(this)
            }
        }

    fun variants(action: Action<in NamedDomainObjectContainer<NamedWinAppOptions>>) {
        action.execute(variants)
    }
}

abstract class NamedWinAppOptions @Inject constructor(
    private val applicationName: String,
    objects: ObjectFactory,
    project: Project,
) : WinAppOptions(objects, project), Named {
    override fun getName(): String = applicationName

    /** Exact Kotlin variant bound to this named application's task graph. */
    val variantName: Property<String> = objects.property(String::class.java)

    fun variant(name: String) {
        variantName.set(name)
    }
}

abstract class WinAppOptions @Inject constructor(
    objects: ObjectFactory,
    private val project: Project,
) {
    internal val runTaskRegistrations = mutableListOf<WinAppRunTaskRegistration>()
    private var runTaskRegistrar: ((WinAppRunTaskRegistration) -> Unit)? = null

    /**
     * Controls whether the application has an AppX/MSIX package identity. Applications are
     * packaged by default; use [WindowsPackageType.None] for a loose unpackaged layout.
     */
    val packageType: Property<WindowsPackageType> =
        objects.property(WindowsPackageType::class.java).convention(WindowsPackageType.Packaged)

    /** Minimum supported Windows version. Required when staging an AppX manifest. */
    val minWindowsVersion: Property<String> = objects.property(String::class.java)
    /** Defaults to the module's selected Windows SDK; may describe a separately tested OS version. */
    val maxVersionTested: Property<String> = objects.property(String::class.java)
    val mainClass: Property<String> = objects.property(String::class.java)
    val console: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val generateProjectPri: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val projectPriIndexName: Property<String> = objects.property(String::class.java).convention("")
    val projectPriInitialPath: Property<String> = objects.property(String::class.java).convention("")
    val projectPriDefaultLanguage: Property<String> = objects.property(String::class.java).convention("")
    val projectPriDefaultQualifiers: ListProperty<String> =
        objects.listProperty(String::class.java).convention(listOf("scale-200", "contrast-standard"))
    val enableDefaultProjectPriResources: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val appxManifestFiles: ConfigurableFileCollection = objects.fileCollection()
    val projectPriResourceFiles: ConfigurableFileCollection = objects.fileCollection()
    val projectPriLayoutFiles: ConfigurableFileCollection = objects.fileCollection()
    val projectPriContentFiles: ConfigurableFileCollection = objects.fileCollection()
    val projectPriEmbedFiles: ConfigurableFileCollection = objects.fileCollection()
    val packagePayloadFiles: ConfigurableFileCollection = objects.fileCollection()
    /** Explicit files supplied to the Windows package asset staging task. */
    val runtimeAssets: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val projectPriTargetPaths: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    val projectPriExcludedFromBuildPaths: SetProperty<String> =
        objects.setProperty(String::class.java).convention(emptySet())
    val makePriExecutable: Property<String> = objects.property(String::class.java).convention("")
    val generatePackage: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val packageOutputFile: RegularFileProperty = objects.fileProperty()
    val makeAppxExecutable: Property<String> = objects.property(String::class.java).convention("")
    val verifyPackage: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val signPackage: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val signedPackageOutputFile: RegularFileProperty = objects.fileProperty()
    val signToolExecutable: Property<String> = objects.property(String::class.java).convention("")
    val signingCertificateThumbprint: Property<String> = objects.property(String::class.java).convention("")
    val signingCertificateFile: RegularFileProperty = objects.fileProperty()
    val signingCertificatePassword: Property<String> = objects.property(String::class.java).convention("")
    val signingTimestampUrl: Property<String> = objects.property(String::class.java).convention("")
    val signingHashAlgorithm: Property<String> = objects.property(String::class.java).convention("SHA256")
    val installPackage: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val installPackageFile: RegularFileProperty = objects.fileProperty()
    /** Optional framework packages supplied to Add-AppxPackage during local installation. */
    val dependencyPackageFiles: ConfigurableFileCollection = objects.fileCollection()
    val installPowerShellExecutable: Property<String> = objects.property(String::class.java).convention("powershell.exe")
    val installForceApplicationShutdown: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    /** Controls whether the application carries a JVM image or expects one on the machine. */
    val jvmRuntimeMode: Property<WinAppJvmRuntimeMode> =
        objects.property(WinAppJvmRuntimeMode::class.java).convention(WinAppJvmRuntimeMode.Bundled)
    /** Optional prebuilt image. When absent, the plugin creates one with the selected JDK's jlink. */
    val jvmRuntimeImage: DirectoryProperty = objects.directoryProperty()
    /** JVM home used only when [jvmRuntimeMode] is [WinAppJvmRuntimeMode.External]. */
    val externalJvmHome: DirectoryProperty = objects.directoryProperty()
    /** Modules used by jlink for the default bundled image. */
    val jvmRuntimeModules: ListProperty<String> = objects.listProperty(String::class.java).convention(
        listOf(
            "java.base",
            "java.desktop",
            "java.logging",
            "java.management",
            "java.naming",
            "jdk.crypto.ec",
            "jdk.management",
            "jdk.unsupported",
        ),
    )
    /** Java major version used for JNI headers, jlink, and the runtime image contract. */
    val jvmToolchainVersion: Property<Int> = objects.property(Int::class.java).convention(25)
    /** Windows App SDK deployment mode; [WindowsAppSdkDeployment.Auto] selects it from the app graph. */
    val windowsAppSdkDeployment: Property<WindowsAppSdkDeployment> =
        objects.property(WindowsAppSdkDeployment::class.java)
            .convention(WindowsAppSdkDeployment.Auto)

    internal fun inheritFrom(defaults: WinAppOptions) {
        packageType.convention(defaults.packageType)
        minWindowsVersion.convention(defaults.minWindowsVersion)
        maxVersionTested.convention(defaults.maxVersionTested)
        mainClass.convention(defaults.mainClass)
        console.convention(defaults.console)
        generateProjectPri.convention(defaults.generateProjectPri)
        projectPriIndexName.convention(defaults.projectPriIndexName)
        projectPriInitialPath.convention(defaults.projectPriInitialPath)
        projectPriDefaultLanguage.convention(defaults.projectPriDefaultLanguage)
        projectPriDefaultQualifiers.convention(defaults.projectPriDefaultQualifiers)
        enableDefaultProjectPriResources.convention(defaults.enableDefaultProjectPriResources)
        appxManifestFiles.convention(defaults.appxManifestFiles)
        projectPriResourceFiles.convention(defaults.projectPriResourceFiles)
        projectPriLayoutFiles.convention(defaults.projectPriLayoutFiles)
        projectPriContentFiles.convention(defaults.projectPriContentFiles)
        projectPriEmbedFiles.convention(defaults.projectPriEmbedFiles)
        packagePayloadFiles.convention(defaults.packagePayloadFiles)
        runtimeAssets.convention(defaults.runtimeAssets)
        projectPriTargetPaths.convention(defaults.projectPriTargetPaths)
        projectPriExcludedFromBuildPaths.convention(defaults.projectPriExcludedFromBuildPaths)
        makePriExecutable.convention(defaults.makePriExecutable)
        generatePackage.convention(defaults.generatePackage)
        packageOutputFile.convention(defaults.packageOutputFile)
        makeAppxExecutable.convention(defaults.makeAppxExecutable)
        verifyPackage.convention(defaults.verifyPackage)
        signPackage.convention(defaults.signPackage)
        signedPackageOutputFile.convention(defaults.signedPackageOutputFile)
        signToolExecutable.convention(defaults.signToolExecutable)
        signingCertificateThumbprint.convention(defaults.signingCertificateThumbprint)
        signingCertificateFile.convention(defaults.signingCertificateFile)
        signingCertificatePassword.convention(defaults.signingCertificatePassword)
        signingTimestampUrl.convention(defaults.signingTimestampUrl)
        signingHashAlgorithm.convention(defaults.signingHashAlgorithm)
        installPackage.convention(defaults.installPackage)
        installPackageFile.convention(defaults.installPackageFile)
        dependencyPackageFiles.convention(defaults.dependencyPackageFiles)
        installPowerShellExecutable.convention(defaults.installPowerShellExecutable)
        installForceApplicationShutdown.convention(defaults.installForceApplicationShutdown)
        jvmRuntimeMode.convention(defaults.jvmRuntimeMode)
        jvmRuntimeImage.convention(defaults.jvmRuntimeImage)
        externalJvmHome.convention(defaults.externalJvmHome)
        jvmRuntimeModules.convention(defaults.jvmRuntimeModules)
        jvmToolchainVersion.convention(defaults.jvmToolchainVersion)
        windowsAppSdkDeployment.convention(defaults.windowsAppSdkDeployment)
    }

    internal fun bindRunTasks(registrar: (WinAppRunTaskRegistration) -> Unit) {
        runTaskRegistrar = registrar
        runTaskRegistrations.forEach(registrar)
        runTaskRegistrations.clear()
    }

    fun appxManifest(input: Any) {
        appxManifestFiles.from(input)
    }

    fun runtimeAsset(input: Any) {
        runtimeAssets.add(input.toString())
    }

    fun frameworkDependent() {
        windowsAppSdkDeployment.set(WindowsAppSdkDeployment.FrameworkDependent)
    }

    fun selfContained() {
        windowsAppSdkDeployment.set(WindowsAppSdkDeployment.SelfContained)
    }

    fun bundledJvmRuntime(image: Any? = null) {
        jvmRuntimeMode.set(WinAppJvmRuntimeMode.Bundled)
        if (image != null) {
            jvmRuntimeImage.set(project.layout.dir(project.provider { project.file(image) }))
        }
    }

    fun externalJvmRuntime(home: Any) {
        jvmRuntimeMode.set(WinAppJvmRuntimeMode.External)
        externalJvmHome.set(project.layout.dir(project.provider { project.file(home) }))
    }

    fun jvmToolchain(version: Int) {
        require(version > 0) { "JVM toolchain version must be positive." }
        jvmToolchainVersion.set(version)
    }

    fun runTask(name: String) {
        runTask(name, Action {})
    }

    fun runTask(name: String, action: Action<in RunWinAppHostTask>) {
        val registration = WinAppRunTaskRegistration(name, action)
        runTaskRegistrar?.invoke(registration) ?: run { runTaskRegistrations += registration }
    }

    fun projectPriResource(input: Any) {
        projectPriResourceFiles.from(input)
    }

    fun projectPriResource(input: Any, targetPath: String) {
        projectPriResource(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun projectPriPage(input: Any) {
        projectPriLayoutFiles.from(input)
    }

    fun projectPriPage(input: Any, targetPath: String) {
        projectPriPage(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun projectPriApplicationDefinition(input: Any) {
        projectPriLayoutFiles.from(input)
    }

    fun projectPriApplicationDefinition(input: Any, targetPath: String) {
        projectPriApplicationDefinition(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun projectPriContent(input: Any) {
        projectPriContentFiles.from(input)
    }

    fun projectPriContent(input: Any, targetPath: String) {
        projectPriContent(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun projectPriImage(input: Any) {
        projectPriContentFiles.from(input)
    }

    fun projectPriImage(input: Any, targetPath: String) {
        projectPriImage(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun projectPriEmbedFile(input: Any) {
        projectPriEmbedFiles.from(input)
    }

    fun projectPriEmbedFile(input: Any, targetPath: String) {
        projectPriEmbedFile(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun packagePayload(input: Any) {
        packagePayloadFiles.from(input)
    }

    fun dependencyPackage(input: Any) {
        dependencyPackageFiles.from(input)
    }

    fun packagePayload(input: Any, targetPath: String) {
        packagePayload(input)
        projectPriTargetPaths.put(project.file(input).toPath().toAbsolutePath().normalize().toString(), targetPath)
    }

    fun projectPriExcludedFromBuild(input: Any) {
        projectPriExcludedFromBuildPaths.add(project.file(input).toPath().toAbsolutePath().normalize().toString())
    }

    fun projectPriDefaultQualifier(qualifier: String) {
        projectPriDefaultQualifiers.add(qualifier)
    }
}

internal fun defaultAppxResourcePackageName(projectName: String): String {
    val module = projectName
        .map { character -> if (character.isLetterOrDigit()) character else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "module" }
    return "io.github.composefluent.winrt.appx.$module"
}

enum class WinAppJvmRuntimeMode {
    Bundled,
    External,
}

enum class WindowsAppSdkDeployment {
    Auto,
    None,
    FrameworkDependent,
    SelfContained,
}

internal data class WinAppRunTaskRegistration(
    val name: String,
    val action: Action<in RunWinAppHostTask>,
)

enum class WindowsPackageType {
    Packaged,
    None,
}

abstract class KotlinWinRTNuGetPackage @Inject constructor(
    val packageId: String,
    objects: ObjectFactory,
) : Named {
    val version: Property<String> = objects.property(String::class.java)
    internal val generateProjectionProperty: Property<Boolean> = objects.property(Boolean::class.java)
    var generateProjection: Boolean
        get() = generateProjectionProperty.get()
        set(value) = generateProjectionProperty.set(value)

    override fun getName(): String = packageId
}
