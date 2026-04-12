package pussylang.lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        //general keywords part!
        KEYWORDS.put("print",  TokenType.PRINT);
        KEYWORDS.put("var",    TokenType.VAR);
        KEYWORDS.put("if",     TokenType.IF);
        KEYWORDS.put("else",   TokenType.ELSE);
        KEYWORDS.put("while",  TokenType.WHILE);
        KEYWORDS.put("func",   TokenType.FUNC);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("true",   TokenType.TRUE);
        KEYWORDS.put("false",  TokenType.FALSE);
        KEYWORDS.put("null",   TokenType.NULL);
        // shit for maldev maybe
        KEYWORDS.put("alloc",  TokenType.ALLOC);
        KEYWORDS.put("free",   TokenType.FREE);
        KEYWORDS.put("write",  TokenType.WRITE);
        KEYWORDS.put("read",   TokenType.READ);
        KEYWORDS.put("exec",   TokenType.EXEC);
        KEYWORDS.put("inject", TokenType.INJECT);
        KEYWORDS.put("cast",   TokenType.CAST);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LEFT_PAREN);
            case ')' -> addToken(TokenType.RIGHT_PAREN);
            case '{' -> addToken(TokenType.LEFT_BRACE);
            case '}' -> addToken(TokenType.RIGHT_BRACE);
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '*' -> addToken(TokenType.STAR);
            case '%' -> addToken(TokenType.PERCENT);
            case ';' -> addToken(TokenType.SEMICOLON);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case '!' -> addToken(match('=') ? TokenType.BANG_EQUAL    : TokenType.BANG);
            case '=' -> addToken(match('=') ? TokenType.EQUAL_EQUAL   : TokenType.EQUAL);
            case '<' -> addToken(match('=') ? TokenType.LESS_EQUAL    : TokenType.LESS);
            case '>' -> addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
            case '&' -> { if (match('&')) addToken(TokenType.AND_AND); }
            case '|' -> { if (match('|')) addToken(TokenType.OR_OR);   }
            case '[' -> addToken(TokenType.LEFT_BRACKET);
            case ']' -> addToken(TokenType.RIGHT_BRACKET);
            case '/' -> {
                if (match('/')) skipLineComment();
                else if (match('*')) skipBlockComment();
                else addToken(TokenType.SLASH);
            }
            case '"' -> scanString();
            case 'b' -> {
                if (peek() == '"') { advance(); scanByteString(); }
                else scanIdentifier();
            }
            case ' ', '\r', '\t' -> {}
            case '\n' -> line++;
            default -> {
                if (isDigit(c))      scanNumber(c);
                else if (isAlpha(c)) scanIdentifier();
                else throw new LexerException("Not expected character: '" + c + "'", line);
            }
        }
    }

    // NUMBERS

    private void scanNumber(char first) {
        if (first == '0' && (peek() == 'x' || peek() == 'X')) {
            scanHexNumber();
            return;
        }
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        double value = Double.parseDouble(source.substring(start, current));
        addToken(TokenType.NUMBER, value);
    }

    private void scanHexNumber() {
        advance();
        while (isHexDigit(peek())) advance();
        String hexStr = source.substring(start + 2, current);
        long value = Long.parseUnsignedLong(hexStr, 16);
        addToken(TokenType.HEX_NUMBER, value);
    }

    // Srings

    private void scanString() {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\n') line++;
            sb.append(scanEscape());
        }
        if (isAtEnd()) throw new LexerException("Not terminated string", line);
        advance();
        addToken(TokenType.STRING, sb.toString());
    }

    //Byte strings
    private void scanByteString() {
        List<Byte> bytes = new ArrayList<>();
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\' && peekNext() == 'x') {
                advance(); advance();
                char h1 = advance(), h2 = advance();
                bytes.add((byte) Integer.parseInt("" + h1 + h2, 16));
            } else {
                bytes.add((byte) advance());
            }
        }
        if (isAtEnd()) throw new LexerException("Not terminated byte string", line);
        advance();
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) arr[i] = bytes.get(i);
        addToken(TokenType.BYTE_STRING, arr);
    }

    private char scanEscape() {
        if (peek() == '\\') {
            advance();
            return switch (advance()) {
                case 'n'  -> '\n';
                case 't'  -> '\t';
                case 'r'  -> '\r';
                case '\\' -> '\\';
                case '"'  -> '"';
                default   -> throw new LexerException("Not known escape sequence", line);
            };
        }
        return advance();
    }

    // Identifiers andKeywords

    private void scanIdentifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }

    // Comments

    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n') advance();
        if (!isAtEnd()) { advance(); line++; }   // consume \n
    }

    private void skipBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') { advance(); advance(); return; }
            if (peek() == '\n') line++;
            advance();
        }
        throw new LexerException("Not terminated block comment", line);
    }

    // Helpers

    private char advance()          { return source.charAt(current++); }
    private char peek()             { return isAtEnd() ? '\0' : source.charAt(current); }
    private char peekNext()         { return (current + 1 >= source.length()) ? '\0' : source.charAt(current + 1); }
    private boolean match(char exp) { if (isAtEnd() || source.charAt(current) != exp) return false; current++; return true; }
    private boolean isAtEnd()       { return current >= source.length(); }
    private boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private boolean isHexDigit(char c) { return isDigit(c) || (c>='a'&&c<='f') || (c>='A'&&c<='F'); }
    private boolean isAlpha(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
    private boolean isAlphaNumeric(char c) { return isAlpha(c) || isDigit(c); }

    private void addToken(TokenType type)              { addToken(type, null); }
    private void addToken(TokenType type, Object lit)  { tokens.add(new Token(type, source.substring(start, current), lit, line)); }
}