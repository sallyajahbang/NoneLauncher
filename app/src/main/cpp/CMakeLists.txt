cmake_minimum_required(VERSION 3.22.1)
project(sallybridge LANGUAGES CXX)

add_library(sallybridge SHARED sally_bridge.cpp)
target_compile_features(sallybridge PRIVATE cxx_std_17)
target_link_libraries(sallybridge PRIVATE dl)
target_compile_options(sallybridge PRIVATE -Wall -Wextra -Werror=return-type)
