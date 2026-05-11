/*
 * natives.c  , C port of NativeRegistry.java for the PussyLang AOT VM.
 *
 * Compile alongside vm.c:
 *   gcc vm.c natives.c -o program -lm -lws2_32   (Windows / MINGW)
 *   gcc vm.c natives.c -o program -lm -ldl        (Linux)
 *
 * Every function receives  (Value* args, int argc)  and returns a Value.
 * Use the helpers at the top (arg_num, arg_str, arg_bytes, make_*) to keep
 * individual functions short and readable in case you want to add your owns <3 , if you do , don't forget to add on java one too.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <time.h>
#include <math.h>


#ifdef _WIN32
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  include <winsock2.h>
#  include <ws2tcpip.h>
#  pragma comment(lib, "ws2_32.lib")
   typedef SOCKET sock_t;
#  define SOCK_INVALID INVALID_SOCKET
#  define SOCK_ERR     SOCKET_ERROR
#else
#  include <dirent.h>
#  include <sys/stat.h>
#  include <unistd.h>
#  include <sys/socket.h>
#  include <netinet/in.h>
#  include <arpa/inet.h>
#  include <netdb.h>
#  include <sys/mman.h>
#  include <dlfcn.h>
   typedef int sock_t;
#  define SOCK_INVALID (-1)
#  define SOCK_ERR     (-1)
#  define Sleep(ms)    usleep((ms)*1000)
#endif


typedef enum {
    VAL_NUMBER, VAL_BOOL, VAL_NULL, VAL_STRING,
    VAL_ARRAY,  VAL_FUNCTION, VAL_BYTES, VAL_NATIVE
} ValueType;

typedef struct Value Value;
struct Value {
    ValueType type;
    union {
        double   num;
        int      boolean;
        char*    str;
        struct { Value*   data; int length; } array;
        struct { uint8_t* data; int length; } bytes;
        int      func_id;
        int      native_id;
    };
};

typedef Value (*NativeFn)(Value* args, int argc);

typedef struct {
    const char* name;
    int         arity;
    NativeFn    fn;
} NativeDef;


static void native_error(const char* msg) {
    fprintf(stderr, "[native] %s\n", msg);
    exit(1);
}

static double arg_num(Value* args, int i) {
    if (args[i].type != VAL_NUMBER) native_error("expected number argument");
    return args[i].num;
}

static const char* arg_str(Value* args, int i) {
    if (args[i].type != VAL_STRING) native_error("expected string argument");
    return args[i].str;
}


static uint8_t* arg_bytes(Value* args, int i, int* out_len) {
    if (args[i].type == VAL_BYTES) {
        *out_len = args[i].bytes.length;
        return args[i].bytes.data;
    }
    if (args[i].type == VAL_STRING) {
        *out_len = (int)strlen(args[i].str);
        return (uint8_t*)args[i].str;
    }
    native_error("expected bytes or string argument");
    return NULL;
}


static Value make_null(void)           { Value v={.type=VAL_NULL};                          return v; }
static Value make_num(double n)        { Value v={.type=VAL_NUMBER,  .num=n};               return v; }
static Value make_bool(int b)          { Value v={.type=VAL_BOOL,    .boolean=!!b};         return v; }
static Value make_str(const char* s)   { Value v={.type=VAL_STRING,  .str=strdup(s)};       return v; }
static Value make_bytes(uint8_t* d, int len) {
    uint8_t* copy = malloc(len);
    memcpy(copy, d, len);
    Value v = { .type=VAL_BYTES };
    v.bytes.data   = copy;
    v.bytes.length = len;
    return v;
}


#define MAX_SOCKETS 64
static sock_t socket_table[MAX_SOCKETS];
static int    socket_used [MAX_SOCKETS];
static int    sockets_init = 0;

static void init_sockets(void) {
    if (sockets_init) return;
    memset(socket_used, 0, sizeof(socket_used));
    for (int i = 0; i < MAX_SOCKETS; i++) socket_table[i] = SOCK_INVALID;
#ifdef _WIN32
    WSADATA wsa;
    WSAStartup(MAKEWORD(2,2), &wsa);
#endif
    sockets_init = 1;
}

static int alloc_socket_slot(sock_t s) {
    for (int i = 0; i < MAX_SOCKETS; i++) {
        if (!socket_used[i]) {
            socket_table[i] = s;
            socket_used [i] = 1;
            return i + 1;
        }
    }
    native_error("too many open sockets");
    return -1;
}

static sock_t get_socket(int handle) {
    int slot = handle - 1;
    if (slot < 0 || slot >= MAX_SOCKETS || !socket_used[slot])
        native_error("invalid socket handle");
    return socket_table[slot];
}

static void free_socket_slot(int handle) {
    int slot = handle - 1;
    if (slot < 0 || slot >= MAX_SOCKETS) return;
    socket_table[slot] = SOCK_INVALID;
    socket_used [slot] = 0;
}




static Value n_clock(Value* a, int argc) {
    (void)a; (void)argc;
    return make_num((double)time(NULL));
}


static Value n_sleep(Value* a, int argc) {
    (void)argc;
    long ms = (long)arg_num(a, 0);
    Sleep(ms);
    return make_null();
}


static Value n_str(Value* a, int argc) {
    (void)argc;
    char buf[64];
    switch (a[0].type) {
        case VAL_NULL:    return make_str("null");
        case VAL_BOOL:    return make_str(a[0].boolean ? "true" : "false");
        case VAL_STRING:  return make_str(a[0].str);
        case VAL_NUMBER:
            snprintf(buf, sizeof(buf), "%g", a[0].num);
            return make_str(buf);
        default:
            return make_str("<value>");
    }
}


static Value n_len(Value* a, int argc) {
    (void)argc;
    switch (a[0].type) {
        case VAL_STRING: return make_num((double)strlen(a[0].str));
        case VAL_ARRAY:  return make_num((double)a[0].array.length);
        case VAL_BYTES:  return make_num((double)a[0].bytes.length);
        default: native_error("len() expects string, array, or bytes");
    }
    return make_null();
}


static Value n_hex(Value* a, int argc) {
    (void)argc;
    char buf[32];
    unsigned long long v = (unsigned long long)(long long)arg_num(a, 0);
    snprintf(buf, sizeof(buf), "0x%llX", v);
    return make_str(buf);
}


static Value n_chr(Value* a, int argc) {
    (void)argc;
    int c = (int)arg_num(a, 0);
    if (c < 0 || c > 255) native_error("chr() expects 0-255");
    char s[2] = { (char)c, '\0' };
    return make_str(s);
}


static Value n_alloc(Value* a, int argc) {
    (void)argc;
    size_t size = (size_t)arg_num(a, 0);
    void* ptr = calloc(1, size);
    if (!ptr) native_error("alloc() out of memory");
    printf("[alloc] %zu bytes @ %p\n", size, ptr);
    return make_num((double)(uintptr_t)ptr);
}


static Value n_free(Value* a, int argc) {
    (void)argc;
    uintptr_t ptr = (uintptr_t)(long long)arg_num(a, 0);
    printf("[free] %p\n", (void*)ptr);
    free((void*)ptr);
    return make_null();
}


static Value n_write(Value* a, int argc) {
    (void)argc;
    uintptr_t ptr = (uintptr_t)(long long)arg_num(a, 0);
    int dlen = 0;
    uint8_t* data = arg_bytes(a, 1, &dlen);
    int count = (int)arg_num(a, 2);
    if (count > dlen) count = dlen;
    memcpy((void*)ptr, data, count);
    printf("[write] %d bytes -> %p\n", count, (void*)ptr);
    return make_null();
}


static Value n_read(Value* a, int argc) {
    (void)argc;
    uintptr_t ptr  = (uintptr_t)(long long)arg_num(a, 0);
    int       size = (int)arg_num(a, 1);
    printf("[read] %d bytes <- %p\n", size, (void*)ptr);
    return make_bytes((uint8_t*)ptr, size);
}


static Value n_ptr_add(Value* a, int argc) {
    (void)argc;
    uintptr_t ptr    = (uintptr_t)(long long)arg_num(a, 0);
    long long offset = (long long)arg_num(a, 1);
    return make_num((double)(ptr + offset));
}


static Value n_xor_bytes(Value* a, int argc) {
    (void)argc;
    int dlen = 0;
    uint8_t* data = arg_bytes(a, 0, &dlen);
    uint8_t  key  = (uint8_t)(int)arg_num(a, 1);
    uint8_t* out  = malloc(dlen);
    for (int i = 0; i < dlen; i++) out[i] = data[i] ^ key;
    Value v = { .type=VAL_BYTES };
    v.bytes.data   = out;
    v.bytes.length = dlen;
    return v;
}


static Value n_bytes_to_ascii(Value* a, int argc) {
    (void)argc;
    int dlen = 0;
    uint8_t* data   = arg_bytes(a, 0, &dlen);
    int      maxLen = (int)arg_num(a, 1);
    int      n      = dlen < maxLen ? dlen : maxLen;
    char*    out    = malloc(n + 1);
    for (int i = 0; i < n; i++)
        out[i] = (data[i] >= 32 && data[i] <= 126) ? (char)data[i] : '.';
    out[n] = '\0';
    Value v = { .type=VAL_STRING, .str=out };
    return v;
}


static Value n_pack(Value* a, int argc) {
    if (argc < 1 || a[0].type != VAL_STRING) native_error("pack: first arg must be format string");
    const char* fmt = a[0].str;
    uint8_t buf[1024];
    int     pos     = 0;
    int     little  = 1;
    int     argIdx  = 1;

    for (int i = 0; fmt[i]; i++) {
        char c = fmt[i];
        if (c == ' ' || c == '\t') continue;
        if (c == '<') { little = 1; continue; }
        if (c == '>') { little = 0; continue; }
        if (argIdx >= argc) native_error("pack: not enough arguments");
        long long val = (long long)arg_num(a, argIdx++);
        switch (c) {
            case 'B':
                buf[pos++] = (uint8_t)val;
                break;
            case 'H': {
                uint16_t v = (uint16_t)val;
                if (little) { buf[pos]=(uint8_t)v; buf[pos+1]=(uint8_t)(v>>8); }
                else         { buf[pos]=(uint8_t)(v>>8); buf[pos+1]=(uint8_t)v; }
                pos += 2; break;
            }
            case 'I': {
                uint32_t v = (uint32_t)val;
                if (little) { for(int k=0;k<4;k++) buf[pos+k]=(uint8_t)(v>>(8*k)); }
                else         { for(int k=0;k<4;k++) buf[pos+k]=(uint8_t)(v>>(24-8*k)); }
                pos += 4; break;
            }
            case 'Q': {
                uint64_t v = (uint64_t)val;
                if (little) { for(int k=0;k<8;k++) buf[pos+k]=(uint8_t)(v>>(8*k)); }
                else         { for(int k=0;k<8;k++) buf[pos+k]=(uint8_t)(v>>(56-8*k)); }
                pos += 8; break;
            }
            default:
                fprintf(stderr,"pack: unknown format char '%c'\n", c);
                native_error("pack: bad format");
        }
    }
    return make_bytes(buf, pos);
}


static const char B64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static Value n_b64encode(Value* a, int argc) {
    (void)argc;
    int      dlen = 0;
    uint8_t* data = arg_bytes(a, 0, &dlen);
    int      outlen = ((dlen + 2) / 3) * 4 + 1;
    char*    out    = malloc(outlen);
    int      j      = 0;
    for (int i = 0; i < dlen; i += 3) {
        unsigned int v = (unsigned int)data[i] << 16;
        if (i+1 < dlen) v |= (unsigned int)data[i+1] << 8;
        if (i+2 < dlen) v |= data[i+2];
        out[j++] = B64[(v >> 18) & 0x3F];
        out[j++] = B64[(v >> 12) & 0x3F];
        out[j++] = (i+1 < dlen) ? B64[(v >> 6) & 0x3F] : '=';
        out[j++] = (i+2 < dlen) ? B64[(v     ) & 0x3F] : '=';
    }
    out[j] = '\0';
    Value v = { .type=VAL_STRING, .str=out };
    return v;
}


static int b64_char(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

static Value n_b64decode(Value* a, int argc) {
    (void)argc;
    const char* s    = arg_str(a, 0);
    int         slen = (int)strlen(s);
    int         outlen = (slen / 4) * 3 + 1;
    uint8_t*    out    = malloc(outlen);
    int         j      = 0;
    for (int i = 0; i < slen; i += 4) {
        int v0 = b64_char(s[i]);
        int v1 = b64_char(s[i+1]);
        int v2 = (s[i+2]=='=') ? 0 : b64_char(s[i+2]);
        int v3 = (s[i+3]=='=') ? 0 : b64_char(s[i+3]);
        if (v0<0||v1<0) break;
        out[j++] = (uint8_t)((v0<<2)|(v1>>4));
        if (s[i+2] != '=') out[j++] = (uint8_t)((v1<<4)|(v2>>2));
        if (s[i+3] != '=') out[j++] = (uint8_t)((v2<<6)|v3);
    }
    Value v = { .type=VAL_BYTES };
    v.bytes.data   = out;
    v.bytes.length = j;
    return v;
}

static Value n_list_dir(Value* a, int argc) {
    (void)argc;
    const char* path = arg_str(a, 0);

    Value* entries = malloc(1024 * sizeof(Value));
    int count = 0;

#ifdef _WIN32
    char pattern[1024];
    snprintf(pattern, sizeof(pattern), "%s\\*", path);

    WIN32_FIND_DATAA fd;
    HANDLE hFind = FindFirstFileA(pattern, &fd);
    if (hFind == INVALID_HANDLE_VALUE) {
        free(entries);
        return make_null();
    }

    do {
        if (strcmp(fd.cFileName, ".") == 0) continue;
        if (strcmp(fd.cFileName, "..") == 0) continue;


        entries[count].type = VAL_STRING;
        entries[count].str  = strdup(fd.cFileName);

        count++;
    } while (FindNextFileA(hFind, &fd) && count < 1024);

    FindClose(hFind);

#else
    DIR* d = opendir(path);
    if (!d) {
        free(entries);
        return make_null();
    }

    struct dirent* entry;
    while ((entry = readdir(d)) && count < 1024) {
        if (strcmp(entry->d_name, ".") == 0) continue;
        if (strcmp(entry->d_name, "..") == 0) continue;

        entries[count].type = VAL_STRING;
        entries[count].str  = strdup(entry->d_name);

        count++;
    }

    closedir(d);
#endif

    Value result;
    result.type = VAL_ARRAY;
    result.array.data = entries;
    result.array.length = count;

    return result;
}


static Value n_is_dir(Value* a, int argc) {
    (void)argc;
    const char* path = arg_str(a, 0);
#ifdef _WIN32
    DWORD attr = GetFileAttributesA(path);
    if (attr == INVALID_FILE_ATTRIBUTES) return make_bool(0);
    return make_bool(attr & FILE_ATTRIBUTE_DIRECTORY);
#else
    struct stat st;
    if (stat(path, &st) != 0) return make_bool(0);
    return make_bool(S_ISDIR(st.st_mode));
#endif
}


static Value n_exec(Value* a, int argc) {
    (void)argc;
    void* ptr = (void*)(uintptr_t)(long long)arg_num(a, 0);
    printf("[exec] shellcode @ %p\n", ptr);
#ifdef _WIN32
    ((void(*)())ptr)();
#else
    /* On Linux the memory must already be PROT_EXEC via protect() */
    ((void(*)())ptr)();
