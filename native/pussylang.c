/*
 * pussylang.c a Native library for PussyLang
 *
 * This file provides native functions for:
 *   - Executable memory allocation
 *   - exec() function
 *   - protect() function
 *   - get_proc() and others
 *  And whatever other we might need add but too much to do it so feel free to do it urself ok
 */


 #include <jni.h>
 #include <stdio.h>
 #include <stdlib.h>
 #include <string.h>
 #include <stdint.h>

 #ifdef _WIN32
 #include <windows.h>
 #else
 #include <sys/mman.h>
 #include <unistd.h>
 #include <pthread.h>
 #endif


 JNIEXPORT void JNICALL Java_pussylang_vm_NativeRegistry_00024Exec_exec0
   (JNIEnv *env, jobject obj, jlong ptr) {

     printf("[exec] shellcode @ 0x%llX\n", (unsigned long long)ptr);

 #ifdef _WIN32
     SYSTEM_INFO si;
     GetSystemInfo(&si);
     DWORD_PTR pageSize = si.dwPageSize;
     LPVOID aligned = (LPVOID)(ptr & ~(pageSize - 1));
     SIZE_T regionSize = pageSize + (ptr - (DWORD_PTR)aligned);

     DWORD oldProtect;
     if (!VirtualProtect(aligned, regionSize, PAGE_EXECUTE_READ, &oldProtect)) {
         printf("[exec] VirtualProtect failed: %lu\n", GetLastError());
         return;
     }

     void (*func)() = (void(*)())ptr;
     func();
 #else
     long pagesize = sysconf(_SC_PAGESIZE);
     void *aligned = (void *)(ptr & ~(pagesize - 1));
     mprotect(aligned, pagesize, PROT_READ | PROT_EXEC);
     void (*func)() = (void(*)())ptr;
     func();
 #endif
 }


 JNIEXPORT void JNICALL Java_pussylang_vm_NativeRegistry_00024Protect_protect0
   (JNIEnv *env, jobject obj, jlong ptr, jlong size, jint flags) {

     printf("[protect] address: 0x%llX, size: %lld, flags: 0x%X\n",
            (unsigned long long)ptr, (long long)size, flags);

 #ifdef _WIN32
     SYSTEM_INFO si;
     GetSystemInfo(&si);
     DWORD_PTR pageSize = si.dwPageSize;
     LPVOID aligned = (LPVOID)(ptr & ~(pageSize - 1));
     SIZE_T regionSize = (SIZE_T)size + (ptr - (DWORD_PTR)aligned);

     DWORD oldProtect;
     DWORD winFlags;
     switch (flags) {
         case 0x01: winFlags = PAGE_NOACCESS; break;
         case 0x02: winFlags = PAGE_READONLY; break;
         case 0x04: winFlags = PAGE_READWRITE; break;
         case 0x10: winFlags = PAGE_EXECUTE; break;
         case 0x20: winFlags = PAGE_EXECUTE_READ; break;
         case 0x40: winFlags = PAGE_EXECUTE_READWRITE; break;
         default:   winFlags = PAGE_EXECUTE_READWRITE;
     }
     VirtualProtect(aligned, regionSize, winFlags, &oldProtect);
 #else
     int prot = 0;
     if (flags & 0x02) prot |= PROT_READ;
     if (flags & 0x04) prot |= PROT_WRITE;
     if (flags & 0x10) prot |= PROT_EXEC;
     long pagesize = sysconf(_SC_PAGESIZE);
     void *aligned = (void *)(ptr & ~(pagesize - 1));
     size_t alignedSize = size + (ptr - (long)aligned);
     mprotect(aligned, alignedSize, prot);
 #endif
 }

 JNIEXPORT jlong JNICALL Java_pussylang_vm_NativeRegistry_00024GetProc_getProc0
   (JNIEnv *env, jobject obj, jstring dll, jstring func) {
     const char *dllStr = (*env)->GetStringUTFChars(env, dll, NULL);
     const char *funcStr = (*env)->GetStringUTFChars(env, func, NULL);
     HMODULE hMod = GetModuleHandleA(dllStr);
     if (!hMod) hMod = LoadLibraryA(dllStr);
     FARPROC proc = GetProcAddress(hMod, funcStr);
     (*env)->ReleaseStringUTFChars(env, dll, dllStr);
     (*env)->ReleaseStringUTFChars(env, func, funcStr);
     return (jlong)proc;
 }


 JNIEXPORT jobject JNICALL Java_pussylang_vm_NativeRegistry_00024Call_call0
   (JNIEnv *env, jobject obj, jlong ptr, jstring returnType, jstring argTypes, jobjectArray args) {

     const char *retStr = (*env)->GetStringUTFChars(env, returnType, NULL);
     const char *argStr = (*env)->GetStringUTFChars(env, argTypes, NULL);
     jsize argc = (*env)->GetArrayLength(env, args);

     uintptr_t vals[4] = {0};
     for (int i = 0; i < argc && i < 4; i++) {
         jobject arg = (*env)->GetObjectArrayElement(env, args, i);
         char type = argStr[i];
         if (type == 'i' || type == 'p' || type == 'l') {
             // Get Double object value
             jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
             jmethodID doubleValue = (*env)->GetMethodID(env, doubleClass, "doubleValue", "()D");
             jdouble d = (*env)->CallDoubleMethod(env, arg, doubleValue);
             vals[i] = (uintptr_t)(int64_t)d;
         } else if (type == 's') {
             const char *s = (*env)->GetStringUTFChars(env, (jstring)arg, NULL);
             vals[i] = (uintptr_t)s;
         }
         (*env)->DeleteLocalRef(env, arg);
     }

     typedef uintptr_t (*func0)(void);
     typedef uintptr_t (*func1)(uintptr_t);
     typedef uintptr_t (*func2)(uintptr_t, uintptr_t);
     typedef uintptr_t (*func3)(uintptr_t, uintptr_t, uintptr_t);
     typedef uintptr_t (*func4)(uintptr_t, uintptr_t, uintptr_t, uintptr_t);

     uintptr_t result = 0;
     switch (argc) {
         case 0: result = ((func0)ptr)(); break;
         case 1: result = ((func1)ptr)(vals[0]); break;
         case 2: result = ((func2)ptr)(vals[0], vals[1]); break;
         case 3: result = ((func3)ptr)(vals[0], vals[1], vals[2]); break;
         case 4: result = ((func4)ptr)(vals[0], vals[1], vals[2], vals[3]); break;
         default: result = 0;
     }

     (*env)->ReleaseStringUTFChars(env, returnType, retStr);
     (*env)->ReleaseStringUTFChars(env, argTypes, argStr);

     if (retStr[0] == 'v') return NULL;
     if (retStr[0] == 'i' || retStr[0] == 'l' || retStr[0] == 'p') {
         jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
         jmethodID init = (*env)->GetMethodID(env, doubleClass, "<init>", "(D)V");
         return (*env)->NewObject(env, doubleClass, init, (jdouble)(int64_t)result);
     }
     if (retStr[0] == 's') {
         return (*env)->NewStringUTF(env, (const char*)result);
     }
     return NULL;
 }