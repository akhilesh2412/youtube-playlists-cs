import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.2")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    apply(plugin = "kotlinx-serialization")

    cloudstream {
        language = "hi"
        authors = listOf("sky")
        status = 1
        tvTypes = listOf("Others")
        description = "Free YouTube playlists — CUET MCA Preparation, CUET PG MCA Computer Science (Adda247), C Programming Course, C Programming"
        iconUrl = "https://raw.githubusercontent.com/sky/youtube-playlists-cs/main/icon.png"
    }

    android {
        compileSdk = 35
        defaultConfig { minSdk = 21 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf(
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions"
            )
        }
    }

    dependencies {
        val apk by configurations
        apk("com.lagradost:cloudstream3:pre-release")
        implementation("com.github.Blatzar:NiceHttp:0.4.5")
        implementation("org.jsoup:jsoup:1.18.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    }
}