#endif
    return make_null();
}

static Value n_protect(Value* a, int argc) {
    (void)argc;
    void*  ptr   = (void*)(uintptr_t)(long long)arg_num(a, 0);
    size_t size  = (size_t)arg_num(a, 1);
    int    flags = (int)arg_num(a, 2);
#ifdef _WIN32
    DWORD old;
    VirtualProtect(ptr, size, (DWORD)flags, &old);
#else
    mprotect(ptr, size, flags);
#endif
    return make_null();
}


static Value n_get_proc(Value* a, int argc) {
    (void)argc;
    const char* dll  = arg_str(a, 0);
    const char* func = arg_str(a, 1);
#ifdef _WIN32
    HMODULE mod  = LoadLibraryA(dll);
    if (!mod) { fprintf(stderr,"get_proc: LoadLibrary(%s) failed\n", dll); return make_num(0); }
    FARPROC  proc = GetProcAddress(mod, func);
    return make_num((double)(uintptr_t)proc);
#else
    void* handle = dlopen(dll, RTLD_LAZY);
    if (!handle) { fprintf(stderr,"get_proc: dlopen(%s) failed\n", dll); return make_num(0); }
    void* proc = dlsym(handle, func);
    return make_num((double)(uintptr_t)proc);
#endif
}



typedef uint64_t (*fn0_t)(void);
typedef uint64_t (*fn1_t)(uint64_t);
typedef uint64_t (*fn2_t)(uint64_t,uint64_t);
typedef uint64_t (*fn3_t)(uint64_t,uint64_t,uint64_t);
typedef uint64_t (*fn4_t)(uint64_t,uint64_t,uint64_t,uint64_t);
typedef uint64_t (*fn5_t)(uint64_t,uint64_t,uint64_t,uint64_t,uint64_t);
typedef uint64_t (*fn6_t)(uint64_t,uint64_t,uint64_t,uint64_t,uint64_t,uint64_t);
typedef uint64_t (*fn7_t)(uint64_t,uint64_t,uint64_t,uint64_t,uint64_t,uint64_t,uint64_t);
typedef uint64_t (*fn8_t)(uint64_t,uint64_t,uint64_t,uint64_t,uint64_t,uint64_t,uint64_t,uint64_t);

