package pussylang.vm;


import java.util.AbstractList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.Base64;

import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * registers every native builtin into the VMss global table.
 * each builtin is its own selfcontained static class.
 */
public class NativeRegistry {

    private static final AtomicInteger nextSocketId = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Socket> sockets = new ConcurrentHashMap<>();

    static {
        try {
            System.loadLibrary("pussylang");
            System.out.println("[NativeRegistry] Loaded pussylang native library.");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Warning: native library 'pussylang' not found. exec/protect will not work.");
            System.err.println("Make sure pussylang.dll (Windows) is in java.library.path.");
        }
    }

    public static void registerAll(BiConsumer<String, Object> define) {
        register(define,
                new Alloc(), new Free(), new Write(), new Read(),
                new Exec(), new Inject(), new Cast(),
                new Clock(), new Str(), new Len(), new Hex(),
                new PtrAdd(), new Buffer(), new MutableBytes(), new Pack(),
                new Sleep(), new Base64Encode(), new Base64Decode(), new XorBytes(),
                new TcpConnect(), new TcpSend(), new TcpRecv(), new TcpClose() , new Chr(),
                new BytesToAscii(), new Protect(), new Call() , new GetProc(), new ListDir(), new IsDir(),
                new FileRead(), new FileWrite(), new FileExists(),
                new Input(), new Format()
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

    static class ListDir implements NativeFunction {
        @Override public String name()  { return "list_dir"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            String path = (String) a.get(0);
            java.io.File dir = new java.io.File(path);
            if (!dir.exists() || !dir.isDirectory()) return null;
            String[] names = dir.list();
            if (names == null) return null;
            List<Object> result = new java.util.ArrayList<>();
            for (String name : names) result.add(name);
            return result;
        }
    }

    static class IsDir implements NativeFunction {
        @Override public String name()  { return "is_dir"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            String path = (String) a.get(0);
            return new java.io.File(path).isDirectory();
        }
    }

    static class FileRead implements NativeFunction {
        @Override public String name()  { return "file_read"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            try {
                return java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get((String) a.get(0)));
            } catch (java.io.IOException e) { return null; }
        }
    }

    static class FileWrite implements NativeFunction {
        @Override public String name()  { return "file_write"; }
        @Override public int    arity() { return 2; }
        @Override public Object call(List<Object> a) {
            try {
                byte[] data = bytes(a.get(1));
                java.nio.file.Files.write(
                        java.nio.file.Paths.get((String) a.get(0)), data);
                return (double) data.length;
            } catch (java.io.IOException e) { return 0.0; }
        }
    }

    static class FileExists implements NativeFunction {
        @Override public String name()  { return "file_exists"; }
        @Override public int    arity() { return 1; }
        @Override public Object call(List<Object> a) {
            return java.nio.file.Files.exists(
                    java.nio.file.Paths.get((String) a.get(0)));
        }
    }

    static class Input implements NativeFunction {
        @Override public String name()  { return "input"; }
        @Override public int    arity() { return -1; }
        @Override public Object call(List<Object> a) {
            if (!a.isEmpty()) System.out.print(stringify(a.get(0)));
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in));
                String line = br.readLine();
                return line != null ? line : "";
            } catch (java.io.IOException e) { return ""; }
        }
    }

    static class Format implements NativeFunction {
        @Override public String name()  { return "format"; }
        @Override public int    arity() { return -1; }
        @Override public Object call(List<Object> a) {
            if (a.isEmpty()) return "";
            String tpl = (String) a.get(0);
            StringBuilder out = new StringBuilder();
            int arg = 1;
            for (int i = 0; i < tpl.length(); i++) {
                if (tpl.charAt(i) == '{' && i+1 < tpl.length()
                        && tpl.charAt(i+1) == '}' && arg < a.size()) {
                    out.append(stringify(a.get(arg++)));
                    i++;
                } else {
                    out.append(tpl.charAt(i));
                }
            }
            return out.toString();
        }
    }

    static class PtrAdd implements NativeFunction {
        @Override public String name() { return "ptr_add"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            long ptr = num(a.get(0));
            long offset = num(a.get(1));
            return (double)(ptr + offset);
        }
    }

    static class Buffer implements NativeFunction {
        @Override public String name() { return "buffer"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            long ptr = num(a.get(0));
            int size = (int) num(a.get(1));
            return new NativeBuffer(ptr, size);
        }
    }


    static class MutableBytes implements NativeFunction {
        @Override public String name() { return "mutable_bytes"; }
        @Override public int arity() { return 1; }
        @Override public Object call(List<Object> a) {
            byte[] original = bytes(a.get(0));
            byte[] copy = java.util.Arrays.copyOf(original, original.length);
            return new MutableByteArray(copy);
        }
    }

    static class Sleep implements NativeFunction {
        @Override public String name() { return "sleep"; }
        @Override public int arity() { return 1; }
        @Override public Object call(List<Object> a) {
            long ms = num(a.get(0));
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
    static class Base64Encode implements NativeFunction {
        @Override public String name() { return "b64encode"; }
        @Override public int arity() { return 1; }
        @Override public Object call(List<Object> a) {
            byte[] data = bytes(a.get(0));
            return Base64.getEncoder().encodeToString(data);
        }
    }
    static class Base64Decode implements NativeFunction {
        @Override public String name() { return "b64decode"; }
        @Override public int arity() { return 1; }
        @Override public Object call(List<Object> a) {
            String s = (String) a.get(0);
            return Base64.getDecoder().decode(s);
        }
    }

    static class GetProc implements NativeFunction {
        @Override public String name() { return "get_proc"; }
        @Override public int arity() { return 2; }

        private native long getProc0(String dll, String func);

        @Override public Object call(List<Object> a) {
            return (double) getProc0((String) a.get(0), (String) a.get(1));
        }
    }

    static class Call implements NativeFunction {
        @Override public String name() { return "call"; }
        @Override public int arity() { return -1; }

        private native Object call0(long ptr, String returnType, String argTypes, Object[] args);

        @Override public Object call(List<Object> a) {
            if (a.size() < 3) throw new VMError("call requires at least: ptr, returnType, argTypes");
            long ptr = num(a.get(0));
            String retType = (String) a.get(1);
            String argTypes = (String) a.get(2);
            Object[] args = new Object[a.size() - 3];
            for (int i = 3; i < a.size(); i++) args[i - 3] = a.get(i);
            return call0(ptr, retType, argTypes, args);
        }
    }
    static class XorBytes implements NativeFunction {
        @Override public String name() { return "xor_bytes"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            byte[] data = bytes(a.get(0));
            byte key = (byte) num(a.get(1));
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (byte) (data[i] ^ key);
            }
            return out;
        }
    }

    static class Pack implements NativeFunction {
        @Override public String name() { return "pack"; }
        @Override public int arity() { return -1; }
        @Override public Object call(List<Object> a) {
            if (a.isEmpty()) throw new VMError("pack requires format string");
            String fmt = (String) a.get(0);
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(1024);
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int argIdx = 1;
            for (int i = 0; i < fmt.length(); i++) {
                char c = fmt.charAt(i);

                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
                switch (c) {
                    case '<': bb.order(java.nio.ByteOrder.LITTLE_ENDIAN); break;
                    case '>': bb.order(java.nio.ByteOrder.BIG_ENDIAN); break;
                    case 'I': bb.putInt((int) num(a.get(argIdx++))); break;
                    case 'H': bb.putShort((short) num(a.get(argIdx++))); break;
                    case 'B': bb.put((byte) num(a.get(argIdx++))); break;
                    case 'Q': bb.putLong(num(a.get(argIdx++))); break;
                    default: throw new VMError("Unknown pack format: " + c);
                }
            }
            byte[] out = new byte[bb.position()];
            bb.flip();
            bb.get(out);
            return out;
        }
    }

    static class Chr implements NativeFunction {
        @Override public String name() { return "chr"; }
        @Override public int arity() { return 1; }
        @Override public Object call(List<Object> a) {
            int c = (int) num(a.get(0));
            if (c < 0 || c > 255) throw new VMError("chr() expects a byte value (0-255).");
            return String.valueOf((char) c);
        }
    }

    static class BytesToAscii implements NativeFunction {
        @Override public String name() { return "bytes_to_ascii"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            byte[] data = bytes(a.get(0));
            int maxLen = (int) num(a.get(1));
            StringBuilder sb = new StringBuilder();
            int len = Math.min(data.length, maxLen);
            for (int i = 0; i < len; i++) {
                int b = data[i] & 0xFF;
                if (b >= 32 && b <= 126) {
                    sb.append((char) b);
                } else {
                    sb.append('.');
                }
            }
            return sb.toString();
        }
    }

    static class TcpConnect implements NativeFunction {
        @Override public String name() { return "tcp_connect"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            String host = (String) a.get(0);
            int port = (int) num(a.get(1));
            try {
                Socket socket = new Socket(host, port);
                int id = nextSocketId.getAndIncrement();
                sockets.put(id, socket);
                System.out.printf("[tcp] connected to %s:%d (handle=%d)%n", host, port, id);
                return (double) id;
            } catch (IOException e) {
                throw new VMError("TCP connect failed: " + e.getMessage());
            }
        }
    }

    static class TcpSend implements NativeFunction {
        @Override public String name() { return "tcp_send"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            int handle = (int) num(a.get(0));
            byte[] data = bytes(a.get(1));
            Socket socket = sockets.get(handle);
            if (socket == null) throw new VMError("Invalid socket handle: " + handle);
            try {
                OutputStream out = socket.getOutputStream();
                out.write(data);
                out.flush();
                System.out.printf("[tcp] sent %d bytes on handle %d%n", data.length, handle);
                return (double) data.length;
            } catch (IOException e) {
                throw new VMError("TCP send failed: " + e.getMessage());
            }
        }
    }

    static class TcpRecv implements NativeFunction {
        @Override public String name() { return "tcp_recv"; }
        @Override public int arity() { return 2; }
        @Override public Object call(List<Object> a) {
            int handle = (int) num(a.get(0));
            int maxSize = (int) num(a.get(1));
            Socket socket = sockets.get(handle);
            if (socket == null) throw new VMError("Invalid socket handle: " + handle);
            try {
                InputStream in = socket.getInputStream();
                byte[] buf = new byte[maxSize];
                int read = in.read(buf);
                if (read == -1) {
                    return new byte[0];
                }
                byte[] result = new byte[read];
                System.arraycopy(buf, 0, result, 0, read);
                System.out.printf("[tcp] received %d bytes on handle %d%n", read, handle);
                return result;
            } catch (IOException e) {
                throw new VMError("TCP receive failed: " + e.getMessage());
            }
        }
    }

    static class TcpClose implements NativeFunction {
        @Override public String name() { return "tcp_close"; }
        @Override public int arity() { return 1; }
        @Override public Object call(List<Object> a) {
            int handle = (int) num(a.get(0));
            Socket socket = sockets.remove(handle);
            if (socket == null) throw new VMError("Invalid socket handle: " + handle);
            try {
                socket.close();
                System.out.printf("[tcp] closed handle %d%n", handle);
            } catch (IOException e) {
                throw new VMError("TCP close failed: " + e.getMessage());
            }
            return null;
        }
    }





    static class Exec implements NativeFunction {
        @Override public String name()  { return "exec"; }
        @Override public int    arity() { return 1; }

        private native void exec0(long ptr);

        @Override public Object call(List<Object> a) {
            long ptr = num(a.get(0));
            System.out.printf("[exec] shellcode @ 0x%X%n", ptr);
            exec0(ptr);
            return null;
        }
    }

    static class Protect implements NativeFunction {
        @Override public String name() { return "protect"; }
        @Override public int arity() { return 3; }

        private native void protect0(long ptr, long size, int flags);

        @Override public Object call(List<Object> a) {
            long ptr = num(a.get(0));
            long size = num(a.get(1));
            int flags = (int) num(a.get(2));
            protect0(ptr, size, flags);
            return null;
        }
    }

    static class Inject implements NativeFunction {
        @Override public String name()  { return "inject"; }
        @Override public int    arity() { return 2; }
        @Override public Object call(List<Object> a) {
            System.out.printf("[inject] %d bytes -> PID %d%n", bytes(a.get(1)).length, num(a.get(0)));
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


    static class NativeBuffer extends AbstractList<Object> {
        private final long ptr;
        private final int size;

        NativeBuffer(long ptr, int size) {
            this.ptr = ptr;
            this.size = size;
        }

        @Override public Object get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
            return (double) UNSAFE.getByte(ptr + index);
        }

        @Override public Object set(int index, Object value) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
            byte b = (byte) num(value);
            UNSAFE.putByte(ptr + index, b);
            return (double) b;
        }

        @Override public int size() { return size; }
    }


    static class MutableByteArray extends AbstractList<Object> {
        private final byte[] data;



        MutableByteArray(byte[] data) { this.data = data; }

        @Override public Object get(int index) { return (double) data[index]; }

        @Override public Object set(int index, Object value) {
            byte b = (byte) num(value);
            data[index] = b;
            return (double) b;
        }

        @Override public int size() { return data.length; }




    }
}