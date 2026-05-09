package pussylang.pussy_aot;

import pussylang.bytecode.PBCReader;
import pussylang.compiler.Chunk;

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class pbc_to_c {

    private static int funcCounter = 0;
    private static final List<Chunk>       allChunks = new ArrayList<>();
    private static final Map<Chunk, Integer> chunkIds = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Use: java pbc_to_c <test.pbc> <out.c>");
            System.exit(1);
        }

        Path pbcPath = Paths.get(args[0]);
        Path outPath = Paths.get(args[1]);

        Chunk root;
        try (PBCReader reader = new PBCReader(pbcPath)) {
            root = reader.read();
        }

        collectChunks(root);

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from: ").append(args[0]).append("\n");
        sb.append("// NOTE: Constant and Function types are defined in vm.c\n");
        sb.append("#include <stddef.h>\n");
        sb.append("#include <stdint.h>\n\n");


        sb.append("// Placeholder for functions with zero constants\n");
        sb.append("static const Constant empty_constants[] = { {CONST_NUMBER, {.num = 0.0}} };\n\n");

        //Per function constant arrays
        for (int idx = 0; idx < allChunks.size(); idx++) {
            Chunk chunk = allChunks.get(idx);
            List<Object> consts = chunk.constants();

            sb.append("// Constants for func_").append(idx)
                    .append("  (").append(chunk.name).append(")\n");

            if (consts.isEmpty()) {
                // Use placeholder; emit only the length = 0 marker.
                sb.append("static const size_t func_").append(idx)
                        .append("_constants_len = 0;\n\n");
                continue;
            }

            sb.append("static const Constant func_").append(idx).append("_constants[] = {\n");
            for (int i = 0; i < consts.size(); i++) {
                Object c = consts.get(i);
                sb.append("    ");
                if (c instanceof Double || c instanceof Number) {
                    double val = ((Number) c).doubleValue();
                    sb.append("{CONST_NUMBER, {.num = ")
                            .append(String.format("%.15g", val))
                            .append("}}");
                } else if (c instanceof String) {
                    String s = (String) c;
                    s = s.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t");
                    sb.append("{CONST_STRING, {.str = \"").append(s).append("\"}}");
                } else if (c instanceof Chunk) {
                    int funcId = chunkIds.get((Chunk) c);
                    sb.append("{CONST_FUNCTION, {.func_id = ").append(funcId).append("}}");
                } else {
                    sb.append("{CONST_NUMBER, {.num = 0.0}}");
                }
                sb.append(",");

                sb.append("  // ").append(i).append("\n");
            }
            sb.append("};\n");
            sb.append("static const size_t func_").append(idx)
                    .append("_constants_len = ").append(consts.size()).append(";\n\n");
        }

        //Bytecode bodies
        for (int idx = 0; idx < allChunks.size(); idx++) {
            Chunk c = allChunks.get(idx);
            byte[] code = c.code();
            sb.append("// Function: ").append(c.name)
                    .append(" (arity=").append(c.arity).append(")\n");
            sb.append("static const unsigned char func_").append(idx).append("_bytecode[] = {\n    ");
            for (int i = 0; i < code.length; i++) {
                sb.append(String.format("0x%02X", code[i] & 0xFF));
                if (i < code.length - 1) sb.append(", ");
                if ((i + 1) % 12 == 0 && i < code.length - 1) sb.append("\n    ");
            }
            sb.append("\n};\n");
            sb.append("static const size_t func_").append(idx)
                    .append("_bytecode_len = ").append(code.length).append(";\n\n");
        }

        // Function table
        // Each row now includes the function's own constant pool pointer and
        // length, matching the Function struct in vm.c.
        sb.append("static const Function functions[] = {\n");
        for (int idx = 0; idx < allChunks.size(); idx++) {
            Chunk c = allChunks.get(idx);
            List<Object> consts = c.constants();
            String constPtr = consts.isEmpty()
                    ? "empty_constants"
                    : "func_" + idx + "_constants";
            sb.append("    {\"").append(c.name).append("\", ")
                    .append(c.arity).append(", ")
                    .append("func_").append(idx).append("_bytecode, ")
                    .append("func_").append(idx).append("_bytecode_len, ")
                    .append(constPtr).append(", ")
                    .append("func_").append(idx).append("_constants_len},\n");
        }
        sb.append("};\n");
        sb.append("static const size_t functions_count = ")
                .append(allChunks.size()).append(";\n\n");

        sb.append("static const int root_function_id = 0;\n");

        Files.writeString(outPath, sb.toString());
        System.out.println("Created in: " + outPath);
    }

    /** Depthfirst collection so each chunk gets a stable numeric ID. */
    private static void collectChunks(Chunk chunk) {
        if (chunkIds.containsKey(chunk)) return;
        int id = funcCounter++;
        chunkIds.put(chunk, id);
        allChunks.add(chunk);
        for (Object c : chunk.constants()) {
            if (c instanceof Chunk) {
                collectChunks((Chunk) c);
            }
        }
    }
}