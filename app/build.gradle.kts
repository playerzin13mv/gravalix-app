plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gravalix.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gravalix.app"
        // Android 10 (API 29) é o mínimo para capturar o som interno do telefone
        minSdk = 29
        targetSdk = 34
        // No GitHub Actions o número da compilação vira o versionCode,
        // assim cada APK novo consegue atualizar o anterior.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "2.0.0"
    }

    // Chave fixa: todo APK compilado tem a mesma assinatura e pode ser atualizado
    // por cima do anterior. (Uso pessoal / instalação por APK, não é para a Play Store.)
    signingConfigs {
        create("gravalix") {
            storeFile = file("gravalix.jks")
            storePassword = "gravalix123"
            keyAlias = "gravalix"
            keyPassword = "gravalix123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("gravalix")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
