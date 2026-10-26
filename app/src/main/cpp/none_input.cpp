#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <deque>
#include <mutex>
#include <new>

#define TAG "NoneInput"

namespace {
enum EventType : jint {
    EVENT_POINTER_DOWN = 1,
    EVENT_POINTER_UP = 2,
    EVENT_POINTER_DELTA = 3,
    EVENT_MOUSE_BUTTON = 4,
    EVENT_SCROLL = 5,
    EVENT_KEY = 6,
    EVENT_CHAR = 7,
    EVENT_RESIZE = 8,
    EVENT_GRAB = 9,
    EVENT_POINTER_MOVE = 10,
    EVENT_MOUSE_DELTA = 11
};

struct NativeEvent {
    jint type;
    jint code;
    jfloat x;
    jfloat y;
    jfloat value;
    jint modifiers;
};

constexpr std::size_t MAX_PENDING_EVENTS = 512;
std::mutex eventMutex;
std::deque<NativeEvent> pendingEvents;
std::uint64_t droppedEvents = 0;

void pushEvent(jint type, jint code = 0, jfloat x = 0.0f, jfloat y = 0.0f,
               jfloat value = 0.0f, jint modifiers = 0) {
    std::lock_guard<std::mutex> lock(eventMutex);
    if (pendingEvents.size() >= MAX_PENDING_EVENTS) {
        pendingEvents.pop_front();
        ++droppedEvents;
    }
    pendingEvents.push_back(NativeEvent{type, code, x, y, value, modifiers});
}
}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerDown(
        JNIEnv*, jobject, jint id, jfloat x, jfloat y) {
    pushEvent(EVENT_POINTER_DOWN, id, x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerUp(
        JNIEnv*, jobject, jint id, jfloat x, jfloat y) {
    pushEvent(EVENT_POINTER_UP, id, x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerMove(
        JNIEnv*, jobject, jint id, jfloat x, jfloat y) {
    pushEvent(EVENT_POINTER_MOVE, id, x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerDelta(
        JNIEnv*, jobject, jfloat x, jfloat y) {
    pushEvent(EVENT_POINTER_DELTA, 0, x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_mouseDelta(
        JNIEnv*, jobject, jfloat x, jfloat y) {
    pushEvent(EVENT_MOUSE_DELTA, 0, x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_mouseButton(
        JNIEnv*, jobject, jint button, jboolean down) {
    pushEvent(EVENT_MOUSE_BUTTON, button, 0.0f, 0.0f, down == JNI_TRUE ? 1.0f : 0.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_scroll(
        JNIEnv*, jobject, jfloat x, jfloat y) {
    pushEvent(EVENT_SCROLL, 0, x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_key(
        JNIEnv*, jobject, jint key, jint scanCode, jboolean down, jint mods) {
    pushEvent(EVENT_KEY, key, static_cast<jfloat>(scanCode),
              down == JNI_TRUE ? 1.0f : 0.0f, 0.0f, mods);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_charInput(
        JNIEnv*, jobject, jint codePoint, jint mods) {
    pushEvent(EVENT_CHAR, codePoint, 0.0f, 0.0f, 0.0f, mods);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_resize(
        JNIEnv*, jobject, jint width, jint height) {
    pushEvent(EVENT_RESIZE, 0, static_cast<jfloat>(width), static_cast<jfloat>(height));
}

extern "C" JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_setGrabbed(
        JNIEnv*, jobject, jboolean grabbed) {
    pushEvent(EVENT_GRAB, 0, 0.0f, 0.0f, grabbed == JNI_TRUE ? 1.0f : 0.0f);
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "grab=%d", grabbed == JNI_TRUE ? 1 : 0);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_sally_nonelauncher_input_NativeInput_drainEvents(JNIEnv* env, jobject) {
    std::deque<NativeEvent> events;
    {
        std::lock_guard<std::mutex> lock(eventMutex);
        events.swap(pendingEvents);
    }

    constexpr jsize EVENT_WIDTH = 6;
    const jsize valueCount = static_cast<jsize>(events.size()) * EVENT_WIDTH;
    jfloatArray result = env->NewFloatArray(valueCount);
    if (result == nullptr) return nullptr;

    if (valueCount == 0) return result;

    jfloat* values = new (std::nothrow) jfloat[static_cast<std::size_t>(valueCount)];
    if (values == nullptr) {
        env->DeleteLocalRef(result);
        return nullptr;
    }

    jsize offset = 0;
    for (const NativeEvent& event : events) {
        values[offset++] = static_cast<jfloat>(event.type);
        values[offset++] = static_cast<jfloat>(event.code);
        values[offset++] = event.x;
        values[offset++] = event.y;
        values[offset++] = event.value;
        values[offset++] = static_cast<jfloat>(event.modifiers);
    }

    env->SetFloatArrayRegion(result, 0, valueCount, values);
    delete[] values;
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_sally_nonelauncher_input_NativeInput_droppedEventCount(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(eventMutex);
    return static_cast<jlong>(droppedEvents);
}
