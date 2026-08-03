cmake_minimum_required(VERSION 3.22.1)

project(reareye_native_hooks)

include(FetchContent)
set(JSON_BuildTests OFF CACHE INTERNAL "")
FetchContent_Declare(nlohmann_json
        URL https://github.com/nlohmann/json/releases/download/v3.12.0/json.tar.xz
)
FetchContent_MakeAvailable(nlohmann_json)

set(HOOK_COMMON_DIR ${CMAKE_CURRENT_SOURCE_DIR}/hook_common)
set(ANDROID_HOME "$ENV{ANDROID_HOME}")
if (NOT ANDROID_HOME)
    set(ANDROID_HOME "$ENV{ANDROID_SDK_ROOT}")
endif ()
if (NOT ANDROID_HOME)
    get_filename_component(ANDROID_HOME "${ANDROID_NDK}/../.." ABSOLUTE)
endif ()
set(NDK_CPP_INCLUDE_DIR "${ANDROID_HOME}/platforms/android-37.0/optional/libbinder_ndk_cpp")
message("libinder_ndk_cpp include directory: ${NDK_CPP_INCLUDE_DIR}")
set(LYRICON_NATIVE_AIDL_DIR ${CMAKE_CURRENT_BINARY_DIR}/lyricon-native-aidl)
set(LYRICON_NATIVE_AIDL_SRC_DIR ${LYRICON_NATIVE_AIDL_DIR}/src)
set(LYRICON_NATIVE_AIDL_INCLUDE_DIR ${LYRICON_NATIVE_AIDL_DIR}/include)
get_filename_component(LYRICON_NATIVE_AIDL_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../native-aidl ABSOLUTE)
file(TO_NATIVE_PATH "${LYRICON_NATIVE_AIDL_ROOT}" LYRICON_NATIVE_AIDL_ROOT_ARG)
file(TO_NATIVE_PATH "${LYRICON_NATIVE_AIDL_SRC_DIR}" LYRICON_NATIVE_AIDL_SRC_ARG)
file(TO_NATIVE_PATH "${LYRICON_NATIVE_AIDL_INCLUDE_DIR}" LYRICON_NATIVE_AIDL_INCLUDE_ARG)
set(LYRICON_NATIVE_AIDL_FILES
        ${LYRICON_NATIVE_AIDL_ROOT}/io/github/proify/lyricon/subscriber/IActivePlayerListener.aidl
        ${LYRICON_NATIVE_AIDL_ROOT}/io/github/proify/lyricon/subscriber/ISubscriberBinder.aidl
        ${LYRICON_NATIVE_AIDL_ROOT}/io/github/proify/lyricon/subscriber/IRemoteService.aidl
)
set(LYRICON_NATIVE_AIDL_SOURCES
        ${LYRICON_NATIVE_AIDL_SRC_DIR}/io/github/proify/lyricon/subscriber/IActivePlayerListener.cpp
        ${LYRICON_NATIVE_AIDL_SRC_DIR}/io/github/proify/lyricon/subscriber/ISubscriberBinder.cpp
        ${LYRICON_NATIVE_AIDL_SRC_DIR}/io/github/proify/lyricon/subscriber/IRemoteService.cpp
)

find_program(ANDROID_AIDL_EXECUTABLE aidl
        HINTS
        "$ENV{ANDROID_HOME}/build-tools/${ANDROID_PLATFORM}"
        "$ENV{ANDROID_SDK_ROOT}/build-tools/${ANDROID_PLATFORM}"
        "C:/Users/25945/AppData/Local/Android/Sdk/build-tools/37.0.0"
        "C:/Users/25945/AppData/Local/Android/Sdk/build-tools/37.0.0-rc2"
        "C:/Users/25945/AppData/Local/Android/Sdk/build-tools/36.1.0"
        "C:/Users/25945/AppData/Local/Android/Sdk/build-tools/36.0.0"
        "C:/Users/25945/AppData/Local/Android/Sdk/build-tools/35.0.0"
)
if (NOT ANDROID_AIDL_EXECUTABLE)
    message(FATAL_ERROR "Android aidl executable not found")
