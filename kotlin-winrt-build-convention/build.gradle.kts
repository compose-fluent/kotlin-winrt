plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("com.vanniktech:gradle-maven-publish-plugin:${libs.versions.mavenPublish.get()}")
}
