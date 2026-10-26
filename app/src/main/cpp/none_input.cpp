#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cmath>

#define TAG "NoneInput"
static std::atomic<int> seq{0};
static std::atomic<float> dx{0}, dy{0};

extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_pointerDown(JNIEnv*, jobject, jint id, jfloat x, jfloat y){seq.fetch_add(1); __android_log_print(ANDROID_LOG_DEBUG,TAG,"down id=%d %.1f %.1f",id,x,y);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_pointerUp(JNIEnv*, jobject, jint id, jfloat x, jfloat y){seq.fetch_add(1); __android_log_print(ANDROID_LOG_DEBUG,TAG,"up id=%d %.1f %.1f",id,x,y);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_pointerDelta(JNIEnv*, jobject, jfloat x, jfloat y){dx.fetch_add(x); dy.fetch_add(y);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_mouseButton(JNIEnv*, jobject, jint b, jboolean down){seq.fetch_add(1);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_scroll(JNIEnv*, jobject, jfloat x, jfloat y){seq.fetch_add(1);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_key(JNIEnv*, jobject, jint key, jint scan, jboolean down, jint mods){seq.fetch_add(1);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_charInput(JNIEnv*, jobject, jint cp, jint mods){seq.fetch_add(1);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_resize(JNIEnv*, jobject, jint w, jint h){seq.fetch_add(1);}
extern "C" JNIEXPORT void JNICALL Java_com_sally_nonelauncher_input_NativeInput_setGrabbed(JNIEnv*, jobject, jboolean grabbed){__android_log_print(ANDROID_LOG_DEBUG,TAG,"grab=%d",grabbed);}
