#ifndef KOTLIN_WINRT_DEBUG_H
#define KOTLIN_WINRT_DEBUG_H

#include <stdint.h>

/* Kotlin/Native tooling adaptation, not a WinRT projection contract. Signatures
 * follow Kotlin 2.4 runtime/src/debug/cpp/KDebug.cpp and tools/konan_lldb.py.
 * The runtime owns object layout and field classification. Keep its public debug
 * ABI reachable in debug executables instead of copying those decisions here.
 * LLVM's link-only-needed pass ignores otherwise unreferenced native bitcode;
 * volatile address reads keep the entire table alive without calling user code.
 * Called only when kotlin.native.Platform.isDebugBinary is true. */
static inline void kotlin_winrt_retain_debug_api(void) {
    extern char *Konan_DebugBuffer(void);
    extern int32_t Konan_DebugBufferSize(void);
    extern int32_t Konan_DebugObjectToUtf8Array(void *, char *, int32_t);
    extern int32_t Konan_DebugIsArray(void *);
    extern int32_t Konan_DebugIsInstance(void *, const void *);
    extern int32_t Konan_DebugGetFieldCount(void *);
    extern int32_t Konan_DebugGetFieldType(void *, int32_t);
    extern void *Konan_DebugGetFieldAddress(void *, int32_t);
    extern const char *Konan_DebugGetFieldName(void *, int32_t);
    static void *volatile api[] = {
        (void *)&Konan_DebugBuffer, (void *)&Konan_DebugBufferSize,
        (void *)&Konan_DebugObjectToUtf8Array, (void *)&Konan_DebugIsArray,
        (void *)&Konan_DebugIsInstance, (void *)&Konan_DebugGetFieldCount,
        (void *)&Konan_DebugGetFieldType, (void *)&Konan_DebugGetFieldAddress,
        (void *)&Konan_DebugGetFieldName
    };
    for (unsigned int i = 0; i < sizeof(api) / sizeof(api[0]); ++i) (void)api[i];
}

#endif
