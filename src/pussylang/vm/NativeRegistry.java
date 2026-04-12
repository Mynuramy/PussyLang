package pussylang.vm;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * registers every native builtin into the VMss global table.
 * each builtin is its own selfcontained static class.
 */
public class NativeRegistry {

    public static void registerAll(BiConsumer<String, Object> define) {
        register(define,
                new Alloc(), new Free(), new Write(), new Read(),
                new Exec(),  new Inject(), new Cast(),
                new Clock(), new Str(), new Len(), new Hex()
        );
    }

    private static void register(BiConsumer<String, Object> def, NativeFunction... fns) {
        for (NativeFunction fn : fns) def.accept(fn.name(), fn);
    }



    private static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    //memory builtins


    static class Alloc implements NativeFunction {
        @Override public String name()  { return "alloc"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            long size = num(a.get(0));
            long ptr  = UNSAFE.allocateMemory(size);
            UNSAFE.setMemory(ptr, size, (byte) 0);
            System.out.printf("[alloc] %d bytes @ 0x%X%n", size, ptr);
            return (double) ptr;
        }
    }

    static class Free implements NativeFunction {
        @Override public String name()  { return "free"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            long ptr = num(a.get(0));
            UNSAFE.freeMemory(ptr);
            System.out.printf("[free] 0x%X%n", ptr);
            return null;
        }
    }

    static class Write implements NativeFunction {
        @Override public String name()  { return "write"; }
        @Override public int    arity() { return 3; }
        @Override public Object call(List<Object> a) {
            long   ptr   = num(a.get(0));
            byte[] data  = bytes(a.get(1));
            int    count = (int) Math.min(num(a.get(2)), data.length);
            for (int i = 0; i < count; i++) UNSAFE.putByte(ptr + i, data[i]);
            System.out.printf("[write] %d bytes -> 0x%X%n", count, ptr);
            return null;
        }
    }

    static class Read implements NativeFunction {
        @Override public String name()  { return "read"; }
        @Override public int    arity() { return 2; }
        @Override public Object call(List<Object> a) {
            long   ptr  = num(a.get(0));
            int    size = (int) num(a.get(1));
            byte[] buf  = new byte[size];
            for (int i = 0; i < size; i++) buf[i] = UNSAFE.getByte(ptr + i);
            System.out.printf("[read] %d bytes <- 0x%X%n", size, ptr);
            return buf;
        }
    }

    static class Exec implements NativeFunction {
        @Override public String name()  { return "exec"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            System.out.printf("[exec] shellcode @ 0x%X%n", num(a.get(0)));
            // WIP!!!!!!!!!!!!!
            return null;
        }
    }

    static class Inject implements NativeFunction {
        @Override public String name()  { return "inject"; }
        @Override public int    arity() { return 2; }
        @Override public Object call(List<Object> a) {
            System.out.printf("[inject] %d bytes → PID %d%n", bytes(a.get(1)).length, num(a.get(0)));
            // WIPP!!!!!!!!!!!!!!!!!!!!!
            return null;
        }
    }

    static class Cast implements NativeFunction {
        @Override public String name()  { return "cast"; }
        @Override public int    arity() { return 2; }
        @Override public Object call(List<Object> a) {
            return switch ((String) a.get(1)) {
                case "int"    -> (double) num(a.get(0));
                case "string" -> stringify(a.get(0));
                case "bytes"  -> bytes(a.get(0));
                default -> throw new VMError("Unknown cast type: '" + a.get(1) + "'.");
            };
        }
    }


    //util builtins


    static class Clock implements NativeFunction {
        @Override public String name()  { return "clock"; }
        @Override public int    arity() { return 0; }
        @Override public Object call(List<Object> a) {
            return (double) System.currentTimeMillis() / 1000.0;
        }
    }

    static class Str implements NativeFunction {
        @Override public String name()  { return "str"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) { return stringify(a.get(0)); }
    }

    static class Len implements NativeFunction {
        @Override public String name()  { return "len"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            Object v = a.get(0);
            if (v instanceof String s) return (double) s.length();
            if (v instanceof byte[] b) return (double) b.length;
            if (v instanceof List<?> l) return (double) l.size();
            throw new VMError("len() expects string, bytes, or array.");
        }
    }

    static class Hex implements NativeFunction {
        @Override public String name()  { return "hex"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            return "0x" + Long.toHexString(num(a.get(0))).toUpperCase();
        }
    }


    //shared type coercions packageprivate so VM.java can use them


    static long num(Object v) {
        if (v instanceof Double d) return d.longValue();
        throw new VMError("Expected number, got: " + v);
    }

    static byte[] bytes(Object v) {
        if (v instanceof byte[] b) return b;
        if (v instanceof String s) return s.getBytes();
        throw new VMError("Expected bytes or string, got: " + v);
    }

    public static String stringify(Object v) {
        if (v == null)             return "null";
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof Double d) {
            String s = d.toString();
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
        if (v instanceof byte[] b) {
            var sb = new StringBuilder("b\"");
            for (byte x : b) sb.append(String.format("\\x%02X", x));
            return sb.append('"').toString();
        }
        return v.toString();
    }
}