static Value n_call(Value* a, int argc) {
    if (argc < 3) native_error("call: needs at least ptr, retType, argTypes");
    void*       ptr     = (void*)(uintptr_t)(long long)arg_num(a, 0);
    const char* retType = arg_str(a, 1);
    const char* argFmt  = arg_str(a, 2);
    int         nargs   = (int)strlen(argFmt);
    if (argc - 3 < nargs) native_error("call: too few arguments for argTypes");

    uint64_t iargs[8] = {0};
    for (int i = 0; i < nargs && i < 8; i++) {
        int ai = i + 3;
        switch (argFmt[i]) {
            case 'i': case 'p': case 'h':
                iargs[i] = (uint64_t)(long long)arg_num(a, ai);
                break;
            case 's':
                iargs[i] = (uint64_t)(uintptr_t)arg_str(a, ai);
                break;
            default:
                fprintf(stderr,"call: unknown argType '%c'\n", argFmt[i]);
                native_error("call: bad argTypes");
        }
    }

    uint64_t result = 0;
    switch (nargs) {
        case 0: result = ((fn0_t)ptr)(); break;
        case 1: result = ((fn1_t)ptr)(iargs[0]); break;
        case 2: result = ((fn2_t)ptr)(iargs[0],iargs[1]); break;
        case 3: result = ((fn3_t)ptr)(iargs[0],iargs[1],iargs[2]); break;
        case 4: result = ((fn4_t)ptr)(iargs[0],iargs[1],iargs[2],iargs[3]); break;
        case 5: result = ((fn5_t)ptr)(iargs[0],iargs[1],iargs[2],iargs[3],iargs[4]); break;
        case 6: result = ((fn6_t)ptr)(iargs[0],iargs[1],iargs[2],iargs[3],iargs[4],iargs[5]); break;
        case 7: result = ((fn7_t)ptr)(iargs[0],iargs[1],iargs[2],iargs[3],iargs[4],iargs[5],iargs[6]); break;
        case 8: result = ((fn8_t)ptr)(iargs[0],iargs[1],iargs[2],iargs[3],iargs[4],iargs[5],iargs[6],iargs[7]); break;
        default: native_error("call: more than 8 args not supported");
    }

    if (strcmp(retType, "void") == 0) return make_null();
    return make_num((double)(long long)result);
}


