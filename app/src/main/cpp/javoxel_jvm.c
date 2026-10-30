/*
 * Javoxel JVM launcher.
 *
 * Membuka libjli.so dari JRE yang sudah diekstrak, lalu memanggil JLI_Launch
 * (launcher resmi OpenJDK) di dalam proses app ini. JLI_Launch memblokir sampai
 * JVM selesai, dan biasanya menutup proses. Panggil dari thread latar, jangan
 * dari UI thread.
 *
 * Output (stdout, stderr) dan log native diarahkan ke file log app, jadi bisa
 * dibaca di dalam app tanpa logcat. Crash native ikut dicatat dengan backtrace.
 *
 * Signature JLI_Launch sama untuk JDK 8 sampai 21.
 */
#define _POSIX_C_SOURCE 200809L

#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <unwind.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "Javoxel"
#define ANDROID_LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ANDROID_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define ANDROID_LOGI(...) ((void)0)
#define ANDROID_LOGE(...) ((void)0)
#endif

/* stderr sudah diarahkan ke file log app, jadi pesan ini ikut terbaca di dalam app. */
#define LOGI(...) do { fprintf(stderr, "[native] " __VA_ARGS__); fputc('\n', stderr); ANDROID_LOGI(__VA_ARGS__); } while (0)
#define LOGE(...) do { fprintf(stderr, "[native E] " __VA_ARGS__); fputc('\n', stderr); ANDROID_LOGE(__VA_ARGS__); } while (0)

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

/* ---- Penulisan aman untuk signal handler (hanya write()) ---- */

static void write_str(const char *s) {
    (void)write(STDERR_FILENO, s, strlen(s));
}

static void write_dec(int v) {
    char buf[16];
    int i = (int)sizeof(buf) - 1;
    buf[i] = '\0';
    unsigned int u = v < 0 ? (unsigned int)(-v) : (unsigned int)v;
    do {
        buf[--i] = (char)('0' + (u % 10));
        u /= 10;
    } while (u && i > 1);
    if (v < 0) buf[--i] = '-';
    write_str(buf + i);
}

static void write_hex(uintptr_t v) {
    static const char digits[] = "0123456789abcdef";
    char buf[2 + sizeof(uintptr_t) * 2 + 1];
    int i = (int)sizeof(buf) - 1;
    buf[i] = '\0';
    do {
        buf[--i] = digits[v & 0xf];
        v >>= 4;
    } while (v && i > 2);
    buf[--i] = 'x';
    buf[--i] = '0';
    write_str(buf + i);
}

static _Unwind_Reason_Code unwind_cb(struct _Unwind_Context *ctx, void *arg) {
    int *count = (int *)arg;
    uintptr_t pc = (uintptr_t)_Unwind_GetIP(ctx);
    if (pc != 0 && *count < 40) {
        write_str("  pc ");
        write_hex(pc);
        write_str("\n");
        (*count)++;
    }
    return _URC_NO_REASON;
}

static void crash_handler(int sig) {
    write_str("\n[native CRASH] signal ");
    write_dec(sig);
    write_str("\n");
    int count = 0;
    _Unwind_Backtrace(unwind_cb, &count);
    write_str("[native CRASH] backtrace selesai\n");
    signal(sig, SIG_DFL);
    raise(sig);
}

static void install_crash_handler(void) {
    static const int sigs[] = {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE};
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = crash_handler;
    sigemptyset(&sa.sa_mask);
    for (size_t i = 0; i < sizeof(sigs) / sizeof(sigs[0]); i++) {
        sigaction(sigs[i], &sa, NULL);
    }
}

static void on_exit_hook(void) {
    write_str("[native] proses keluar (exit dipanggil)\n");
}

/* ---- JNI ---- */

static void throw_runtime(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

/*
 * Kotlin: private external fun redirectOutput(path: String)
 * Mengarahkan stdout dan stderr ke file log, dan memasang catcher crash native.
 */
JNIEXPORT void JNICALL
Java_com_sally_javoxel_runtime_JvmLauncher_redirectOutput(JNIEnv *env, jobject thiz, jstring jpath) {
    (void)thiz;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (path == NULL) return;

    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
    } else {
        LOGE("gagal membuka file log %s", path);
    }

    install_crash_handler();
    atexit(on_exit_hook);
    LOGI("log native aktif: %s", path);

    (*env)->ReleaseStringUTFChars(env, jpath, path);
}

/*
 * Kotlin: private external fun preloadLibraries(dir: String): Int
 *
 * Memuat semua .so di folder lib JRE dengan RTLD_GLOBAL. Linker Android tidak mencari
 * dependency (DT_NEEDED) di folder JRE, jadi library yang sudah dimuat dengan nama yang
 * sama akan dipakai ulang saat library lain memintanya (mis. libnio butuh libnet).
 * Diulang selama masih ada kemajuan, karena urutan dependency belum tentu benar.
 */
#define PRELOAD_MAX 512

JNIEXPORT jint JNICALL
Java_com_sally_javoxel_runtime_JvmLauncher_preloadLibraries(JNIEnv *env, jobject thiz, jstring jdir) {
    (void)thiz;
    const char *dir = (*env)->GetStringUTFChars(env, jdir, NULL);
    if (dir == NULL) return -1;

    DIR *d = opendir(dir);
    if (d == NULL) {
        LOGE("preload: gagal membuka folder %s", dir);
        (*env)->ReleaseStringUTFChars(env, jdir, dir);
        return -1;
    }

    char *names[PRELOAD_MAX];
    int n = 0;
    struct dirent *ent;
    while ((ent = readdir(d)) != NULL && n < PRELOAD_MAX) {
        size_t len = strlen(ent->d_name);
        if (len > 3 && strcmp(ent->d_name + len - 3, ".so") == 0) {
            names[n++] = strdup(ent->d_name);
        }
    }
    closedir(d);

    int done[PRELOAD_MAX];
    memset(done, 0, sizeof(done));
    char path[4096];
    int loaded = 0;
    int progress = 1;
    while (progress) {
        progress = 0;
        for (int i = 0; i < n; i++) {
            if (done[i]) continue;
            snprintf(path, sizeof(path), "%s/%s", dir, names[i]);
            if (dlopen(path, RTLD_LAZY | RTLD_GLOBAL) != NULL) {
                done[i] = 1;
                loaded++;
                progress = 1;
            }
        }
    }

    LOGI("preload: %d dari %d library dimuat", loaded, n);
    for (int i = 0; i < n; i++) {
        if (!done[i]) {
            snprintf(path, sizeof(path), "%s/%s", dir, names[i]);
            dlopen(path, RTLD_LAZY | RTLD_GLOBAL);
            const char *err = dlerror();
            LOGE("preload gagal %s: %s", names[i], err ? err : "?");
        }
        free(names[i]);
    }

    (*env)->ReleaseStringUTFChars(env, jdir, dir);
    return loaded;
}

/*
 * Kotlin: private external fun launchJvm(jliPath: String, fullVersion: String,
 *                                        dotVersion: String, args: Array<String>): Int
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
    LOGI("libjli dimuat: %s", path);

    JLI_Launch_t jli_launch = (JLI_Launch_t)dlsym(libjli, "JLI_Launch");
    if (jli_launch == NULL) {
        LOGE("JLI_Launch tidak ditemukan di libjli.so");
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

    LOGI("JLI_Launch dengan %d argumen, mulai JVM", (int)argc);
    fflush(stdout);

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
