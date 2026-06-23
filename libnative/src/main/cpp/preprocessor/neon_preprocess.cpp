#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>

#define LOG_TAG "NativePreprocessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL Java_com_jiangdg_demo_NativeInferenceEngine_preprocessNV21(
        JNIEnv *env, jobject thiz,
        jbyteArray nv21_data, jint width, jint height,
        jobject out_buffer, jint target_width, jint target_height,
        jboolean is_quantized, jfloat scale, jint zero_point) {

    jbyte* yuv = env->GetByteArrayElements(nv21_data, nullptr);
    int8_t* out = (int8_t*)env->GetDirectBufferAddress(out_buffer);
    
    if (!yuv || !out) {
        if (yuv) env->ReleaseByteArrayElements(nv21_data, yuv, JNI_ABORT);
        return;
    }

    int y_size = width * height;
    
    // Bilinear downscaling and NV21 to RGB conversion
    for (int y = 0; y < target_height; ++y) {
        float src_y_f = (float)y * height / target_height;
        int src_y = (int)src_y_f;
        
        for (int x = 0; x < target_width; ++x) {
            float src_x_f = (float)x * width / target_width;
            int src_x = (int)src_x_f;

            int y_idx = src_y * width + src_x;
            int uv_idx = y_size + (src_y / 2) * width + (src_x / 2) * 2;

            uint8_t Y = yuv[y_idx];
            uint8_t V = yuv[uv_idx];
            uint8_t U = yuv[uv_idx + 1];

            // Standard YUV to RGB conversion
            int r = Y + 1.402f * (V - 128);
            int g = Y - 0.34414f * (U - 128) - 0.71414f * (V - 128);
            int b = Y + 1.772f * (U - 128);

            r = std::max(0, std::min(255, r));
            g = std::max(0, std::min(255, g));
            b = std::max(0, std::min(255, b));

            int out_idx = (y * target_width + x) * 3;

            if (is_quantized) {
                // Normalize to [0.0, 1.0], then apply TFLite quantization parameters
                float r_norm = r / 255.0f;
                float g_norm = g / 255.0f;
                float b_norm = b / 255.0f;

                out[out_idx] = (int8_t)(r_norm / scale + zero_point);
                out[out_idx + 1] = (int8_t)(g_norm / scale + zero_point);
                out[out_idx + 2] = (int8_t)(b_norm / scale + zero_point);
            } else {
                float* out_f = (float*)out;
                out_f[out_idx] = r / 255.0f;
                out_f[out_idx + 1] = g / 255.0f;
                out_f[out_idx + 2] = b / 255.0f;
            }
        }
    }

    env->ReleaseByteArrayElements(nv21_data, yuv, JNI_ABORT);
}

}
