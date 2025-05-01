plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.supuestopitidoalpasar100latidosporsegundo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.supuestopitidoalpasar100latidosporsegundo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        viewBinding = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets {
        getByName("main").assets.srcDirs("src/main/assets") // Confirmar que los archivos en assets sean empaquetados
    }
}

dependencies {
    // Dependencias de Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")

    // Otras dependencias comunes
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.core.ktx)

    // Dependencias de TensorFlow Lite
    implementation(libs.tensorflow.lite) // Para cargar el modelo
    implementation(libs.tensorflow.lite.support) // Utilidades adicionales
    implementation(libs.tensorflow.lite.gpu) // Opcional si utilizas la aceleración por GPU

    // Dependencias de prueba
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Dependencia de WorkManager
    implementation(libs.work.runtime)
}
