package io.github.composefluent.winrt.gradle

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder

@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun KotlinHierarchyBuilder.winui() {
    group("winui") {
        withJvm()
        withMingw()
    }
}
