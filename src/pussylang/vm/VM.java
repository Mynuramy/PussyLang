package pussylang.vm;

import pussylang.compiler.Chunk;
import pussylang.compiler.OpCode;

import java.util.*;


public class VM {

    private static final int STACK_MAX  = 1024;
    private static final int FRAMES_MAX = 512;



     final Object[] stack = new Object[STACK_MAX];
      int top   = 0;
     final List<Upvalue> openUpvalues = new ArrayList<>();



     final CallFrame[] frames = new CallFrame[FRAMES_MAX];
      int depth  = 0;



    private final Map<String, Object> globals = new LinkedHashMap<>();

    public VM() {
        NativeRegistry.registerAll(globals::put);
    }



    public void run(Chunk script) {
        pushFrame(new CallFrame(script, 0));
        execute();
    }



    private void execute() {
        CallFrame f = frames[depth - 1];

        byte[] code = f.code();

        System.out.println("BYTECODE");
        for (int i = 0; i < code.length; i++) {
           // System.out.printf("%04d: %d\n", i, code[i]);
        }
        System.out.println("- - - - -");

        loop: for (;;) {
            try {
                int ipBefore = f.ip;
                int raw = f.readByte() & 0xFF;
                OpCode op = OpCode.values()[raw];


                switch (op) {

                    case PUSH_CONST  -> {
                        Object v = f.readConst();
                       // System.out.println("  -> PUSH_CONST " + v);
                        push(v);
                    }
                    case PUSH_NULL   -> push(null);
                    case PUSH_TRUE   -> push(Boolean.TRUE);
                    case PUSH_FALSE  -> push(Boolean.FALSE);

                    case POP -> {
                        //System.out.println("  -> POP (top before=" + top + ")");
                        pop();
                    }

                    case ADD -> {
                        Object b = pop(), a = pop();
                        Object res;
                        if (a instanceof Double da && b instanceof Double db)
                            res = da + db;
                        else
                            res = NativeRegistry.stringify(a) + NativeRegistry.stringify(b);

                     //   System.out.println("  -> ADD result=" + res);
                        push(res);
                    }

                    case SUB -> {
                        double b = popNum();
                        double a = popNum();
                        double r = a - b;
                      //  System.out.println("  -> SUB result=" + r);
                        push(r);
                    }
                    case NEW_ARRAY -> {
                        int size = f.readByte() & 0xFF;
                        List<Object> array = new ArrayList<>(size);

                        for (int i = size - 1; i >= 0; i--) {
                            array.add(0, stack[top - size + i]);
                        }
                        top -= size;
                        push(array);
                    }

                    case INDEX_GET -> {
                        Object index = pop();
                        Object array = pop();
                        if (array instanceof List<?> list && index instanceof Double d) {
                            int idx = d.intValue();
                            if (idx < 0 || idx >= list.size()) throw new VMError("Index out of bounds: " + idx);
                            push(list.get(idx));
                        } else {
                            throw new VMError("Only arrays can be indexed.");
                        }
                    }

                    case INDEX_SET -> {
                        Object value = pop();
                        Object index = pop();
                        Object array = pop();
                        if (array instanceof List<?> list && index instanceof Double d) {
                            int idx = d.intValue();

                            if (idx < 0) throw new VMError("Negative index: " + idx);
                            if (idx > list.size()) throw new VMError("Index out of bounds: " + idx);
                            if (idx == list.size()) {
                                ((List<Object>) list).add(value);
                            } else {
                                ((List<Object>) list).set(idx, value);
                            }
                            push(value);
                        } else {
                            throw new VMError("Invalid array assignment.");
                        }
                    }

                    case MUL -> {
                        double b = popNum();
                        double a = popNum();
                        double r = a * b;
                     //   System.out.println("  -> MUL result=" + r);
                        push(r);
                    }

                    case DIV -> {
                        double b = popNum();
                        if (b == 0) throw new VMError("Division by zero.");
                        double a = popNum();
                        double r = a / b;
                     //   System.out.println("  -> DIV result=" + r);
                        push(r);
                    }

                    case MOD -> {
                        double b = popNum();
                        double a = popNum();
                        double r = a % b;
                     //   System.out.println("  -> MOD result=" + r);
                        push(r);
                    }

                    case NEGATE -> {
                        double r = -popNum();
                     //   System.out.println("  -> NEGATE result=" + r);
                        push(r);
                    }

                    case NOT -> {
                        Object v = pop();
                        boolean r = !isTruthy(v);
                      //  System.out.println("  -> NOT result=" + r);
                        push(r);
                    }

                    case EQUAL -> {
                        Object b = pop();
                        Object a = pop();
                        boolean r = isEqual(a, b);
                      //  System.out.println("  -> EQUAL result=" + r);
                        push(r);
                    }

                    case NOT_EQUAL -> {
                        Object b = pop();
                        Object a = pop();
                        boolean r = !isEqual(a, b);
                      //  System.out.println("  -> NOT_EQUAL result=" + r);
                        push(r);
                    }

                    case LESS -> {
                        double b = popNum();
                        double a = popNum();
                        boolean r = a < b;
                     //   System.out.println("  -> LESS result=" + r);
                        push(r);
                    }

                    case LESS_EQ -> {
                        double b = popNum();
                        double a = popNum();
                        boolean r = a <= b;
                     //   System.out.println("  -> LESS_EQ result=" + r);
                        push(r);
                    }

                    case GREATER -> {
                        double b = popNum();
                        double a = popNum();
                        boolean r = a > b;
                      //  System.out.println("  -> GREATER result=" + r);
                        push(r);
                    }

                    case GREATER_EQ -> {
                        double b = popNum();
                        double a = popNum();
                        boolean r = a >= b;
                      //  System.out.println("  -> GREATER_EQ result=" + r);
                        push(r);
                    }

                    case DEFINE_GLOBAL -> {
                        String name = (String) f.readConst();
                        Object val = pop();
                     //   System.out.println("  -> DEFINE_GLOBAL " + name + " = " + val);
                        globals.put(name, val);
                    }

                    case GET_GLOBAL -> {
                        String name = (String) f.readConst();
                        Object val = globals.get(name);
                    //    System.out.println("  -> GET_GLOBAL " + name + " = " + val);
                        push(val);
                    }

                    case SET_GLOBAL -> {
                        String name = (String) f.readConst();
                        Object val = peek(0);
                     //   System.out.println("  -> SET_GLOBAL " + name + " = " + val);
                        globals.put(name, val);
                    }

                    case GET_LOCAL -> {
                        int slot = f.readByte();
                        Object val = stack[f.base + slot];
                      //  System.out.println("  -> GET_LOCAL " + slot + " = " + val);
                        push(val);
                    }

                    case SET_LOCAL -> {
                        int slot = f.readByte();
                        Object val = peek(0);
                      //  System.out.println("  -> SET_LOCAL " + slot + " = " + val);
                        stack[f.base + slot] = val;
                    }

                    case JUMP -> {
                        int off = f.readShort();
                        f.ip += off;
                    }

                    case JUMP_IF_FALSE -> {
                        int off = f.readShort();
                        Object condition = pop();
                        if (!isTruthy(condition)) {
                            f.ip += off;
                        }
                    }

                    case LOOP -> {
                        int off = f.readShort();
                      //  System.out.printf("  -> LOOP back=%d\n", off);
                        f.ip -= off;
                    }

                    case CLOSURE -> {
                        Chunk c = (Chunk) f.readConst();
                        int upvalueCount = c.upvalueCount;
                        Upvalue[] upvalues = new Upvalue[upvalueCount];
                        for (int i = 0; i < upvalueCount; i++) {
                            boolean isLocal = f.readByte() != 0;
                            int index = f.readByte() & 0xFF;
                            if (isLocal) {

                                upvalues[i] = captureUpvalue(f.base + index);
                            } else {

                                upvalues[i] = f.function.upvalues()[index];
                            }
                        }
                        push(new PussyFunction(c, upvalues));
                    }

                    case CALL -> {
                        int argc = f.readByte();
                        Object callee = stack[top - argc - 1];

                        if (callee instanceof PussyFunction fn) {
                            f = pushFrame(new CallFrame(fn, top - argc));
                        } else if (callee instanceof NativeFunction fn) {
                            List<Object> args = new ArrayList<>(argc);
                            for (int i = top - argc; i < top; i++) args.add(stack[i]);
                            top -= argc + 1;
                            push(fn.call(args));
                        } else {
                            throw new VMError("Not callable");
                        }
                    }

                    case GET_UPVALUE -> {
                        int slot = f.readByte() & 0xFF;
                        push(f.function.upvalues()[slot].get());
                    }

                    case SET_UPVALUE -> {
                        int slot = f.readByte() & 0xFF;
                        f.function.upvalues()[slot].set(peek(0));
                    }

                    case RETURN -> {
                        Object result = pop();
                        int base = f.base;
                        closeUpvalues(base);
                        depth--;
                        if (depth == 0) break loop;
                        top = base - 1;
                        push(result);
                        f = frames[depth - 1];
                    }


                    case PRINT -> {
                        Object v = pop();
                      //  System.out.println("  -> PRINT " + v);
                        System.out.println(NativeRegistry.stringify(v));
                    }

                    case HALT -> {
                      //  System.out.println("  -> HALT");
                        break loop;
                    }
                }

            } catch (Exception e) {
                System.out.println("\nCRASH DETECTED");
                System.out.println("ip=" + f.ip);
                System.out.println("top=" + top);
                System.out.println("stack snapshot:");

                for (int i = 0; i < top; i++) {
                    System.out.println("  [" + i + "] = " + stack[i]);
                }

                System.out.println("code length=" + code.length);

                throw e;
            }
        }
    }


