package pussylang.ast;

import pussylang.ast.expr.*;

public interface ExprVisitor<R> {
    R visitBinary(BinaryExpr expr);
    R visitUnary(UnaryExpr expr);
    R visitLiteral(LiteralExpr expr);
    R visitGrouping(GroupingExpr expr);
    R visitVariable(VariableExpr expr);
    R visitAssign(AssignExpr expr);
    R visitCall(CallExpr expr);
    R visitHexLiteral(HexLiteralExpr expr);
    R visitArrayLiteral(ArrayLiteralExpr expr);
    R visitIndex(IndexExpr expr);
    R visitIndexAssign(IndexAssignExpr expr);
}