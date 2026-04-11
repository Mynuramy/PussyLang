package pussylang.compiler;

import pussylang.ast.Expr;
import pussylang.ast.ExprVisitor;
import pussylang.ast.Stmt;
import pussylang.ast.StmtVisitor;
import pussylang.ast.expr.*;
import pussylang.ast.stmt.*;
import pussylang.lexer.Token;
import pussylang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

import static pussylang.compiler.OpCode.*;

public class Compiler implements ExprVisitor<Void>, StmtVisitor<Void> {


    private static class Local {
        final String name;
        final int depth;
        boolean captured;

        Local(String name, int depth, boolean captured) {
            this.name = name;
            this.depth = depth;
            this.captured = captured;
        }
    }


    private static class Upvalue {
        final int index;
        final boolean isLocal;
        Upvalue(int index, boolean isLocal) {
            this.index = index;
            this.isLocal = isLocal;
        }
    }

    private final Compiler enclosing;
    private final List<Local> locals = new ArrayList<>();
    private final List<Upvalue> upvalues = new ArrayList<>();
    private int scopeDepth = 0;

    private Chunk chunk;
    private int currentLine = 1;


    public Compiler() {
        this.enclosing = null;
    }


    private Compiler(Compiler enclosing) {
        this.enclosing = enclosing;
    }

    
    public Chunk compileScript(List<Stmt> stmts) {
        chunk = new Chunk("<script>", 0);
        for (Stmt s : stmts) if (s != null) compileStmt(s);
        emit(HALT);
        return chunk;
    }

   
    Chunk compileFunctionBody(FuncStmt func) {
        chunk = new Chunk(func.name().lexeme(), func.params().size());
        scopeDepth = 1;
        locals.clear();
        upvalues.clear();

        // Parameters become locals at slot 0, 1, 2, etcccc
        for (Token p : func.params()) {
            locals.add(new Local(p.lexeme(), 1, false));
        }

        for (Stmt s : func.body()) if (s != null) compileStmt(s);


        emit(PUSH_NULL);
        emit(RETURN);


        chunk.upvalueCount = upvalues.size();
        return chunk;
    }

    private void compileStmt(Stmt s) { s.accept(this); }

    @Override
    public Void visitExpr(ExprStmt s) {
        compileExpr(s.expression());
        emit(POP);
        return null;
    }

    @Override
    public Void visitPrint(PrintStmt s) {
        compileExpr(s.expression());
        emit(PRINT);
        return null;
    }

    @Override
    public Void visitVar(VarStmt s) {
        currentLine = s.name().line();

        if (s.initializer() != null) compileExpr(s.initializer());
        else emit(PUSH_NULL);

        if (scopeDepth == 0) {
            emitWithByte(DEFINE_GLOBAL, addConstant(s.name().lexeme()));
        } else {
            locals.add(new Local(s.name().lexeme(), scopeDepth, false));
        }
        return null;
    }

    @Override
    public Void visitBlock(BlockStmt s) {
        beginScope();
        for (Stmt inner : s.statements()) if (inner != null) compileStmt(inner);
        endScope();
        return null;
    }

    @Override
    public Void visitIf(IfStmt s) {
        compileExpr(s.condition());

        int thenJump = emitJump(JUMP_IF_FALSE);
        compileStmt(s.thenBranch());

        if (s.elseBranch() != null) {
            int elseJump = emitJump(JUMP);
            patchJump(thenJump);
            compileStmt(s.elseBranch());
            patchJump(elseJump);
        } else {
            patchJump(thenJump);
        }
        return null;
    }

    @Override
    public Void visitWhile(WhileStmt s) {
        int loopStart = chunk.currentOffset();
        compileExpr(s.condition());
        int exitJump = emitJump(JUMP_IF_FALSE);
        compileStmt(s.body());
        emitLoop(loopStart);
        patchJump(exitJump);
        return null;
    }

