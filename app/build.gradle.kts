plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.lifelink"
    compileSdk = 36

    aaptOptions {
        noCompress("tflite")
        noCompress("json")
        noCompress("gguf")
    }

    defaultConfig {
        applicationId = "com.example.lifelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    // ⭐ 必须配置这一段，否则 C++ 代码不会参与编译
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1" // 请确保与你安装的 CMake 版本一致
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
    implementation(libs.activity)
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
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.fragment:fragment:1.6.0")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
}