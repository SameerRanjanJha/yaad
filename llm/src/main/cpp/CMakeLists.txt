cmake_minimum_required(VERSION 3.22.1)
project(yaad_llm CXX C)

set(CMAKE_CXX_STANDARD 17)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Faster than baseline arm64, but crashes on chips older than about 2019. x86_64 only serves the emulator.
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
elseif (ANDROID_ABI STREQUAL "x86_64")
    # The emulator's virtual CPU stops at SSE4.2: it has no AVX.
    set(GGML_SSE42 ON CACHE BOOL "" FORCE)
    foreach (instruction_set GGML_AVX GGML_AVX2 GGML_FMA GGML_F16C GGML_BMI2 GGML_AVX_VNNI)
        set(${instruction_set} OFF CACHE BOOL "" FORCE)
    endforeach ()
endif ()

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp llama-build)

add_library(yaad_llm SHARED yaad_llm.cpp)
target_link_libraries(yaad_llm PRIVATE llama log)
# Keeps this library's ggml from clashing with the copy inside the speech engine.
target_link_options(yaad_llm PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