    @Override
    public Void visitFunc(FuncStmt s) {
        currentLine = s.name().line();


        Compiler nested = new Compiler(this);
        Chunk funcChunk = nested.compileFunctionBody(s);


        emitWithByte(CLOSURE, addConstant(funcChunk));


        for (Upvalue uv : nested.upvalues) {
            chunk.write((byte) (uv.isLocal ? 1 : 0), currentLine);
            chunk.write((byte) uv.index, currentLine);
        }


        if (scopeDepth == 0) {
            emitWithByte(DEFINE_GLOBAL, addConstant(s.name().lexeme()));
        } else {
            locals.add(new Local(s.name().lexeme(), scopeDepth, false));
        }
        return null;
    }

    @Override
    public Void visitReturn(ReturnStmt s) {
        currentLine = s.keyword().line();
        if (s.value() != null) compileExpr(s.value());
        else emit(PUSH_NULL);
        emit(RETURN);
        return null;
    }

    private void compileExpr(Expr e) { e.accept(this); }

    @Override
    public Void visitLiteral(LiteralExpr e) {
        Object v = e.value();
        if (v == null) emit(PUSH_NULL);
        else if (v.equals(true)) emit(PUSH_TRUE);
        else if (v.equals(false)) emit(PUSH_FALSE);
        else emitWithByte(PUSH_CONST, addConstant(v));
        return null;
    }

    @Override
    public Void visitHexLiteral(HexLiteralExpr e) {
        emitWithByte(PUSH_CONST, addConstant((double) e.value()));
        return null;
    }

    @Override
    public Void visitGrouping(GroupingExpr e) {
        compileExpr(e.expression());
        return null;
    }

    @Override
    public Void visitVariable(VariableExpr e) {
        currentLine = e.name().line();
        VarResolution res = resolveVariable(e.name().lexeme());
        switch (res.kind) {
            case LOCAL -> emitWithByte(GET_LOCAL, res.index);
            case UPVALUE -> emitWithByte(GET_UPVALUE, res.index);
            case GLOBAL -> emitWithByte(GET_GLOBAL, addConstant(e.name().lexeme()));
        }
        return null;
    }

    @Override
    public Void visitAssign(AssignExpr e) {
        currentLine = e.name().line();
        compileExpr(e.value());
        VarResolution res = resolveVariable(e.name().lexeme());
        switch (res.kind) {
            case LOCAL -> emitWithByte(SET_LOCAL, res.index);
            case UPVALUE -> emitWithByte(SET_UPVALUE, res.index);
            case GLOBAL -> emitWithByte(SET_GLOBAL, addConstant(e.name().lexeme()));
        }
        return null;
    }

    @Override
    public Void visitUnary(UnaryExpr e) {
        currentLine = e.operator().line();
        compileExpr(e.right());
        switch (e.operator().type()) {
            case MINUS -> emit(NEGATE);
            case BANG -> emit(NOT);
            default -> throw new CompileError("Unknown unary op: " + e.operator().type());
        }
        return null;
    }

    @Override
    public Void visitBinary(BinaryExpr e) {
        currentLine = e.operator().line();
        TokenType op = e.operator().type();

        if (op == TokenType.AND_AND) { compileAnd(e); return null; }
        if (op == TokenType.OR_OR)   { compileOr(e);  return null; }

        compileExpr(e.left());
        compileExpr(e.right());

        switch (op) {
            case PLUS -> emit(ADD);
            case MINUS -> emit(SUB);
            case STAR -> emit(MUL);
            case SLASH -> emit(DIV);
            case PERCENT -> emit(MOD);
            case EQUAL_EQUAL -> emit(EQUAL);
            case BANG_EQUAL -> emit(NOT_EQUAL);
            case LESS -> emit(LESS);
            case LESS_EQUAL -> emit(LESS_EQ);
            case GREATER -> emit(GREATER);
            case GREATER_EQUAL -> emit(GREATER_EQ);
            default -> throw new CompileError("Unknown binary op: " + op);
        }
        return null;
    }

