/*
 * Javoxel JVM launcher.
 *
 * Membuka libjli.so dari JRE yang sudah diekstrak, lalu memanggil JLI_Launch
 * (launcher resmi OpenJDK) di dalam proses app ini. JLI_Launch memblokir sampai
 * JVM selesai, dan biasanya menutup proses. Panggil dari thread latar, jangan
 * dari UI thread.
 *
 * Signature JLI_Launch sama untuk JDK 8 sampai 21.
 */
#define _POSIX_C_SOURCE 200809L

#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "Javoxel"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#else
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#define LOGI(...) fprintf(stderr, __VA_ARGS__)
#endif

typedef jint (*JLI_Launch_t)(int argc, char **argv,
                             int jargc, const char **jargv,
                             int appclassc, const char **appclassv,
                             const char *fullversion,
                             const char *dotversion,
                             const char *pname,
                             const char *lname,
                             jboolean javaargs,
                             jboolean cpwildcard,
                             jboolean javaw,
                             jint ergo);

static void throw_runtime(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

/*
 * Kotlin: private external fun launchJvm(jliPath: String, fullVersion: String,
 *                                        dotVersion: String, args: Array<String>): Int
 * Kelas: com.sally.javoxel.runtime.JvmLauncher (object, jadi method instance).
 */
JNIEXPORT jint JNICALL
Java_com_sally_javoxel_runtime_JvmLauncher_launchJvm(JNIEnv *env, jobject thiz,
                                                     jstring jliPath,
                                                     jstring fullVersion,
                                                     jstring dotVersion,
                                                     jobjectArray args) {
    (void)thiz;

    const char *path = (*env)->GetStringUTFChars(env, jliPath, NULL);
    const char *full = (*env)->GetStringUTFChars(env, fullVersion, NULL);
    const char *dot = (*env)->GetStringUTFChars(env, dotVersion, NULL);
    if (path == NULL || full == NULL || dot == NULL) {
        throw_runtime(env, "Gagal membaca string dari JVM launcher");
        goto release_strings;
    }

    void *libjli = dlopen(path, RTLD_LAZY | RTLD_GLOBAL);
    if (libjli == NULL) {
        const char *err = dlerror();
        LOGE("dlopen %s gagal: %s", path, err ? err : "?");
        throw_runtime(env, err ? err : "dlopen libjli.so gagal");
        goto release_strings;
    }

    JLI_Launch_t jli_launch = (JLI_Launch_t)dlsym(libjli, "JLI_Launch");
    if (jli_launch == NULL) {
        throw_runtime(env, "JLI_Launch tidak ditemukan di libjli.so");
        goto release_strings;
    }

    jsize argc = (*env)->GetArrayLength(env, args);
    if (argc == 0) {
        throw_runtime(env, "Argumen JVM kosong");
        goto release_strings;
    }

    /* Salin argumen ke C. Sengaja tidak di-free: JLI_Launch bisa menyimpan pointer-nya. */
    char **argv = calloc((size_t)argc + 1, sizeof(char *));
    if (argv == NULL) {
        throw_runtime(env, "Kehabisan memori untuk argumen JVM");
        goto release_strings;
    }
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        const char *utf = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(utf != NULL ? utf : "");
        (*env)->ReleaseStringUTFChars(env, s, utf);
        (*env)->DeleteLocalRef(env, s);
    }
    argv[argc] = NULL;

    LOGI("JLI_Launch dengan %d argumen", (int)argc);

    jint code = jli_launch(
        (int)argc, argv,
        0, NULL,            /* jargs: tidak dipakai */
        0, NULL,            /* appclasspath: tidak dipakai */
        full,               /* fullversion */
        dot,                /* dotversion */
        argv[0],            /* pname */
        argv[0],            /* lname */
        JNI_FALSE,          /* javaargs */
        JNI_FALSE,          /* cpwildcard: classpath kami sudah lengkap */
        JNI_FALSE,          /* javaw (khusus Windows) */
        0                   /* ergo: DEFAULT_POLICY */
    );

    /* Hanya sampai sini jika JVM kembali tanpa exit(). */
    LOGI("JLI_Launch kembali dengan kode %d", (int)code);
    (*env)->ReleaseStringUTFChars(env, jliPath, path);
    (*env)->ReleaseStringUTFChars(env, fullVersion, full);
    (*env)->ReleaseStringUTFChars(env, dotVersion, dot);
    return code;

release_strings:
    if (path != NULL) (*env)->ReleaseStringUTFChars(env, jliPath, path);
    if (full != NULL) (*env)->ReleaseStringUTFChars(env, fullVersion, full);
    if (dot != NULL) (*env)->ReleaseStringUTFChars(env, dotVersion, dot);
    return -1;
}
