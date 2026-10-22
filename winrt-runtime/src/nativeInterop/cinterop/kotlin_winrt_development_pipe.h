#ifndef KOTLIN_WINRT_DEVELOPMENT_PIPE_H
#define KOTLIN_WINRT_DEVELOPMENT_PIPE_H
#include <windows.h>
#include <aclapi.h>
#include <bcrypt.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

/* The owner and, when present, this process's own AppContainer can use the
 * endpoint. Never grant all application packages access to development data. */
static inline PACL kotlin_winrt_development_acl(void) {
    HANDLE token = NULL;
    TOKEN_USER *user = NULL;
    TOKEN_APPCONTAINER_INFORMATION *container = NULL;
    PACL acl = NULL;
    DWORD size = 0;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) return NULL;
    GetTokenInformation(token, TokenUser, NULL, 0, &size);
    user = (TOKEN_USER *)malloc(size);
    if (!user || !GetTokenInformation(token, TokenUser, user, size, &size)) goto done;
    EXPLICIT_ACCESSW entries[2] = {0};
    entries[0].grfAccessPermissions = GENERIC_ALL;
    entries[0].grfAccessMode = SET_ACCESS;
    entries[0].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[0].Trustee.ptstrName = (LPWSTR)user->User.Sid;
    ULONG count = 1;
    DWORD app_container = 0;
    if (!GetTokenInformation(token, TokenIsAppContainer, &app_container, sizeof(app_container), &size)) goto done;
    if (app_container) {
        GetTokenInformation(token, TokenAppContainerSid, NULL, 0, &size);
        container = (TOKEN_APPCONTAINER_INFORMATION *)malloc(size);
        if (!container || !GetTokenInformation(token, TokenAppContainerSid, container, size, &size) || !container->TokenAppContainer) goto done;
        entries[1] = entries[0];
        entries[1].Trustee.ptstrName = (LPWSTR)container->TokenAppContainer;
        count = 2;
    }
    if (SetEntriesInAclW(count, entries, NULL, &acl) != ERROR_SUCCESS) acl = NULL;
done:
    free(container);
    free(user);
    CloseHandle(token);
    return acl;
}

typedef struct kotlin_winrt_development_pipe {
    HANDLE handle;
    CRITICAL_SECTION lock;
    int closed;
    char name[96];
    char object_namespace[320];
} kotlin_winrt_development_pipe;

static inline kotlin_winrt_development_pipe *kotlin_winrt_development_pipe_open(void) {
    PACL acl = kotlin_winrt_development_acl();
    if (!acl) return NULL;
    kotlin_winrt_development_pipe *pipe = (kotlin_winrt_development_pipe *)calloc(1, sizeof(*pipe));
    if (!pipe) { LocalFree(acl); return NULL; }
    pipe->handle = INVALID_HANDLE_VALUE;
    InitializeCriticalSection(&pipe->lock);
    unsigned char random[16];
    SECURITY_DESCRIPTOR descriptor;
    if (BCryptGenRandom(NULL, random, sizeof(random), BCRYPT_USE_SYSTEM_PREFERRED_RNG) < 0 ||
        !InitializeSecurityDescriptor(&descriptor, SECURITY_DESCRIPTOR_REVISION) ||
        !SetSecurityDescriptorDacl(&descriptor, TRUE, acl, FALSE) ||
        !SetSecurityDescriptorControl(&descriptor, SE_DACL_PROTECTED, SE_DACL_PROTECTED)) goto done;
    char nonce[33];
    for (int i = 0; i < 16; ++i) snprintf(nonce + i * 2, 3, "%02x", random[i]);
    snprintf(pipe->name, sizeof(pipe->name), "KotlinWinRT.%lu.%s", GetCurrentProcessId(), nonce);
    wchar_t path[160];
    swprintf(path, 160, L"\\\\.\\pipe\\LOCAL\\%hs", pipe->name);
    SECURITY_ATTRIBUTES attributes = { sizeof(SECURITY_ATTRIBUTES), &descriptor, FALSE };
    pipe->handle = CreateNamedPipeW(path, PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED | FILE_FLAG_FIRST_PIPE_INSTANCE,
        PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
        1, 65536, 65536, 3000, &attributes);
    if (pipe->handle != INVALID_HANDLE_VALUE) {
        /* LOCAL is redirected inside AppContainer. Publish the OS-owned namespace
         * for desktop clients, using forward slashes to preserve Java properties. */
        HANDLE token = NULL;
        DWORD container = 0, size = 0, session = 0;
        wchar_t object_path[256];
        ULONG required = 0;
        int valid = OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token) &&
            GetTokenInformation(token, TokenIsAppContainer, &container, sizeof(container), &size);
        if (valid && container) {
            /* Kotlin/Native's bundled MinGW headers omit this Windows 8 API. */
            typedef BOOL (WINAPI *get_container_path_fn)(HANDLE, PSID, ULONG, LPWSTR, PULONG);
            get_container_path_fn get_container_path = (get_container_path_fn)GetProcAddress(
                GetModuleHandleW(L"kernel32.dll"), "GetAppContainerNamedObjectPath");
            valid = ProcessIdToSessionId(GetCurrentProcessId(), &session) &&
                get_container_path && get_container_path(token, NULL, 256, object_path, &required);
            if (valid) {
                snprintf(pipe->object_namespace, sizeof(pipe->object_namespace), "Sessions/%lu/%ls", session, object_path);
                for (char *c = pipe->object_namespace; *c; ++c) if (*c == '\\') *c = '/';
            }
        } else if (valid) snprintf(pipe->object_namespace, sizeof(pipe->object_namespace), "LOCAL");
        if (token) CloseHandle(token);
        if (!valid) { CloseHandle(pipe->handle); pipe->handle = INVALID_HANDLE_VALUE; }
    }
