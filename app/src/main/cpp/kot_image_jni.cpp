#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "stable-diffusion.h"

namespace {

std::mutex g_mutex;
sd_ctx_t* g_ctx = nullptr;
std::string g_model_path;

class JStringUtf {
public:
    JStringUtf(JNIEnv* env, jstring value)
        : env_(env),
          value_(value),
          chars_(value ? env->GetStringUTFChars(value, nullptr) : nullptr) {}

    ~JStringUtf() {
        if (chars_ && value_) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char* c_str() const {
        return chars_ ? chars_ : "";
    }

private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_;
};

void throw_runtime(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
}

void close_context() {
    if (g_ctx != nullptr) {
        free_sd_ctx(g_ctx);
        g_ctx = nullptr;
    }
    g_model_path.clear();
}

bool ensure_context(
    JNIEnv* env,
    const std::string& model_path
) {
    if (g_ctx != nullptr && g_model_path == model_path) {
        return true;
    }

    close_context();

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);

    params.model_path = model_path.c_str();
    params.enable_mmap = true;
    params.n_threads = std::max(
        2,
        std::min(
            8,
            static_cast<int>(
                std::thread::hardware_concurrency() == 0
                    ? 4
                    : std::thread::hardware_concurrency()
            )
        )
    );

    g_ctx = new_sd_ctx(&params);
    if (g_ctx == nullptr) {
        throw_runtime(
            env,
            "stable-diffusion.cpp не смог загрузить модель. "
            "Проверь файл модели и свободную память."
        );
        return false;
    }

    g_model_path = model_path;
    return true;
}

}  // namespace

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_tj_kod_assistant_LocalImageEngine_nativeGenerateRgb(
    JNIEnv* env,
    jobject,
    jstring model_path_value,
    jstring prompt_value,
    jstring negative_value,
    jint width,
    jint height,
    jint steps,
    jlong seed
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    JStringUtf model_path_utf(env, model_path_value);
    JStringUtf prompt_utf(env, prompt_value);
    JStringUtf negative_utf(env, negative_value);

    const std::string model_path(model_path_utf.c_str());
    const std::string prompt(prompt_utf.c_str());
    const std::string negative(negative_utf.c_str());

    if (model_path.empty()) {
        throw_runtime(env, "Не указан путь к модели изображения.");
        return nullptr;
    }

    if (prompt.empty()) {
        throw_runtime(env, "Пустое описание изображения.");
        return nullptr;
    }

    const int safe_width =
        std::max(256, std::min(1024, static_cast<int>(width)));
    const int safe_height =
        std::max(256, std::min(1024, static_cast<int>(height)));
    const int safe_steps =
        std::max(1, std::min(50, static_cast<int>(steps)));

    if (!ensure_context(env, model_path)) {
        return nullptr;
    }

    sd_img_gen_params_t params;
    sd_img_gen_params_init(&params);

    params.prompt = prompt.c_str();
    params.negative_prompt = negative.c_str();
    params.width = safe_width;
    params.height = safe_height;
    params.seed = static_cast<int64_t>(seed);
    params.batch_count = 1;
    params.sample_params.sample_steps = safe_steps;
    params.sample_params.sample_method =
        sd_get_default_sample_method(g_ctx);
    params.sample_params.scheduler =
        sd_get_default_scheduler(
            g_ctx,
            params.sample_params.sample_method
        );

    sd_image_t* images = nullptr;
    int count = 0;

    const bool ok = generate_image(
        g_ctx,
        &params,
        &images,
        &count
    );

    if (
        !ok ||
        images == nullptr ||
        count < 1 ||
        images[0].data == nullptr
    ) {
        if (images != nullptr) {
            free_sd_images(images, count);
        }
        throw_runtime(env, "Генерация изображения не удалась.");
        return nullptr;
    }

    const sd_image_t& image = images[0];

    if (
        image.width == 0 ||
        image.height == 0 ||
        image.channel < 3
    ) {
        free_sd_images(images, count);
        throw_runtime(env, "Модель вернула некорректное изображение.");
        return nullptr;
    }

    const size_t pixels =
        static_cast<size_t>(image.width) *
        static_cast<size_t>(image.height);
    std::vector<uint8_t> rgb(pixels * 3);

    for (size_t i = 0; i < pixels; ++i) {
        rgb[i * 3 + 0] = image.data[i * image.channel + 0];
        rgb[i * 3 + 1] = image.data[i * image.channel + 1];
        rgb[i * 3 + 2] = image.data[i * image.channel + 2];
    }

    free_sd_images(images, count);

    jbyteArray result = env->NewByteArray(
        static_cast<jsize>(rgb.size())
    );
    if (result == nullptr) {
        throw_runtime(env, "Не удалось выделить память для изображения.");
        return nullptr;
    }

    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(rgb.size()),
        reinterpret_cast<const jbyte*>(rgb.data())
    );

    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_tj_kod_assistant_LocalImageEngine_nativeClose(
    JNIEnv*,
    jobject
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    close_context();
}
