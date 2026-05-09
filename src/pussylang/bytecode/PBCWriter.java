package pussylang.bytecode;

import pussylang.compiler.Chunk;

import java.io.*;
import java.nio.file.Path;


public class PBCWriter implements Closeable {

    static final byte[] MAGIC   = {'P', 'U', 'S', 'S', 0x1A};
    static final byte   VERSION = 0x01;


    static final byte TAG_NULL    = 0x00;
    static final byte TAG_BOOL    = 0x01;
    static final byte TAG_DOUBLE  = 0x02;
    static final byte TAG_STRING  = 0x03;
    static final byte TAG_BYTES   = 0x04;
    static final byte TAG_CHUNK   = 0x05;

    private final DataOutputStream out;

    public PBCWriter(Path path) throws IOException {
        this.out = new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(path.toFile())));
    }



    public void write(Chunk root) throws IOException {
        out.write(MAGIC);
        out.writeByte(VERSION);
        writeChunk(root);
    }

    @Override
    public void close() throws IOException { out.close(); }



    private void writeChunk(Chunk chunk) throws IOException {
        out.writeUTF(chunk.name);
        out.writeInt(chunk.arity);
        out.writeInt(chunk.upvalueCount);


        byte[] code = chunk.code();
        out.writeInt(code.length);
        out.write(code);


        out.writeInt(code.length);
        for (int i = 0; i < code.length; i++) {
            out.writeInt(chunk.lineAt(i));
        }


        out.writeInt(chunk.constants.size());
        for (Object c : chunk.constants) {
            writeConstant(c);
        }
    }

    private void writeConstant(Object v) throws IOException {
        if (v == null) {
            out.writeByte(TAG_NULL);

        } else if (v instanceof Boolean b) {
            out.writeByte(TAG_BOOL);
            out.writeByte(b ? 1 : 0);

        } else if (v instanceof Double d) {
            out.writeByte(TAG_DOUBLE);
            out.writeDouble(d);

        } else if (v instanceof String s) {
            out.writeByte(TAG_STRING);
            out.writeUTF(s);

        } else if (v instanceof byte[] bytes) {
            out.writeByte(TAG_BYTES);
            out.writeInt(bytes.length);
            out.write(bytes);

        } else if (v instanceof Chunk nested) {
            out.writeByte(TAG_CHUNK);
            writeChunk(nested);

        } else {
            throw new IOException("Unknown constant type: " + v.getClass());
        }
    }
}