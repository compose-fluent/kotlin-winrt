package io.github.composefluent.windows.toolkit.gradle

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class JvmProjectionReferenceArtifactTest(private val kotlinVersion: String, private val incremental: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "Kotlin {0}, incremental={1}")
        fun versions(): List<Array<Any>> = projectionTestCompilerVersions().flatMap { version -> listOf(false, true).map { arrayOf<Any>(version, it) } }
    }
    @get:Rule val temporary = TemporaryFolder()

    @Test fun reference_preserves_kotlin_contract_and_is_stable_across_implementation_edits() {
        // CsWinRT reference projections expose the API without implementation bodies.
        // Kotlin additionally needs inline bytecode, metadata, defaults and internal bridges.
        val root = temporary.newFolder()
        fun write(path: String, text: String) = root.resolve(path).apply {
            parentFile.mkdirs()
            writeText(text.trimIndent())
        }
        write("settings.gradle", """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = 'projection-reference-test'
            include 'projection', 'consumer'
        """)
        write("gradle.properties", """
            org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
        """)
        write("build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion' apply false
                id 'io.github.compose-fluent.windows-toolkit' apply false
            }
            project(':projection') {
                apply plugin: 'org.jetbrains.kotlin.jvm'
                tasks.named('compileKotlin') { task ->
                    incremental = $incremental
                    io.github.composefluent.windows.toolkit.gradle.JvmProjectionReferenceArtifactKt
                        .configureJvm${if (incremental) "Incremental" else "Projection"}ReferenceArtifact(project, task)
                }
            }
            project(':consumer') {
                apply plugin: 'org.jetbrains.kotlin.jvm'
                def producer = project(':projection')
                def reference = producer.layout.buildDirectory.dir('classes/kotlin-winrt/reference/compileKotlin')
                dependencies {
                    compileOnly files(reference).builtBy(producer.tasks.named('compileKotlin'))
                    runtimeOnly files(producer.layout.buildDirectory.dir('classes/kotlin/main'))
                }
                tasks.register('verifyRun', JavaExec) {
                    dependsOn tasks.named('classes'), producer.tasks.named('compileKotlin')
                    classpath = sourceSets.main.runtimeClasspath
                    mainClass = 'ConsumerKt'
                }
            }
        """)
        write("projection/src/main/kotlin/Unchanged.kt", "package sample; class Unchanged(val value: String)")
        val source = write("projection/src/main/kotlin/Projection.kt", """
            package sample
            open class Widget(val name: String = "widget") {
                open fun value(): Int = 41
                companion object { fun create() = Widget() }
            }
            data class Point(val x: Int, val y: Int)
            enum class Direction { Left, Right }
            fun interface Callback { fun invoke(value: Int): Int }
            @PublishedApi internal fun bridge(value: Int) = value + 1
            inline fun <reified T> typeName() = T::class.java.simpleName
            inline fun mapped(value: Int, transform: (Int) -> Int) = transform(bridge(value))
            class RemovedLater
        """)
        write("consumer/src/main/kotlin/Consumer.kt", """
            import sample.*
            class Derived : Widget() { override fun value() = super.value() + 1 }
            fun main() {
                check(Widget.create().name == "widget")
                check(Point(1, 2).copy(y = 3).y == 3)
                check(Direction.valueOf("Left") == Direction.Left)
                check(Callback { it + 1 }.invoke(2) == 3)
                check(typeName<Widget>() == "Widget")
                check(mapped(1) { it + 1 } == 3)
                println("projection-value=" + Derived().value())
            }
        """)
        fun run() = GradleRunner.create().withProjectDir(root).withProjectionTestEnvironment()
            .withArguments(":consumer:verifyRun", "--stacktrace", "--console=plain").build()
        val first = run()
        assertTrue(first.output, first.output.contains("projection-value=42"))
        val reference = root.resolve("projection/build/classes/kotlin-winrt/reference/compileKotlin")
        fun snapshot() = reference.walkTopDown().filter(File::isFile)
            .associate { it.relativeTo(reference).invariantSeparatorsPath to it.readBytes() }
        val before = snapshot()
        assertTrue("sample/Widget.class" in before)
        assertTrue("sample/Unchanged.class" in before)
        assertTrue(before.keys.any { it.endsWith(".kotlin_module") })
        source.writeText(source.readText().replace("= 41", "= 42"))
        val second = run()
        assertEquals(TaskOutcome.SUCCESS, second.task(":projection:compileKotlin")?.outcome)
        val after = snapshot()
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, after.getValue(name)) }
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":consumer:compileKotlin")?.outcome)
        assertTrue(second.output, second.output.contains("projection-value=43"))
        assertFalse(second.output, second.output.contains("NoSuchMethodError"))
        source.writeText(source.readText().replace("class RemovedLater", ""))
        run()
        assertFalse(reference.resolve("sample/RemovedLater.class").exists())
    }
}
