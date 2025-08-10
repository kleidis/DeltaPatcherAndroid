# liblzma
add_subdirectory(xz)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/xz/src/liblzma/api)

#libxdelta3
set(XD3_BUILD_AS_EXE OFF CACHE BOOL "" FORCE)
set(XD3_ENABLE_SECONDARY_COMPRESSION ON CACHE BOOL "" FORCE)
set(XD3_ENABLE_LZMA ON CACHE BOOL "" FORCE)
set(XD3_ENABLE_ENCODER sON CACHE BOOL "" FORCE)
set(XD3_ENABLE_VCDIFF_TOOLS OFF CACHE BOOL "" FORCE)

add_subdirectory(xdelta)
