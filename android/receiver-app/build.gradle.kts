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
        versionCode = 2
        versionName = "0.1.1"
        buildConfigField("String", "BACKEND_BASE_URL", "\"${providers.gradleProperty("BACKEND_BASE_URL").getOrElse("https://replace-with-your-backend.example").trimEnd('/')}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
