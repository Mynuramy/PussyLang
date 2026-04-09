


# PussyLang

A lightweight scripting language designed for **malware development**, **red teaming**, and **low-level systems programming**.

![Status](https://img.shields.io/badge/Status-Early%20Development-red.svg)
![Language](https://img.shields.io/badge/Language-Java-blue.svg)

## Features

- Fast bytecode VM with a simple compiler
- C-like syntax with a few touches
- Native support for **byte strings** (`b"\x90\x90\xCC"`)
- Hex literals (`0xDEAD`)
- Built-in memory manipulation: `alloc`, `write`, `exec`, `free` (WIP!!!)
- Functions, recursion, control flow (`if`, `while`) (WIPPP!!!)
- Switchable execution with a tree walk interpreter or bytecode compiler
- Easy to extend with native functions

## Demo

```pussy
func factorial(n) {
    if (n <= 1) { return 1; }
    return n * factorial(n - 1);
}

print factorial(6);

var sc = b"\x90\x90\xCC";
print len(sc);
print hex(0xDEAD);

var buf = alloc(0x1000);
write(buf, sc, 3);
exec(buf);
free(buf);
```

**Output:**

```
720
3
0xDEAD
[alloc] 4096 bytes @ 0x16D0C345B00
[write] 3 bytes -> 0x16D0C345B00
[exec] shellcode @ 0x16D0C345B00
[free] 0x16D0C345B00
```

## Running the Language

### Run a script file
```bash
java pussylang.Main example.pussy
```

### Disassemble bytecode
```bash
java pussylang.Main --dis example.pussy
```

### Run with tree‑walk interpreter (legacy)
```bash
java pussylang.Main --interpret example.pussy
```

## Contributing

This is currently a solo project focused on learning and red team tooling.  
Ideas, feedback, and contributions are welcome especially in these areas:

- Maldev specific built ins
- Better syntax design
- A testing framework


