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

/** For the debug speech test, which tries model files that aren't in the APK. */
extern "C" JNIEXPORT jlong JNICALL
Java_com_sameer_yaad_capture_WhisperNative_loadFile(JNIEnv *env, jobject, jstring modelPath) {
    JavaString path(env, modelPath);
    whisper_context_params contextParams = whisper_context_default_params();
    contextParams.use_gpu = false;
    return reinterpret_cast<jlong>(whisper_init_from_file_with_params(path.get(), contextParams));
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

/**
 * Finds the stretches of speech, anyone's, as start and end seconds one after another.
 * Returns null if the detector can't be loaded.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_sameer_yaad_capture_WhisperNative_speechStretches(
        JNIEnv *env, jobject, jstring detectorPath, jfloatArray samples, jint threads) {
    JavaString path(env, detectorPath);
    whisper_vad_context_params contextParams = whisper_vad_default_context_params();
    contextParams.n_threads = threads;
    contextParams.use_gpu = false;
    whisper_vad_context *detector = whisper_vad_init_from_file_with_params(path.get(), contextParams);
    if (detector == nullptr) {
        LOGW("Speech detector failed to load: %s", path.get());
        return nullptr;
    }
    whisper_vad_params params = whisper_vad_default_params();
    // Phrases rather than words, so each stretch is long enough to judge how loud it is.
    params.min_silence_duration_ms = 500;
    params.speech_pad_ms = 200;

    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    whisper_vad_segments *segments = whisper_vad_segments_from_samples(detector, params, pcm, env->GetArrayLength(samples));
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);

    const int count = segments == nullptr ? 0 : whisper_vad_segments_n_segments(segments);
    jfloatArray result = env->NewFloatArray(count * 2);
    for (int i = 0; i < count; ++i) {
        // The detector reports centiseconds.
        const jfloat bounds[2] = {
            whisper_vad_segments_get_segment_t0(segments, i) / 100.0f,
            whisper_vad_segments_get_segment_t1(segments, i) / 100.0f,
        };
        env->SetFloatArrayRegion(result, i * 2, 2, bounds);
    }
    if (segments != nullptr) whisper_vad_free_segments(segments);
    whisper_vad_free(detector);
    return result;
}
