cmake_minimum_required(VERSION 3.22.1)

project(reareye_native_hooks)

function(add_reareye_hook target_name source_file)
    add_library(${target_name} SHARED
        ${source_file}
        reareye_native_hook.cpp
    )

    target_compile_features(${target_name} PRIVATE cxx_std_17)

    target_compile_options(${target_name} PRIVATE
        -Wall
        -Wextra
        -Werror=return-type
        -fvisibility=hidden
    )

    target_link_libraries(${target_name}
        android
        log
    )
endfunction()

add_reareye_hook(reareye_weather_hook weather_native_hook.cpp)
add_reareye_hook(reareye_gallery_hook gallery_native_hook.cpp)
