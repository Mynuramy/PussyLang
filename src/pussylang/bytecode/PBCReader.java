package pussylang.bytecode;

import pussylang.compiler.Chunk;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;


public class PBCReader implements Closeable {

    private final DataInputStream in;

    public PBCReader(Path path) throws IOException {
        this.in = new DataInputStream(
                new BufferedInputStream(
                        new FileInputStream(path.toFile())));
    }



    public Chunk read() throws IOException {
        validateMagic();
        byte version = in.readByte();
        if (version != PBCWriter.VERSION)
            throw new IOException("Unsupported .pbc version: " + version);
        return readChunk();
    }

    @Override
    public void close() throws IOException { in.close(); }



    private Chunk readChunk() throws IOException {
        String name  = in.readUTF();
        int    arity = in.readInt();
        int    upvalueCount = in.readInt();
        Chunk  chunk = new Chunk(name, arity);
        chunk.upvalueCount = upvalueCount;


        int    codeLen = in.readInt();
        byte[] code    = in.readNBytes(codeLen);


        int lineCount = in.readInt();
        int[] lines   = new int[lineCount];
        for (int i = 0; i < lineCount; i++) lines[i] = in.readInt();


        for (int i = 0; i < codeLen; i++) {
            chunk.write(code[i], lines[i]);
        }


        int constCount = in.readInt();
        for (int i = 0; i < constCount; i++) {
            chunk.addConstant(readConstant());
        }

        return chunk;
    }

    private Object readConstant() throws IOException {
        byte tag = in.readByte();
        return switch (tag) {
            case PBCWriter.TAG_NULL   -> null;
            case PBCWriter.TAG_BOOL   -> in.readByte() != 0;
            case PBCWriter.TAG_DOUBLE -> in.readDouble();
            case PBCWriter.TAG_STRING -> in.readUTF();
            case PBCWriter.TAG_BYTES  -> {
                int len = in.readInt();
                yield in.readNBytes(len);
            }
            case PBCWriter.TAG_CHUNK  -> readChunk();
            default -> throw new IOException("Unknown constant tag: 0x" + Integer.toHexString(tag));
        };
    }



    private void validateMagic() throws IOException {
        byte[] magic = in.readNBytes(PBCWriter.MAGIC.length);
        if (!Arrays.equals(magic, PBCWriter.MAGIC))
            throw new IOException("Not a .pbc file (bad magic bytes).");
    }
}