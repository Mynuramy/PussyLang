package pussylang.compiler;


public class Disassembler {

    public static void disassemble(Chunk chunk) {

        System.out.println(chunk.name + " (arity=" + chunk.arity + ") ");


        byte[] code = chunk.code();
        int offset = 0;
        while (offset < code.length) {
            offset = disassembleInstruction(chunk, code, offset);
        }


        for (Object constant : chunk.constants) {
            if (constant instanceof Chunk nestedChunk) {
                System.out.println();
                disassemble(nestedChunk);
            }
        }
    }


    private static int disassembleInstruction(Chunk chunk, byte[] code, int offset) {

        System.out.printf("%04d ", offset);


        printLineNumber(chunk, code, offset);


        int opcodeByte = code[offset] & 0xFF;
        OpCode op = OpCode.values()[opcodeByte];


        switch (op) {

            case PUSH_NULL: case PUSH_TRUE: case PUSH_FALSE:
            case POP: case ADD: case SUB: case MUL: case DIV: case MOD:
            case NEGATE: case NOT: case EQUAL: case NOT_EQUAL:
            case LESS: case LESS_EQ: case GREATER: case GREATER_EQ:
            case PRINT: case RETURN: case HALT:
                System.out.println(op);
                return offset + 1;


            case PUSH_CONST: case DEFINE_GLOBAL: case GET_GLOBAL: case SET_GLOBAL:
                return disassembleConstantOp(chunk, code, offset, op);


            case CLOSURE:
                return disassembleClosure(chunk, code, offset, op);


            case GET_LOCAL: case SET_LOCAL: case CALL:
                return disassembleSlotOp(code, offset, op);



            case GET_UPVALUE: case SET_UPVALUE:
                return disassembleUpvalueOp(code, offset, op);


            case JUMP: case JUMP_IF_FALSE:
                return disassembleJump(code, offset, op);


            case LOOP:
                return disassembleLoop(code, offset, op);

            case NEW_ARRAY:
                return disassembleNewArray(code, offset, op);

            case INDEX_GET:
            case INDEX_SET:
                System.out.println(op);
                return offset + 1;


            default:
                System.out.println(op + " (unhandled)");
                return offset + 1;
        }
    }

    private static void printLineNumber(Chunk chunk, byte[] code, int offset) {
        int currentLine = chunk.lineAt(offset);
        if (offset > 0 && currentLine == chunk.lineAt(offset - 1)) {
            System.out.print("   | ");
        } else {
            System.out.printf("%4d ", currentLine);
        }
    }

    private static int disassembleConstantOp(Chunk chunk, byte[] code, int offset, OpCode op) {
        int constIndex = code[offset + 1] & 0xFF;
        Object value = chunk.constants.get(constIndex);
        String display = formatConstant(value);
        System.out.printf("%-20s  [%d] %s%n", op, constIndex, display);
        return offset + 2;
    }

    private static int disassembleNewArray(byte[] code, int offset, OpCode op) {
        int elementCount = code[offset + 1] & 0xFF;
        System.out.printf("%-20s  %d elements%n", op, elementCount);
        return offset + 2;
    }

    private static int disassembleClosure(Chunk chunk, byte[] code, int offset, OpCode op) {
        int constIndex = code[offset + 1] & 0xFF;
        Object value = chunk.constants.get(constIndex);
        String display = formatConstant(value);
        System.out.printf("%-20s  [%d] %s", op, constIndex, display);

        int upvalueCount = (value instanceof Chunk c) ? c.upvalueCount : 0;
        System.out.printf("  upvalues=%d", upvalueCount);

        int pos = offset + 2;
        for (int i = 0; i < upvalueCount; i++) {
            boolean isLocal = code[pos++] != 0;
            int index = code[pos++] & 0xFF;
            System.out.printf(" %s%d", isLocal ? "L" : "U", index);
        }
        System.out.println();
        return pos;
    }

    private static int disassembleSlotOp(byte[] code, int offset, OpCode op) {
        int slot = code[offset + 1] & 0xFF;
        System.out.printf("%-20s  slot=%d%n", op, slot);
        return offset + 2;
    }

    private static int disassembleUpvalueOp(byte[] code, int offset, OpCode op) {
        int upvalueIndex = code[offset + 1] & 0xFF;
        System.out.printf("%-20s  upvalue=%d%n", op, upvalueIndex);
        return offset + 2;
    }

    private static int disassembleJump(byte[] code, int offset, OpCode op) {
        int jump = readBigEndianShort(code, offset + 1);
        int target = offset + 3 + jump;
        System.out.printf("%-20s  +%d  ->  %04d%n", op, jump, target);
        return offset + 3;
    }

    private static int disassembleLoop(byte[] code, int offset, OpCode op) {
        int jump = readBigEndianShort(code, offset + 1);
        int target = offset + 3 - jump;
        System.out.printf("%-20s  -%d  ->  %04d%n", op, jump, target);
        return offset + 3;
    }

    private static int readBigEndianShort(byte[] code, int offset) {
        return ((code[offset] & 0xFF) << 8) | (code[offset + 1] & 0xFF);
    }

    private static String formatConstant(Object value) {
        if (value instanceof Chunk chunk) {
            return "<func " + chunk.name + ">";
        }
        return String.valueOf(value);
    }
}