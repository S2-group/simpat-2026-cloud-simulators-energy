cmake_minimum_required(VERSION 3.5)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

project(SimGridEnergyEvaluation

# Search for SimGrid
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CMAKE_SOURCE_DIR}/cmake/Modules/")
find_package(SimGrid 3.27 REQUIRED) # This template requires SimGrid v3.27 because we use templated get()

include_directories(${SimGrid_INCLUDE_DIR})

add_executable(simple simple.cpp)
target_link_libraries(simple ${SimGrid_LIBRARY})

add_executable(simple-with-comms simple-with-comms.cpp)
target_link_libraries(simple-with-comms ${SimGrid_LIBRARY})
