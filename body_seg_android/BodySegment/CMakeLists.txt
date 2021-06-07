# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

# CMAKE_SOURCE_DIR   CMAKE_CURRENT_SOURCE_DIR
set(CUR_PATH ${CMAKE_CURRENT_SOURCE_DIR})

include_directories(${CUR_PATH}/src/main/jni/BodySegment/
                    ${CUR_PATH}/src/main/jni/cc/
                    ${CUR_PATH}/../../third_party/tnn-mobile

                    ${CUR_PATH}/../../third_party/tnn-mobile/zoo/portrait_seg/include
                    ${CUR_PATH}/../../third_party/tnn-mobile/zoo/tnn_helper/include
                    ${CUR_PATH}/../../third_party/tnn-mobile/zoo/kannarotate
                    ${CUR_PATH}/../../third_party/tnn-mobile/zoo/kannarotate/kannarotate-android-lib/include)


set(CMAKE_BUILD_TYPE Release)

FIND_PACKAGE( OpenMP REQUIRED)
if(OPENMP_FOUND)
    message("OPENMP FOUND")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OpenMP_C_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${OpenMP_EXE_LINKER_FLAGS}")
    set(CMAKE_SHARE_LINKER_FLAGS "${CMAKE_SHARE_LINKER_FLAGS} ${OpenMP_SHARE_LINKER_FLAGS}")
    set(CMAKE_STATIC_LINKER_FLAGS "${CMAKE_STATIC_LINKER_FLAGS} ${OpenMP_STATIC_LINKER_FLAGS}")
else()
    message("OPENMP NOT FOUND")
endif()


file(GLOB_RECURSE BODYSEGMENT_SRCS ${CUR_PATH}/src/main/jni/cc/*.cc ${CUR_PATH}/src/main/jni/BodySegment/*.cc)
add_library(BodySegment SHARED ${BODYSEGMENT_SRCS})
add_subdirectory(${CUR_PATH}/../../third_party/tnn-mobile/zoo/kannarotate DeepvacKannaRotate)
add_subdirectory(${CUR_PATH}/../../third_party/tnn-mobile/zoo/portrait_seg DeepvacPortraitSeg)


#file(GLOB_RECURSE THIRDPARTY_SRCS ${CUR_PATH}/src/main/jni/thirdparty/kannarotate-android-lib/src/*.c)
#file(GLOB_RECURSE BASE_SRC ${CUR_PATH}/src/main/jni/base/*.cc)
#file(GLOB_RECURSE WRAPPER_SRCS ${CUR_PATH}/src/main/jni/cc/*.cc)
#file(GLOB_RECURSE BODYSEGMENT_SRCS ${CUR_PATH}/src/main/jni/BodySegment/*.cc)
#add_library(BodySegment SHARED ${THIRDPARTY_SRCS} ${BASE_SRC} ${WRAPPER_SRCS} ${BODYSEGMENT_SRCS})

add_library(TNN
            SHARED
            IMPORTED)

set_target_properties(TNN
        PROPERTIES
        IMPORTED_LOCATION
        ${CUR_PATH}/../../third_party/tnn-mobile/libs/${ANDROID_ABI}/libTNN.so)

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        BodySegment
        DeepvacKannaRotate
        DeepvacPortraitSeg
        -ljnigraphics
        TNN
#                        -Wl,--whole-archive TNN -Wl,--no-whole-archive
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}

        # runtime exception not found libomp.so
        # ndk-21 -static-openmp success         ndk-20 error           ndk-19 not need -static-openmp
        -static-openmp
        )
