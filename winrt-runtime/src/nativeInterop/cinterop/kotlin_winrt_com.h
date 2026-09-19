#ifndef KOTLIN_WINRT_COM_H
#define KOTLIN_WINRT_COM_H

#include <stdint.h>

typedef uint32_t (__stdcall *kotlin_winrt_ref_count_function)(void*);

/* Keep pointer traversal inside the ordinary cinterop call. Release may call back
 * into Kotlin, so this must retain cinterop's managed/native thread transition.
 * The low word is the full ULONG result; a nonzero high word reports an invalid
 * pointer shape so Kotlin can preserve its existing exceptions. */
static inline uint64_t kotlin_winrt_invoke_ref_count(uint64_t address, int32_t slot) {
    void* object = (void*)(uintptr_t)address;
    if (!object) return UINT64_C(1) << 32;
    void** vtable = *(void***)object;
    if (!vtable) return UINT64_C(2) << 32;
    kotlin_winrt_ref_count_function function = (kotlin_winrt_ref_count_function)vtable[slot];
    if (!function) return UINT64_C(3) << 32;
    return function(object);
}

#endif
