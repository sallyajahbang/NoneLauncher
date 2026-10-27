cmake_minimum_required(VERSION 3.22.1)
project(sallybridge LANGUAGES C CXX)

find_package(bytehook REQUIRED CONFIG)

add_library(sallybridge SHARED sally_bridge.cpp)
target_compile_features(sallybridge PRIVATE cxx_std_17)
target_link_libraries(sallybridge PRIVATE dl)
target_compile_options(sallybridge PRIVATE -Wall -Wextra -Werror=return-type)

# PojavLauncher LGPL-3.0 Android compatibility layer. Keep this as a separate
# shared object because the patched LWJGL GLFW classes resolve its JNI symbols
# and its JNI_OnLoad runs once for Android's VM and again for the embedded JVM.
set(POJAV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/pojav")
add_library(pojavexec SHARED
    ${POJAV_DIR}/bigcoreaffinity.c
    ${POJAV_DIR}/egl_bridge.c
    ${POJAV_DIR}/ctxbridges/loader_dlopen.c
    ${POJAV_DIR}/ctxbridges/gl_bridge.c
    ${POJAV_DIR}/ctxbridges/osm_bridge.c
    ${POJAV_DIR}/ctxbridges/egl_loader.c
    ${POJAV_DIR}/ctxbridges/osmesa_loader.c
    ${POJAV_DIR}/ctxbridges/swap_interval_no_egl.c
    ${POJAV_DIR}/environ/environ.c
    ${POJAV_DIR}/jvm_hooks/emui_iterator_fix_hook.c
    ${POJAV_DIR}/jvm_hooks/java_exec_hooks.c
    ${POJAV_DIR}/jvm_hooks/lwjgl_dlopen_hook.c
    ${POJAV_DIR}/input_bridge_v3.c
    ${POJAV_DIR}/jre_launcher.c
    ${POJAV_DIR}/utils.c
    ${POJAV_DIR}/stdio_is.c
    ${POJAV_DIR}/driver_helper/nsbypass.c
)
target_include_directories(pojavexec PRIVATE ${POJAV_DIR})
set_target_properties(pojavexec PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON C_EXTENSIONS ON)
target_compile_options(pojavexec PRIVATE
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-deprecated-declarations
)
target_link_libraries(pojavexec PRIVATE log android dl m)

# Mirror upstream Android.mk: Adreno-specific driver bypasses apply only to arm64.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(pojavexec PRIVATE ADRENO_POSSIBLE)
endif()


# Caciocavallo/AWT JNI bridge. These two libraries are absent from a plain GLFW/LWJGL
# payload but are loaded when Java initializes AWT/Swing or Minecraft libraries that use it.
add_library(pojavexec_awt SHARED ${POJAV_DIR}/awt_bridge.c)
set_target_properties(pojavexec_awt PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON C_EXTENSIONS ON)
target_include_directories(pojavexec_awt PRIVATE ${POJAV_DIR})
target_compile_options(pojavexec_awt PRIVATE -Wno-unused-parameter -Wno-unused-variable)
target_link_libraries(pojavexec_awt PRIVATE log android dl)

# Pojav's Android substitute for desktop X11 AWT symbols. Do not bundle a dummy
# libawt_headless.so: that would shadow the real library shipped inside the selected JRE.
add_library(awt_xawt SHARED ${POJAV_DIR}/awt_xawt/xawt_fake.c)
set_target_properties(awt_xawt PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON C_EXTENSIONS ON)
target_include_directories(awt_xawt PRIVATE ${POJAV_DIR})
target_compile_options(awt_xawt PRIVATE -Wno-unused-parameter -Wno-unused-variable)


# Exit/signal handling used by upstream Pojav. ByteHook is kept as a dynamic
# Android dependency so native System.exit/atexit paths can report the game status.
add_library(exithook SHARED
    ${POJAV_DIR}/native_hooks/exit_hook.c
    ${POJAV_DIR}/native_hooks/chmod_hook.c
)
set_target_properties(exithook PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON C_EXTENSIONS ON)
target_include_directories(exithook PRIVATE ${POJAV_DIR} ${POJAV_DIR}/native_hooks)
target_compile_options(exithook PRIVATE -Wno-unused-parameter -Wno-unused-variable -Wno-deprecated-declarations)
target_link_libraries(exithook PRIVATE pojavexec bytehook::bytehook log android dl)
