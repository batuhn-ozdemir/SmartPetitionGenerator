import java.util.Properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

android {
    namespace = "com.gpproject.smartpetitiongenerator"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.gpproject.smartpetitiongenerator"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val baseUrl = localProperties.getProperty("BASE_URL") ?: "http://10.0.2.2:8080/"
        val appSigningSecret = localProperties.getProperty("APP_SIGNING_SECRET") ?: ""
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "APP_SIGNING_SECRET", "\"$appSigningSecret\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // 1. Room Database (Raporda: Data Layer & Persistence) [cite: 612]
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version") // ksp plugin ekli olmalı, yoksa kapt kullan

    // 2. Retrofit & Gson (Raporda: Backend API iletişimi) [cite: 659]
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // 3. ViewModel & LiveData (Raporda: MVVM Architecture)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // 4. Navigation (Ekranlar arası geçiş için)
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // İkon kütüphanesi (Share, Person, Home gibi ikonlar için gereklidir)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    implementation("androidx.exifinterface:exifinterface:1.3.7")
}