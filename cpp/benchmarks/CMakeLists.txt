cmake_minimum_required(VERSION 3.20)
project(glide-cpp-benchmark)

find_package(Threads REQUIRED)
find_package(absl REQUIRED)
find_package(Protobuf REQUIRED)

set(CMAKE_BUILD_TYPE Debug)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -O0")

include(../build/glide-cpp-targets.cmake)

add_executable(${PROJECT_NAME} benchmark.cc)
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
