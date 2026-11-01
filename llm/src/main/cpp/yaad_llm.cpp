#include <android/log.h>
#include <jni.h>
#include <unistd.h>

#include <chrono>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "YaadLlm"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kMaxPieceBytes = 256;

std::once_flag backendOnce;

struct Engine {
    llama_model *model;
    llama_context *context;
    // What the context has already read. A new prompt only pays for the part that differs.
    std::vector<llama_token> cached;
};

class JavaString {
public:
    JavaString(JNIEnv *env, jstring text) : env_(env), text_(text), chars_(env->GetStringUTFChars(text, nullptr)) {}
    ~JavaString() { env_->ReleaseStringUTFChars(text_, chars_); }
    JavaString(const JavaString &) = delete;
    JavaString &operator=(const JavaString &) = delete;
    const char *get() const { return chars_; }

private:
    JNIEnv *env_;
    jstring text_;
    const char *chars_;
};

std::vector<llama_token> tokenize(const llama_vocab *vocab, const char *text, int length) {
    const int count = -llama_tokenize(vocab, text, length, nullptr, 0, true, true);
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, text, length, tokens.data(), count, true, true) < 0) tokens.clear();
    return tokens;
}

void setBatch(llama_batch_ext *batch, const llama_token *tokens, int count, llama_pos firstPosition) {
    llama_batch_ext_clear(batch);
    for (int i = 0; i < count; ++i) {
        const int index = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos position = firstPosition + i;
        llama_batch_ext_set_pos(batch, index, &position);
    }
    llama_batch_ext_set_output_logits(batch, count - 1, true);
}

/** Drops whatever differs from `tokens` and returns how many leading tokens are already read. */
size_t keepCommonPrefix(Engine &engine, const std::vector<llama_token> &tokens) {
    size_t common = 0;
    // At least one token must be read again, because reading is what produces the next prediction.
    while (common < engine.cached.size() && common + 1 < tokens.size() && engine.cached[common] == tokens[common]) {
        ++common;
    }
    llama_memory_t memory = llama_get_memory(engine.context);
    if (!llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(common), -1)) {
        llama_memory_clear(memory, true);
        common = 0;
    }
    engine.cached.resize(common);
    return common;
}

}  // namespace

/** Reads the model from `fd` at `offset`, which lets it stay inside the APK instead of being copied out. */
extern "C" JNIEXPORT jlong JNICALL
Java_com_sameer_yaad_llm_LlamaNative_load(JNIEnv *, jobject, jint fd, jlong offset, jint contextSize, jint threads) {
    std::call_once(backendOnce, [] { llama_backend_init(); });

    FILE *file = fdopen(dup(fd), "rb");
    if (file == nullptr || fseeko(file, offset, SEEK_SET) != 0) {
        LOGW("Could not open the model");
        if (file != nullptr) fclose(file);
        return 0;
    }
    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    // Memory-mapping needs the data at a 32-byte offset in the file, which a position inside an APK can't promise.
    modelParams.load_mode = LLAMA_LOAD_MODE_NONE;
    llama_model *model = llama_model_load_from_file_ptr(file, modelParams);
    fclose(file);
    if (model == nullptr) return 0;

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = contextSize;
    contextParams.n_batch = contextSize;
    contextParams.n_threads = threads;
    contextParams.n_threads_batch = threads;
    llama_context *context = llama_init_from_model(model, contextParams);
    if (context == nullptr) {
        LOGW("Could not create the context");
        llama_model_free(model);
        return 0;
    }
    return reinterpret_cast<jlong>(new Engine{model, context, {}});
}

extern "C" JNIEXPORT void JNICALL
Java_com_sameer_yaad_llm_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    llama_free(engine->context);
    llama_model_free(engine->model);
    delete engine;
}

/** Returns UTF-8 bytes, or null on failure. A timeout returns what was written so far, which won't be valid JSON. */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sameer_yaad_llm_LlamaNative_generate(
        JNIEnv *env, jobject, jlong handle, jstring prompt, jstring grammar, jint maxTokens, jlong timeoutMs) {
    Engine &engine = *reinterpret_cast<Engine *>(handle);
    const llama_vocab *vocab = llama_model_get_vocab(engine.model);
    JavaString promptText(env, prompt);
    JavaString grammarText(env, grammar);

    std::vector<llama_token> tokens = tokenize(vocab, promptText.get(), env->GetStringUTFLength(prompt));
    if (tokens.empty() || tokens.size() + maxTokens > llama_n_ctx(engine.context)) {
        LOGW("The prompt doesn't fit: %zu tokens", tokens.size());
        return nullptr;
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_grammar(vocab, grammarText.get(), "root"));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    const size_t common = keepCommonPrefix(engine, tokens);
    engine.cached.assign(tokens.begin(), tokens.end());

    llama_batch_ext *batch = llama_batch_ext_init(engine.context);
    setBatch(batch, tokens.data() + common, static_cast<int>(tokens.size() - common), static_cast<llama_pos>(common));

    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    std::string output;
    bool failed = false;

    for (int generated = 0; generated < maxTokens; ++generated) {
        if (llama_process(engine.context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            LOGW("Decoding failed");
            failed = true;
            break;
        }

        llama_token next = llama_sampler_sample(sampler, engine.context, -1);
        if (llama_vocab_is_eog(vocab, next)) break;

        char piece[kMaxPieceBytes];
        const int pieceLength = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (pieceLength < 0) {
            failed = true;
            break;
        }
        output.append(piece, pieceLength);

        if (std::chrono::steady_clock::now() > deadline) {
            LOGW("Timed out after %d tokens", generated);
            break;
        }
        setBatch(batch, &next, 1, static_cast<llama_pos>(engine.cached.size()));
        engine.cached.push_back(next);
    }

    llama_batch_ext_free(batch);
    llama_sampler_free(sampler);
    if (failed) {
        llama_memory_clear(llama_get_memory(engine.context), true);
        engine.cached.clear();
        return nullptr;
    }

    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(output.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(output.size()), reinterpret_cast<const jbyte *>(output.data()));
    return bytes;
}
