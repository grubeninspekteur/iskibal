parser grammar IskaraParser;

options { tokenVocab=IskaraLexer; }

// Helper for optional newlines
nl : NEWLINE* ;

// ============================================================================
// Module Structure
// ============================================================================

ruleModule
    : nl moduleHeader? (nl preamble)* (nl ruleDefinition)* nl EOF
    ;

moduleHeader
    : MODULE (IDENTIFIER | STRING)
    ;

preamble
    : importSection
    | factSection
    | globalSection
    | outputSection
    | dataTableDef
    ;

importSection
    : IMPORTS nl LBRACE nl (importDecl (nl importDecl)*)? nl RBRACE
    ;

importDecl
    : identifier ASSIGN qualifiedName
    ;

qualifiedName
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

factSection
    : FACTS nl LBRACE nl (factDecl (nl factDecl)*)? nl RBRACE
    ;

factDecl
    : identifier COLON typeRef (STRING)?
    ;

globalSection
    : GLOBALS nl LBRACE nl (globalDecl (nl globalDecl)*)? nl RBRACE
    ;

globalDecl
    : identifier COLON typeRef (STRING)?
    ;

outputSection
    : OUTPUTS nl LBRACE nl (outputDecl (nl outputDecl)*)? nl RBRACE
    ;

outputDecl
    : identifier COLON typeRef (ASSIGN expression)? (STRING)?
    ;

// Type references: Type, Type[], Type{}, [KeyType:ValueType]
typeRef
    : qualifiedName LBRACK RBRACK                              # listType
    | qualifiedName LBRACE RBRACE                              # setType
    | LBRACK typeRef COLON typeRef RBRACK                      # mapType
    | qualifiedName                                            # simpleType
    ;

// ============================================================================
// Data Tables
// ============================================================================

dataTableDef
    : DATA TABLE identifier nl LBRACE nl tableContent nl RBRACE
    ;

tableContent
    : tableRow (nl tableRow)*
    ;

tableRow
    : PIPE (tableCell PIPE)+
    ;

tableCell
    : tableCellContent*
    ;

tableCellContent
    : ~(PIPE | RBRACE | NEWLINE)
    ;

// ============================================================================
// Rules
// ============================================================================

ruleDefinition
    : simpleRule
    | templateRule
    | decisionTableRule
    ;

simpleRule
    : RULE identifier (STRING)? nl localDataTable? whenSection thenSection elseSection? END
    ;

templateRule
    : TEMPLATE RULE identifier (STRING)? nl anonymousDataTable whenSection thenSection END
    ;

decisionTableRule
    : DECISION TABLE identifier (STRING)? nl LBRACE nl tableContent nl RBRACE whereClause?
    ;

localDataTable
    : DATA TABLE identifier nl LBRACE nl tableContent nl RBRACE nl
    ;

anonymousDataTable
    : DATA TABLE nl LBRACE nl tableContent nl RBRACE nl
    ;

whereClause
    : nl WHERE nl aliasDefinition (nl COMMA nl aliasDefinition)*
    ;

aliasDefinition
    : identifier ASSIGN block
    ;

whenSection
    : WHEN COLON? nl statementList
    ;

thenSection
    : THEN COLON? nl statementList
    ;

elseSection
    : ELSE COLON? nl statementList
    ;

// ============================================================================
// Statements
// ============================================================================

statementList
    : (statement (nl statement)*)? nl
    ;

statement
    : letStatement
    | expressionStatement
    ;

letStatement
    : LET identifier ASSIGN expression
    ;

expressionStatement
    : expression
    ;

// ============================================================================
// Expressions (precedence from lowest to highest)
// ============================================================================

expression
    : expression COMMA nl expression                           # commaExpr
    | <assoc=right> expression ASSIGN expression               # assignmentExpr
    | expression comparisonOp expression                       # comparisonExpr
    | expression additiveOp expression                         # additiveExpr
    | expression multiplicativeOp expression                   # multiplicativeExpr
    | MINUS expression                                         # unaryMinusExpr
    | messageSendExpr                                          # messageSend
    ;

comparisonOp
    : EQUALS | NOT_EQUALS | GREATER | LESS | GREATER_EQ | LESS_EQ
    ;

additiveOp
    : PLUS | MINUS
    ;

multiplicativeOp
    : STAR | SLASH
    ;

// Smalltalk-style message sending
// Non-left-recursive version for better ANTLR compatibility
messageSendExpr
    : navigationExpr messagePart*
    ;

messagePart
    : IDENTIFIER COLON expression (IDENTIFIER COLON expression)*  # keywordMessagePart
    | IDENTIFIER                                                   # unaryMessagePart
    | BANG                                                         # defaultMessagePart
    ;

// Navigation with dot
navigationExpr
    : primaryExpr (DOT identifier)*
    ;

// Primary expressions
primaryExpr
    : literal                                                  # literalExpr
    | identifier                                               # identifierExpr
    | globalRef                                                # globalRefExpr
    | block                                                    # blockExpr
    | LPAREN expression RPAREN                                 # parenExpr
    | primaryExpr LBRACK expression RBRACK                     # indexExpr
    ;

// Identifiers (regular or quoted)
identifier
    : IDENTIFIER
    | QUOTED_ID
    ;

// Global variable reference
globalRef
    : AT identifier
    ;

// Block expression [:param1 :param2 | statements]
block
    : LBRACE nl statementList RBRACE
    | LBRACK blockParams? PIPE? nl statementList RBRACK
    ;

blockParams
    : (COLON identifier)+
    ;

// ============================================================================
// Literals
// ============================================================================

literal
    : STRING                                                   # stringLiteral
    | templateString                                           # templateStringLiteral
    | NUMBER                                                   # numberLiteral
    | TRUE                                                     # trueLiteral
    | FALSE                                                    # falseLiteral
    | NULL                                                     # nullLiteral
    | listLiteral                                              # listLit
    | setLiteral                                               # setLit
    | mapLiteral                                               # mapLit
    ;

templateString
    : TEMPLATE_STRING_START templatePart* TEMPLATE_STRING_END
    ;

templatePart
    : TEMPLATE_TEXT
    | TEMPLATE_EXPR_START expression RBRACE
    ;

listLiteral
    : LBRACK nl (expression (COMMA nl expression)*)? nl RBRACK
    ;

setLiteral
    : LBRACE nl setElement (COMMA nl setElement)* nl RBRACE
    | LBRACE nl RBRACE
    ;

setElement
    : expression (DOTDOT expression)?                          // range support: {1..10}
    ;

mapLiteral
    : LBRACK nl mapEntry (COMMA nl mapEntry)* nl RBRACK
    | LBRACK COLON RBRACK                                      // empty map [:]
    ;

mapEntry
    : expression COLON expression
    ;
