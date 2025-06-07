cmake_minimum_required(VERSION 3.22.1)

project("theone")

include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.8.0 # Using version 1.8.0 as specified in the user's plan
)
FetchContent_MakeAvailable(oboe)

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
        # FetchContent_MakeAvailable makes Oboe's targets available.
        oboe ) #