    //stack helpers

    private Upvalue captureUpvalue(int localSlot) {

        for (Upvalue uv : openUpvalues) {
            if (!uv.isClosed && uv.index == localSlot) {
                return uv;
            }
        }
        Upvalue uv = new Upvalue(this, localSlot);
        openUpvalues.add(uv);
        return uv;
    }

    private void closeUpvalues(int lastBase) {

        Iterator<Upvalue> it = openUpvalues.iterator();
        while (it.hasNext()) {
            Upvalue uv = it.next();
            if (uv.index >= lastBase && uv.index < top) {
                uv.close();
                it.remove();
            }
        }
    }


    private void      push(Object v)       { stack[top++] = v; }
    private Object pop() {
        if (top <= 0) {
            throw new VMError("Stack underflow so extra POP in if/while or unbalanced stack");
        }
        return stack[--top];
    }
    private Object    peek(int dist)       { return stack[top - 1 - dist]; }
    private CallFrame pushFrame(CallFrame f) {
        frames[depth++] = f;
        return f;
    }

    private double popNum() {
        Object v = pop();
        if (v instanceof Double d) return d;
        throw new VMError("Expected a number, got: " + NativeRegistry.stringify(v));
    }

    private boolean isTruthy(Object v) {
        if (v == null)              return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Double  d) return d != 0;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}