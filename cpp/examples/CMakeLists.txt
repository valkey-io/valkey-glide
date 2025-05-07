cmake_minimum_required(VERSION 3.20)
project(glide-cpp-sample)

find_package(Threads REQUIRED)
find_package(absl REQUIRED)
find_package(Protobuf REQUIRED)

option(DEBUG_MODE "enable debugging mode" OFF)
if (DEBUG_MODE)
    set(CMAKE_BUILD_TYPE Debug)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -O0")
else()
    set(CMAKE_BUILD_TYPE Release)
    add_compile_options("-O3")
endif()

include(../build/glide-cpp-targets.cmake)

add_executable(${PROJECT_NAME} main.cc)
include_directories(../include)
target_link_directories(
    ${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../target/release/
)
target_link_libraries(
    ${PROJECT_NAME}
    PRIVATE
    glide-cpp
    Threads::Threads
    dl
    absl::log_internal_check_op
    glide_rs
)
