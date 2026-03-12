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
    c_params.n_ctx = 2048;
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
    if (!session || !session->ctx) return env->NewStringUTF("引擎未就绪");

    const char * prompt_ptr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_ptr);
    env->ReleaseStringUTFChars(prompt, prompt_ptr);

    if (prompt_str.empty()) return env->NewStringUTF("输入为空");

    const auto vocab = llama_model_get_vocab(session->model);

    // 1. Tokenize
    std::vector<llama_token> tokens(prompt_str.length() + 8);
    int n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), tokens.data(), tokens.size(), true, true);
    if (n_tokens <= 0) {
        LOGE("Tokenize 失败");
        return env->NewStringUTF("无法理解输入");
    }
    tokens.resize(n_tokens);

    // 2. 重置记忆
    llama_memory_clear(llama_get_memory(session->ctx), true);

    // 3. 构建批处理
    llama_batch batch = llama_batch_init(std::max((int)tokens.size(), 512), 0, 1);
    batch.n_tokens = 0;

    for (int i = 0; i < tokens.size(); i++) {
        batch_add(batch, tokens[i], i, 0, (i == tokens.size() - 1));
    }

    // 4. 解码
    int res = llama_decode(session->ctx, batch);
    if (res != 0) {
        LOGE("解码失败，错误码: %d", res);
        llama_batch_free(batch);
        return env->NewStringUTF("AI 思考出错了");
    }

    // 5. 生成
    std::string response_text = "";
    auto smpl = llama_sampler_init_greedy();
    int n_cur = tokens.size();
    const int max_new_tokens = 256;

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) response_text.append(buf, n);

        // ⭐ 修正：手动清除 batch
        batch.n_tokens = 0;
        batch_add(batch, id, n_cur++, 0, true);
        
        if (llama_decode(session->ctx, batch) != 0) break;
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