static Value n_inject(Value* a, int argc) {
    (void)argc;
    int dlen = 0;
    arg_bytes(a, 1, &dlen);
    printf("[inject] %d bytes -> PID %d  (not yet implemented)\n", dlen, (int)arg_num(a, 0));
    return make_null();
}


static Value n_cast(Value* a, int argc) {
    (void)argc;
    const char* t = arg_str(a, 1);
    if (strcmp(t, "int") == 0) {
        if (a[0].type == VAL_NUMBER) return make_num((double)(long long)a[0].num);
        native_error("cast to int: not a number");
    } else if (strcmp(t, "string") == 0) {
        return n_str(a, 1);
    } else if (strcmp(t, "bytes") == 0) {
        int dlen = 0; uint8_t* d = arg_bytes(a, 0, &dlen);
        return make_bytes(d, dlen);
    }
    native_error("cast: unknown target type");
    return make_null();
}


static Value n_buffer(Value* a, int argc) {
    (void)argc;
    void* ptr  = (void*)(uintptr_t)(long long)arg_num(a, 0);
    int   size = (int)arg_num(a, 1);

    return make_bytes((uint8_t*)ptr, size);
}


static Value n_mutable_bytes(Value* a, int argc) {
    (void)argc;
    int dlen = 0; uint8_t* d = arg_bytes(a, 0, &dlen);
    return make_bytes(d, dlen);
}


