cmake_minimum_required(VERSION 3.22.1)

project(kot_image_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_OPENCL OFF CACHE BOOL "" FORCE)
set(SD_VULKAN OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    stable_diffusion_cpp
    GIT_REPOSITORY https://github.com/leejet/stable-diffusion.cpp.git
    GIT_TAG c92d73c408515c94beef32161bb5960764fde7a0
    GIT_SHALLOW FALSE
    GIT_SUBMODULES ggml
)

FetchContent_MakeAvailable(stable_diffusion_cpp)

add_library(
    kot_image
    SHARED
    kot_image_jni.cpp
)

target_include_directories(
    kot_image
    PRIVATE
    \${stable_diffusion_cpp_SOURCE_DIR}/include
)

find_library(log_lib log)
find_library(android_lib android)

target_link_libraries(
    kot_image
    PRIVATE
    stable-diffusion
    \${log_lib}
    \${android_lib}
)
