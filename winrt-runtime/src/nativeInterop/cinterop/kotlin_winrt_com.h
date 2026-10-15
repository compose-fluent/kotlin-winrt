#ifndef KOTLIN_WINRT_COM_H
#define KOTLIN_WINRT_COM_H

#include <stdint.h>

typedef uint32_t (__stdcall *kotlin_winrt_ref_count_function)(void*);
typedef int32_t (__stdcall *kotlin_winrt_query_interface_function)(void*, const void*, void**);

/* Optional QI, as used by CsWinRT 3's object-reference initialization. The caller
 * owns a successful result. Failed HRESULTs do not transfer ownership.
 * The call can enter Kotlin: this must remain an ordinary cinterop boundary. */
static inline uint64_t kotlin_winrt_try_query_interface(uint64_t address, uint64_t iid_low, uint64_t iid_high) {
    void* object = (void*)(uintptr_t)address;
    if (!object) return 0;
    const uint64_t iid[2] = { iid_low, iid_high };
    void* result = 0;
    void** vtable = *(void***)object;
    int32_t hr = ((kotlin_winrt_query_interface_function)vtable[0])(object, iid, &result);
    return hr < 0 ? 0 : (uint64_t)(uintptr_t)result;
}

/* CsWinRT 3 ComObjectHelpers.IsFreeThreadedUnsafe releases the queried interface.
 * Keep both calls inside one ordinary cinterop boundary and allocate no wrapper. */
static inline int32_t kotlin_winrt_supports_interface(uint64_t address, uint64_t iid_low, uint64_t iid_high) {
    void* result = (void*)(uintptr_t)kotlin_winrt_try_query_interface(address, iid_low, iid_high);
    if (!result) return 0;
    void** result_vtable = *(void***)result;
    ((kotlin_winrt_ref_count_function)result_vtable[2])(result);
    return 1;
}

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