static Value n_tcp_connect(Value* a, int argc) {
    (void)argc;
    init_sockets();
    const char* host = arg_str(a, 0);
    int         port = (int)arg_num(a, 1);

    struct addrinfo hints = {0}, *res = NULL;
    hints.ai_family   = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    char portstr[8]; snprintf(portstr, sizeof(portstr), "%d", port);
    if (getaddrinfo(host, portstr, &hints, &res) != 0)
        native_error("tcp_connect: getaddrinfo failed");

    sock_t s = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (s == SOCK_INVALID) { freeaddrinfo(res); native_error("tcp_connect: socket() failed"); }
    if (connect(s, res->ai_addr, (int)res->ai_addrlen) == SOCK_ERR) {
        freeaddrinfo(res);
#ifdef _WIN32
        closesocket(s);
#else
        close(s);
#endif
        native_error("tcp_connect: connect() failed");
    }
    freeaddrinfo(res);
    int handle = alloc_socket_slot(s);
    printf("[tcp] connected to %s:%d (handle=%d)\n", host, port, handle);
    return make_num((double)handle);
}


static Value n_tcp_send(Value* a, int argc) {
    (void)argc;
    init_sockets();
    int      handle = (int)arg_num(a, 0);
    int      dlen   = 0;
    uint8_t* data   = arg_bytes(a, 1, &dlen);
    sock_t   s      = get_socket(handle);
    int      sent   = (int)send(s, (char*)data, dlen, 0);
    if (sent == SOCK_ERR) native_error("tcp_send: send() failed");
    printf("[tcp] sent %d bytes on handle %d\n", sent, handle);
    return make_num((double)sent);
}

static Value n_file_read(Value* a, int argc) {
    const char* path = arg_str(a, 0);
    FILE* f = fopen(path, "rb");
    if (!f) return make_null();
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    rewind(f);
    uint8_t* buf = malloc(size);
    fread(buf, 1, size, f);
    fclose(f);
    Value v = { .type=VAL_BYTES };
    v.bytes.data = buf; v.bytes.length = (int)size;
    return v;
}

static Value n_file_write(Value* a, int argc) {
    const char* path = arg_str(a, 0);
    int dlen = 0; uint8_t* data = arg_bytes(a, 1, &dlen);
    FILE* f = fopen(path, "wb");
    if (!f) return make_num(0);
    fwrite(data, 1, dlen, f);
    fclose(f);
    return make_num(dlen);
}

static Value n_file_exists(Value* a, int argc) {
    (void)argc;
    FILE* f = fopen(arg_str(a, 0), "r");
    if (!f) return make_bool(0);
    fclose(f);
    return make_bool(1);
}

static Value n_input(Value* a, int argc) {
    if (argc > 0) printf("%s", arg_str(a, 0));
    char buf[1024];
    if (!fgets(buf, sizeof(buf), stdin)) return make_str("");

    int len = strlen(buf);
    if (len > 0 && buf[len-1] == '\n') buf[len-1] = '\0';
    return make_str(buf);
}

static Value n_format(Value* a, int argc) {

    const char* tpl = arg_str(a, 0);
    char out[4096];
    int  o = 0, arg = 1;
    for (int i = 0; tpl[i] && o < 4090; i++) {
        if (tpl[i] == '{' && tpl[i+1] == '}' && arg < argc) {
            Value v = a[arg++];
            char tmp[256];
            switch (v.type) {
                case VAL_NUMBER: snprintf(tmp,sizeof(tmp),"%g",v.num); break;
                case VAL_BOOL:   snprintf(tmp,sizeof(tmp),"%s",v.boolean?"true":"false"); break;
                case VAL_NULL:   snprintf(tmp,sizeof(tmp),"null"); break;
                case VAL_STRING: snprintf(tmp,sizeof(tmp),"%s",v.str); break;
                default:         snprintf(tmp,sizeof(tmp),"<val>"); break;
            }
            int tlen = strlen(tmp);
            memcpy(out+o, tmp, tlen); o += tlen; i++;
        } else {
            out[o++] = tpl[i];
        }
    }
    out[o] = '\0';
    return make_str(out);
}





static Value n_tcp_recv(Value* a, int argc) {
    (void)argc;
    init_sockets();
    int    handle  = (int)arg_num(a, 0);
    int    maxSize = (int)arg_num(a, 1);
    sock_t s       = get_socket(handle);
    uint8_t* buf   = malloc(maxSize);
    int      r     = (int)recv(s, (char*)buf, maxSize, 0);
    if (r <= 0) { free(buf); return make_bytes(NULL, 0); }
    printf("[tcp] received %d bytes on handle %d\n", r, handle);
    Value v = { .type=VAL_BYTES };
    v.bytes.data   = buf;
    v.bytes.length = r;
    return v;
}


