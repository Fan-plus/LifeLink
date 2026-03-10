cmake_minimum_required(VERSION 3.18.1)

project("lifelink_jni")

set(CMAKE_CXX_STANDARD 17)

# 定义预编译库所在的路径
set(libs_dir ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# 1. 导入所有依赖库
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION ${libs_dir}/libggml.so)

add_library(ggml_cpu SHARED IMPORTED)
set_target_properties(ggml_cpu PROPERTIES IMPORTED_LOCATION ${libs_dir}/libggml-cpu.so)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${libs_dir}/libllama.so)

# ⭐ 新增：导入 OpenMP 库 (libomp.so)
add_library(omp SHARED IMPORTED)
set_target_properties(omp PROPERTIES IMPORTED_LOCATION ${libs_dir}/libomp.so)

# 2. 编译我们的 JNI 包装库
add_library(lifelink_jni SHARED llama-jni.cpp)

# 3. 指定头文件路径
target_include_directories(lifelink_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# 4. ⭐ 关键：按照依赖顺序链接
# 顺序：包装库 -> llama -> ggml_cpu -> ggml -> omp
target_link_libraries(lifelink_jni
        llama
        ggml_cpu
        ggml
        omp
        android
        log)
