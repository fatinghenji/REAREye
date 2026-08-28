cmake_minimum_required(VERSION 3.22.1)

project(reareye_native_hooks)

set(HOOK_COMMON_DIR ${CMAKE_CURRENT_SOURCE_DIR}/hook_common)

set(HOOK_COMMON_SOURCES
        hook_common/reareye_native_hook.cpp
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
    )
endfunction()

add_reareye_hook(reareye_weather_hook weather/weather_native_hook.cpp)

add_reareye_hook(reareye_gallery_hook gallery/gallery_native_hook.cpp)
