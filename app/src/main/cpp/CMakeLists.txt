cmake_minimum_required(VERSION 3.22.1)

# JVM launcher bridge, ported from ZalithLauncher2 (GPL-3.0) and the OpenJDK launcher.
# See NOTICE.md in this folder for the origin of each file.
project(nonelauncher_jvm C)

add_library(nonelauncher_jvm SHARED
        jre_launcher.c
        utils.c
        stdio_is.c
        logger/logger.c
        environ/environ.c
)

target_include_directories(nonelauncher_jvm PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_options(nonelauncher_jvm PRIVATE -Wall -Wno-unused-parameter)

target_link_libraries(nonelauncher_jvm
        log
        dl
        android
)
