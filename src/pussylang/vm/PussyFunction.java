package pussylang.vm;

import pussylang.compiler.Chunk;

public class PussyFunction {
    private final Chunk chunk;
    private final Upvalue[] upvalues;

    public PussyFunction(Chunk chunk, Upvalue[] upvalues) {
        this.chunk = chunk;
        this.upvalues = upvalues;
    }

    public PussyFunction(Chunk chunk) {
        this(chunk, new Upvalue[0]);
    }

    public Chunk chunk() { return chunk; }
    public Upvalue[] upvalues() { return upvalues; }

    @Override
    public String toString() {
        return "<func " + chunk.name + ">";
    }
}