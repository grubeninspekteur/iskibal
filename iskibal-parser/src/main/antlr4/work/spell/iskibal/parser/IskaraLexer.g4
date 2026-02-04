lexer grammar IskaraLexer;

// Tracks brace nesting depth inside each active template expression so that
// the closing '}' can pop back to TEMPLATE_STRING_MODE at the right level.
@members {
    private final java.util.ArrayDeque<Integer> templateBraceDepths = new java.util.ArrayDeque<>();
}

// Keywords
MODULE      : 'module' ;
IMPORTS     : 'imports' ;
FACTS       : 'facts' ;
GLOBALS     : 'globals' ;
OUTPUTS     : 'outputs' ;
RULE        : 'rule' ;
TEMPLATE    : 'template' ;
DECISION    : 'decision' ;
TABLE       : 'table' ;
DATA        : 'data' ;
WHEN        : 'when' ;
THEN        : 'then' ;
ELSE        : 'else' ;
END         : 'end' ;
WHERE       : 'where' ;
LET         : 'let' ;

// Boolean literals
TRUE        : 'true' ;
FALSE       : 'false' ;

// Null literal
NULL        : 'null' ;

// Operators
ASSIGN      : ':=' ;
EQUALS      : '=' ;
NOT_EQUALS  : '~=' ;
GREATER_EQ  : '>=' ;
LESS_EQ     : '<=' ;
GREATER     : '>' ;
LESS        : '<' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
COMMA       : ',' ;
DOT         : '.' ;
DOTDOT      : '..' ;
BANG        : '!' ;
COLON       : ':' ;
AT          : '@' ;

// Composite collection literal delimiters (must come before single HASH)
HASH_LPAREN : '#(' ;
HASH_LBRACE : '#{' ;
HASH_LBRACK : '#[' ;
HASH        : '#' ;

// Delimiters
LBRACE      : '{' { if (!templateBraceDepths.isEmpty()) templateBraceDepths.push(templateBraceDepths.pop() + 1); } ;
RBRACE      : '}' {
    if (!templateBraceDepths.isEmpty()) {
        if (templateBraceDepths.peek() == 0) {
            templateBraceDepths.pop();
            popMode();
        } else {
            templateBraceDepths.push(templateBraceDepths.pop() - 1);
        }
    }
} ;
LBRACK      : '[' ;
RBRACK      : ']' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
PIPE        : '|' ;

// Template string start - switches to template mode
TEMPLATE_STRING_START : '$"' -> pushMode(TEMPLATE_STRING_MODE) ;

// Regular strings
STRING
    : '"' ( ESC_SEQ | ~["\\] )* '"'
    | '\'' ( ESC_SEQ | ~['\\] )* '\''
    ;

fragment ESC_SEQ
    : '\\' [btnfr"'\\$]
    | '\\u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;

fragment HEX_DIGIT
    : [0-9a-fA-F]
    ;

// Numbers - support both '.' and ',' as decimal separator based on locale
// For now we use '.' as default; locale handling is done in the visitor
NUMBER
    : DIGIT+ ( '.' DIGIT+ )?
    ;

fragment DIGIT
    : [0-9]
    ;

// Backtick-quoted identifiers (can contain spaces and reserved words)
QUOTED_ID
    : '`' ~[`]+ '`'
    ;

// Regular identifier - must not start with underscore (per spec)
IDENTIFIER
    : LETTER LETTER_OR_DIGIT*
    ;

fragment LETTER
    : [a-zA-Z]
    ;

fragment LETTER_OR_DIGIT
    : [a-zA-Z0-9_]
    ;

// Comments
LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

// Newlines are kept for statement separation
NEWLINE
    : ('\r'? '\n' | '\r')+
    ;

WS
    : [ \t]+ -> skip
    ;

// Template string mode
mode TEMPLATE_STRING_MODE;

TEMPLATE_TEXT
    : ( ~[$"\\] | '\\' . )+
    ;

TEMPLATE_EXPR_START
    : '${' { templateBraceDepths.push(0); } -> pushMode(DEFAULT_MODE)
    ;

TEMPLATE_STRING_END
    : '"' -> popMode
    ;

TEMPLATE_DOLLAR
    : '$' -> type(TEMPLATE_TEXT)
    ;
