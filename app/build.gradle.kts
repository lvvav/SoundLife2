plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.soundlife.admin"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.soundlife.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0 alpha"
        // Хранилище с музыкой (как у сайта): папка music, ветка по умолчанию
        buildConfigField("String", "GH_REPO", "\"lvvav/wavelab\"")
        buildConfigField("String", "GH_BRANCH", "\"main\"")
        buildConfigField("String", "GH_FOLDER", "\"music\"")
        // Версия сборки: коммит из CI (для проверки обновлений), dev — локально
        buildConfigField("String", "COMMIT", "\"${System.getenv("GITHUB_SHA") ?: "dev"}\"")
    }
    // Один ключ для всех сборок, иначе обновление поверх установленной версии не встанет
    signingConfigs {
        create("shared") {
            storeFile = file("admin.jks")
            storePassword = "soundlife-admin"
            keyAlias = "admin"
            keyPassword = "soundlife-admin"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
