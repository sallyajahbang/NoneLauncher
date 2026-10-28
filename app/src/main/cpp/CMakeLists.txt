# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Buntuk

cmake_minimum_required(VERSION 3.22.1)
project(nonelauncher_jvm C CXX)

# gl4es (MIT, lihat third_party/gl4es/LICENSE): OpenGL ke OpenGL ES untuk Android.
# Opsi ANDROID mengaktifkan -DANDROID -DNOX11 -DNO_GBM -DDEFAULT_ES=2 di CMakeLists-nya.
set(ANDROID ON)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/gl4es
                 ${CMAKE_CURRENT_BINARY_DIR}/gl4es)

add_library(nonelauncher_jvm SHARED jvm_launcher.cpp egl_bridge.cpp)
target_link_libraries(nonelauncher_jvm EGL android log dl)
