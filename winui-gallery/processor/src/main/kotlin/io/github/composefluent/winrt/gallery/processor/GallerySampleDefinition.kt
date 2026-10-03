package io.github.composefluent.winrt.gallery.processor

/** Mirrors WinUI-Gallery ControlExample.SampleDefinition; C# sections become Kotlin. */
internal data class GallerySampleDefinition(val header: String, val xaml: String, val kotlin: String) {
    companion object {
        fun parse(source: String): GallerySampleDefinition {
            val sections = linkedMapOf<String, MutableList<String>>()
            var current: MutableList<String>? = null
            source.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
                if (line.startsWith("--- ")) {
                    val name = line.removePrefix("--- ").trim().lowercase()
                    require(name in setOf("header", "xaml", "kotlin")) { "Unknown sample section: $name" }
                    require(name !in sections) { "Duplicate sample section: $name" }
                    current = mutableListOf<String>().also { sections[name] = it }
                } else {
                    require(current != null || line.isBlank()) { "Sample text must follow a section header" }
                    current?.add(line)
                }
            }
            fun section(name: String) = sections[name]?.joinToString("\n")?.trim('\n').orEmpty()
            val result = GallerySampleDefinition(section("header"), section("xaml"), section("kotlin"))
            // The original Gallery keeps HeaderText from XAML when this section is absent.
            return result
        }
    }
}
