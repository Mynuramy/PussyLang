package pussylang.interpreter;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

/**
 * registers all native  functions into the global environment.
 * and each builtin is its own static class implementing PussyCallable.
 */
public class NativeBuiltins {

    public static void registerAll(Environment globals) {
        globals.define("alloc",  new Alloc());
        globals.define("free",   new Free());
        globals.define("write",  new Write());
        globals.define("read",   new Read());
        globals.define("exec",   new Exec());
        globals.define("inject", new Inject());
        globals.define("cast",   new Cast());
        globals.define("clock",  new Clock());
        globals.define("str",    new Str());
        globals.define("len",    new Len());
        globals.define("hex",    new Hex());
    }

    // unsafe handle (shared by all memory ops)

    static Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Unsafe not available: " + e);
        }
    }


    //memory builtins


    /** alloc(size) -> ptr  , allocates raw native memory */
    static class Alloc implements PussyCallable {
        @Override public int arity()  { return 1; }
        @Override public String name() { return "alloc"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            long size = toLong(args.get(0));
            long ptr  = UNSAFE.allocateMemory(size);
            UNSAFE.setMemory(ptr, size, (byte) 0); // zero it out
            System.out.printf("[alloc] %d bytes @ 0x%X%n", size, ptr);
            return (double) ptr;
        }
    }

    /** free(ptr) , frees native memory */
    static class Free implements PussyCallable {
        @Override public int arity()  { return 1; }
        @Override public String name() { return "free"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            long ptr = toLong(args.get(0));
            UNSAFE.freeMemory(ptr);
            System.out.printf("[free] 0x%X%n", ptr);
            return null;
        }
    }

    /** write(ptr, data, size) , writes byte[] into native memory */
    static class Write implements PussyCallable {
        @Override public int arity()  { return 3; }
        @Override public String name() { return "write"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            long ptr  = toLong(args.get(0));
            byte[] data = toBytes(args.get(1));
            int size  = (int) toLong(args.get(2));
            int count = Math.min(size, data.length);
            for (int j = 0; j < count; j++) {
                UNSAFE.putByte(ptr + j, data[j]);
            }
            System.out.printf("[write] %d bytes -> 0x%X%n", count, ptr);
            return null;
        }
    }

    /** read(ptr, size) -> byte[] */
    static class Read implements PussyCallable {
        @Override public int arity()  { return 2; }
        @Override public String name() { return "read"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            long ptr  = toLong(args.get(0));
            int  size = (int) toLong(args.get(1));
            byte[] buf = new byte[size];
            for (int j = 0; j < size; j++) {
                buf[j] = UNSAFE.getByte(ptr + j);
            }
            System.out.printf("[read] %d bytes <- 0x%X%n", size, ptr);
            return buf;
        }
    }

    /**
     * exec(ptr)  marks memory executable and runs it via JNI trampoline.
     * WIP:  a real execution requires a native agent or JNI bridge;
     *       this stub logs and simulates until I wire up the native layer.
     */
    static class Exec implements PussyCallable {
        @Override public int arity()  { return 1; }
        @Override public String name() { return "exec"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            long ptr = toLong(args.get(0));
            System.out.printf("[exec] executing shellcode @ 0x%X%n", ptr);
            // WIP!!!!
            return null;
        }
    }

    /** inject(pid, shellcode) , process injection stub */
    static class Inject implements PussyCallable {
        @Override public int arity()  { return 2; }
        @Override public String name() { return "inject"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            long   pid       = toLong(args.get(0));
            byte[] shellcode = toBytes(args.get(1));
            System.out.printf("[inject] %d bytes → PID %d%n", shellcode.length, pid);
            // WIP!!!
            return null;
        }
    }

    /** cast(value, "type") -> reinterpreted value */
    static class Cast implements PussyCallable {
        @Override public int arity()  { return 2; }
        @Override public String name() { return "cast"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            Object value = args.get(0);
            String type  = (String) args.get(1);
            return switch (type) {
                case "int"    -> (double) toLong(value);
                case "double" -> ((Number) value).doubleValue();
                case "string" -> stringify(value);
                case "bytes"  -> toBytes(value);
                default       -> throw new RuntimeError("Unknown cast type: '" + type + "'.");
            };
        }
    }


    //utility builtins


    /** clock() -> !!!seconds!!! <- since epoch  */
    static class Clock implements PussyCallable {
        @Override public int arity()   { return 0; }
        @Override public String name() { return "clock"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            return (double) System.currentTimeMillis() / 1000.0;
        }
    }

    /** str(value) -> string */
    static class Str implements PussyCallable {
        @Override public int arity()   { return 1; }
        @Override public String name() { return "str"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            return stringify(args.get(0));
        }
    }

    /** len(string|bytes) -> number */
    static class Len implements PussyCallable {
        @Override public int arity()   { return 1; }
        @Override public String name() { return "len"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            Object val = args.get(0);
            if (val instanceof String s)  return (double) s.length();
            if (val instanceof byte[] b)  return (double) b.length;
            throw new RuntimeError("len() expects string or bytes.");
        }
    }

    /** hex(number) -> "0xFF" string */
    static class Hex implements PussyCallable {
        @Override public int arity()   { return 1; }
        @Override public String name() { return "hex"; }
        @Override
        public Object call(Interpreter i, List<Object> args) {
            return "0x" + Long.toHexString(toLong(args.get(0))).toUpperCase();
        }
    }


    //shared type helpers


    static long toLong(Object v) {
        if (v instanceof Double d) return d.longValue();
        throw new RuntimeError("Expected a number, got: " + v);
    }

    static byte[] toBytes(Object v) {
        if (v instanceof byte[] b) return b;
        if (v instanceof String s) return s.getBytes();
        throw new RuntimeError("Expected bytes or string, got: " + v);
    }

    static String stringify(Object v) {
        if (v == null)           return "null";
        if (v instanceof Double d) {
            String s = d.toString();
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
        if (v instanceof byte[] b) {
            StringBuilder sb = new StringBuilder("b\"");
            for (byte x : b) sb.append(String.format("\\x%02X", x));
            return sb.append('"').toString();
        }
        return v.toString();
    }
}