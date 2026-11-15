import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")   // Room 코드 생성 (Kotlin 1.9.25에 포함, 별도 버전 지정 불필요)
}

val releaseSigning = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.hmgchallenge.pleosstarter"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "com.hmgchallenge.pleosstarter"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "com.hmgchallenge.pleosstarter.product.ProductSmokeRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigning.isNotEmpty()) {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // Driving coach speech; ON_DEVICE avoids server registration and project secrets.
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
