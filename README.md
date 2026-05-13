<p align="center">
  <img src="logo.png" alt="PussyLang Logo">
</p>

# PussyLang
**Full documentation: [pussylang-docs.surge.sh](http://pussylang-docs.surge.sh/)**

A dynamically typed imperative scripting language with its own bytecode compiler, Java VM, and ahead of time C backend.




---

## Overview

PussyLang compiles source files (`.pussy`) to bytecode (`.pbc`) which runs on a custom Java VM. The bytecode can also be compiled ahead-of-time to a native executable by transpiling to C and compiling with GCC.

The language is intentionally minimal.

---

## Types

| Type | Example |
|------|---------|
| Number | `42` `3.14` `-7` |
| Boolean | `true` `false` |
| String | `"hello"` |
| Array | `[1, "two", true]` |
| Bytes | `b"\x48\x65\x6C\x6C\x6F"` |
| Function | `func foo() { ... }` |
| Null | `null` |

All numbers are doubles. Strings are immutable UTF-8. Arrays are dynamic and mixed-type. Bytes are a raw buffer type returned by native functions.

---

## Syntax

### Variables

```
var x = 10;
x = 20;
```

### Control flow

```
if (condition) {
    // ...
} else {
    if (other_condition) {
        // ...
    }
}

while (condition) {
    // ...
}
```

No `for` loops. No `else if`. No `&&` or `||` For now but switch and for loops in work!

### Functions

```
func add(a, b) {
    return a + b;
}

var result = add(1, 2);
```

Functions are first class values. Recursion is fully supported.

### Arrays

```
var arr = [10, 20, 30];
print arr[0];
arr[1] = 99;
```

---

## Native Functions

All builtins are available globally with no imports.

**Utility**
```
str(val)                   // convert to string
len(str_or_array_or_bytes) // length
chr(number)                // ASCII code to single char
hex(number)                // number to hex string
format("Hi {}!", name)     // string interpolation
input("prompt: ")          // read line from stdin
clock()                    // unix timestamp
sleep(ms)                  // sleep N milliseconds
cast(value, "int")         // truncate to integer
cast(value, "bytes")       // string to byte buffer
```

**File I/O**
```
file_read(path)            // returns bytes
file_write(path, bytes)    // writes bytes, returns count
file_exists(path)          // returns bool
list_dir(path)             // returns array of filenames
is_dir(path)               // returns bool
```

**Bytes**
```
xor_bytes(bytes, key)      // XOR every byte with key (0-255)
b64encode(bytes)           // bytes to base64 string
b64decode(string)          // base64 string to bytes
bytes_to_ascii(bytes, max) // bytes to printable string
pack(fmt, ...)             // binary pack  B H I Q, < > endian
mutable_bytes(bytes)       // copy of bytes buffer
```

**Networking**
```
tcp_connect(host, port)    // returns socket handle
tcp_send(handle, bytes)    // send bytes
tcp_recv(handle, maxsize)  // receive bytes
tcp_close(handle)          // close socket
```

**Memory**
```
alloc(size)                // allocate N bytes, returns pointer
free(ptr)                  // free pointer
write(ptr, bytes, count)   // write bytes to address
read(ptr, size)            // read bytes from address
ptr_add(ptr, offset)       // pointer arithmetic
```

**Windows**
```
get_proc(dll, funcname)    // get function pointer from DLL
call(ptr, rettype, argtypes, ...)  // call native function pointer
protect(ptr, size, flags)  // VirtualProtect / mprotect
exec(ptr)                  // execute 
```

---

## Running

**Interpreter (Java VM)**
```bash
java pussylang.Main --vm yourfile.pussy
```

**AOT compilation**
```bash
java pbc_to_c yourfile.pbc bytecode_embedded.c
gcc vm.c natives.c -o program.exe -lm -lws2_32
./program.exe
```

---

## What it doesn't have for now

No `for` loops. No `else if`. No `&&` / `||`. No `try`/`catch`. No classes. No modules. No ternary operator. No `break` or `continue`.





