cmake_minimum_required(VERSION 3.20)

project(glide-cpp LANGUAGES CXX)
find_package(Protobuf REQUIRED)
find_package(absl REQUIRED)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(CMAKE_CXX_STANDARD 17)

option(DEBUG_MODE "enable debugging mode" OFF)
if (DEBUG_MODE)
    set(CMAKE_BUILD_TYPE Debug)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -O0")
else()
    set(CMAKE_BUILD_TYPE Release)
    add_compile_options("-O3")
endif()

include_directories(
    ${Protobuf_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Building.
file(GLOB PROJECT_FILES "src/*.cc")
add_library(
    ${PROJECT_NAME}
    STATIC
    ${PROJECT_FILES}
    ${PROTO_SRCS}
    ${PROTO_HDRS}
)
target_link_libraries(
    ${PROJECT_NAME}
    PRIVATE
    protobuf::libprotobuf
    glide_rs
    absl::log_internal_check_op
    absl::statusor
    dl
    pthread
)
target_link_directories(
    ${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../target/release/
)
export(TARGETS glide-cpp FILE ${CMAKE_BINARY_DIR}/glide-cpp-targets.cmake)

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    set(SED_BACKUP_ARG "-i ''")
else()
    set(SED_BACKUP_ARG "-i")
endif()

# Generate C++ bindings for proto files.
set(PROTO_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../glide-core/src/protobuf")
file(GLOB PROTO_FILES "${PROTO_SRC_DIR}/connection_request.proto")
message(STATUS "found proto files: ${PROTO_FILES}")
add_custom_target(generate-proto
    COMMAND ${Protobuf_PROTOC_EXECUTABLE} --cpp_out=${CMAKE_CURRENT_SOURCE_DIR}/include/glide -I ${PROTO_SRC_DIR} ${PROTO_FILES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    COMMAND mv include/glide/connection_request.pb.cc src/
    # Fixing the include path for the generated C++ bindings.
    COMMAND sed ${SED_BACKUP_ARG} 's|"connection_request.pb.h"|<glide/connection_request.pb.h>|' ${CMAKE_CURRENT_SOURCE_DIR}/src/connection_request.pb.cc
    COMMENT "Generating C++ bindings for proto files"
)

# Generate C binding code with cbindgen.
add_custom_target(generate-cbinding
    COMMAND cbindgen --config ${CMAKE_CURRENT_SOURCE_DIR}/cbindgen.toml --crate glide-rs --output ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    # Add header guards and namespace to the generated file.
    COMMAND ${CMAKE_COMMAND} -E echo "\\#ifndef GLIDE_BASE_HPP_" > temp_file.h
    COMMAND ${CMAKE_COMMAND} -E echo "\\#define GLIDE_BASE_HPP_" >> temp_file.h
    COMMAND ${CMAKE_COMMAND} -E cat ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h >> temp_file.h
    COMMAND ${CMAKE_COMMAND} -E echo "\\#endif  // GLIDE_BASE_HPP_" >> temp_file.h
    COMMAND ${CMAKE_COMMAND} -E copy temp_file.h ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    COMMAND ${CMAKE_COMMAND} -E remove temp_file.h
    COMMENT "Generating C binding code with cbindgen and adding header guards"
)


# Build Rust library with Cargo.
add_custom_target(prebuild
    COMMAND cargo build --release
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    COMMENT "Building Rust library (glide_rs) with Cargo"
)

# Format.
add_custom_target(format
    COMMAND clang-format -i src/*.cc include/glide/*.h example/main.cc benchmarks/benchmark.cc
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    COMMENT "Format source files"
)

add_subdirectory(tests)
