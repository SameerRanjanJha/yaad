#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <string>

#include "whisper.h"

#define LOG_TAG "YaadWhisper"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

size_t assetRead(void *asset, void *output, size_t size) {
    return static_cast<size_t>(AAsset_read(static_cast<AAsset *>(asset), output, size));
}

bool assetAtEnd(void *asset) {
    return AAsset_getRemainingLength64(static_cast<AAsset *>(asset)) <= 0;
}

void assetClose(void *asset) {
    AAsset_close(static_cast<AAsset *>(asset));
}

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

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_sameer_yaad_capture_WhisperNative_load(JNIEnv *env, jobject, jobject assetManager, jstring assetPath) {
    JavaString path(env, assetPath);
    AAsset *asset = AAssetManager_open(AAssetManager_fromJava(env, assetManager), path.get(), AASSET_MODE_STREAMING);
    if (asset == nullptr) {
        LOGW("Model asset not found: %s", path.get());
        return 0;
    }
    whisper_model_loader loader = {};
    loader.context = asset;
    loader.read = assetRead;
    loader.eof = assetAtEnd;
    loader.close = assetClose;
    whisper_context_params contextParams = whisper_context_default_params();
    contextParams.use_gpu = false;
    return reinterpret_cast<jlong>(whisper_init_with_params(&loader, contextParams));
}

extern "C" JNIEXPORT void JNICALL
Java_com_sameer_yaad_capture_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

/** Returns the transcript, or null if the model failed to run. */
extern "C" JNIEXPORT jstring JNICALL
Java_com_sameer_yaad_capture_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language, jboolean translate,
        jint threads, jstring prompt) {
    auto *context = reinterpret_cast<whisper_context *>(handle);
    JavaString languageCode(env, language);
    JavaString initialPrompt(env, prompt);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    params.beam_search.beam_size = 3;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.no_context = true;
    params.suppress_nst = true;
    params.language = languageCode.get();
    params.translate = translate == JNI_TRUE;
    params.n_threads = threads;
    params.initial_prompt = initialPrompt.get();

    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    const int status = whisper_full(context, params, pcm, env->GetArrayLength(samples));
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    if (status != 0) {
        LOGW("whisper_full failed: %d", status);
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) {
        text += whisper_full_get_segment_text(context, i);
    }
    return env->NewStringUTF(text.c_str());
}
