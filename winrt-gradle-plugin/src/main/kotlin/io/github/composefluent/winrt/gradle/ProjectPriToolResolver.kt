package io.github.composefluent.winrt.gradle

import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal object ProjectPriToolResolver {
    fun makePriExecutable(configuredExecutable: String, windowsSdkVersion: String, runtimeIdentifier: String): Path? {
        configuredExecutable.takeIf { it.isNotBlank() }?.let { configured ->
            return Path.of(configured).takeIf { it.isRegularFile() }
        }
        val sdk = findWindowsSdk(windowsSdkVersion.takeIf { it.isNotBlank() }) ?: return null
        return sdk.tool("makepri.exe", windowsSdkArchitecture(runtimeIdentifier))
    }

    fun makeAppxExecutable(configuredExecutable: String, windowsSdkVersion: String, runtimeIdentifier: String): Path? {
        configuredExecutable.takeIf { it.isNotBlank() }?.let { configured ->
            return Path.of(configured).takeIf { it.isRegularFile() }
        }
        val sdk = findWindowsSdk(windowsSdkVersion.takeIf { it.isNotBlank() }) ?: return null
        return sdk.tool("makeappx.exe", windowsSdkArchitecture(runtimeIdentifier))
    }

    fun signToolExecutable(configuredExecutable: String, windowsSdkVersion: String, runtimeIdentifier: String): Path? {
        configuredExecutable.takeIf { it.isNotBlank() }?.let { configured ->
            return Path.of(configured).takeIf { it.isRegularFile() }
        }
        val sdk = findWindowsSdk(windowsSdkVersion.takeIf { it.isNotBlank() }) ?: return null
        return sdk.tool("signtool.exe", windowsSdkArchitecture(runtimeIdentifier))
    }
}