static Value n_tcp_close(Value* a, int argc) {
    (void)argc;
    init_sockets();
    int    handle = (int)arg_num(a, 0);
    sock_t s      = get_socket(handle);
#ifdef _WIN32
    closesocket(s);
#else
    close(s);
#endif
    free_socket_slot(handle);
    printf("[tcp] closed handle %d\n", handle);
    return make_null();
}

static Value n_system(Value* a, int argc) {
    (void)argc;
    const char* cmd = arg_str(a, 0);
    int ret = system(cmd);
    return make_num((double)ret);
}



#ifdef _WIN32

static HWND     gfx_hwnd     = NULL;
static HDC      gfx_mem_dc   = NULL;
static HBITMAP  gfx_bitmap   = NULL;
static HBITMAP  gfx_old_bmp  = NULL;
static int      gfx_win_w    = 0;
static int      gfx_win_h    = 0;
static int      gfx_canvas_h = 0;
static COLORREF gfx_color    = RGB(0, 0, 0);
static int      gfx_pen_size = 4;
static int      gfx_mouse_x  = 0;
static int      gfx_mouse_y  = 0;
static int      gfx_btn_down = 0;
static int      gfx_prev_x   = -1;
static int      gfx_prev_y   = -1;
static int      gfx_last_evt = 0;
static int      gfx_alive    = 0;
static int gfx_stroke_enabled = 1;

static void gfx_stroke(int x, int y) {
    if (!gfx_mem_dc) return;
    if (y >= gfx_canvas_h) return;
    HPEN   pen       = CreatePen(PS_SOLID, gfx_pen_size, gfx_color);
    HPEN   old_pen   = (HPEN)SelectObject(gfx_mem_dc, pen);
    HBRUSH brush     = CreateSolidBrush(gfx_color);
    HBRUSH old_brush = (HBRUSH)SelectObject(gfx_mem_dc, brush);
    if (gfx_prev_x >= 0 && gfx_btn_down) {
        MoveToEx(gfx_mem_dc, gfx_prev_x, gfx_prev_y, NULL);
        LineTo(gfx_mem_dc, x, y);
    }
    int r = gfx_pen_size / 2 + 1;
    Ellipse(gfx_mem_dc, x - r, y - r, x + r, y + r);
    SelectObject(gfx_mem_dc, old_pen);
    SelectObject(gfx_mem_dc, old_brush);
    DeleteObject(pen);
    DeleteObject(brush);
    InvalidateRect(gfx_hwnd, NULL, FALSE);
    UpdateWindow(gfx_hwnd);
}

static LRESULT CALLBACK GfxWndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC hdc = BeginPaint(hwnd, &ps);
            if (gfx_mem_dc)
                BitBlt(hdc, 0, 0, gfx_win_w, gfx_win_h, gfx_mem_dc, 0, 0, SRCCOPY);
            EndPaint(hwnd, &ps);
            return 0;
        }
        case WM_DESTROY:
            gfx_alive = 0;
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcA(hwnd, msg, wp, lp);
}

static Value n_gfx_create(Value* a, int argc) {
    int w         = (int)arg_num(a, 0);
    int h         = (int)arg_num(a, 1);
    int toolbar_h = (int)arg_num(a, 2);
    const char* t = arg_str(a, 3);

    gfx_win_w    = w;
    gfx_win_h    = h;
    gfx_canvas_h = h - toolbar_h;
    gfx_alive    = 1;

    WNDCLASSEXA wc   = {0};
    wc.cbSize        = sizeof(wc);
    wc.style         = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc   = GfxWndProc;
    wc.hInstance     = GetModuleHandleA(NULL);
    wc.hCursor       = LoadCursorA(NULL, IDC_CROSS);
    wc.hbrBackground = (HBRUSH)GetStockObject(WHITE_BRUSH);
    wc.lpszClassName = "PLGfx";
    static int gfx_class_registered = 0;

    if (!gfx_class_registered) {

        RegisterClassExA(&wc);

        gfx_class_registered = 1;
    }

    RECT rc = {0, 0, w, h};
    AdjustWindowRect(&rc, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU, FALSE);

    gfx_hwnd = CreateWindowExA(0, "PLGfx", t,
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU,
        CW_USEDEFAULT, CW_USEDEFAULT,
        rc.right - rc.left, rc.bottom - rc.top,
        NULL, NULL, GetModuleHandleA(NULL), NULL);

    if (!gfx_hwnd) return make_bool(0);

    HDC hdc    = GetDC(gfx_hwnd);
    gfx_mem_dc = CreateCompatibleDC(hdc);
    gfx_bitmap = CreateCompatibleBitmap(hdc, w, h);
    gfx_old_bmp = (HBITMAP)SelectObject(gfx_mem_dc, gfx_bitmap);
    ReleaseDC(gfx_hwnd, hdc);

    RECT fill = {0, 0, w, h};
    FillRect(gfx_mem_dc, &fill, (HBRUSH)GetStockObject(WHITE_BRUSH));

    ShowWindow(gfx_hwnd, SW_SHOW);
    UpdateWindow(gfx_hwnd);
    return make_bool(1);
}

