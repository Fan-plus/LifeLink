package com.example.lifelink.llm;

import android.util.Log;

public class LlamaBridge {
    static {
        try {
            // ⭐ 核心修改：只加载我们自己编译的 JNI 库。
            // 只要 CMakeLists.txt 中正确链接了 llama 和 ggml，系统会自动找到它们。
            System.loadLibrary("lifelink_jni");
            Log.d("LlamaBridge", "JNI 桥接库 lifelink_jni 加载成功");
        } catch (UnsatisfiedLinkError e) {
            Log.e("LlamaBridge", "加载 JNI 库失败，请检查 CMake 或 so 路径: " + e.getMessage());
        }
    }

    public native long nativeInit(String modelPath);
    public native String nativeInference(long handle, String prompt);
    public native void nativeFree(long handle);
}
