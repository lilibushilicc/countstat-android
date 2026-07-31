plugins {
    id("com.android.application")
}

import java.util.Properties

val props = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStorePass: String? = props.getProperty("countstat.storePass")
val releaseKeyPass: String? = props.getProperty("countstat.keyPass")
val releaseKeyAlias: String? = props.getProperty("countstat.keyAlias")
val releaseStore: File? = rootProject.file("countstat-release.jks").takeIf { it.exists() }

android {
    namespace = "com.countstat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.countstat.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseStore != null && releaseStorePass != null && releaseKeyPass != null && releaseKeyAlias != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = releaseStorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