static Value n_gfx_poll(Value* a, int argc) {
    if (!gfx_hwnd || !gfx_alive) return make_num(4);
    gfx_last_evt = 0;
    MSG msg;
    while (PeekMessageA(&msg, NULL, 0, 0, PM_REMOVE)) {
        if (msg.message == WM_QUIT) { gfx_alive = 0; return make_num(4); }
        int mx = (short)LOWORD(msg.lParam);
        int my = (short)HIWORD(msg.lParam);
        if (msg.message == WM_LBUTTONDOWN) {
            gfx_btn_down = 1;
            gfx_mouse_x  = mx; gfx_mouse_y = my;
            gfx_prev_x   = mx; gfx_prev_y  = my;
            if (my < gfx_canvas_h) {
                if (gfx_stroke_enabled) {
                    gfx_stroke(mx, my);
                }
                gfx_last_evt = 2;
            } else {
                gfx_last_evt = 5;
            }
        }

        if (msg.message == WM_MOUSEMOVE) {
            gfx_mouse_x = mx; gfx_mouse_y = my;
            if (gfx_btn_down && my < gfx_canvas_h) {
                if (gfx_stroke_enabled) {
                    gfx_stroke(mx, my);
                }
                gfx_last_evt = 1;
            }
            gfx_prev_x = mx; gfx_prev_y = my;
        }
        if (msg.message == WM_LBUTTONUP) {
            gfx_btn_down = 0;
            gfx_prev_x   = -1; gfx_prev_y = -1;
            gfx_last_evt = 3;
        }
        TranslateMessage(&msg);
        DispatchMessageA(&msg);
    }
    return make_num((double)gfx_last_evt);
}

static Value n_gfx_set_stroke(Value* a, int argc) {
    (void)argc;
    int enable = (int)arg_num(a, 0);
    gfx_stroke_enabled = (enable != 0);
    return make_null();
}

static Value n_gfx_mouse_x(Value* a, int argc) { return make_num(gfx_mouse_x); }
static Value n_gfx_mouse_y(Value* a, int argc) { return make_num(gfx_mouse_y); }
static Value n_gfx_is_open(Value* a, int argc) { return make_bool(gfx_alive);  }

static Value n_gfx_set_color(Value* a, int argc) {
    gfx_color = RGB((int)arg_num(a,0),(int)arg_num(a,1),(int)arg_num(a,2));
    return make_null();
}

static Value n_gfx_set_size(Value* a, int argc) {
    gfx_pen_size = (int)arg_num(a, 0);
    if (gfx_pen_size < 1)  gfx_pen_size = 1;
    if (gfx_pen_size > 60) gfx_pen_size = 60;
    return make_null();
}

static Value n_gfx_clear(Value* a, int argc) {
    if (!gfx_mem_dc) return make_null();
    RECT rc = {0, 0, gfx_win_w, gfx_canvas_h};
    HBRUSH br = CreateSolidBrush(RGB((int)arg_num(a,0),(int)arg_num(a,1),(int)arg_num(a,2)));
    FillRect(gfx_mem_dc, &rc, br);
    DeleteObject(br);
    InvalidateRect(gfx_hwnd, NULL, FALSE);
    UpdateWindow(gfx_hwnd);
    return make_null();
}

static Value n_gfx_fill_rect(Value* a, int argc) {
    if (!gfx_mem_dc) return make_null();
    RECT rc = {(int)arg_num(a,0),(int)arg_num(a,1),(int)arg_num(a,2),(int)arg_num(a,3)};
    HBRUSH br = CreateSolidBrush(RGB((int)arg_num(a,4),(int)arg_num(a,5),(int)arg_num(a,6)));
    FillRect(gfx_mem_dc, &rc, br);
    DeleteObject(br);
    InvalidateRect(gfx_hwnd, NULL, FALSE);
    UpdateWindow(gfx_hwnd);
    return make_null();
}

static Value n_gfx_draw_text(Value* a, int argc) {
    if (!gfx_mem_dc) return make_null();
    int x = (int)arg_num(a,0);
    int y = (int)arg_num(a,1);
    const char* txt = arg_str(a,2);
    SetBkMode(gfx_mem_dc, TRANSPARENT);
    SetTextColor(gfx_mem_dc, RGB((int)arg_num(a,3),(int)arg_num(a,4),(int)arg_num(a,5)));
    TextOutA(gfx_mem_dc, x, y, txt, (int)strlen(txt));
    InvalidateRect(gfx_hwnd, NULL, FALSE);
    UpdateWindow(gfx_hwnd);
    return make_null();
}

static Value n_gfx_close(Value* a, int argc) {
    (void)a;
    (void)argc;

    gfx_alive = 0;

    if (gfx_hwnd) {
        DestroyWindow(gfx_hwnd);
        gfx_hwnd = NULL;
    }

    if (gfx_mem_dc) {

        if (gfx_old_bmp) {
            SelectObject(gfx_mem_dc, gfx_old_bmp);
        }

        DeleteDC(gfx_mem_dc);

        gfx_mem_dc = NULL;
    }

    if (gfx_bitmap) {
        DeleteObject(gfx_bitmap);
        gfx_bitmap = NULL;
    }

    gfx_old_bmp = NULL;

    gfx_prev_x = -1;
    gfx_prev_y = -1;

    gfx_btn_down = 0;

    return make_null();
}

