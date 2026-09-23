#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <climits>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "stable-diffusion.h"

namespace {

std::mutex g_mutex;
sd_ctx_t* g_ctx = nullptr;
std::string g_model_path;

sd_ctx_t* g_video_ctx = nullptr;
std::string g_video_key;

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


void close_video_context() {
    if (g_video_ctx != nullptr) {
        free_sd_ctx(g_video_ctx);
        g_video_ctx = nullptr;
    }
    g_video_key.clear();
}

bool ensure_video_context(
    JNIEnv* env,
    const std::string& diffusion_path,
    const std::string& vae_path,
    const std::string& text_encoder_path
) {
    const std::string key =
        diffusion_path + "\n" + vae_path + "\n" + text_encoder_path;

    if (g_video_ctx != nullptr && g_video_key == key) {
        return true;
    }

    close_video_context();

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.diffusion_model_path = diffusion_path.c_str();
    params.vae_path = vae_path.c_str();
    params.t5xxl_path = text_encoder_path.c_str();
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

    g_video_ctx = new_sd_ctx(&params);

    if (g_video_ctx == nullptr) {
        throw_runtime(env, "Не удалось загрузить Wan video models.");
        return false;
    }

    if (!sd_ctx_supports_video_generation(g_video_ctx)) {
        close_video_context();
        throw_runtime(env, "Этот комплект не поддерживает video generation.");
        return false;
    }

    g_video_key = key;
    return true;
}

void write_i32_le(
    std::vector<uint8_t>& out,
    size_t offset,
    int32_t value
) {
    out[offset + 0] = static_cast<uint8_t>(value & 0xff);
    out[offset + 1] = static_cast<uint8_t>((value >> 8) & 0xff);
    out[offset + 2] = static_cast<uint8_t>((value >> 16) & 0xff);
    out[offset + 3] = static_cast<uint8_t>((value >> 24) & 0xff);
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
JNIEXPORT jbyteArray JNICALL
Java_tj_kod_assistant_LocalVideoEngine_nativeGenerateVideoRgb(
    JNIEnv* env,
    jobject,
    jstring diffusion_value,
    jstring vae_value,
    jstring text_encoder_value,
    jstring prompt_value,
    jstring negative_value,
    jint width,
    jint height,
    jint steps,
    jint video_frames,
    jint fps,
    jlong seed
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    JStringUtf diffusion_utf(env, diffusion_value);
    JStringUtf vae_utf(env, vae_value);
    JStringUtf text_encoder_utf(env, text_encoder_value);
    JStringUtf prompt_utf(env, prompt_value);
    JStringUtf negative_utf(env, negative_value);

    const std::string diffusion(diffusion_utf.c_str());
    const std::string vae(vae_utf.c_str());
    const std::string text_encoder(text_encoder_utf.c_str());
    const std::string prompt(prompt_utf.c_str());
    const std::string negative(negative_utf.c_str());

    if (diffusion.empty() || vae.empty() || text_encoder.empty()) {
        throw_runtime(env, "Нужны diffusion, VAE и UMT5.");
        return nullptr;
    }

    if (prompt.empty()) {
        throw_runtime(env, "Пустое описание видео.");
        return nullptr;
    }

    const int safe_width =
        std::max(256, std::min(832, static_cast<int>(width)));
    const int safe_height =
        std::max(160, std::min(832, static_cast<int>(height)));
    const int safe_steps =
        std::max(1, std::min(30, static_cast<int>(steps)));
    const int safe_frames =
        std::max(5, std::min(33, static_cast<int>(video_frames)));
    const int safe_fps =
        std::max(4, std::min(24, static_cast<int>(fps)));

    if (!ensure_video_context(env, diffusion, vae, text_encoder)) {
        return nullptr;
    }

    sd_vid_gen_params_t params;
    sd_vid_gen_params_init(&params);
    params.prompt = prompt.c_str();
    params.negative_prompt = negative.c_str();
    params.width = safe_width;
    params.height = safe_height;
    params.seed = static_cast<int64_t>(seed);
    params.video_frames = safe_frames;
    params.fps = safe_fps;
    params.sample_params.sample_steps = safe_steps;
    params.sample_params.sample_method = EULER_SAMPLE_METHOD;
    params.sample_params.scheduler =
        sd_get_default_scheduler(
            g_video_ctx,
            EULER_SAMPLE_METHOD
        );
    params.sample_params.guidance.txt_cfg = 6.0f;
    params.sample_params.flow_shift = 3.0f;

    sd_image_t* frames_out = nullptr;
    int frame_count = 0;
    sd_audio_t* audio_out = nullptr;
    int fps_out = safe_fps;

    const bool ok = generate_video(
        g_video_ctx,
        &params,
        &frames_out,
        &frame_count,
        &audio_out,
        &fps_out
    );

    if (audio_out != nullptr) {
        free_sd_audio(audio_out);
    }

    if (
        !ok ||
        frames_out == nullptr ||
        frame_count < 1 ||
        frames_out[0].data == nullptr
    ) {
        if (frames_out != nullptr) {
            free_sd_images(frames_out, frame_count);
        }
        throw_runtime(env, "Генерация видео не удалась.");
        return nullptr;
    }

    const int out_width =
        static_cast<int>(frames_out[0].width);
    const int out_height =
        static_cast<int>(frames_out[0].height);
    const int out_fps =
        fps_out > 0 ? fps_out : safe_fps;

    const size_t pixels_per_frame =
        static_cast<size_t>(out_width) *
        static_cast<size_t>(out_height);
    const size_t header_size = 20;
    const size_t total_size =
        header_size +
        pixels_per_frame * 3 *
        static_cast<size_t>(frame_count);

    if (total_size > static_cast<size_t>(INT32_MAX)) {
        free_sd_images(frames_out, frame_count);
        throw_runtime(env, "Видео слишком большое для памяти Android.");
        return nullptr;
    }

    std::vector<uint8_t> packed(total_size);
    write_i32_le(packed, 0, 0x3156544B);
    write_i32_le(packed, 4, out_width);
    write_i32_le(packed, 8, out_height);
    write_i32_le(packed, 12, frame_count);
    write_i32_le(packed, 16, out_fps);

    size_t dst = header_size;

    for (int f = 0; f < frame_count; ++f) {
        const sd_image_t& frame = frames_out[f];

        if (
            static_cast<int>(frame.width) != out_width ||
            static_cast<int>(frame.height) != out_height ||
            frame.channel < 3 ||
            frame.data == nullptr
        ) {
            free_sd_images(frames_out, frame_count);
            throw_runtime(env, "Кадры имеют разные размеры.");
            return nullptr;
        }

        for (size_t i = 0; i < pixels_per_frame; ++i) {
            packed[dst++] = frame.data[i * frame.channel + 0];
            packed[dst++] = frame.data[i * frame.channel + 1];
            packed[dst++] = frame.data[i * frame.channel + 2];
        }
    }

    free_sd_images(frames_out, frame_count);

    jbyteArray result = env->NewByteArray(
        static_cast<jsize>(packed.size())
    );
    if (result == nullptr) {
        throw_runtime(env, "Не удалось выделить память для видео.");
        return nullptr;
    }

    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(packed.size()),
        reinterpret_cast<const jbyte*>(packed.data())
    );

    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_tj_kod_assistant_LocalVideoEngine_nativeCloseVideo(
    JNIEnv*,
    jobject
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    close_video_context();
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
