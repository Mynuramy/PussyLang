package pussylang.parser;

import pussylang.ast.Expr;
import pussylang.ast.Stmt;
import pussylang.ast.expr.*;
import pussylang.ast.stmt.*;
import pussylang.lexer.Token;
import pussylang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

import static pussylang.lexer.TokenType.*;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }



    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }


    // declarations  (highest level: func / var / statement)


    private Stmt declaration() {
        if (match(FUNC)) return funcDeclaration();
        if (match(VAR))  return varDeclaration();
        return statement();
    }

    /** func name(a, b, c) { ... } */
    private Stmt funcDeclaration() {
        Token name = consume(IDENTIFIER, "Expect function name.");
        consume(LEFT_PAREN, "Expect '(' after function name.");
        List<Token> params = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (params.size() >= 255) error(peek(), "Cannot have more than 255 parameters.");
                params.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '{' before function body.");
        List<Stmt> body = block();
        return new FuncStmt(name, params, body);
    }

    /** var x = expr; */
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expected variable name.");
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expected ';' after variable declaration.");
        return new VarStmt(name, initializer);
    }


    // statements


    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(RETURN)) return returnStatement();
        if (match(LEFT_BRACE)) return new BlockStmt(block());
        if (match(FUNC)) return funcDeclaration();
        if (match(VAR)) return varDeclaration();
        return expressionStatement();
    }

    /** if (cond) stmt [else stmt] */
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expected '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    /** while (cond) stmt */
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expected '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after while condition.");
        Stmt body = statement();
        return new WhileStmt(condition, body);
    }

    /** print expr; */
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expected ';' after print value.");
        return new PrintStmt(value);
    }

    /** return [expr]; */
    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expected ';' after return value.");
        return new ReturnStmt(keyword, value);
    }

    /** expr; */
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expected ';' after expression.");
        return new ExprStmt(expr);
    }

    /** Parses everything inside { } and called after LEFT_BRACE is consumed */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expected '}' to close block.");
        return statements;
    }


    //expressions low to high


    private Expr expression() {
        return assignment();
    }

    /** x = expr  right associative */
    private Expr assignment() {
        Expr expr = or();
        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof VariableExpr v) {
                return new AssignExpr(v.name(), value);
            } else if (expr instanceof IndexExpr idx) {

                return new IndexAssignExpr(idx.array(), idx.bracket(), idx.index(), value);
            }
            throw error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    /** expr || expr */
    private Expr or() {
        Expr expr = and();
        while (match(OR_OR)) {
            Token op = previous();
            expr = new BinaryExpr(expr, op, and());
        }
        return expr;
    }

    /** expr && expr */
    private Expr and() {
        Expr expr = equality();
        while (match(AND_AND)) {
            Token op = previous();
            expr = new BinaryExpr(expr, op, equality());
        }
        return expr;
    }

    /** expr == expr  |  expr != expr */
    private Expr equality() {
        Expr expr = comparison();
        while (match(EQUAL_EQUAL, BANG_EQUAL)) {
            Token op = previous();
            expr = new BinaryExpr(expr, op, comparison());
        }
        return expr;
    }

    /** expr > expr  |  expr >= expr  |  expr < expr  |  expr <= expr */
    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token op = previous();
            expr = new BinaryExpr(expr, op, term());
        }
        return expr;
    }

    /** expr + expr  |  expr - expr */
    private Expr term() {
        Expr expr = factor();
        while (match(PLUS, MINUS)) {
            Token op = previous();
            expr = new BinaryExpr(expr, op, factor());
        }
        return expr;
    }

    /** expr * expr  |  expr / expr  |  expr % expr */
    private Expr factor() {
        Expr expr = unary();
        while (match(STAR, SLASH, PERCENT)) {
            Token op = previous();
            expr = new BinaryExpr(expr, op, unary());
        }
        return expr;
    }

    private Expr arrayLiteral() {
        List<Expr> elements = new ArrayList<>();
        if (!check(RIGHT_BRACKET)) {
            do {
                elements.add(expression());
            } while (match(COMMA));
        }
        consume(RIGHT_BRACKET, "Expected ']' after array elements.");
        return new ArrayLiteralExpr(elements);
    }

    /** !expr  |  -expr */
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token op = previous();
            return new UnaryExpr(op, unary()); // rightrecursive
        }
        return call();
    }

    /** expr(args...)  it handles chained calls like foo()() */
    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(LEFT_BRACKET)) {
                expr = finishIndex(expr);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr finishIndex(Expr array) {
        Token bracket = previous();
        Expr index = expression();
        consume(RIGHT_BRACKET, "Expected ']' after index.");

        if (match(EQUAL)) {
            Expr value = expression();
            return new IndexAssignExpr(array, bracket, index, value);
        }
        return new IndexExpr(array, bracket, index);
    }

    private Expr finishCall(Expr callee) {
        List<Expr> args = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (args.size() >= 255)
                    throw error(peek(), "Can't have more than 255 arguments.");
                args.add(expression());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN, "Expected ')' after arguments.");
        return new CallExpr(callee, paren, args);
    }

    //primary (atoms)

    private Expr primary() {
        if (match(TRUE))  return new LiteralExpr(true);
        if (match(FALSE)) return new LiteralExpr(false);
        if (match(NULL))  return new LiteralExpr(null);

        if (match(NUMBER))     return new LiteralExpr(previous().literal());
        if (match(STRING))     return new LiteralExpr(previous().literal());
        if (match(BYTE_STRING)) return new LiteralExpr(previous().literal());
        if (match(HEX_NUMBER)) return new HexLiteralExpr((long) previous().literal());
        if (match(LEFT_BRACKET)) {
            return arrayLiteral();
        }

        if (match(IDENTIFIER)) return new VariableExpr(previous());

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expected ')' after expression.");
            return new GroupingExpr(expr);
        }

        // maldev builtin calls (WIP) parsed as regular calls via IDENTIFIER path,
        // but we detect their keywords here so they arent rejected as unknowns there
        if (match(ALLOC, FREE, WRITE, READ, EXEC, INJECT, CAST)) {
            // treat keyword token as a named calle
            Token kw = previous();
            Token fakeIdent = new Token(IDENTIFIER, kw.lexeme(), null, kw.line());
            Expr callee = new VariableExpr(fakeIdent);
            consume(LEFT_PAREN, "Expected '(' after '" + kw.lexeme() + "'.");
            return finishCall(callee);
        }

        throw error(peek(), "Expected expression.");
    }


    //token stream helpers


    /** next if current token matches any of the given types */
    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) { advance(); return true; }
        }
        return false;
    }

    /** consume token of expected type, or throw */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }
    /** just check */
    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd()  { return peek().type() == EOF; }
    private Token peek()       { return tokens.get(current); }
    private Token previous()   { return tokens.get(current - 1); }


    // error handling + PANICC!!! recovery


    private ParseException error(Token token, String message) {
        return new ParseException(token, message);
    }

    /**
     * after an error it skip tokens until we hit a likely statement boundary
     * so we can keep parsing and report multiple errors in one run.
     */
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type() == SEMICOLON) return;
            switch (peek().type()) {
                case FUNC, VAR, IF, WHILE, PRINT, RETURN -> { return; }
                default -> advance();
            }
        }
    }
}