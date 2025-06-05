cmake_minimum_required(VERSION 3.22.1)

project("theone")

# --- Remove or comment out these FetchContent lines for Oboe ---
# include(FetchContent)
# FetchContent_Declare(
#     oboe
#     GIT_REPOSITORY https://github.com/google/oboe.git
#     GIT_TAG 1.8.0 # Use a specific stable tag
# )
# FetchContent_MakeAvailable(oboe)
# --- End of lines to remove/comment ---

# --- Add Oboe from your local project folder ---
# The path here is CRITICAL. It's relative to THIS CMakeLists.txt file.
# Based on your screenshot (image_4482a8.png), the "oboe" folder appears
# to be at the root of your "TheOne" project, alongside the "app" folder.
# If so, the relative path from "TheOne/app/src/main/cpp/" to "TheOne/oboe/"
# would be "../../../../oboe".
#
# Adjust this path if your "oboe" folder is in a different location.
# For example, if you put "oboe" inside "app" (TheOne/app/oboe),
# the path would be "../../../oboe".

add_subdirectory(../../../../oboe ${CMAKE_CURRENT_BINARY_DIR}/oboe_build)
# The second argument "${CMAKE_CURRENT_BINARY_DIR}/oboe_build" tells CMake
# where to put the build files for the Oboe library, keeping them organized.

add_library( # Sets the name of the library.
        theone

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp ) #

find_library( # Defines the name of the NDK library that
        # you want CMake to locate.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log ) #

target_link_libraries( # Specifies the target library.
        theone

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}

        # Links to Oboe. This should now work because
        # add_subdirectory makes Oboe's targets available.
        oboe ) #


