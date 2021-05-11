# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        )
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

SET(CMAKE_BUILD_TYPE "RELEASE")
SET(CMAKE_CXX_FLAGS "-std=c++11 -O3 -mfpu=neon")

#set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -s" )
#set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -s" )
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

FIND_PACKAGE( OpenMP REQUIRED)
if(OPENMP_FOUND)
        message("OPENMP FOUND")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OpenMP_C_FLAGS}")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
        set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${OpenMP_EXE_LINKER_FLAGS}")
endif()



add_library(lib_ncnn STATIC IMPORTED)
set_target_properties(lib_ncnn PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.a)

add_library(lib_yuv STATIC IMPORTED)
set_target_properties(lib_yuv PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libyuv.so)

#add_library(lib_opencv STATIC IMPORTED)
#set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_world.so)
add_library( # Sets the name of the library.
        scene_recognition-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).

        SceneClassify.cpp

        native-lib.cpp)


# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
#
find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        scene_recognition-lib

        lib_ncnn

        jnigraphics

        lib_yuv

        ${log-lib})