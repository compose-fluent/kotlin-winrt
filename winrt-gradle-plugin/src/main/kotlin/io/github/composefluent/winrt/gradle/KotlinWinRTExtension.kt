package io.github.composefluent.winrt.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
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

interface BaseWinRTExtension {
    val includeNamespaces: ListProperty<String>
    val includeTypes: ListProperty<String>
    val excludeNamespaces: ListProperty<String>
    val excludeTypes: ListProperty<String>
    val additionExcludeNamespaces: ListProperty<String>
    val metadataInputs: ListProperty<String>
    val windowsSdkVersion: Property<String>
    val includeWindowsSdkExtensions: Property<Boolean>
    val generateWindowsSdkProjection: Property<Boolean>
    val nugetExecutable: Property<String>
    val nugetCliVersion: Property<String>
    val restoreNuGetPackages: Property<Boolean>
    val useNuGetCliGlobalPackages: Property<Boolean>
    val nugetGlobalPackagesRoots: ListProperty<String>
    val nugetPackages: NamedNuGetPackageContainer
    val runtimeAssets: ListProperty<String>

    fun namespace(name: String)

    fun type(name: String)

    fun excludeNamespace(name: String)

    fun excludeType(name: String)

    fun excludeAdditionNamespace(name: String)

    fun winmd(input: Any)

    fun runtimeAsset(input: Any)

    fun windowsSdk(
        version: String? = null,
        includeExtensions: Boolean = false,
        generateProjection: Boolean = false,
    )

    fun nugetPackage(packageId: String, version: String)

    fun nugetPackage(packageId: String, version: String, action: Action<in KotlinWinRTNuGetPackage>)

    fun nugetPackage(packageId: String, action: Action<in KotlinWinRTNuGetPackage>)
}

abstract class BaseWinRTExtensionSupport @Inject constructor(
    objects: ObjectFactory,
) : BaseWinRTExtension {
    override val includeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val includeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val excludeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val excludeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val additionExcludeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val metadataInputs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val windowsSdkVersion: Property<String> = objects.property(String::class.java)
    override val includeWindowsSdkExtensions: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    override val generateWindowsSdkProjection: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
    override val nugetExecutable: Property<String> = objects.property(String::class.java).convention("nuget")
    override val nugetCliVersion: Property<String> = objects.property(String::class.java).convention("7.3.1")
    override val restoreNuGetPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    override val useNuGetCliGlobalPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    override val nugetGlobalPackagesRoots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val runtimeAssets: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

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

    override fun runtimeAsset(input: Any) {
        runtimeAssets.add(input.toString())
    }

    override fun windowsSdk(
        version: String?,
        includeExtensions: Boolean,
        generateProjection: Boolean,
    ) {
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

abstract class WinRTExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
) : BaseWinRTExtensionSupport(objects) {
    val applicationEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    private val applicationConfiguredActions = mutableListOf<() -> Unit>()

    @get:Nested
    val application: WinRTApplicationOptions = objects.newInstance(WinRTApplicationOptions::class.java, project)

    fun application(action: Action<in WinRTApplicationOptions>) {
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
}

abstract class WinRTApplicationOptions @Inject constructor(
    objects: ObjectFactory,
    private val project: Project,
) {
    val packageMode: Property<WinRTApplicationPackageMode> =
        objects.property(WinRTApplicationPackageMode::class.java).convention(WinRTApplicationPackageMode.Unpackaged)
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
    val installPowerShellExecutable: Property<String> = objects.property(String::class.java).convention("powershell.exe")
    val installForceApplicationShutdown: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    fun appxManifest(input: Any) {
        appxManifestFiles.from(input)
    }

    fun packaged() {
        packageMode.set(WinRTApplicationPackageMode.Packaged)
    }

    fun unpackaged() {
        packageMode.set(WinRTApplicationPackageMode.Unpackaged)
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

enum class WinRTApplicationPackageMode {
    Unpackaged,
    Packaged,
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
