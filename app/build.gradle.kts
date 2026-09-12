plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sanchat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sanchat.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.0"

        // URL do proxy (Vercel) e token opcional — podem vir de gradle.properties
        // (o workflow do GitHub injeta BACKEND_URL das repository variables)
        buildConfigField(
            "String",
            "BACKEND_URL",
            "\"${project.findProperty("BACKEND_URL") ?: ""}\""
        )
        buildConfigField(
            "String",
            "APP_TOKEN",
            "\"${project.findProperty("APP_TOKEN") ?: ""}\""
        )
    }

    signingConfigs {
        create("ci") {
            storeFile = rootProject.file("signing/release.p12")
            storePassword = "sanchat2024"
            keyAlias = "sanchat"
            keyPassword = "sanchat2024"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ci")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
