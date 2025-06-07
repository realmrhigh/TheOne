cmake_minimum_required(VERSION 3.18.1)

project(audioengine)

# Add Oboe
find_package(Oboe REQUIRED CONFIG) # Add this line

add_library(audioengine SHARED
            audioengine/audio_engine.cpp
            )

find_library(log-lib log)

target_link_libraries(audioengine
                      ${log-lib}
                      Oboe::oboe # Add this line
                      )
