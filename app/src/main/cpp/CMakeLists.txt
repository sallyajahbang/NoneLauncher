cmake_minimum_required(VERSION 3.22.1)
project(noneinput)
add_library(noneinput SHARED none_input.cpp)
target_link_libraries(noneinput android log)

target_compile_features(noneinput PRIVATE cxx_std_17)
