plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.trackgas.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeystorePassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.trackgas.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.patrykandpatrick.vico:compose:3.3.1")
    implementation("com.patrykandpatrick.vico:compose-m3:3.3.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
