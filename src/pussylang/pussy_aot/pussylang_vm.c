#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>




typedef enum {
    VAL_NUMBER,
    VAL_BOOL,
    VAL_NULL,
    VAL_STRING,
    VAL_ARRAY,
    VAL_FUNCTION,
    VAL_BYTES,
    VAL_NATIVE,
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


typedef enum { CONST_NUMBER, CONST_STRING, CONST_FUNCTION } ConstType;
typedef struct {
    ConstType type;
    union {
        double      num;
        const char* str;
        int         func_id;
    };
} Constant;

typedef struct {
    const char*          name;
    int                  arity;
    const unsigned char* code;
    size_t               code_len;
    const Constant*      constants;
    size_t               constants_len;
} Function;


#define STACK_MAX 16384
static Value stack[STACK_MAX];
static int   stack_top = 0;

static void  push(Value v)  { stack[stack_top++] = v; }
static Value pop(void)      { return stack[--stack_top]; }
static Value peek(int dist) { return stack[stack_top - 1 - dist]; }


typedef struct {
    const uint8_t*       return_ip;
    int                  base;
    int                  locals_count;
    const unsigned char* code;
    size_t               code_len;
    const Constant*      constants;
} CallFrame;

#define FRAMES_MAX 256
static CallFrame frames[FRAMES_MAX];
static int       frame_depth = 0;

static CallFrame* current_frame(void) { return &frames[frame_depth - 1]; }


#include "bytecode_embedded.c"


typedef Value (*NativeFn)(Value* args, int argc);

typedef struct {
    const char* name;
    int         arity;
    NativeFn    fn;
} NativeDef;


extern NativeDef native_table[];
extern int       native_table_count;


#define GLOBAL_MAX 512
static struct { char* name; Value val; } globals[GLOBAL_MAX];
static int global_count = 0;

static Value* find_global(const char* name) {
    for (int i = 0; i < global_count; i++)
        if (strcmp(globals[i].name, name) == 0) return &globals[i].val;
    return NULL;
}

static void define_global(const char* name, Value v) {
    Value* existing = find_global(name);
    if (existing) { *existing = v; return; }
    globals[global_count].name = strdup(name);
    globals[global_count].val  = v;
    global_count++;
}


static void free_value(Value v) {
    switch (v.type) {
        case VAL_STRING:
            free(v.str);
            break;
        case VAL_ARRAY:
            for (int i = 0; i < v.array.length; i++) free_value(v.array.data[i]);
            free(v.array.data);
            break;
        case VAL_BYTES:
            free(v.bytes.data);
            break;
        default: break;
    }
}

static Value copy_value(Value v) {
    Value out = v;

    switch (v.type) {
        case VAL_STRING:
            out.str = strdup(v.str);
            break;

        case VAL_ARRAY:
            out.array.data = malloc(sizeof(Value) * v.array.length);
            out.array.length = v.array.length;
            for (int i = 0; i < v.array.length; i++) {
                out.array.data[i] = copy_value(v.array.data[i]);
            }
            break;

        case VAL_BYTES:
            out.bytes.data = malloc(v.bytes.length);
            memcpy(out.bytes.data, v.bytes.data, v.bytes.length);
            out.bytes.length = v.bytes.length;
            break;

        default:
            break;
    }

    return out;
}

static void print_value(Value v) {
    switch (v.type) {
        case VAL_NULL:     printf("null"); break;
        case VAL_BOOL:     printf("%s", v.boolean ? "true" : "false"); break;
        case VAL_NUMBER:   printf("%g", v.num); break;
        case VAL_STRING:   printf("%s", v.str); break;
        case VAL_FUNCTION: printf("<func %s>", functions[v.func_id].name); break;
        case VAL_NATIVE:   printf("<native %s>", native_table[v.native_id].name); break;
        case VAL_BYTES:
            printf("b\"");
            for (int i = 0; i < v.bytes.length; i++) printf("\\x%02X", v.bytes.data[i]);
            printf("\"");
            break;
        case VAL_ARRAY:
            printf("[");
            for (int i = 0; i < v.array.length; i++) {
                print_value(v.array.data[i]);
                if (i < v.array.length - 1) printf(", ");
            }
            printf("]");
            break;
    }
}

static double pop_num(void) {
    Value v = pop();
    if (v.type != VAL_NUMBER) { fprintf(stderr, "Expected number!\n"); exit(1); }
    return v.num;
}

static int is_truthy(Value v) {
    switch (v.type) {
        case VAL_NULL:     return 0;
        case VAL_BOOL:     return v.boolean;
        case VAL_NUMBER:   return v.num != 0.0;
        case VAL_STRING:   return strlen(v.str) > 0;
        case VAL_ARRAY:    return v.array.length > 0;
        case VAL_BYTES:    return v.bytes.length > 0;
        case VAL_FUNCTION:
        case VAL_NATIVE:   return 1;
    }
    return 1;
}

static inline uint8_t  read_byte (const uint8_t** ip) { return *((*ip)++); }
static inline uint16_t read_short(const uint8_t** ip) {
    uint16_t v = ((uint16_t)(*ip)[0] << 8) | (*ip)[1];
    *ip += 2;
    return v;
}


static void register_natives(void) {
    for (int i = 0; i < native_table_count; i++) {
        Value v = { .type = VAL_NATIVE, .native_id = i };
        define_global(native_table[i].name, v);
    }
}


int main(void) {
#ifdef _WIN32
    system("chcp 65001 > nul");
    setvbuf(stdout, NULL, _IONBF, 0);
#endif

    register_natives();

    const Function* root_func = &functions[root_function_id];
    CallFrame root_frame = {
        .return_ip    = NULL,
        .base         = 0,
        .locals_count = root_func->arity,
        .code         = root_func->code,
        .code_len     = root_func->code_len,
        .constants    = root_func->constants,
    };
    frames[frame_depth++] = root_frame;

    for (int i = 0; i < 256; i++) { Value v = { .type = VAL_NULL }; push(v); }
    stack_top = root_frame.locals_count;

    const uint8_t* ip = root_frame.code;

    while (1) {
        CallFrame* frame = current_frame();
        if (ip >= frame->code + frame->code_len) break;

        uint8_t op = read_byte(&ip);
        switch (op) {


        case 0x00: { /* PUSH_CONST */
            uint8_t  idx = read_byte(&ip);
            Constant c   = frame->constants[idx];
            if      (c.type == CONST_NUMBER)   { Value v = { .type=VAL_NUMBER,   .num=c.num };          push(v); }
            else if (c.type == CONST_STRING)   { Value v = { .type=VAL_STRING,   .str=strdup(c.str) };  push(v); }
            else if (c.type == CONST_FUNCTION) { Value v = { .type=VAL_FUNCTION, .func_id=c.func_id };  push(v); }
            else                               { Value v = { .type=VAL_NULL };                           push(v); }
            break;
        }
        case 0x01: { Value v = { .type=VAL_NULL };             push(v); break; }
        case 0x02: { Value v = { .type=VAL_BOOL, .boolean=1 }; push(v); break; }
        case 0x03: { Value v = { .type=VAL_BOOL, .boolean=0 }; push(v); break; }
        case 0x04: { Value v = pop(); free_value(v); break; }


        case 0x05: { /* ADD */
            Value b = pop(), a = pop();
            if (a.type == VAL_NUMBER && b.type == VAL_NUMBER) {
                Value v = { .type=VAL_NUMBER, .num=a.num+b.num }; push(v);
            } else if (a.type == VAL_STRING && b.type == VAL_STRING) {
                char* s = malloc(strlen(a.str)+strlen(b.str)+1);
                strcpy(s, a.str); strcat(s, b.str);
                Value v = { .type=VAL_STRING, .str=s }; push(v);
            } else { fprintf(stderr,"ADD: type error\n"); return 1; }
            free_value(a); free_value(b);
            break;
        }
        case 0x06: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_NUMBER,.num=a-b}; push(v); break; }
        case 0x07: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_NUMBER,.num=a*b}; push(v); break; }
        case 0x08: {
            double b=pop_num();
            if (b==0.0) { fprintf(stderr,"Division by zero\n"); return 1; }
            double a=pop_num(); Value v={.type=VAL_NUMBER,.num=a/b}; push(v); break;
        }
        case 0x09: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_NUMBER,.num=fmod(a,b)}; push(v); break; }
        case 0x0A: { double a=pop_num();             Value v={.type=VAL_NUMBER,.num=-a};        push(v); break; }
        case 0x0B: { Value a=pop(); Value v={.type=VAL_BOOL,.boolean=!is_truthy(a)}; free_value(a); push(v); break; }


        case 0x0C: {
            Value b=pop(), a=pop();
            int eq = (a.type==b.type) ?
                (a.type==VAL_NULL   ? 1 :
                (a.type==VAL_BOOL   ? a.boolean==b.boolean :
                (a.type==VAL_NUMBER ? a.num==b.num :
                (a.type==VAL_STRING ? strcmp(a.str,b.str)==0 : 0)))) : 0;
            free_value(a); free_value(b);
            Value v={.type=VAL_BOOL,.boolean=eq}; push(v); break;
        }
        case 0x0D: {
            Value b=pop(), a=pop();
            int neq = (a.type==b.type) ?
                (a.type==VAL_NULL   ? 0 :
                (a.type==VAL_BOOL   ? a.boolean!=b.boolean :
                (a.type==VAL_NUMBER ? a.num!=b.num :
                (a.type==VAL_STRING ? strcmp(a.str,b.str)!=0 : 1)))) : 1;
            free_value(a); free_value(b);
            Value v={.type=VAL_BOOL,.boolean=neq}; push(v); break;
        }
        case 0x0E: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_BOOL,.boolean=a< b}; push(v); break; }
        case 0x0F: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_BOOL,.boolean=a<=b}; push(v); break; }
        case 0x10: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_BOOL,.boolean=a> b}; push(v); break; }
        case 0x11: { double b=pop_num(),a=pop_num(); Value v={.type=VAL_BOOL,.boolean=a>=b}; push(v); break; }


        case 0x12: {
            uint8_t size = read_byte(&ip);
            Value* arr = malloc(size * sizeof(Value));
            for (int i = size-1; i >= 0; i--) arr[i] = pop();
            Value v = { .type=VAL_ARRAY }; v.array.data=arr; v.array.length=size;
            push(v); break;
        }
        case 0x13: {
            Value idx = pop(), arr = pop();
            if (arr.type == VAL_ARRAY) {
                if (idx.type != VAL_NUMBER) { fprintf(stderr, "Bad index\n"); return 1; }
                int i = (int)idx.num;
                if (i < 0 || i >= arr.array.length) { fprintf(stderr, "Index out of bounds\n"); return 1; }
                Value v = copy_value(arr.array.data[i]);
                push(v);
            } else if (arr.type == VAL_BYTES) {
                if (idx.type != VAL_NUMBER) { fprintf(stderr, "Bad index\n"); return 1; }
                int i = (int)idx.num;
                if (i < 0 || i >= arr.bytes.length) { fprintf(stderr, "Index out of bounds\n"); return 1; }
                Value v = { .type = VAL_NUMBER, .num = (double)arr.bytes.data[i] };
                push(v);
            } else { fprintf(stderr, "INDEX_GET: not an array or bytes\n"); return 1; }
            free_value(arr);
            free_value(idx);
            break;
        }
        case 0x14: {
            Value val=pop(), idx=pop(), arr=pop();
            if (idx.type!=VAL_NUMBER||arr.type!=VAL_ARRAY) { fprintf(stderr,"Bad index\n"); return 1; }
            int i=(int)idx.num;
            if (i<0||i>=arr.array.length) { fprintf(stderr,"Index out of bounds\n"); return 1; }
            free_value(arr.array.data[i]);
            arr.array.data[i]=val; push(val); free_value(idx); break;
        }


        case 0x15: {
            uint8_t idx=read_byte(&ip);
            const char* nm=frame->constants[idx].str;
            Value val=pop(); define_global(nm,val); break;
        }
        case 0x16: { /* LOAD_GLOBAL */
            uint8_t idx = read_byte(&ip);
            const char* nm = frame->constants[idx].str;
            Value* g = find_global(nm);
            if (!g) { fprintf(stderr, "Undefined global: %s\n", nm); return 1; }
            Value v = copy_value(*g);
            push(v);
            break;
        }

        case 0x17: {
            uint8_t idx = read_byte(&ip);
            const char* nm = frame->constants[idx].str;
            Value val = copy_value(peek(0));
            Value* g = find_global(nm);
            if (!g) define_global(nm, val);
            else { free_value(*g); *g = val; }
            break;
        }


        case 0x18: { /* LOAD_LOCAL */
            uint8_t slot = read_byte(&ip);
            Value v = copy_value(stack[frame->base + slot]);
            push(v);
            break;
        }
        case 0x19: {
            uint8_t slot = read_byte(&ip);
            free_value(stack[frame->base + slot]);
            stack[frame->base + slot] = copy_value(peek(0));
            break;
        }


        case 0x1A: { uint16_t off=read_short(&ip); ip+=off; break; }
        case 0x1B: {
            uint16_t off=read_short(&ip);
            Value cond=pop();
            if (!is_truthy(cond)) ip+=off;
            free_value(cond); break;
        }
        case 0x1C: { uint16_t off=read_short(&ip); ip-=off; break; }


        case 0x1D: {
            uint8_t idx=read_byte(&ip);
            Constant c=frame->constants[idx];
            Value v={.type=VAL_FUNCTION,.func_id=c.func_id}; push(v); break;
        }


                case 0x20: {
                    uint8_t argc   = read_byte(&ip);
                    Value   callee = stack[stack_top - argc - 1];

                    if (callee.type == VAL_FUNCTION) {
                        int fid = callee.func_id;
                        if (fid<0||fid>=(int)functions_count) { fprintf(stderr,"Invalid func id\n"); return 1; }
                        const Function* func = &functions[fid];
                        if (argc != func->arity) {
                            fprintf(stderr,"Arity mismatch: expected %d got %d\n", func->arity, argc);
                            return 1;
                        }
                        CallFrame nf = {
                            .return_ip    = ip,
                            .base         = stack_top - argc,
                            .locals_count = func->arity,
                            .code         = func->code,
                            .code_len     = func->code_len,
                            .constants    = func->constants,
                        };
                        frames[frame_depth++] = nf;

                        for (int i = argc; i < func->arity; i++) {
                            Value null_val = { .type = VAL_NULL };
                            push(null_val);
                        }
                        ip    = func->code;
                        frame = &frames[frame_depth-1];

                    } else if (callee.type == VAL_NATIVE) {

                        int       nid = callee.native_id;
                        NativeDef* nd = &native_table[nid];
                        if (nd->arity != -1 && argc != nd->arity) {
                            fprintf(stderr,"Native '%s': arity mismatch (expected %d, got %d)\n",
                                    nd->name, nd->arity, argc);
                            return 1;
                        }
                        Value* args   = &stack[stack_top - argc];
                        Value  result = nd->fn(args, argc);

                        for (int i = 0; i < argc; i++) free_value(args[i]);
                            stack_top -= argc + 1;
                            push(result);

                    } else {
                        fprintf(stderr,"Callee is not callable\n"); return 1;
                    }
                    break;
                }


        case 0x21: {
            Value v = pop();
            print_value(v);
            printf("\n");
            if (v.type != VAL_ARRAY) free_value(v);
            break;
        }
        case 0x22: { /* RETURN */
            Value     result    = pop();
            CallFrame* ret      = current_frame();
            frame_depth--;
            if (frame_depth == 0) {
                print_value(result); printf("\n");
                free_value(result); return 0;
            }
            ip        = ret->return_ip;
            frame     = &frames[frame_depth-1];
            stack_top = ret->base - 1;
            push(result);
            break;
        }
        case 0x23: return 0; /* HALT */

        default:
            fprintf(stderr,"Unsupported opcode: 0x%02X at offset %td\n",
                    op, ip - frame->code - 1);
            return 1;
        }
    }
    return 0;
}