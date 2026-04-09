package pussylang.compiler;

/**
 *  a bad debuging shit
 */
public class Disassembler {

    public static void disassemble(Chunk chunk) {
        System.out.println("" + chunk.name + " (arity=" + chunk.arity + ") ");
        byte[] code = chunk.toByteArray();
        int i = 0;
        while (i < code.length) {
            i = instruction(chunk, code, i);
        }

        for (Object c : chunk.constants) {
            if (c instanceof Chunk nested) {
                System.out.println();
                disassemble(nested);
            }
        }
    }

    private static int instruction(Chunk chunk, byte[] code, int offset) {
        System.out.printf("%04d ", offset);


        int line = chunk.lineAt(offset);
        if (offset > 0 && line == chunk.lineAt(offset - 1)) System.out.print("   | ");
        else  System.out.printf("%4d ", line);

        OpCode op = OpCode.values()[code[offset] & 0xFF];

        return switch (op) {


            case PUSH_NULL, PUSH_TRUE, PUSH_FALSE,
                 POP, ADD, SUB, MUL, DIV, MOD, NEGATE,
                 NOT, EQUAL, NOT_EQUAL,
                 LESS, LESS_EQ, GREATER, GREATER_EQ,
                 PRINT, RETURN, HALT -> {
                System.out.println(op);
                yield offset + 1;
            }


            case PUSH_CONST, DEFINE_GLOBAL, GET_GLOBAL, SET_GLOBAL, CLOSURE -> {
                int idx = code[offset + 1] & 0xFF;
                Object val = chunk.constants.get(idx);
                String display = val instanceof Chunk c ? "<func " + c.name + ">" : String.valueOf(val);
                System.out.printf("%-20s  [%d] %s%n", op, idx, display);
                yield offset + 2;
            }


            case GET_LOCAL, SET_LOCAL, CALL -> {
                int slot = code[offset + 1] & 0xFF;
                System.out.printf("%-20s  slot=%d%n", op, slot);
                yield offset + 2;
            }


            case JUMP, JUMP_IF_FALSE -> {
                int jump = ((code[offset + 1] & 0xFF) << 8) | (code[offset + 2] & 0xFF);
                System.out.printf("%-20s  +%d  ->  %04d%n", op, jump, offset + 3 + jump);
                yield offset + 3;
            }


            case LOOP -> {
                int jump = ((code[offset + 1] & 0xFF) << 8) | (code[offset + 2] & 0xFF);
                System.out.printf("%-20s  -%d  ->  %04d%n", op, jump, offset + 3 - jump);
                yield offset + 3;
            }
        };
    }
}