    private void compileAnd(BinaryExpr e) {
        compileExpr(e.left());
        int exitJump = emitJump(JUMP_IF_FALSE);
        compileExpr(e.right());
        patchJump(exitJump);
    }

    private void compileOr(BinaryExpr e) {
        compileExpr(e.left());
        int elseJump = emitJump(JUMP_IF_FALSE);
        int endJump = emitJump(JUMP);
        patchJump(elseJump);
        compileExpr(e.right());
        patchJump(endJump);
    }

    @Override
    public Void visitCall(CallExpr e) {
        currentLine = e.paren().line();
        compileExpr(e.callee());
        for (Expr arg : e.args()) compileExpr(arg);
        emitWithByte(CALL, e.args().size());
        return null;
    }

    private void beginScope() { scopeDepth++; }

    private void endScope() {
        scopeDepth--;
        while (!locals.isEmpty() && locals.getLast().depth > scopeDepth) {
            Local local = locals.removeLast();
            if (local.captured) {
                // ... empty for now <3
            }
            emit(POP);
        }
    }

    //VARIABLE RESOLUTIN
    private enum VarKind { LOCAL, UPVALUE, GLOBAL }
    private record VarResolution(VarKind kind, int index) {}

    private VarResolution resolveVariable(String name) {

        Integer localIndex = resolveLocalHere(name);
        if (localIndex != null) {
            return new VarResolution(VarKind.LOCAL, localIndex);
        }

        Integer upvalueIndex = resolveUpvalue(name);
        if (upvalueIndex != null) {
            return new VarResolution(VarKind.UPVALUE, upvalueIndex);
        }

        return new VarResolution(VarKind.GLOBAL, -1);
    }

    private Integer resolveLocalHere(String name) {
        for (int i = locals.size() - 1; i >= 0; i--) {
            if (locals.get(i).name.equals(name)) {
                return i;
            }
        }
        return null;
    }

    private Integer resolveUpvalue(String name) {
        if (enclosing == null) return null;


        Integer enclosingLocal = enclosing.resolveLocalHere(name);
        if (enclosingLocal != null) {

            enclosing.locals.get(enclosingLocal).captured = true;

            return addUpvalue(enclosingLocal, true);
        }


        Integer enclosingUpvalue = enclosing.resolveUpvalue(name);
        if (enclosingUpvalue != null) {
            return addUpvalue(enclosingUpvalue, false);
        }

        return null;
    }

    private int addUpvalue(int index, boolean isLocal) {

        for (int i = 0; i < upvalues.size(); i++) {
            Upvalue uv = upvalues.get(i);
            if (uv.index == index && uv.isLocal == isLocal) {
                return i;
            }
        }
        upvalues.add(new Upvalue(index, isLocal));
        return upvalues.size() - 1;
    }


    //PCB EMITION HELP

    private void emit(OpCode op) {
        chunk.write((byte) op.ordinal(), currentLine);
    }

    private void emitWithByte(OpCode op, int operand) {
        if (operand > 0xFF) throw new CompileError("Operand too large: " + operand);
        emit(op);
        chunk.write((byte) (operand & 0xFF), currentLine);
    }

    private int addConstant(Object v) { return chunk.addConstant(v); }

    private int emitJump(OpCode op) {
        emit(op);
        chunk.write((byte) 0xFF, currentLine);
        chunk.write((byte) 0xFF, currentLine);
        return chunk.currentOffset() - 2;
    }

    private void patchJump(int offset) { chunk.patchJump(offset); }

    private void emitLoop(int loopStart) {
        emit(LOOP);
        int offset = chunk.currentOffset() - loopStart + 2;
        if (offset > 0xFFFF) throw new CompileError("Loop body too large.");
        chunk.write((byte) ((offset >> 8) & 0xFF), currentLine);
        chunk.write((byte) (offset & 0xFF), currentLine);
    }
}
