plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.plantdoctor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.plantdoctor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        // Адрес сервера анализа (Cloudflare Worker). Задаётся в gradle.properties.
        val apiUrl = (project.findProperty("plantApiUrl") as String?).orEmpty()
        buildConfigField("String", "PLANT_API_URL", "\"$apiUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подписываем отладочным ключом, чтобы APK можно было сразу установить.
            signingConfig = signingConfigs.getByName("debug")
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
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.version",
                "META-INF/FastDoubleParser-*",
                "META-INF/thirdparty-LICENSE",
                "META-INF/bigint-LICENSE",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
