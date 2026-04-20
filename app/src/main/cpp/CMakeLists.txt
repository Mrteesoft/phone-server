cmake_minimum_required(VERSION 3.22.1)

project(phoneserverpty)

add_library(
    phoneserverpty
    SHARED
    phoneserver_terminal.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    phoneserverpty
    ${log-lib}
)
