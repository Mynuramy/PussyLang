package pussylang.ast.expr;
import pussylang.ast.Expr;
import pussylang.ast.ExprVisitor;
import pussylang.lexer.Token;

public record IndexAssignExpr(Expr array, Token bracket, Expr index, Expr value) implements Expr {
    public <R> R accept(ExprVisitor<R> v) { return v.visitIndexAssign(this); }
}