#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cmath>

#define TAG "NoneInput"

// Sequence/event counter
static std::atomic<int> seq{0};

// Android NDK/toolchain tertentu tidak menyediakan
// std::atomic<float>::fetch_add().
//
// Gunakan fixed-point integer:
// 1000 = 1.0f
constexpr float INPUT_SCALE = 1000.0f;

static std::atomic<int> dx{0};
static std::atomic<int> dy{0};

static inline int toFixed(float value) {
    return static_cast<int>(std::lround(value * INPUT_SCALE));
}

static inline float fromFixed(int value) {
    return static_cast<float>(value) / INPUT_SCALE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerDown(
        JNIEnv*,
        jobject,
        jint id,
        jfloat x,
        jfloat y) {

    seq.fetch_add(1, std::memory_order_relaxed);

    __android_log_print(
        ANDROID_LOG_DEBUG,
        TAG,
        "down id=%d %.1f %.1f",
        id,
        x,
        y
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerUp(
        JNIEnv*,
        jobject,
        jint id,
        jfloat x,
        jfloat y) {

    seq.fetch_add(1, std::memory_order_relaxed);

    __android_log_print(
        ANDROID_LOG_DEBUG,
        TAG,
        "up id=%d %.1f %.1f",
        id,
        x,
        y
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_pointerDelta(
        JNIEnv*,
        jobject,
        jfloat x,
        jfloat y) {

    dx.fetch_add(
        toFixed(x),
        std::memory_order_relaxed
    );

    dy.fetch_add(
        toFixed(y),
        std::memory_order_relaxed
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_mouseButton(
        JNIEnv*,
        jobject,
        jint b,
        jboolean down) {

    (void)b;
    (void)down;

    seq.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_scroll(
        JNIEnv*,
        jobject,
        jfloat x,
        jfloat y) {

    (void)x;
    (void)y;

    seq.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_key(
        JNIEnv*,
        jobject,
        jint key,
        jint scan,
        jboolean down,
        jint mods) {

    (void)key;
    (void)scan;
    (void)down;
    (void)mods;

    seq.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_charInput(
        JNIEnv*,
        jobject,
        jint cp,
        jint mods) {

    (void)cp;
    (void)mods;

    seq.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_resize(
        JNIEnv*,
        jobject,
        jint w,
        jint h) {

    (void)w;
    (void)h;

    seq.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sally_nonelauncher_input_NativeInput_setGrabbed(
        JNIEnv*,
        jobject,
        jboolean grabbed) {

    __android_log_print(
        ANDROID_LOG_DEBUG,
        TAG,
        "grab=%d",
        grabbed
    );
}