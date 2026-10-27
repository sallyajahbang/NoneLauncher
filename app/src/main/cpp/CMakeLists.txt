cmake_minimum_required(VERSION 3.22.1)
project(sallybridge LANGUAGES C CXX)

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