endif ()

add_custom_command(
        OUTPUT ${LYRICON_NATIVE_AIDL_SOURCES}
        COMMAND ${CMAKE_COMMAND}
        -DAIDL_EXECUTABLE=${ANDROID_AIDL_EXECUTABLE}
        -DAIDL_ROOT=${LYRICON_NATIVE_AIDL_ROOT}
        -DAIDL_SRC_OUT=${LYRICON_NATIVE_AIDL_SRC_DIR}
        -DAIDL_HEADER_OUT=${LYRICON_NATIVE_AIDL_INCLUDE_DIR}
        -P ${CMAKE_CURRENT_SOURCE_DIR}/tools/generate_lyricon_aidl.cmake
        DEPENDS ${LYRICON_NATIVE_AIDL_FILES} ${CMAKE_CURRENT_SOURCE_DIR}/tools/generate_lyricon_aidl.cmake
        VERBATIM
)
add_custom_target(lyricon_native_aidl_gen DEPENDS ${LYRICON_NATIVE_AIDL_SOURCES})

set(HOOK_COMMON_SOURCES
        hook_common/reareye_native_hook.cpp
)

set(HYPERLIB_SOURCES
        hook_common/hyperlib/hyper_abi.cpp
        hook_common/hyperlib/hyper_binder.cpp
        hook_common/hyperlib/hyper_broadcast.cpp
        hook_common/hyperlib/hyper_logging.cpp
        hook_common/hyperlib/hyper_runtime.cpp
)

set(LYRICON_SDK_SOURCES
        hook_common/lyricon/lyricon_api.cpp
        hook_common/lyricon/lyricon_compress.cpp
        hook_common/lyricon/lyricon_model.cpp
        hook_common/lyricon/lyricon_subscriber.cpp
        hook_common/lyricon/lyricon_native_broadcast.cpp
)

set(SUPERLYRIC_SDK_SOURCES
        hook_common/superlyric/superlyric_abi.cpp
        hook_common/superlyric/superlyric_parcel.cpp
        hook_common/superlyric/superlyric_bundle.cpp
        hook_common/superlyric/superlyric_media.cpp
        hook_common/superlyric/superlyric_manager.cpp
        hook_common/superlyric/superlyric_receiver.cpp
        hook_common/superlyric/superlyric_api.cpp
)

function(add_reareye_hook target_name source_file)
    add_library(${target_name} SHARED
        ${source_file}
            ${HOOK_COMMON_SOURCES}
    )

    target_compile_features(${target_name} PRIVATE cxx_std_17)

    target_include_directories(${target_name} PRIVATE
            ${HOOK_COMMON_DIR}
    )

    target_compile_options(${target_name} PRIVATE
        -Wall
        -Wextra
        -Werror=return-type
        -fvisibility=hidden
    )

    target_link_libraries(${target_name}
        android
        log
            z
            nlohmann_json::nlohmann_json
    )
endfunction()

function(enable_lyricon_native_aidl target_name)
    add_dependencies(${target_name} lyricon_native_aidl_gen)
    target_sources(${target_name} PRIVATE ${LYRICON_NATIVE_AIDL_SOURCES})
    target_include_directories(${target_name} PRIVATE
            ${LYRICON_NATIVE_AIDL_INCLUDE_DIR}
            ${NDK_CPP_INCLUDE_DIR}
    )
    target_link_libraries(${target_name} binder_ndk)
endfunction()

add_reareye_hook(reareye_weather_hook weather/weather_native_hook.cpp)
target_sources(reareye_weather_hook PRIVATE
        ${HYPERLIB_SOURCES}
        ${LYRICON_SDK_SOURCES}
        ${SUPERLYRIC_SDK_SOURCES}
)
enable_lyricon_native_aidl(reareye_weather_hook)

add_reareye_hook(reareye_gallery_hook gallery/gallery_native_hook.cpp)
target_sources(reareye_gallery_hook PRIVATE
        ${HYPERLIB_SOURCES}
)
