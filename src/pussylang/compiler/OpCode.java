package pussylang.compiler;

public enum OpCode {


    PUSH_CONST,
    PUSH_NULL,
    PUSH_TRUE,
    PUSH_FALSE,


    POP,


    ADD, SUB, MUL, DIV, MOD,
    NEGATE,


    NOT,
    EQUAL, NOT_EQUAL,
    LESS, LESS_EQ,
    GREATER, GREATER_EQ,


    NEW_ARRAY,
    INDEX_GET,
    INDEX_SET,


    DEFINE_GLOBAL,
    GET_GLOBAL,
    SET_GLOBAL,


    GET_LOCAL,
    SET_LOCAL,


    JUMP,
    JUMP_IF_FALSE,
    LOOP,


    CLOSURE,
    GET_UPVALUE,
    SET_UPVALUE,
    CALL,


    PRINT,


    RETURN,
    HALT


}