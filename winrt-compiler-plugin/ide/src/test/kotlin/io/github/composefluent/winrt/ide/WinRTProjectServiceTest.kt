package io.github.composefluent.winrt.ide

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTSourceSetData
import io.github.composefluent.winrt.ide.gradle.WinRTTargetData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class WinRTProjectServiceTest : BasePlatformTestCase() {
    fun testReimportRemovesOldModulesAndPreservesOtherBuilds() {
        val service = project.service<WinRTProjectService>()
        val first = module("/first", ":app")
        val other = module("/other", ":library")
        service.replaceBuildModels("/first", listOf(first))
        service.replaceBuildModels("/other", listOf(other))
        service.replaceBuildModels("/first", emptyList())
        assertEquals(listOf(other), service.modules.value)
        service.refreshFromGradleCache()
        assertEquals(listOf(other), service.modules.value)
    }

    fun testImportedSnapshotCanBeSerializedWithoutToolingApiProxies() {
        val model = module("/first", ":app")
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(model) }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as WinRTModuleData }
        assertEquals(model.projectPath, restored.projectPath)
        assertEquals(model.sourceSets, restored.sourceSets)
        assertEquals(model.targets, restored.targets)
    }

    private fun module(root: String, path: String) = WinRTModuleData(
        path, "$root/${path.substringAfterLast(':')}", "$root/build", "2.4.0", "10.0.26100.0",
        listOf(WinRTSourceSetData("main", listOf("$root/src/main/kotlin"), emptyList(), listOf("$root/src/main/appxResources"))),
        listOf(WinRTTargetData("jvm", "jvm", listOf("main"))), emptyList(), emptyList(),
    )
}
