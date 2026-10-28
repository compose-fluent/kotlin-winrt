#ifndef KOTLIN_WINRT_DEVELOPMENT_H
#define KOTLIN_WINRT_DEVELOPMENT_H
/* Windows-only transport mechanics. Projection and UI transactions stay in Kotlin.
 * Session directories/files grant access only to this user when created here. */
#include <winsock2.h>
#include <windows.h>
#include <bcrypt.h>
#include <aclapi.h>
#include <shlobj.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include "kotlin_winrt_development_pipe.h"

typedef struct kotlin_winrt_development_server {
    SOCKET listener;
    SOCKET client;
    kotlin_winrt_development_pipe *pipe;
    CRITICAL_SECTION lock;
    int closed;
    int published;
    unsigned short port;
    wchar_t *session;
    char token[44];
    int protocol;
} kotlin_winrt_development_server;

static inline void kotlin_winrt_development_stop(kotlin_winrt_development_server *server) {
    EnterCriticalSection(&server->lock);
    if (!server->closed) {
        server->closed = 1;
        if (server->pipe) kotlin_winrt_development_pipe_stop(server->pipe);
        if (server->listener != INVALID_SOCKET) {
            closesocket(server->listener);
            server->listener = INVALID_SOCKET;
        }
        /* The worker retains ownership of the accepted handle. */
        if (server->client != INVALID_SOCKET) shutdown(server->client, SD_BOTH);
        if (server->published) DeleteFileW(server->session);
    }
    LeaveCriticalSection(&server->lock);
}
static inline void kotlin_winrt_development_destroy(kotlin_winrt_development_server *server) {
    kotlin_winrt_development_stop(server);
    if (server->client != INVALID_SOCKET) closesocket(server->client);
    if (server->pipe) kotlin_winrt_development_pipe_destroy(server->pipe);
    DeleteCriticalSection(&server->lock);
    free(server->session);
    free(server);
    WSACleanup();
}
static inline int kotlin_winrt_development_publish(kotlin_winrt_development_server *server, const wchar_t *directory) {
    PACL acl = NULL;
    HANDLE file = INVALID_HANDLE_VALUE;
    int success = 0;
    /* Require an absolute drive or UNC path, as on JVM. */
    if (!directory || !(directory[0] && directory[1] == L':' && (directory[2] == L'\\' || directory[2] == L'/')) &&
        !(directory[0] == L'\\' && directory[1] == L'\\')) return 0;
    acl = kotlin_winrt_development_acl();
    if (!acl) goto done;
    SECURITY_DESCRIPTOR descriptor;
    if (!InitializeSecurityDescriptor(&descriptor, SECURITY_DESCRIPTOR_REVISION) ||
        !SetSecurityDescriptorDacl(&descriptor, TRUE, acl, FALSE) ||
        !SetSecurityDescriptorControl(&descriptor, SE_DACL_PROTECTED, SE_DACL_PROTECTED)) goto done;
    SECURITY_ATTRIBUTES attributes = { sizeof(SECURITY_ATTRIBUTES), &descriptor, FALSE };
    int directory_result = SHCreateDirectoryExW(NULL, directory, &attributes);
    if (directory_result != ERROR_SUCCESS && directory_result != ERROR_ALREADY_EXISTS && directory_result != ERROR_FILE_EXISTS) goto done;
    size_t length = wcslen(directory) + 100;
    wchar_t *temporary = (wchar_t *)calloc(length, sizeof(wchar_t));
    server->session = (wchar_t *)calloc(length, sizeof(wchar_t));
    if (!temporary || !server->session) { free(temporary); goto done; }
    /* Keep the authentication token out of the directory listing. */
    swprintf(temporary, length, L"%ls\\%lu-%u-%llu.tmp", directory, GetCurrentProcessId(), server->port, GetTickCount64());
    swprintf(server->session, length, L"%ls\\%lu-%u.session", directory, GetCurrentProcessId(), server->port);
    file = CreateFileW(temporary, GENERIC_WRITE, 0, &attributes, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file != INVALID_HANDLE_VALUE) {
        char properties[768];
        int count = snprintf(properties, sizeof(properties), "protocol=%d\nport=%u\ntoken=%s\npid=%lu\npipe=%s\npipeNamespace=%s\n", server->protocol, server->port, server->token, GetCurrentProcessId(), server->pipe ? server->pipe->name : "", server->pipe ? server->pipe->object_namespace : "");
        DWORD written = 0;
        success = WriteFile(file, properties, count, &written, NULL) && written == (DWORD)count && FlushFileBuffers(file);
        CloseHandle(file); file = INVALID_HANDLE_VALUE;
        if (success) success = MoveFileExW(temporary, server->session, MOVEFILE_WRITE_THROUGH);
        server->published = success;
        DeleteFileW(temporary);
    }
    free(temporary);
 done:
    if (file != INVALID_HANDLE_VALUE) CloseHandle(file);
    if (acl) LocalFree(acl);
    return success;
}
static inline kotlin_winrt_development_server *kotlin_winrt_development_open(const wchar_t *directory, int protocol) {
    WSADATA data;
    if (WSAStartup(MAKEWORD(2, 2), &data)) return NULL;
    kotlin_winrt_development_server *server = (kotlin_winrt_development_server *)calloc(1, sizeof(*server));
    if (!server) { WSACleanup(); return NULL; }
    server->listener = server->client = INVALID_SOCKET;
    server->protocol = protocol;
    InitializeCriticalSection(&server->lock);
    unsigned char random[32];
    if (BCryptGenRandom(NULL, random, sizeof(random), BCRYPT_USE_SYSTEM_PREFERRED_RNG) < 0) goto fail;
    const char alphabet[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    uint32_t bits = 0;
    int available = 0, output = 0;
    for (int index = 0; index < 32; ++index) {
        bits = (bits << 8) | random[index]; available += 8;
        while (available >= 6) { available -= 6; server->token[output++] = alphabet[(bits >> available) & 63]; }
    }
    if (available) server->token[output++] = alphabet[(bits << (6 - available)) & 63];
    wchar_t transport[16];
    if (GetEnvironmentVariableW(L"KOTLIN_WINRT_DEVELOPMENT_TRANSPORT", transport, 16) == 4 && wcscmp(transport, L"pipe") == 0) {
        server->pipe = kotlin_winrt_development_pipe_open();
        if (!server->pipe || !kotlin_winrt_development_publish(server, directory)) goto fail;
        return server;
    }
    server->listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server->listener == INVALID_SOCKET) goto fail;
    BOOL exclusive = TRUE;
    if (setsockopt(server->listener, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, (const char *)&exclusive, sizeof(exclusive))) goto fail;
    struct sockaddr_in address = {0};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(server->listener, (struct sockaddr *)&address, sizeof(address)) || listen(server->listener, 16)) goto fail;
    int length = sizeof(address);
    if (getsockname(server->listener, (struct sockaddr *)&address, &length)) goto fail;
    server->port = ntohs(address.sin_port);
    if (!kotlin_winrt_development_publish(server, directory)) goto fail;
    return server;
 fail:
    kotlin_winrt_development_destroy(server);
    return NULL;
}
static inline int kotlin_winrt_development_socket_timeout(uint64_t socket_handle, uint32_t milliseconds) {
    DWORD timeout = milliseconds;
    return setsockopt((SOCKET)socket_handle, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout, sizeof(timeout)) == 0 &&
        setsockopt((SOCKET)socket_handle, SOL_SOCKET, SO_SNDTIMEO, (char *)&timeout, sizeof(timeout)) == 0;
}
static inline int kotlin_winrt_development_accept(kotlin_winrt_development_server *server) {
    if (server->pipe) {
        DWORD count = 0;
        return kotlin_winrt_development_pipe_io(server->pipe, 0, NULL, 0, INFINITE, &count);
    }
    EnterCriticalSection(&server->lock);
    SOCKET listener = server->listener;
    int closed = server->closed;
    LeaveCriticalSection(&server->lock);
    if (closed) return 0;
    SOCKET client = accept(listener, NULL, NULL);
    if (client == INVALID_SOCKET) return 0;
    EnterCriticalSection(&server->lock);
    if (server->closed) { closesocket(client); LeaveCriticalSection(&server->lock); return 0; }
    server->client = client;
    LeaveCriticalSection(&server->lock);
    if (!kotlin_winrt_development_socket_timeout(client, 3000)) return 0;
    return 1;
}
static inline void kotlin_winrt_development_disconnect(kotlin_winrt_development_server *server) {
    EnterCriticalSection(&server->lock);
    if (server->pipe) DisconnectNamedPipe(server->pipe->handle);
    if (server->client != INVALID_SOCKET) { closesocket(server->client); server->client = INVALID_SOCKET; }
    LeaveCriticalSection(&server->lock);
}
static inline int kotlin_winrt_development_read(kotlin_winrt_development_server *server, void *bytes, int length) {
    if (server->pipe) return kotlin_winrt_development_pipe_transfer(server->pipe, 1, bytes, length);
    int offset = 0;
    ULONGLONG deadline = GetTickCount64() + 3000;
    while (offset < length) {
        if (GetTickCount64() >= deadline) return 0;
        int count = recv(server->client, (char *)bytes + offset, length - offset, 0);
        if (count <= 0) return 0;
        offset += count;
    }
    return 1;
}
static inline int kotlin_winrt_development_write(kotlin_winrt_development_server *server, const void *bytes, int length) {
    if (server->pipe) {
        /* DisconnectNamedPipe discards unread buffered bytes. A bounded receipt
         * replaces an unbounded synchronous FlushFileBuffers on the worker. */
        unsigned char receipt = 0;
        return kotlin_winrt_development_pipe_transfer(server->pipe, 2, (void *)bytes, length) &&
            kotlin_winrt_development_pipe_transfer(server->pipe, 1, &receipt, 1) && receipt == 0x6b;
    }
    int offset = 0;
    ULONGLONG deadline = GetTickCount64() + 3000;
    while (offset < length) {
        if (GetTickCount64() >= deadline) return 0;
        int count = send(server->client, (const char *)bytes + offset, length - offset, 0);
        if (count <= 0) return 0;
        offset += count;
    }
    return 1;
}
static inline int kotlin_winrt_development_authenticate(kotlin_winrt_development_server *server, const void *token_bytes, int length) {
    const char *token = (const char *)token_bytes;
    if (length != 43) return 0;
    unsigned char difference = 0;
    for (int index = 0; index < 43; ++index) difference |= server->token[index] ^ token[index];
    return difference == 0;
}
#endif
