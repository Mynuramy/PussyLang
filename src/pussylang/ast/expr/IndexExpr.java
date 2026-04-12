package pussylang.ast.expr;
import pussylang.ast.Expr;
import pussylang.ast.ExprVisitor;
import pussylang.lexer.Token;

public record IndexExpr(Expr array, Token bracket, Expr index) implements Expr {
    public <R> R accept(ExprVisitor<R> v) { return v.visitIndex(this); }
}