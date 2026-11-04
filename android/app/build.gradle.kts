plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 10.0.2.2 is the host machine as the emulator sees it. A phone passes -PbackendUrl=http://<computer-ip>:8000/
val backendUrl = providers.gradleProperty("backendUrl").getOrElse("http://10.0.2.2:8000/")

android {
    namespace = "com.series76.assistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.series76.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // A debug build is far slower than what a user would get, so evaluators install this one. It optimizes like
        // release, signs with the debug key so it needs no keystore, and can reach a local backend over plain HTTP.
        create("local") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    sourceSets {
        getByName("local") {
            manifest.srcFile("src/debug/AndroidManifest.xml")
            res.directories.add("src/debug/res")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.datastore)
    implementation(libs.datastore.tink)
    implementation(libs.tink.android)
    // Installs the profile Compose ships with, so that its hot paths are compiled ahead of the first frames.
    implementation(libs.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
