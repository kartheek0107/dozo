// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("com.google.gms:google-services:4.4.0")
    }
}

plugins {
    id("com.android.application") version "8.5.1" apply false
    id("com.android.library") version "8.5.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
