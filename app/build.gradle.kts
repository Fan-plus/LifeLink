plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.lifelink"
    compileSdk = 36

    aaptOptions {
        noCompress("tflite")
        noCompress("json")
    }

    defaultConfig {
        applicationId = "com.example.lifelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.androidx.cardview)

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // TensorFlow Lite - 最新版本，完整支持库
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    
    // JSON parsing
    implementation("org.json:json:20231013")
    // Charting
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // Google ML Kit - 官方OCR文字识别 (稳定可靠)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    
    // 权限库
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.fragment:fragment:1.6.0")
    
    implementation("androidx.test.espresso:espresso-core:3.5.1")
}