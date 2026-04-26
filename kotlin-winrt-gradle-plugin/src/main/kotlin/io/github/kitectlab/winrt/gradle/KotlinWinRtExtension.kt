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

abstract class KotlinWinRtExtension @Inject constructor(
    objects: ObjectFactory,
) {
    val includeNamespaces: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val includeTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val metadataInputs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val windowsSdkVersion: Property<String> = objects.property(String::class.java)
    val includeWindowsSdkExtensions: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val nugetExecutable: Property<String> = objects.property(String::class.java).convention("nuget")
    val restoreNuGetPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val useNuGetCliGlobalPackages: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val nugetGlobalPackagesRoots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    @get:Nested
    val nugetPackages: NamedNuGetPackageContainer =
        objects.domainObjectContainer(KotlinWinRtNuGetPackage::class.java) { name ->
            objects.newInstance(KotlinWinRtNuGetPackage::class.java, name)
        }

    fun namespace(name: String) {
        includeNamespaces.add(name)
    }

    fun type(name: String) {
        includeTypes.add(name)
    }

    fun winmd(input: Any) {
        metadataInputs.add(input.toString())
    }

    fun windowsSdk(version: String? = null, includeExtensions: Boolean = false) {
        version?.let(windowsSdkVersion::set)
        includeWindowsSdkExtensions.set(includeExtensions)
    }

    fun nugetPackage(packageId: String, version: String) {
        val versionValue = version
        nugetPackages.create(packageId, Action<KotlinWinRtNuGetPackage> {
            this.version.set(versionValue)
        })
    }

    fun nugetPackage(packageId: String, action: Action<in KotlinWinRtNuGetPackage>) {
        nugetPackages.create(packageId, action)
    }
}

abstract class KotlinWinRtNuGetPackage @Inject constructor(
    val packageId: String,
    objects: ObjectFactory,
) : Named {
    val version: Property<String> = objects.property(String::class.java)

    override fun getName(): String = packageId
}
