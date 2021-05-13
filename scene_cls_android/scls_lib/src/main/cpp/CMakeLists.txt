# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html


cmake_minimum_required(VERSION 3.4.1)

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ../../../../../../ncnn-mobile/zoo/scene_classify/include
        )

SET(CMAKE_BUILD_TYPE "RELEASE")
SET(CMAKE_CXX_FLAGS "-std=c++11 -O3 -mfpu=neon")


set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

FIND_PACKAGE( OpenMP REQUIRED)
if(OPENMP_FOUND)
        message("OPENMP FOUND")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OpenMP_C_FLAGS}")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
        set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${OpenMP_EXE_LINKER_FLAGS}")
endif()


add_subdirectory(${CMAKE_SOURCE_DIR}/../../../../../../ncnn-mobile/zoo/scene_classify cls_build)
add_library(lib_ncnn STATIC IMPORTED)
set_target_properties(lib_ncnn PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../../../../../../ncnn-mobile/libs/${ANDROID_ABI}/libncnn.a)

add_library(lib_yuv STATIC IMPORTED)
set_target_properties(lib_yuv PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libyuv.so)


add_library( # Sets the name of the library.
        scene_recognition-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).

#        SceneClassify.cpp

        native-lib.cpp)



find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)



target_link_libraries( # Specifies the target library.
        scene_recognition-lib

        jnigraphics

        lib_yuv

        DeepvacCLS

        lib_ncnn

        ${log-lib})