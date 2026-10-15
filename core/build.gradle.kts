plugins {
    id("com.android.application")
    id("kotlinx-serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.extension.all.personalhentai"
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.all.personalhentai"
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
        versionCode = extVersionCode
        versionName = "1.$extVersionCode"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

ext {
    extName = "Personal Hentai Catalog"
    extClass = ".PersonalHentai"
    extVersionCode = 1
    isNsfw = true
}

apply(from = "$rootDir/common.gradle")
