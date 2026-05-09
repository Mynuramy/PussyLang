package pussylang.interpreter;

import pussylang.ast.Expr;
import pussylang.ast.ExprVisitor;
import pussylang.ast.Stmt;
import pussylang.ast.StmtVisitor;
import pussylang.ast.expr.*;
import pussylang.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * THIS SHIT IS NOT BEING USED ANYMORE BUT IS LIKE LEGACY IG?
 * treewalk interpreter and it implements both visitor interfaces so every
 * AST node type has exactly one  method to execute it
 */
public class Interpreter implements ExprVisitor<Object>, StmtVisitor<Void> {

    private final Environment globals = new Environment();
    private Environment current = globals;

    public Interpreter() {
        NativeBuiltins.registerAll(globals);
    }


    //Public API <-------


    public void interpret(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            if (stmt != null) execute(stmt);
        }
    }

    public void executeBlock(List<Stmt> stmts, Environment scope) {
        Environment previous = this.current;
        try {
            this.current = scope;
            for (Stmt stmt : stmts) {
                if (stmt != null) execute(stmt);
            }
        } finally {
            this.current = previous;
        }
    }


    //stmt vis


    @Override
    public Void visitExpr(ExprStmt stmt) {
        evaluate(stmt.expression());
        return null;
    }

    @Override
    public Void visitPrint(PrintStmt stmt) {
        Object value = evaluate(stmt.expression());
        System.out.println(NativeBuiltins.stringify(value));
        return null;
    }

    @Override
    public Object visitArrayLiteral(ArrayLiteralExpr expr) {
        List<Object> array = new ArrayList<>();
        for (Expr element : expr.elements()) {
            array.add(evaluate(element));
        }
        return array;
    }

    @Override
    public Object visitIndex(IndexExpr expr) {
        Object array = evaluate(expr.array());
        Object index = evaluate(expr.index());
        if (array instanceof List<?> list && index instanceof Double d) {
            int idx = d.intValue();
            if (idx < 0 || idx >= list.size())
                throw new RuntimeError("Index out of bounds: " + idx);
            return list.get(idx);
        }
        throw new RuntimeError("Only arrays can be indexed.");
    }

    @Override
    public Object visitIndexAssign(IndexAssignExpr expr) {
        Object array = evaluate(expr.array());
        Object index = evaluate(expr.index());
        Object value = evaluate(expr.value());
        if (array instanceof List<?> list && index instanceof Double d) {
            int idx = d.intValue();
            if (idx < 0 || idx >= list.size())
                throw new RuntimeError("Index out of bounds: " + idx);

            ((List<Object>) list).set(idx, value);
            return value;
        }
        throw new RuntimeError("Invalid array assignment target.");
    }

    @Override
    public Void visitVar(VarStmt stmt) {
        Object value = null;
        if (stmt.initializer() != null) {
            value = evaluate(stmt.initializer());
        }
        current.define(stmt.name().lexeme(), value);
        return null;
    }

    @Override
    public Void visitBlock(BlockStmt stmt) {
        executeBlock(stmt.statements(), new Environment(current));
        return null;
    }

    @Override
    public Void visitIf(IfStmt stmt) {
        if (isTruthy(evaluate(stmt.condition()))) {
            execute(stmt.thenBranch());
        } else if (stmt.elseBranch() != null) {
            execute(stmt.elseBranch());
        }
        return null;
    }

    @Override
    public Void visitWhile(WhileStmt stmt) {
        while (isTruthy(evaluate(stmt.condition()))) {
            execute(stmt.body());
        }
        return null;
    }

    @Override
    public Void visitFunc(FuncStmt stmt) {
        PussyFunction func = new PussyFunction(stmt, current);
        current.define(stmt.name().lexeme(), func);
        return null;
    }

    @Override
    public Void visitReturn(ReturnStmt stmt) {
        Object value = (stmt.value() != null) ? evaluate(stmt.value()) : null;
        throw new ReturnSignal(value);
    }


    // Expr vis


    @Override
    public Object visitLiteral(LiteralExpr expr) {
        return expr.value();
    }

    @Override
    public Object visitHexLiteral(HexLiteralExpr expr) {
        return (double) expr.value();
    }

    @Override
    public Object visitGrouping(GroupingExpr expr) {
        return evaluate(expr.expression());
    }

    @Override
    public Object visitVariable(VariableExpr expr) {
        return current.get(expr.name().lexeme());
    }

    @Override
    public Object visitAssign(AssignExpr expr) {
        Object value = evaluate(expr.value());
        current.assign(expr.name().lexeme(), value);
        return value;
    }

    @Override
    public Object visitUnary(UnaryExpr expr) {
        Object right = evaluate(expr.right());
        return switch (expr.operator().type()) {
            case MINUS -> -toNumber(expr.operator(), right);
            case BANG  -> !isTruthy(right);
            default    -> null;
        };
    }

    @Override
    public Object visitBinary(BinaryExpr expr) {
        Object left  = evaluate(expr.left());
        Object right = evaluate(expr.right());

        return switch (expr.operator().type()) {

            case PLUS -> {
                if (left instanceof Double l && right instanceof Double r) yield l + r;
                if (left instanceof String  || right instanceof String)
                    yield NativeBuiltins.stringify(left) + NativeBuiltins.stringify(right);
                throw new RuntimeError("Operands must be numbers or strings for '+'.");
            }
            case MINUS   -> toNumber(expr.operator(), left) - toNumber(expr.operator(), right);
            case STAR    -> toNumber(expr.operator(), left) * toNumber(expr.operator(), right);
            case PERCENT -> toNumber(expr.operator(), left) % toNumber(expr.operator(), right);
            case SLASH   -> {
                double r = toNumber(expr.operator(), right);
                if (r == 0) throw new RuntimeError("Division by zero.");
                yield toNumber(expr.operator(), left) / r;
            }

            case GREATER       -> toNumber(expr.operator(), left) >  toNumber(expr.operator(), right);
            case GREATER_EQUAL -> toNumber(expr.operator(), left) >= toNumber(expr.operator(), right);
            case LESS          -> toNumber(expr.operator(), left) <  toNumber(expr.operator(), right);
            case LESS_EQUAL    -> toNumber(expr.operator(), left) <= toNumber(expr.operator(), right);

            case EQUAL_EQUAL -> isEqual(left, right);
            case BANG_EQUAL  -> !isEqual(left, right);

            case AND_AND -> isTruthy(left) && isTruthy(right);
            case OR_OR   -> isTruthy(left) || isTruthy(right);
            default -> null;
        };
    }

    @Override
    public Object visitCall(CallExpr expr) {
        Object callee = evaluate(expr.callee());
        if (!(callee instanceof PussyCallable func))
            throw new RuntimeError("'" + NativeBuiltins.stringify(callee) + "' is not callable.");

        List<Object> args = new ArrayList<>();
        for (Expr arg : expr.args()) args.add(evaluate(arg));

        if (func.arity() != -1 && func.arity() != args.size())
            throw new RuntimeError("'" + func.name() + "' expects " + func.arity()
                    + " args but got " + args.size() + ".");

        return func.call(this, args);
    }

    //helpers


    private Object evaluate(Expr expr)  { return expr.accept(this); }
    private void   execute(Stmt stmt)   { stmt.accept(this); }

    private boolean isTruthy(Object obj) {
        if (obj == null)             return false;
        if (obj instanceof Boolean b) return b;
        if (obj instanceof Double d)  return d != 0;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null)              return false;
        return a.equals(b);
    }

    private double toNumber(pussylang.lexer.Token op, Object val) {
        if (val instanceof Double d) return d;
        throw new RuntimeError("Operand for '" + op.lexeme() + "' must be a number.");
    }
}