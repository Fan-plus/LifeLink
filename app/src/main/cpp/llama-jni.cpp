#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define JNI_API __attribute__((visibility("default"))) extern "C"

extern "C" {

struct LlamaSession {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
};

// 内部辅助：添加 Token 到批处理
static void batch_add(llama_batch & batch, llama_token id, llama_pos pos, llama_seq_id seq_id, bool logits) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id[batch.n_tokens][0] = seq_id;
    batch.logits[batch.n_tokens] = logits;
    batch.n_tokens++;
}

JNI_API jlong JNICALL
Java_com_example_lifelink_llm_LlamaBridge_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    llama_backend_init();
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    llama_model_params m_params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path, m_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) { LOGE("无法加载模型文件"); return 0; }

    llama_context_params c_params = llama_context_default_params();
    c_params.n_ctx = 2048; // 上下文窗口
    c_params.n_threads = 4;
    llama_context * ctx = llama_init_from_model(model, c_params);

    if (!ctx) {
        llama_model_free(model);
        LOGE("无法初始化上下文");
        return 0;
    }

    LlamaSession * session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    return reinterpret_cast<jlong>(session);
}

JNI_API jstring JNICALL
Java_com_example_lifelink_llm_LlamaBridge_nativeInference(JNIEnv *env, jobject thiz, jlong handle, jstring prompt) {
    LlamaSession * session = reinterpret_cast<LlamaSession *>(handle);
    if (!session || !session->ctx) return env->NewStringUTF("");

    const char * prompt_ptr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_ptr);
    env->ReleaseStringUTFChars(prompt, prompt_ptr);

    const struct llama_vocab * vocab = llama_model_get_vocab(session->model);

    // 1. Tokenize (支持动态扩容)
    std::vector<llama_token> tokens(prompt_str.length() + 4);
    int n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    // 2. 截断保护：防止超过上下文 (留出 256 为生成预留)
    const int max_tokens = 2048 - 256;
    if (tokens.size() > max_tokens) {
        LOGD("输入过长 (%zu tokens)，已截断", tokens.size());
        tokens.erase(tokens.begin(), tokens.begin() + (tokens.size() - max_tokens));
    }

    // 3. 动态初始化 Batch (大小必须 >= tokens.size())
    llama_batch batch = llama_batch_init(std::max((int)tokens.size(), 512), 0, 1);
    batch.n_tokens = 0;

    for (int i = 0; i < tokens.size(); i++) {
        batch_add(batch, tokens[i], i, 0, (i == tokens.size() - 1));
    }

    // 4. 首轮解码
    int res = llama_decode(session->ctx, batch);
    if (res != 0) {
        LOGE("解码失败，错误码: %d", res);
        llama_batch_free(batch);
        return env->NewStringUTF("AI 提炼失败，请尝试拍摄更清晰的局部图片");
    }

    // 5. 生成循环
    std::string response_text = "";
    llama_sampler * smpl = llama_sampler_init_greedy();
    int n_cur = tokens.size();
    int n_decode = 0;
    const int max_new_tokens = 256;

    while (n_decode < max_new_tokens) {
        const llama_token id = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) response_text.append(buf, n);

        batch.n_tokens = 0; // 重置 batch 准备下个 token
        batch_add(batch, id, n_cur, 0, true);

        if (llama_decode(session->ctx, batch) != 0) break;
        n_cur++;
        n_decode++;
    }

    llama_batch_free(batch);
    llama_sampler_free(smpl);

    return env->NewStringUTF(response_text.c_str());
}

JNI_API void JNICALL
Java_com_example_lifelink_llm_LlamaBridge_nativeFree(JNIEnv *env, jobject thiz, jlong handle) {
    LlamaSession * session = reinterpret_cast<LlamaSession *>(handle);
    if (session) {
        if (session->ctx) llama_free(session->ctx);
        if (session->model) llama_model_free(session->model);
        delete session;
    }
}

}