done:
    LocalFree(acl);
    if (pipe->handle == INVALID_HANDLE_VALUE) { DeleteCriticalSection(&pipe->lock); free(pipe); return NULL; }
    return pipe;
}

static inline void kotlin_winrt_development_pipe_stop(kotlin_winrt_development_pipe *pipe) {
    EnterCriticalSection(&pipe->lock);
    pipe->closed = 1;
    CancelIoEx(pipe->handle, NULL);
    LeaveCriticalSection(&pipe->lock);
}

static inline void kotlin_winrt_development_pipe_destroy(kotlin_winrt_development_pipe *pipe) {
    kotlin_winrt_development_pipe_stop(pipe);
    CloseHandle(pipe->handle);
    DeleteCriticalSection(&pipe->lock);
    free(pipe);
}

/* operation: 0=accept, 1=read, 2=write. The worker owns completion storage;
 * cancellation is drained before freeing OVERLAPPED or its event. */
static inline int kotlin_winrt_development_pipe_io(kotlin_winrt_development_pipe *pipe,
    int operation, void *bytes, DWORD length, DWORD timeout, DWORD *transferred) {
    OVERLAPPED pending = {0};
    pending.hEvent = CreateEventW(NULL, TRUE, FALSE, NULL);
    if (!pending.hEvent) return 0;
    EnterCriticalSection(&pipe->lock);
    if (pipe->closed) { LeaveCriticalSection(&pipe->lock); CloseHandle(pending.hEvent); return 0; }
    BOOL ready = operation == 0 ? ConnectNamedPipe(pipe->handle, &pending) :
        operation == 1 ? ReadFile(pipe->handle, bytes, length, transferred, &pending) :
        WriteFile(pipe->handle, bytes, length, transferred, &pending);
    DWORD error = ready ? ERROR_SUCCESS : GetLastError();
    LeaveCriticalSection(&pipe->lock);
    if (error == ERROR_PIPE_CONNECTED && operation == 0) ready = TRUE;
    else if (error == ERROR_IO_PENDING) {
        if (WaitForSingleObject(pending.hEvent, timeout) == WAIT_OBJECT_0) {
            ready = GetOverlappedResult(pipe->handle, &pending, transferred, FALSE);
        } else {
            CancelIoEx(pipe->handle, &pending);
            GetOverlappedResult(pipe->handle, &pending, transferred, TRUE);
            ready = FALSE;
        }
    }
    CloseHandle(pending.hEvent);
    return ready != FALSE;
}

static inline int kotlin_winrt_development_pipe_transfer(kotlin_winrt_development_pipe *pipe,
    int operation, void *bytes, int length) {
    int offset = 0;
    ULONGLONG deadline = GetTickCount64() + 3000;
    while (offset < length) {
        ULONGLONG now = GetTickCount64();
        if (now >= deadline) return 0;
        DWORD count = 0;
        if (!kotlin_winrt_development_pipe_io(pipe, operation, (char *)bytes + offset,
            (DWORD)(length - offset), (DWORD)(deadline - now), &count) || !count) return 0;
        offset += (int)count;
    }
    return 1;
}
#endif
