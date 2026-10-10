import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.util.Properties

// Like CsWinRT's WinRT.SourceGenerator.props, compile shared sources separately
// against each compiler API. Only the final compiler adaptation has versioned sources.
val versioned = projectDir.name.startsWith("kotlin-")
val sourceDirectory = if (versioned) projectDir.parentFile else projectDir
val lowering = sourceDirectory.name == "callsite-lowering"
val compilerDirectory = if (lowering) sourceDirectory.parentFile else sourceDirectory
val versions = Properties().apply {
    compilerDirectory.resolve("compiler-versions.properties").inputStream().use(::load)
}
val defaultCompilerVersion = versions.getProperty("default")
val targetCompilerVersion = if (versioned) projectDir.name.removePrefix("kotlin-") else defaultCompilerVersion
require(targetCompilerVersion in versions.getProperty("supported").split(',')) {
    "Unregistered Kotlin compiler version: $targetCompilerVersion"
}
val suffix = if (targetCompilerVersion == defaultCompilerVersion) "" else "-kotlin-$targetCompilerVersion"
val moduleName = if (lowering) "callsite-lowering" else "winrt-compiler-plugin"
val artifactName = moduleName + suffix
val compilerProject = rootProject.findProject(":winrt-compiler-plugin") ?: rootProject
val compilerPath = compilerProject.path.takeUnless { it == ":" }.orEmpty()
val loweringPath = "$compilerPath:callsite-lowering" +
    if (targetCompilerVersion == defaultCompilerVersion) "" else ":lowering-kotlin-${targetCompilerVersion.replace('.', '-')}"
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

description = if (lowering) {
    "WinRT call-site lowering for Kotlin $targetCompilerVersion"
} else {
    "WinRT and WinUI compiler plugin for Kotlin $targetCompilerVersion"
}

extensions.configure<KotlinJvmProjectExtension> {
    sourceSets.named("main") {
        kotlin.setSrcDirs(listOf(sourceDirectory.resolve("src/main/kotlin"), sourceDirectory.resolve("src/compiler-compat/$targetCompilerVersion/kotlin")))
        resources.setSrcDirs(listOf(sourceDirectory.resolve("src/main/resources")))
    }
    sourceSets.named("test") {
        kotlin.setSrcDirs(listOf(sourceDirectory.resolve("src/test/kotlin")))
        resources.setSrcDirs(listOf(sourceDirectory.resolve("src/test/resources")))
    }
    if (!lowering) compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}

dependencies {
    add("implementation", project("$compilerPath:callsite-contract"))
    add("compileOnly", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$targetCompilerVersion")
    add("testImplementation", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$targetCompilerVersion")
    add("testImplementation", libs.findLibrary("junit").get())
    if (lowering) {
        add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit:$defaultCompilerVersion")
    } else {
        add("implementation", project(loweringPath))
        add("implementation", project(":winrt-authoring"))
        add("implementation", project(":winrt-runtime"))
        add("implementation", project(":winrt-metadata"))
        add("implementation", libs.findLibrary("kotlinpoet").get())
        add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
    }
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set(artifactName)
    manifest.attributes("Kotlin-WinRT-Compiler-Version" to targetCompilerVersion)
}
extensions.configure<MavenPublishBaseExtension> {
    coordinates(artifactId = artifactName)
}

if (!lowering) {
    val callSiteTestPluginClasspath = configurations.create("callSiteTestPluginClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(callSiteTestPluginClasspath.name, project(loweringPath))
    tasks.withType<Test>().configureEach {
        workingDir(sourceDirectory)
        val fullPluginJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
        val pluginClasspath = files(callSiteTestPluginClasspath)
        inputs.file(fullPluginJar).withPropertyName("fullPluginJar")
        inputs.files(pluginClasspath).withPropertyName("callSiteTestPluginClasspath")
        doFirst {
            systemProperty("winrt.test.callsitePluginClasspath", pluginClasspath.asPath)
            systemProperty("winrt.test.fullPluginJar", fullPluginJar.get().asFile.absolutePath)
        }
    }
}
