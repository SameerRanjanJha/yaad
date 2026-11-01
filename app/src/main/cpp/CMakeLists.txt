cmake_minimum_required(VERSION 3.22.1)
project(yaad_whisper CXX C)

set(CMAKE_CXX_STANDARD 17)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Faster than baseline arm64, but crashes on chips older than about 2019.
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif ()

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp whisper-build)

add_library(yaad_whisper SHARED yaad_whisper.cpp)
target_link_libraries(yaad_whisper PRIVATE whisper android log)
# --exclude-libs keeps this library's ggml from clashing with the copy inside the language model.
target_link_options(yaad_whisper PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
