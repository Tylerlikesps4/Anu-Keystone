import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing: keys/keystore.properties is generated locally and never committed.
val keyProps = Properties().apply {
    val f = rootProject.file("keys/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "keystone.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tylerlikesps4.keystone"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.04"
    }

    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin") } }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation(project(":engine"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

// Ship CREDITS.md inside the app (shown on the Licenses screen).
val copyCredits by tasks.registering(Copy::class) {
    from(rootProject.file("CREDITS.md"))
    into(layout.buildDirectory.dir("generated/credits"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/credits"))
tasks.named("preBuild") { dependsOn(copyCredits) }
