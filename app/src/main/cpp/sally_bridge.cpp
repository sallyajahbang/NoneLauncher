#include <jni.h>
#include <algorithm>
#include <array>
#include <cstddef>
#include <mutex>

namespace {
constexpr std::size_t kQueueCapacity = 2048;
struct InputEvent {
    jfloat type;
    jfloat code;
    jfloat action;
    jfloat x;
    jfloat y;
    jfloat value;
};

std::array<InputEvent, kQueueCapacity> gQueue{};
std::size_t gHead = 0;
std::size_t gTail = 0;
std::size_t gCount = 0;
std::mutex gQueueMutex;

jboolean enqueue(const InputEvent& event) {
    const std::lock_guard<std::mutex> lock(gQueueMutex);
    if (gCount == kQueueCapacity) {
        return JNI_FALSE; // Never silently overwrite an older key-up event.
    }
    gQueue[gTail] = event;
    gTail = (gTail + 1) % kQueueCapacity;
    ++gCount;
    return JNI_TRUE;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativeBridgeVersion(
    JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF("Sally Native Input Bridge 1.0");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativeSendInput(
    JNIEnv* /* env */, jobject /* thiz */, jint type, jint code, jint action,
    jfloat x, jfloat y, jfloat value) {
    return enqueue(InputEvent{static_cast<jfloat>(type), static_cast<jfloat>(code),
                              static_cast<jfloat>(action), x, y, value});
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativePollEvents(
    JNIEnv* env, jobject /* thiz */, jint maxEvents) {
    const std::size_t boundedMax = static_cast<std::size_t>(std::clamp(maxEvents, 0, 256));
    std::array<jfloat, 256 * 6> batch{};
    std::size_t eventCount = 0;
    {
        const std::lock_guard<std::mutex> lock(gQueueMutex);
        while (eventCount < boundedMax && gCount > 0) {
            const InputEvent& event = gQueue[gHead];
            const std::size_t offset = eventCount * 6;
            batch[offset] = event.type;
            batch[offset + 1] = event.code;
            batch[offset + 2] = event.action;
            batch[offset + 3] = event.x;
            batch[offset + 4] = event.y;
            batch[offset + 5] = event.value;
            gHead = (gHead + 1) % kQueueCapacity;
            --gCount;
            ++eventCount;
        }
    }

    const jsize resultSize = static_cast<jsize>(eventCount * 6);
    jfloatArray result = env->NewFloatArray(resultSize);
    if (result == nullptr || resultSize == 0) return result;
    env->SetFloatArrayRegion(result, 0, resultSize, batch.data());
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativeQueuedEventCount(
    JNIEnv* /* env */, jobject /* thiz */) {
    const std::lock_guard<std::mutex> lock(gQueueMutex);
    return static_cast<jint>(gCount);
}
