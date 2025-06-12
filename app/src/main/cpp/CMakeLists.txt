cmake_minimum_required(VERSION 3.18.1)

project(theone)

# This command is all you need. It builds Oboe from the submodule.
add_subdirectory(oboe)

# DELETE THE FOLLOWING LINE:
# find_package(Oboe REQUIRED CONFIG)

add_library(theone SHARED
        native-lib.cpp
        EnvelopeGenerator.cpp
        LfoGenerator.cpp
)

find_library(log-lib log)

# This works because add_subdirectory makes "Oboe::oboe" available.
target_link_libraries(theone
        oboe
        ${log-lib}
)

# Google Test Setup
include(FetchContent)
FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG main # Or a specific release tag e.g. release-1.12.1
)
# For Windows: Prevent overriding the parent project's compiler/linker settings
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# Enable testing
enable_testing()

# Add test executable
add_executable(EnvelopeGeneratorTests
    ../test/cpp/EnvelopeGeneratorTests.cpp # Path relative to this CMakeLists.txt
    # EnvelopeGenerator.cpp is already part of the 'theone' library
)

# Link tests with GoogleTest and the main library
target_link_libraries(EnvelopeGeneratorTests PRIVATE
    theone
    GTest::gtest_main # Use GTest::gtest_main to get both gtest and gtest_main
)

# Add include directories for the tests to find headers from the main library
# This ensures that #include "../../main/cpp/EnvelopeGenerator.h" works as expected,
# or allows for #include "EnvelopeGenerator.h" if preferred.
target_include_directories(EnvelopeGeneratorTests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR} # For EnvelopeGenerator.h etc.
)

# Discover and add tests to CTest
include(GoogleTest)
gtest_discover_tests(EnvelopeGeneratorTests)