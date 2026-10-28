// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

// Memuat libjvm.so dari JRE yang sudah dipasang, lalu menjalankan mainClass
// di dalam proses app. Android tidak bisa mengeksekusi binary `java` begitu saja,
// jadi JVM dibuat lewat JNI_CreateJavaVM.
//
// Panggil dari thread latar belakang. Fungsi ini memblokir sampai main() selesai.

#include <jni.h>
#include <dlfcn.h>

// Beberapa versi NDK tidak mendefinisikan JNI_VERSION_1_8 di jni.h.
#ifndef JNI_VERSION_1_8
#define JNI_VERSION_1_8 0x00010008
#endif

#include <android/log.h>

#include <string>
#include <vector>

#define TAG "NoneJvm"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef jint (*JNI_CreateJavaVM_t)(JavaVM **, void **, void *);

namespace {

std::string toStd(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

std::vector<std::string> toStdVector(JNIEnv *env, jobjectArray arr) {
    std::vector<std::string> out;
    if (arr == nullptr) return out;
    jsize n = env->GetArrayLength(arr);
    out.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        out.push_back(toStd(env, item));
        env->DeleteLocalRef(item);
    }
    return out;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_sally_nonelauncher_runtime_jvm_NativeJvm_run(
        JNIEnv *env, jclass,
        jstring jLibjvm, jobjectArray jOptions,
        jstring jMainClass, jobjectArray jArgs) {

    // Ambil semua string dulu, selagi env dari pemanggil masih aktif.
    const std::string libjvm = toStd(env, jLibjvm);
    std::vector<std::string> options = toStdVector(env, jOptions);
    std::string mainName = toStd(env, jMainClass);
    std::vector<std::string> args = toStdVector(env, jArgs);

    for (auto &ch : mainName) {
        if (ch == '.') ch = '/';
    }

    void *handle = dlopen(libjvm.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        LOGE("dlopen gagal untuk %s: %s", libjvm.c_str(), dlerror());
        return -1;
    }

    auto create = reinterpret_cast<JNI_CreateJavaVM_t>(dlsym(handle, "JNI_CreateJavaVM"));
    if (create == nullptr) {
        LOGE("JNI_CreateJavaVM tidak ditemukan di %s", libjvm.c_str());
        return -2;
    }

    // Pointer ke isi std::string tetap valid karena vector sudah di-reserve sebelumnya.
    std::vector<JavaVMOption> opts(options.size());
    for (size_t i = 0; i < options.size(); ++i) {
        opts[i].optionString = const_cast<char *>(options[i].c_str());
        opts[i].extraInfo = nullptr;
    }

    JavaVMInitArgs vmArgs{};
    vmArgs.version = JNI_VERSION_1_8;
    vmArgs.nOptions = static_cast<jint>(opts.size());
    vmArgs.options = opts.data();
    vmArgs.ignoreUnrecognized = JNI_FALSE;

    JavaVM *jvm = nullptr;
    JNIEnv *jenv = nullptr;
    jint rc = create(&jvm, reinterpret_cast<void **>(&jenv), &vmArgs);
    if (rc != JNI_OK) {
        LOGE("JNI_CreateJavaVM gagal: %d", rc);
        return -3;
    }
    LOGI("JVM berhasil dibuat, memanggil %s", mainName.c_str());

    int result = 0;
    jclass mainCls = jenv->FindClass(mainName.c_str());
    if (mainCls == nullptr) {
        LOGE("Kelas %s tidak ditemukan", mainName.c_str());
        jenv->ExceptionDescribe();
        jenv->ExceptionClear();
        result = -4;
    } else {
        jmethodID mainId = jenv->GetStaticMethodID(mainCls, "main", "([Ljava/lang/String;)V");
        if (mainId == nullptr) {
            LOGE("main(String[]) tidak ditemukan di %s", mainName.c_str());
            jenv->ExceptionClear();
            result = -5;
        } else {
            jclass strCls = jenv->FindClass("java/lang/String");
            jobjectArray jargs = jenv->NewObjectArray(static_cast<jsize>(args.size()), strCls, nullptr);
            for (size_t i = 0; i < args.size(); ++i) {
                jstring s = jenv->NewStringUTF(args[i].c_str());
                jenv->SetObjectArrayElement(jargs, static_cast<jsize>(i), s);
                jenv->DeleteLocalRef(s);
            }

            jenv->CallStaticVoidMethod(mainCls, mainId, jargs);
            if (jenv->ExceptionCheck()) {
                jenv->ExceptionDescribe();
                jenv->ExceptionClear();
                result = -6;
            }
        }
    }

    // VM dibiarkan hidup. Minecraft biasanya keluar lewat System.exit atau
    // setelah main() selesai. DestroyJavaVM akan memblokir sampai semua thread selesai.
    jvm->DetachCurrentThread();
    LOGI("main() selesai dengan kode %d", result);
    return result;
}
