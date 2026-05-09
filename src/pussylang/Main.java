package pussylang;

import pussylang.ast.Stmt;
import pussylang.bytecode.PBCReader;
import pussylang.bytecode.PBCWriter;
import pussylang.compiler.Chunk;
import pussylang.compiler.Compiler;
import pussylang.compiler.Disassembler;
import pussylang.interpreter.Interpreter;
import pussylang.lexer.Lexer;
import pussylang.lexer.LexerException;
import pussylang.lexer.Token;
import pussylang.parser.ParseException;
import pussylang.parser.Parser;
import pussylang.vm.VM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) { runVM(DEMO, false); return; }

        switch (args[0]) {
            case "--interpret" -> runInterpreter(srcFile(args));
            case "--vm"        -> runVM(srcFile(args), false);
            case "--dis"       -> runVM(srcFile(args), true);
            case "--compile"   -> compileToPBC(srcFile(args), derivePbcPath(args));
           case "--run-pbc"   -> runPBC(explicitPbcPath(args));

            default            -> runVM(Files.readString(Path.of(args[0])), false);
        }
    }



    private static String srcFile(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Missing source file. Using demo.");
            return DEMO;
        }
        return Files.readString(Path.of(args[1]));
    }


    private static Path derivePbcPath(String[] args) {
        if (args.length < 2) return Path.of("out/script.pbc");
        String srcPath = args[1];
        if (srcPath.endsWith(".pussy")) {
            srcPath = srcPath.substring(0, srcPath.length() - 6);
        }
        return Path.of(srcPath + ".pbc");
    }


    private static Path explicitPbcPath(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Missing .pbc file path.");
        }
        return Path.of(args[1]);
    }

    private static void runInterpreter(String source) {
        List<Stmt> ast = frontend(source);
        if (ast == null) return;
        System.out.println("tree-walk interpreter ");
        new Interpreter().interpret(ast);
    }

    private static void runVM(String source, boolean dis) {
        List<Stmt> ast = frontend(source);
        if (ast == null) return;
        Chunk chunk = new Compiler().compileScript(ast);
        if (dis) { Disassembler.disassemble(chunk); System.out.println(); }
        System.out.println(" bytecode VM ");
        new VM().run(chunk);
    }

    private static void compileToPBC(String source, Path out) throws IOException {
        List<Stmt> ast = frontend(source);
        if (ast == null) return;
        Chunk chunk = new Compiler().compileScript(ast);
        try (PBCWriter w = new PBCWriter(out)) { w.write(chunk); }
        System.out.println("Compiled -> " + out + "  (" +
                Files.size(out) + " bytes)");
    }

    private static void runPBC(Path pbc) throws IOException {
        Chunk chunk;
        try (PBCReader r = new PBCReader(pbc)) { chunk = r.read(); }
        System.out.println(" loaded " + pbc + " ");
        new VM().run(chunk);
    }





    private static List<Stmt> frontend(String source) {
        try {
            List<Token> tokens = new Lexer(source).tokenize();
            return new Parser(tokens).parse();
        } catch (LexerException | ParseException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    private static String src(String[] args) throws IOException {
        return args.length > 1 ? Files.readString(Path.of(args[1])) : DEMO;
    }

    private static Path pbcPath(String[] args) {
        if (args.length > 1) return Path.of(args[1]);
        return Path.of("out/script.pbc");
    }

    private static String formatDouble(double d) {
        return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
    }



    private static final String DEMO = """
        func factorial(n) {
            if (n <= 1) { return 1; }
            return n * factorial(n - 1);
        }

        print factorial(6);

        var sc = b"\\x90\\x90\\xCC";
        var buf = alloc(0x1000);
        write(buf, sc, 3);
        exec(buf);
        free(buf);
    """;
}