package pussylang.ast.expr;
import pussylang.ast.Expr;
import pussylang.ast.ExprVisitor;
import java.util.List;

public record ArrayLiteralExpr(List<Expr> elements) implements Expr {
    public <R> R accept(ExprVisitor<R> v) { return v.visitArrayLiteral(this); }
}