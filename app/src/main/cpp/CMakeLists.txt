cmake_minimum_required(VERSION 3.22)
project(javoxel_jvm C)

add_library(javoxel_jvm SHARED javoxel_jvm.c)
target_link_libraries(javoxel_jvm log dl)
