


# PussyLang

A lightweight scripting language designed for sexy stuff.

![Status](https://img.shields.io/badge/Status-Early%20Development-red.svg)


## Features(some WIP)

| Category                | Supported                                                                               |
|-------------------------|-----------------------------------------------------------------------------------------|
| **Data Types**          | Numbers (double & hex), booleans, `null`, strings, byte strings (`b"\x90\x90"`)          |
| **Control Flow**        | `if` / `else`, `while` loops                                                            |
| **Functions**           | First class functions, recursion, **lexical closures** with mutable upvalues             |
| **Variable Scoping**    | Blockscoped `var` declarations, proper shadowing                                       |
| **Execution Engines**   | Treewalk interpreter  and a **bytecode VM** with closure support             |
| **Tooling**             | Bytecode disassembler, error messages with line numbers                                  |
| **Native Interface**    | Easily add Java‑backed builtins                                                         |
| **Maldev Primitives**   | `alloc`, `free`, `write`, `exec`, `read` (WIPPPPP)                                       |

## Example

```pussy
// Variables
var x = 10;
x = x + 5;

// Control flow
if (x > 10) {
    print "big";
} else {
    print "small";
}

while (x < 20) {
    print x;
    x = x + 1;
}

// Functions and Closures
func makeCounter() {
    var count = 0;
    func inc() {
        count = count + 1;
        return count;
    }
    return inc;
}

var c = makeCounter();
print c();  // 1
print c();  // 2
```




## Running the Language

### Run a script file(on VM)
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


