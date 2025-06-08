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