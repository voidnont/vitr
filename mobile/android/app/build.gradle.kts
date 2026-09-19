plugins {
    id("com.android.application")
}

val vitrVersion = rootProject.file("../../VERSION").readText().trim()
val vitrVersionParts = vitrVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
val vitrVersionCode =
    (vitrVersionParts.getOrElse(0) { 0 } * 10000) +
    (vitrVersionParts.getOrElse(1) { 0 } * 100) +
    vitrVersionParts.getOrElse(2) { 0 }

android {
    namespace = "com.bloodvitr.vitr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bloodvitr.vitr"
        minSdk = 26
        targetSdk = 35
        versionCode = vitrVersionCode
        versionName = vitrVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/standalone/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/standalone/java"))
            res.setSrcDirs(listOf("src/standalone/res"))
            assets.setSrcDirs(listOf("src/standalone/assets"))
        }
        getByName("test") {
            java.setSrcDirs(emptyList<String>())
            resources.setSrcDirs(emptyList<String>())
        }
        getByName("androidTest") {
            java.setSrcDirs(emptyList<String>())
            res.setSrcDirs(emptyList<String>())
        }
    }
}
