// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

#include "egl_bridge.h"

#include <EGL/egl.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <cstdio>
#include <string>

#define TAG "NoneEgl"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {
EGLDisplay gDisplay = EGL_NO_DISPLAY;
EGLSurface gSurface = EGL_NO_SURFACE;
EGLContext gContext = EGL_NO_CONTEXT;
ANativeWindow *gWindow = nullptr;
std::string gLastError;

// Mencatat langkah yang gagal beserta kode error EGL-nya, lalu mengembalikan false.
bool fail(const char *step) {
    char buf[200];
    std::snprintf(buf, sizeof buf, "%s (EGL 0x%x)", step, eglGetError());
    gLastError = buf;
    LOGE("%s", buf);
    return false;
}
}  // namespace

std::string egl_bridge_last_error() {
    return gLastError;
}

bool egl_bridge_setup(JNIEnv *env, jobject surface) {
    gLastError.clear();

    gWindow = ANativeWindow_fromSurface(env, surface);
    if (gWindow == nullptr) {
        gLastError = "ANativeWindow_fromSurface gagal";
        LOGE("%s", gLastError.c_str());
        return false;
    }

    gDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (gDisplay == EGL_NO_DISPLAY) return fail("eglGetDisplay gagal");
    if (!eglInitialize(gDisplay, nullptr, nullptr)) return fail("eglInitialize gagal");

    const EGLint configAttribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_NONE,
    };
    EGLConfig config = nullptr;
    EGLint numConfigs = 0;
    if (!eglChooseConfig(gDisplay, configAttribs, &config, 1, &numConfigs) || numConfigs < 1) {
        return fail("eglChooseConfig tidak menemukan config");
    }

    // Format piksel harus cocok dengan config sebelum surface dibuat.
    EGLint visualId = 0;
    eglGetConfigAttrib(gDisplay, config, EGL_NATIVE_VISUAL_ID, &visualId);
    ANativeWindow_setBuffersGeometry(gWindow, 0, 0, visualId);

    gSurface = eglCreateWindowSurface(gDisplay, config, gWindow, nullptr);
    if (gSurface == EGL_NO_SURFACE) return fail("eglCreateWindowSurface gagal");

    const EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    gContext = eglCreateContext(gDisplay, config, EGL_NO_CONTEXT, contextAttribs);
    if (gContext == EGL_NO_CONTEXT) return fail("eglCreateContext gagal");

    if (!eglMakeCurrent(gDisplay, gSurface, gSurface, gContext)) {
        return fail("eglMakeCurrent gagal");
    }

    LOGI("EGL GLES2 siap di thread ini");
    return true;
}

void egl_bridge_swap() {
    if (gDisplay != EGL_NO_DISPLAY && gSurface != EGL_NO_SURFACE) {
        eglSwapBuffers(gDisplay, gSurface);
    }
}
