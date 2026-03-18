plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.lifelink"
    compileSdk = 36 // ⭐ 升级到 36 以满足 activity:1.12.2 的要求

    aaptOptions {
        noCompress("tflite", "json", "gguf")
    }

    defaultConfig {
        applicationId = "com.example.lifelink"
        minSdk = 26
        targetSdk = 35 // targetSdk 可以保持在 35
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    
    // ⭐ 注意：这里强制指定较低版本以匹配 compileSdk 35 (如果你不想升 36)
    // 但既然报错推荐升 36，我们已经在上面升级了，这里保留原样或更新
    implementation("androidx.activity:activity:1.9.3") 
    implementation("androidx.fragment:fragment:1.8.5")
    
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.androidx.cardview)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    
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