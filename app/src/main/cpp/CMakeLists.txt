cmake_minimum_required(VERSION 3.18.1)

project(audioengine) # This project name might also need to change to native_lib or similar

# Add Oboe
find_package(Oboe REQUIRED CONFIG) # Add this line

# Remove existing audioengine library
# add_library(audioengine SHARED
#             audioengine/audio_engine.cpp
#             )

# Add new native_lib library
add_library(native_lib SHARED
            native-lib.cpp
            EnvelopeGenerator.cpp
            LfoGenerator.cpp
            )

find_library(log-lib log)

# Update target_link_libraries for native_lib
target_link_libraries(native_lib
                      Oboe::oboe # Ensure Oboe is linked
                      ${log-lib} # Ensure log is linked
                      )
