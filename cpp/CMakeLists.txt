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
    ${Protobuf_LIBRARIES}
    glide_rs
    absl::log_internal_check_op
    dl
    pthread
)
target_link_directories(
    ${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/target/release/
)
export(TARGETS glide-cpp FILE ${CMAKE_BINARY_DIR}/glide-cpp-targets.cmake)

# Generate C++ bindings for proto files.
set(PROTO_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../glide-core/src/protobuf")
file(GLOB PROTO_FILES "${PROTO_SRC_DIR}/connection_request.proto")
message(STATUS "found proto files: ${PROTO_FILES}")
add_custom_target(generate-proto
    COMMAND ${Protobuf_PROTOC_EXECUTABLE} --cpp_out=${CMAKE_CURRENT_SOURCE_DIR}/include/glide -I ${PROTO_SRC_DIR} ${PROTO_FILES}
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    COMMAND mv include/glide/connection_request.pb.cc src/
    # Fixing the include path for the generated C++ bindings.
    COMMAND sed -i 's|"connection_request.pb.h"|<glide/connection_request.pb.h>|' ${CMAKE_CURRENT_SOURCE_DIR}/src/connection_request.pb.cc
    COMMENT "Generating C++ bindings for proto files"
)

# Generate C binding code with cbindgen.
add_custom_target(generate-cbinding
    COMMAND cbindgen --config ${CMAKE_CURRENT_SOURCE_DIR}/cbindgen.toml --crate glide-rs --output ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    # Adding header guards and namespace to C binding code.
    COMMAND sed -i '2i\#ifndef GLIDE_BASE_HPP_'  ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    COMMAND sed -i '2a\#define GLIDE_BASE_HPP_' ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    COMMAND sed -i '10anamespace glide {' ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    COMMAND ${CMAKE_COMMAND} -E echo '$a\}  // namespace glide' >> ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    COMMAND ${CMAKE_COMMAND} -E echo '$a\#endif  // GLIDE_BASE_HPP_' >> ${CMAKE_CURRENT_SOURCE_DIR}/include/glide/glide_base.h
    COMMENT "Generating C binding code with cbindgen"
)

# Build Rust library with Cargo.
add_custom_target(prebuild
    COMMAND cargo build --release
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    COMMENT "Building Rust library (glide_rs) with Cargo"
)
