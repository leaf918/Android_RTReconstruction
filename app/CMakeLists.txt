#
# Copyright (C) The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.4.1)

# configure import sdk directories
set(opencl_DIR ${CMAKE_SOURCE_DIR}/../opencl_sdk)
set(vslam_DIR ${CMAKE_SOURCE_DIR}/../vslam_sdk)

# configure vslam sdk
add_library(lib_vslam SHARED IMPORTED)
set_target_properties(lib_vslam PROPERTIES IMPORTED_LOCATION
    ${vslam_DIR}/libs/${ANDROID_ABI}/libvslam.so)

# configure opencl sdk
add_library(lib_ocl SHARED IMPORTED)
set_target_properties(lib_ocl PROPERTIES IMPORTED_LOCATION
    ${opencl_DIR}/libs/${ANDROID_ABI}/libGLES_mali.so)

# configure native jni code
add_library(native-lib SHARED
            src/main/cpp/native-lib.cpp
            src/main/cpp/convert.cpp
            src/main/cpp/net_zjucvg_rtreconstruction_NativeVSLAM.cpp
            src/main/cpp/net_zjucvg_rtreconstruction_OpenCLUtility.cpp)

# configure include directories
target_include_directories(native-lib PRIVATE
                           ${opencl_DIR}/include
                           ${vslam_DIR}/include)
# include libraries
target_link_libraries(native-lib
                      android
                      lib_ocl
                      lib_vslam
                      log)