static Value n_gfx_save(Value* a, int argc) {
    if (!gfx_mem_dc || !gfx_bitmap) return make_bool(0);
    const char* path = arg_str(a, 0);
    BITMAP bm;
    GetObject(gfx_bitmap, sizeof(bm), &bm);
    BITMAPINFOHEADER bmih = {0};
    bmih.biSize     = sizeof(bmih);
    bmih.biWidth    = bm.bmWidth;
    bmih.biHeight   = bm.bmHeight;
    bmih.biPlanes   = 1;
    bmih.biBitCount = 24;
    int stride    = ((bm.bmWidth * 3 + 3) / 4) * 4;
    int data_size = stride * bm.bmHeight;
    uint8_t* pix  = (uint8_t*)malloc(data_size);
    GetDIBits(gfx_mem_dc, gfx_bitmap, 0, bm.bmHeight, pix, (BITMAPINFO*)&bmih, DIB_RGB_COLORS);
    BITMAPFILEHEADER bmfh = {0};
    bmfh.bfType    = 0x4D42;
    bmfh.bfOffBits = sizeof(bmfh) + sizeof(bmih);
    bmfh.bfSize    = bmfh.bfOffBits + data_size;
    FILE* f = fopen(path, "wb");
    if (!f) { free(pix); return make_bool(0); }
    fwrite(&bmfh, 1, sizeof(bmfh), f);
    fwrite(&bmih, 1, sizeof(bmih), f);
    fwrite(pix,   1, data_size,    f);
    fclose(f);
    free(pix);
    return make_bool(1);
}

#endif


static Value n_is_key_pressed(Value* a, int argc) {
    (void)argc;
    int vk = (int)arg_num(a, 0);
#ifdef _WIN32
    short state = GetAsyncKeyState(vk);

    int pressed = (state & 0x8000) != 0;
#else
    int pressed = 0; // not implemented on Linux
#endif
    return make_bool(pressed);
}

#ifdef _WIN32
static Value n_millis(Value* a, int argc) {
    (void)a; (void)argc;
    return make_num((double)GetTickCount());
}
#else
static Value n_millis(Value* a, int argc) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return make_num((double)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000));
}
#endif


NativeDef native_table[] = {
    /* name             arity   function       */
    { "clock",          0,      n_clock        },
    { "sleep",          1,      n_sleep        },
    { "str",            1,      n_str          },
    { "len",            1,      n_len          },
    { "hex",            1,      n_hex          },
    { "chr",            1,      n_chr          },
    { "alloc",          1,      n_alloc        },
    { "free",           1,      n_free         },
    { "write",          3,      n_write        },
    { "read",           2,      n_read         },
    { "ptr_add",        2,      n_ptr_add      },
    { "system", 1, n_system },
    { "millis", 0, n_millis },
    { "is_key_pressed", 1, n_is_key_pressed },
    { "xor_bytes",      2,      n_xor_bytes    },
    { "bytes_to_ascii", 2,      n_bytes_to_ascii },
    { "pack",          -1,      n_pack         },
    { "file_read",   1, n_file_read   },
    { "file_write",  2, n_file_write  },
    { "file_exists", 1, n_file_exists },
    { "list_dir",  1, n_list_dir  },
    { "is_dir",    1, n_is_dir    },
    { "input", -1, n_input },
    { "format",        -1,      n_format       },
    { "b64encode",      1,      n_b64encode    },
    { "b64decode",      1,      n_b64decode    },
    { "exec",           1,      n_exec         },
    { "protect",        3,      n_protect      },
    { "get_proc",       2,      n_get_proc     },
    { "call",          -1,      n_call         },
    { "inject",         2,      n_inject       },
    { "cast",           2,      n_cast         },
    { "buffer",         2,      n_buffer       },
    { "mutable_bytes",  1,      n_mutable_bytes },
    { "tcp_connect",    2,      n_tcp_connect  },
    { "tcp_send",       2,      n_tcp_send     },
    { "tcp_recv",       2,      n_tcp_recv     },
    { "tcp_close",      1,      n_tcp_close    },

    #ifdef _WIN32
        { "gfx_create",    4, n_gfx_create    },
        { "gfx_poll",      0, n_gfx_poll      },
        { "gfx_mouse_x",   0, n_gfx_mouse_x   },
        { "gfx_mouse_y",   0, n_gfx_mouse_y   },
        { "gfx_is_open",   0, n_gfx_is_open   },
        { "gfx_set_color", 3, n_gfx_set_color },
        { "gfx_set_stroke",1, n_gfx_set_stroke },
        { "gfx_set_size",  1, n_gfx_set_size  },
        { "gfx_close", 0, n_gfx_close },
        { "gfx_clear",     3, n_gfx_clear     },
        { "gfx_fill_rect", 7, n_gfx_fill_rect },
        { "gfx_draw_text", 6, n_gfx_draw_text },
        { "gfx_save",      1, n_gfx_save      },
    #endif
};

int native_table_count = sizeof(native_table) / sizeof(native_table[0]);