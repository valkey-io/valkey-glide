enable_testing()

find_package(Threads REQUIRED)
find_package(absl REQUIRED)
find_package(Protobuf REQUIRED)

include(FetchContent)
FetchContent_Declare(
  googletest
  GIT_REPOSITORY https://github.com/google/googletest.git
  GIT_TAG release-1.12.1
)
FetchContent_MakeAvailable(googletest)

add_executable(client_test client_test.cc)
include_directories(../include)
target_link_directories(
    ${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../target/release/    
)
target_link_directories(client_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../target/release)

target_link_libraries(client_test
  PRIVATE
    glide-cpp    
    GTest::gtest_main
    Threads::Threads
    absl::log_internal_check_op
)

include(GoogleTest)
gtest_discover_tests(client_test)
