plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.lifelink"
    compileSdk = 36

    aaptOptions {
        noCompress("tflite", "json", "gguf")
    }

    defaultConfig {
        applicationId = "com.example.lifelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 解决 LiteRT 和旧 TFLite 冲突的全局配置
    configurations.all {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.activity:activity:1.9.3") 
    implementation("androidx.fragment:fragment:1.8.5")
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.androidx.cardview)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ⭐ 统一使用 LiteRT (TFLite 的新版)，解决冲突并支持 Opcode v12
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    implementation("com.google.ai.edge.litert:litert-support:1.0.1")
    implementation("com.google.ai.edge.litert:litert-metadata:1.0.1")
    
    implementation("org.json:json:20231013")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // OCR & 扫码
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    
    // CameraX
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    implementation("androidx.test.espresso:espresso-core:3.5.1")
}
