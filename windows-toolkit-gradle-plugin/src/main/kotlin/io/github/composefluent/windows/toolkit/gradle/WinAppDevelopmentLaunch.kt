package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Path

/** The activation broker does not inherit Gradle's development environment.
 * WinApp's --args carries this opt-in to the JVM host, which consumes it before
 * creating the VM. No token or development setting is written into the package. */
internal const val WINAPP_HOT_RELOAD_ARGUMENT = "--kotlin-winrt-hot-reload-directory="

/** Preserve the inner CommandLineToArgvW string through ProcessImpl's outer
 * Windows argument. Backslash runs before literal quotes need 2*n+1 slashes. */
internal fun winAppRunArgumentsOption(arguments: String): String = "--args=" + buildString {
    var backslashes = 0
    arguments.forEach { character ->
        if (character == '\\') backslashes++ else {
            append("\\".repeat(if (character == '"') backslashes * 2 + 1 else backslashes))
            append(character)
            backslashes = 0
        }
    }
    // ProcessImpl itself quotes and doubles trailing slashes of the outer argument.
    append("\\".repeat(backslashes))
}

internal fun winAppDevelopmentArguments(arguments: String, sessionDirectory: String?): String {
    if (sessionDirectory.isNullOrBlank()) return arguments
    val directory = Path.of(sessionDirectory)
    require(directory.isAbsolute) { "The development session directory must be absolute." }
    val argument = WINAPP_HOT_RELOAD_ARGUMENT + directory.normalize()
    require(argument.none { it == '\u0000' || it == '\r' || it == '\n' || it == '"' }) {
        "The development session directory contains invalid characters."
    }
    // CommandLineToArgvW/CRT quoting: double trailing backslashes before the closing quote.
    val trailing = argument.takeLastWhile { it == '\\' }.length
    val quoted = "\"$argument${"\\".repeat(trailing)}\""
    return if (arguments.isBlank()) quoted else "$quoted $arguments"
}
