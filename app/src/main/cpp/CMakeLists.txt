# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Buntuk

cmake_minimum_required(VERSION 3.22.1)
project(nonelauncher_jvm C CXX)

add_library(nonelauncher_jvm SHARED jvm_launcher.cpp)
target_link_libraries(nonelauncher_jvm log dl)
