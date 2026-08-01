plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
}

description = "Bootstrap compiler lowering for statically planned WinRT projection call sites"

dependencies {
    implementation(project.parent!!.childProjects.getValue("callsite-contract"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}
