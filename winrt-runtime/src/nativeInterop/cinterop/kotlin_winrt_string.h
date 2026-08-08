#ifndef KOTLIN_WINRT_STRING_H
#define KOTLIN_WINRT_STRING_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

__declspec(dllimport) int32_t __stdcall WindowsDeleteString(void* string);

#ifdef __cplusplus
}
#endif

#endif
