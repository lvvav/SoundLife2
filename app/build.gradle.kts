plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.soundlife"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.soundlife.v2"
        minSdk = 26
        targetSdk = 35
        versionCode = 52
        versionName = "2.46.3"
        // Откуда брать музыку (как на сайте: user/repo, папка music)
        // Несколько хранилищ через запятую: "user/repo1,user/repo2". В каждом — папка music.
        buildConfigField("String", "GH_REPOS", "\"lvvav/wavelab\"")
        buildConfigField("String", "GH_BRANCH", "\"HEAD\"")
        buildConfigField("String", "GH_FOLDER", "\"music\"")
        buildConfigField("String", "SUPA_URL", "\"https://zpmzqgxwqoisfvahfhqc.supabase.co\"")
        // Публичный anon-ключ (он уже открыт в index.html сайта)
        // Ключ Last.fm API (тот же, что в index.html сайта). Пусто = функция выключена
        buildConfigField("String", "LASTFM_KEY", "\"db2ee2727d2baf6e7571de2fc6236e49\"")
        buildConfigField("String", "SUPA_KEY", "\"sb_publishable_EJD_GXiloGMd6DDMt87G8Q_PxYmI8eF\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
}
