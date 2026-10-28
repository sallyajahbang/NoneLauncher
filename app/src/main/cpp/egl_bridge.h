// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

#pragma once

#include <jni.h>

// Membuat display, surface, dan context GLES2 dari android.view.Surface,
// lalu menjadikannya current di thread yang memanggil fungsi ini.
// gl4es memakai context yang sudah current, jadi thread harus sama dengan thread JVM.
bool egl_bridge_setup(JNIEnv *env, jobject surface);

// Menukar buffer ke layar. Dipanggil dari thread render.
void egl_bridge_swap();
