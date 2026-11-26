import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.murilo.bloqueadorreels"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.murilo.bloqueadorreels"
        minSdk = 26
        targetSdk = 36
        // O CI passa -PversionCode=<n> -PversionName=<x.y.z> (a Play exige versionCode crescente).
        // Local, sem parâmetros: 1 / 0.1.0.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
    }

    // Chave de assinatura local (arquivos fora do git). Sem ela, o release sai sem assinatura.
    val propsChave = rootProject.file("keystore.properties")
    val chave = Properties().apply { if (propsChave.exists()) propsChave.inputStream().use { load(it) } }
    signingConfigs {
        if (propsChave.exists()) {
            create("release") {
                storeFile = rootProject.file(chave.getProperty("storeFile"))
                storePassword = chave.getProperty("storePassword")
                keyAlias = chave.getProperty("keyAlias")
                keyPassword = chave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
