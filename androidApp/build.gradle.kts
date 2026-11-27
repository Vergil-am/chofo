import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Release signing comes from a git-ignored keystore.properties at the project root; see
// keystore.properties.example. It is kept out of secrets.properties, whose keys are compiled
// into the app. Without it, release builds are signed with the debug key, as before.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}
if (keystoreProperties == null && gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
    logger.warn("keystore.properties not found: the release build is signed with the debug key.")
}

android {
    namespace = "dev.dogetime"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.dogetime"
        minSdk = 24
        targetSdk = 37
        versionCode = providers.gradleProperty("app.versionCode").get().toInt()
        versionName = providers.gradleProperty("app.version").get()
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                fun required(key: String): String = keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
                    ?: throw GradleException("keystore.properties is missing \"$key\".")
                storeFile = file(required("storeFile"))
                storePassword = required("storePassword")
                keyAlias = required("keyAlias")
                keyPassword = required("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystoreProperties != null) "release" else "debug")
//            isShrinkResources = true
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}
