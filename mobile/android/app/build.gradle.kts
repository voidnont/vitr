plugins {
    id("com.android.application")
}

android {
    namespace = "com.bloodvitr.vitr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bloodvitr.vitr"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
