cmake_minimum_required(VERSION 3.22.1)
project("deltapatcher")

add_subdirectory(libs)

add_library(${PROJECT_NAME} SHARED
    cpp/native.cpp
    cpp/XDeltaPatch.cpp
    cpp/utils/base64.cpp
)

target_link_libraries(${PROJECT_NAME} PRIVATE
    android
    xdelta3
    liblzma
    log
)