plugins {
    `kotlin-dsl`
}

dependencies {
    // Expose the version catalog accessor generated classes to precompiled script plugins
    // so that convention scripts can use `libs.versions.*` directly.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.maven.publish.plugin)
}
