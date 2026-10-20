package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irString
import io.github.composefluent.winrt.compiler.xaml.JVM_XAML_DEVELOPMENT_ACCESSORS
import io.github.composefluent.winrt.compiler.xaml.lowerNativeXamlDevelopmentAccessors
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class NativeProjectionRegistrationLoweringTest {
    @Test fun registration_edges_follow_executed_bodies_and_concrete_type_arguments() {
        // Exercise the Native IR pass with the JVM backend for fast, observable execution.
        // Actual Native optimized linking is additionally covered by the KMP WinUI fixture.
        val root = Files.createTempDirectory("native-demand-registration").toFile()
        try {
            val plugin = File(root, "test-plugin.jar")
            val classes = File(NativeRegistrationTestRegistrar::class.java.protectionDomain.codeSource.location.toURI())
            JarOutputStream(plugin.outputStream()).use { jar ->
                classes.walkTopDown().filter { it.isFile && it.name.startsWith("NativeRegistrationTestRegistrar") }.forEach {
                    jar.putNextEntry(JarEntry(it.relativeTo(classes).invariantSeparatorsPath))
                    jar.write(it.readBytes()); jar.closeEntry()
                }
                jar.putNextEntry(JarEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar"))
                jar.write(NativeRegistrationTestRegistrar::class.java.name.toByteArray()); jar.closeEntry()
            }
            val input = File(root, "Use.kt").apply { writeText("""
                package demand
                const val UNUSED_NAME = "Demand.Unused"
                val registrations = mutableSetOf<String>()
                fun mark(name: String) { registrations.add(name) }
                open class Base
                class Used : Base() { object Metadata { val instance = Used(); fun value() = 1 } }
                enum class Choice { A; object Metadata { val values = Choice.entries } }
                class GenericOnly { object ComposableFactory }
                class NamedOnly { object ActivationFactory { fun acquire() = 1 } }
                class XamlOnly { object Metadata }
                class MetadataOnly { companion object { fun typedCall() = 1 } }
                class RuntimeOwned
                class Unused
                open class SignatureOnly
                interface Instance { fun read(): Int; fun unusedInstance(): Int; var value: Int }
                open class InstanceBase : Instance {
                    override fun read() = 1
                    override fun unusedInstance() = 0
                    override var value = 1
                }
                class BusinessDerived : InstanceBase() { override fun read() = super.read() }
                class StaticOnly { companion object {
                    fun answer(): Int = 42
                    var setting: Int = 1
                    fun referencedOnly(): Int = 7
                    fun unused(): Int = 0
                } }
                fun unused(): Unused = Unused()
                inline fun <reified T> generic() = T::class.simpleName
            """.trimIndent()) }
            // Keep callers in a different file: Native file-private helper references must
            // never cross this boundary. The pass emits direct registration expressions.
            val calls = File(root, "Calls.kt").apply { writeText("""
                package demand
                fun <T : SignatureOnly> signature(value: T): T = value
                fun staticOnly(): Int = StaticOnly.answer()
                @Suppress("$JVM_XAML_DEVELOPMENT_ACCESSORS")
                private fun sdkDevelopmentMetadata() {
                    StaticOnly.unused()
                    error("Native must omit JVM SDK development callbacks")
                }
                fun constructorReference() = ::StaticOnly
                fun factoryReference() = NamedOnly.ActivationFactory.acquire()
                fun factoryObject() = GenericOnly.ComposableFactory
                fun typedMetadata() = io.github.composefluent.winrt.runtime.WinUIXamlTypeDefinition(
                    type = MetadataOnly::class, name = "Demand.MetadataOnly", baseName = "Demand.SignatureOnly",
                    members = listOf(io.github.composefluent.winrt.runtime.WinUIXamlMemberDefinition(
                        name = "Value", typeName = "Demand.GenericOnly", get = { MetadataOnly.typedCall() })))
                fun typedRegistration() = io.github.composefluent.winrt.runtime.Projections.registerAuthoredRuntimeClassType(
                    MetadataOnly::class, "Demand.MetadataOnly", "Demand.SignatureOnly")
                fun xamlStartup() {
                    XamlOnly::class
                    RuntimeOwned::class
                }
                fun instanceReferences(value: Instance): Int {
                    val call = value::read
                    val property = value::value
                    property.set(4)
                    return call() + property.get()
                }
                fun staticReferences(): Int {
                    val function = StaticOnly::referencedOnly
                    val property = StaticOnly::setting
                    property.set(3)
                    return property.get() + function()
                }
                fun run(): Set<String> {
                    sdkDevelopmentMetadata()
                    check(staticOnly() == 42)
                    xamlStartup()
                    Used.Metadata.value()
                    generic<List<GenericOnly>>()
                    generic<List<Choice>>()
                    check(Choice.Metadata.values.size == 1)
                    val name = "Demand.NamedOnly"
                    check(name.isNotEmpty())
                    return registrations.toSet()
                }
            """.trimIndent()) }
            val runtime = File(root, "Registry.kt").apply { writeText("""
                package io.github.composefluent.winrt.runtime
                class WinUIXamlTypeDefinition(val type: kotlin.reflect.KClass<*>, val name: String,
                    val baseName: String, val members: List<WinUIXamlMemberDefinition>)
                class WinUIXamlMemberDefinition(val name: String, val typeName: String, val get: () -> Any?)
                object Projections {
                    fun registerAuthoredRuntimeClassType(type: kotlin.reflect.KClass<*>, name: String,
                        baseName: String? = null) = Unit
                }
                fun registerGeneratedProjectionTypeIndex(type: kotlin.reflect.KClass<*>, name: String,
                    kind: String, base: String, iid: String) { demand.mark(type.simpleName!!) }
            """.trimIndent()) }
            val output = File(root, "classes")
            val messages = ByteArrayOutputStream()
            val stdlib = File(Unit::class.java.protectionDomain.codeSource.location.toURI())
            val plugins = listOf(plugin.absolutePath, System.getProperty("winrt.test.fullPluginJar"))
            val exit = PrintStream(messages).use { K2JVMCompiler().exec(it,
                "-no-stdlib", "-no-reflect", "-classpath", stdlib.absolutePath,
                "-Xplugin=${plugins.joinToString(",")}", "-d", output.absolutePath,
                input.absolutePath, calls.absolutePath, runtime.absolutePath) }
            assertEquals(messages.toString(), ExitCode.OK, exit)
            URLClassLoader(arrayOf(output.toURI().toURL()), javaClass.classLoader).use { loader ->
                assertEquals("Demand.Unused", loader.loadClass("demand.UseKt").getField("UNUSED_NAME").get(null))
                val names = loader.loadClass("demand.CallsKt").getMethod("run").invoke(null) as Set<*>
                assertTrue(names.toString(), names.containsAll(listOf("Used", "Base", "GenericOnly", "NamedOnly", "Choice", "XamlOnly")))
                assertFalse("Runtime-owned XAML types need no generated Metadata: $names", "RuntimeOwned" in names)
                assertFalse("An unexecuted body must not eagerly register its types: $names", "Unused" in names)
            }
        } finally { root.deleteRecursively() }
    }
}

@OptIn(ExperimentalCompilerApi::class, UnsafeDuringIrConstructionAPI::class)
class NativeRegistrationTestRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true
    override val pluginId = "native-registration-test"
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                val entries = listOf("Base", "Used", "GenericOnly", "NamedOnly", "Unused", "Choice", "SignatureOnly", "StaticOnly", "Instance", "InstanceBase", "XamlOnly", "MetadataOnly").map {
                    KotlinWinRTProjectionRegistrarEntry("demand.$it", "Demand.$it", if (it == "Choice") "Enum" else "RuntimeClass", "",
                        if (it in listOf("Used", "Choice", "XamlOnly")) "demand.$it.Metadata" else "", "")
                }
                check("Demand.StaticOnly\tunused" in NativeProjectionUseCollector(entries).collectStaticMembers(moduleFragment))
                lowerNativeXamlDevelopmentAccessors(moduleFragment, pluginContext)
                val demand = NativeProjectionUseCollector(entries).collect(moduleFragment, includeDeclarations = true)
                    .map { it.projectedTypeName }.toSet()
                check(demand.containsAll(listOf("Demand.SignatureOnly", "Demand.StaticOnly", "Demand.GenericOnly", "Demand.Base"))) {
                    "Semantic demand omitted signature, companion, generic or base uses: $demand"
                }
                val collector = NativeProjectionUseCollector(entries)
                val nameBasedTypes = collector.collectNameBasedTypes(moduleFragment)
                // CsWinRT Discover runs before Emit. Simulate the ABI identity constant
                // introduced by Kotlin call-site lowering after discovery, while retaining
                // actual typed member edges from the resulting executable bodies.
                val caller = moduleFragment.files.flatMap { it.declarations }.filterIsInstance<IrSimpleFunction>()
                    .single { it.name.asString() == "staticOnly" }
                (caller.body as IrBlockBody).statements.add(0,
                    DeclarationIrBuilder(pluginContext, caller.symbol).irString("Demand.Used"))
                check("Demand.Used" in collector.collectNameBasedTypes(moduleFragment))
                val members = collector.collectStaticMembers(moduleFragment, nameBasedTypes)
                check("Demand.Used\t*" !in members) { "Lowering metadata must not become name-based demand: $members" }
                check("Demand.StaticOnly\tanswer" in members) { "Static member demand missing: $members" }
                check("Demand.StaticOnly\tsetting" in members && "Demand.StaticOnly\treferencedOnly" in members) {
                    "Callable/property references must retain their member family: $members"
                }
                check("Demand.StaticOnly\tunused" !in members) { "Declarations must not root unused members: $members" }
                check("Demand.MetadataOnly\ttypedCall" in members) { "Typed metadata callbacks must still be scanned: $members" }
                check(listOf("Demand.MetadataOnly\t*", "Demand.SignatureOnly\t*", "Demand.GenericOnly\t*").none { it in members }) {
                    "Typed metadata labels must not root every static member: $members"
                }
                check("Demand.NamedOnly\t*" in members) { "Ordinary name-based uses must stay conservative: $members" }
                val (instances, exposed) = collector.collectInstanceMembers(moduleFragment, nameBasedTypes)
                check("Demand.Used\t*" !in instances) { "Lowering metadata must not root the instance surface: $instances" }
                check(instances.containsAll(listOf("Demand.Instance\tread", "Demand.Instance\tvalue"))) { "$instances" }
                check("Demand.Instance\tunusedInstance" !in instances) { "Unused declarations are not calls: $instances" }
                check(listOf("Demand.MetadataOnly\t*", "Demand.SignatureOnly\t*", "Demand.GenericOnly\t*").none { it in instances }) {
                    "Typed metadata labels must not root every instance member: $instances"
                }
                check("Demand.NamedOnly\t*" in instances) { "Ordinary dynamic names must retain their instance surface: $instances" }
                check(exposed.containsAll(listOf("Demand.Instance", "Demand.InstanceBase"))) { "Inbound inheritance missing: $exposed" }
                val constructed = NativeProjectionUseCollector(entries).collectConstructionTypes(moduleFragment)
                check("Demand.Used" in constructed && "Demand.InstanceBase" in constructed) { "$constructed" }
                check("Demand.StaticOnly" in constructed && "Demand.NamedOnly" in constructed) { "Constructor/factory references missing: $constructed" }
                check("Demand.GenericOnly" in constructed) { "Factory object references must retain factories: $constructed" }
                check("Demand.SignatureOnly" !in constructed) { "Ordinary type-only uses must not construct: $constructed" }
                check("Demand.XamlOnly" !in constructed) { "XAML type identity must not retain activation factories: $constructed" }
                lowerNativeProjectionRegistrations(moduleFragment, pluginContext, entries)
                initializeNativeProjectionMetadata(moduleFragment, pluginContext, entries)
            }
        })
    }
}
