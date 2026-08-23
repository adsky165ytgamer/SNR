plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "app.receiver"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.receiver"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.2-beta-material3"
        buildConfigField("String", "BACKEND_BASE_URL", "\"${providers.gradleProperty("BACKEND_BASE_URL").getOrElse("https://replace-with-your-backend.example").trimEnd('/')}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").getOrElse("replace-with-production-web-client-id")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${providers.gradleProperty("FIREBASE_PROJECT_ID").getOrElse("school-notics")}\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${providers.gradleProperty("FIREBASE_APPLICATION_ID").getOrElse("1:763216367314:android:9af9287a9df5aeddc4670b")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${providers.gradleProperty("FIREBASE_API_KEY").getOrElse("replace-with-firebase-api-key")}\"")
    }
    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("RECEIVER_RELEASE_STORE_FILE").orNull
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("RECEIVER_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("RECEIVER_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("RECEIVER_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        getByName("release") {
            val keystorePath = providers.gradleProperty("RECEIVER_RELEASE_STORE_FILE").orNull
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
