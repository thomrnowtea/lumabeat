plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lumabeat.app"
    compileSdk = 36

    flavorDimensions += "distribution"

    defaultConfig {
        applicationId = "com.lumabeat.app"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 6
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            providers.environmentVariable("RELEASE_STORE_FILE").orNull?.let { storeFile = file(it) }
            storeType = providers.environmentVariable("RELEASE_STORE_TYPE").orNull ?: "PKCS12"
            storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
        }
    }

    productFlavors {
        create("core") {
            dimension = "distribution"
            buildConfigField("boolean", "ARTWORK_COLORS_AVAILABLE", "true")
            buildConfigField("boolean", "NOTIFICATION_ARTWORK_AVAILABLE", "false")
            buildConfigField("boolean", "SCREEN_COLOR_CAPTURE_AVAILABLE", "true")
        }
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "ARTWORK_COLORS_AVAILABLE", "true")
            buildConfigField("boolean", "NOTIFICATION_ARTWORK_AVAILABLE", "true")
            buildConfigField("boolean", "SCREEN_COLOR_CAPTURE_AVAILABLE", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
