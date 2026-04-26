package io.github.kitectlab.winrt.gradle

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import javax.inject.Inject

typealias NamedNuGetPackageContainer = NamedDomainObjectContainer<KotlinWinRtNuGetPackage>

interface BaseWinRtExtension {
    val includeNamespaces: ListProperty<String>
    val includeTypes: ListProperty<String>
    val excludeNamespaces: ListProperty<String>
    val excludeTypes: ListProperty<String>
    val additionExcludeNamespaces: ListProperty<String>
    val metadataInputs: ListProperty<String>
    val windowsSdkVersion: Property<String>
    val includeWindowsSdkExtensions: Property<Boolean>
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

    fun windowsSdk(version: String? = null, includeExtensions: Boolean = false)

    fun nugetPackage(packageId: String, version: String)

    fun nugetPackage(packageId: String, action: Action<in KotlinWinRtNuGetPackage>)

    fun windowsAppSdk(
        winuiVersion: String,
        foundationVersion: String = winuiVersion,
        interactiveExperiencesVersion: String = winuiVersion,
    )
}

abstract class BaseWinRtExtensionSupport @Inject constructor(
    objects: ObjectFactory,
) : BaseWinRtExtension {
    override val includeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val includeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val excludeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val excludeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val additionExcludeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val metadataInputs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val windowsSdkVersion: Property<String> = objects.property(String::class.java)
    override val includeWindowsSdkExtensions: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    override val nugetExecutable: Property<String> = objects.property(String::class.java).convention("nuget")
    override val nugetCliVersion: Property<String> = objects.property(String::class.java).convention("7.3.1")
    override val restoreNuGetPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    override val useNuGetCliGlobalPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    override val nugetGlobalPackagesRoots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    override val runtimeAssets: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    @get:Nested
    override val nugetPackages: NamedNuGetPackageContainer =
        objects.domainObjectContainer(KotlinWinRtNuGetPackage::class.java) { name ->
            objects.newInstance(KotlinWinRtNuGetPackage::class.java, name)
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

    override fun windowsSdk(version: String?, includeExtensions: Boolean) {
        version?.let(windowsSdkVersion::set)
        includeWindowsSdkExtensions.set(includeExtensions)
    }

    override fun nugetPackage(packageId: String, version: String) {
        val versionValue = version
        nugetPackages.create(packageId, Action<KotlinWinRtNuGetPackage> { nugetPackage ->
            nugetPackage.version.set(versionValue)
        })
    }

    override fun nugetPackage(packageId: String, action: Action<in KotlinWinRtNuGetPackage>) {
        nugetPackages.create(packageId, action)
    }

    override fun windowsAppSdk(
        winuiVersion: String,
        foundationVersion: String,
        interactiveExperiencesVersion: String,
    ) {
        setNuGetPackageVersion("Microsoft.WindowsAppSDK.Foundation", foundationVersion)
        setNuGetPackageVersion("Microsoft.WindowsAppSDK.InteractiveExperiences", interactiveExperiencesVersion)
        setNuGetPackageVersion("Microsoft.WindowsAppSDK.WinUI", winuiVersion)
        includeNamespaces.add("Microsoft")
        includeTypes.addAll(
            listOf(
                "Windows.UI.Xaml.Interop.Type",
                "Windows.UI.Xaml.Interop.NotifyCollectionChangedAction",
                "Windows.UI.Xaml.Markup.ContentPropertyAttribute",
                "Windows.UI.Xaml.StyleTypedPropertyAttribute",
                "Windows.UI.Xaml.TemplatePartAttribute",
                "Windows.UI.Xaml.TemplateVisualStateAttribute",
                "Windows.UI.Xaml.Data.BindableAttribute",
                "Windows.UI.Xaml.Markup.FullXamlMetadataProviderAttribute",
                "Windows.UI.Xaml.Markup.MarkupExtensionReturnTypeAttribute",
                "Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute",
                "Windows.UI.Xaml.Media.Animation.IndependentlyAnimatableAttribute",
            ),
        )
        excludeTypes.addAll(
            listOf(
                "Microsoft.UI.Xaml.Controls.WebView2",
                "Microsoft.UI.Xaml.Controls.IWebView",
                "Microsoft.UI.Xaml.Automation.Peers.IWebView",
                "Microsoft.UI.Xaml.Automation.Peers.WebView",
            ),
        )
        excludeNamespaces.addAll(listOf("Windows", "Windows.UI.Xaml.Media.Animation"))
    }

    private fun setNuGetPackageVersion(packageId: String, version: String) {
        val existing = nugetPackages.findByName(packageId)
        val target = existing ?: nugetPackages.create(packageId)
        target.version.set(version)
    }
}

abstract class WinRtExtension @Inject constructor(
    objects: ObjectFactory,
) : BaseWinRtExtensionSupport(objects) {
    val applicationEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    private val applicationConfiguredActions = mutableListOf<() -> Unit>()

    @get:Nested
    val application: WinRtApplicationOptions = objects.newInstance(WinRtApplicationOptions::class.java)

    fun application(action: Action<in WinRtApplicationOptions>) {
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

abstract class WinRtApplicationOptions @Inject constructor()

abstract class KotlinWinRtNuGetPackage @Inject constructor(
    val packageId: String,
    objects: ObjectFactory,
) : Named {
    val version: Property<String> = objects.property(String::class.java)

    override fun getName(): String = packageId
}
