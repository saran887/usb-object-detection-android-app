#include <jni.h>
#include <vector>
#include <algorithm>

struct BBox {
    float x1, y1, x2, y2;
    float score;
    int class_id;
};

inline float compute_iou(const BBox& a, const BBox& b) {
    float inter_x1 = std::max(a.x1, b.x1);
    float inter_y1 = std::max(a.y1, b.y1);
    float inter_x2 = std::min(a.x2, b.x2);
    float inter_y2 = std::min(a.y2, b.y2);

    float inter_w = std::max(0.0f, inter_x2 - inter_x1);
    float inter_h = std::max(0.0f, inter_y2 - inter_y1);
    float inter_area = inter_w * inter_h;

    if (inter_area == 0.0f) return 0.0f;

    float area_a = (a.x2 - a.x1) * (a.y2 - a.y1);
    float area_b = (b.x2 - b.x1) * (b.y2 - b.y1);
    return inter_area / (area_a + area_b - inter_area);
}

extern "C" {

JNIEXPORT jint JNICALL Java_com_jiangdg_demo_NativeInferenceEngine_nativeNMS(
        JNIEnv *env, jobject thiz,
        jobject output_buffer, jint num_anchors, jint num_elements,
        jfloat score_threshold, jfloat iou_threshold,
        jfloatArray out_detections, jint max_detections) {

    float* output = (float*)env->GetDirectBufferAddress(output_buffer);
    jfloat* detections = env->GetFloatArrayElements(out_detections, nullptr);

    if (!output || !detections) {
        if (detections) env->ReleaseFloatArrayElements(out_detections, detections, JNI_ABORT);
        return 0;
    }

    std::vector<BBox> candidates;
    
    // Parse YOLOv8 output tensor: shape [84, 2100]
    for (int i = 0; i < num_anchors; ++i) {
        float max_class_score = 0.0f;
        int max_class_id = -1;

        for (int c = 0; c < num_elements - 4; ++c) {
            float score = output[(c + 4) * num_anchors + i];
            if (score > max_class_score) {
                max_class_score = score;
                max_class_id = c;
            }
        }

        if (max_class_score >= score_threshold) {
            float cx = output[0 * num_anchors + i];
            float cy = output[1 * num_anchors + i];
            float w = output[2 * num_anchors + i];
            float h = output[3 * num_anchors + i];

            // Normalize coordinate conversions
            float x1 = cx - w / 2.0f;
            float y1 = cy - h / 2.0f;
            float x2 = cx + w / 2.0f;
            float y2 = cy + h / 2.0f;

            candidates.push_back({x1, y1, x2, y2, max_class_score, max_class_id});
        }
    }

    // Sort by confidence descending
    std::sort(candidates.begin(), candidates.end(), [](const BBox& a, const BBox& b) {
        return a.score > b.score;
    });

    std::vector<BBox> kept;
    std::vector<bool> suppressed(candidates.size(), false);

    for (size_t i = 0; i < candidates.size(); ++i) {
        if (suppressed[i]) continue;
        kept.push_back(candidates[i]);
        if (kept.size() >= (size_t)max_detections) break;

        for (size_t j = i + 1; j < candidates.size(); ++j) {
            if (suppressed[j]) continue;
            if (candidates[i].class_id == candidates[j].class_id) {
                if (compute_iou(candidates[i], candidates[j]) > iou_threshold) {
                    suppressed[j] = true;
                }
            }
        }
    }

    // Copy kept detections back to Java flat float array
    // Format per detection: [class_id, score, x1, y1, x2, y2]
    int count = std::min((int)kept.size(), max_detections);
    for (int i = 0; i < count; ++i) {
        int idx = i * 6;
        detections[idx] = (float)kept[i].class_id;
        detections[idx + 1] = kept[i].score;
        detections[idx + 2] = kept[i].x1;
        detections[idx + 3] = kept[i].y1;
        detections[idx + 4] = kept[i].x2;
        detections[idx + 5] = kept[i].y2;
    }

    env->ReleaseFloatArrayElements(out_detections, detections, 0);
    return count;
}

}
