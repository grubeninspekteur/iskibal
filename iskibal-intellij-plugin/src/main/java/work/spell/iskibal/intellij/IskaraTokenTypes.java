package work.spell.iskibal.intellij;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import work.spell.iskibal.parser.IskaraLexer;

import java.util.HashMap;
import java.util.Map;

public final class IskaraTokenTypes {

    public static final IElementType KEYWORD = new IskaraElementType("KEYWORD");
    public static final IElementType STRING = new IskaraElementType("STRING");
    public static final IElementType NUMBER = new IskaraElementType("NUMBER");
    public static final IElementType IDENTIFIER = new IskaraElementType("IDENTIFIER");
    public static final IElementType QUOTED_IDENTIFIER = new IskaraElementType("QUOTED_IDENTIFIER");
    public static final IElementType OPERATOR = new IskaraElementType("OPERATOR");
    public static final IElementType LINE_COMMENT = new IskaraElementType("LINE_COMMENT");
    public static final IElementType BLOCK_COMMENT = new IskaraElementType("BLOCK_COMMENT");
    public static final IElementType LBRACE = new IskaraElementType("LBRACE");
    public static final IElementType RBRACE = new IskaraElementType("RBRACE");
    public static final IElementType LBRACK = new IskaraElementType("LBRACK");
    public static final IElementType RBRACK = new IskaraElementType("RBRACK");
    public static final IElementType LPAREN = new IskaraElementType("LPAREN");
    public static final IElementType RPAREN = new IskaraElementType("RPAREN");
    public static final IElementType HASH_LPAREN = new IskaraElementType("HASH_LPAREN");
    public static final IElementType HASH_LBRACE = new IskaraElementType("HASH_LBRACE");
    public static final IElementType HASH_LBRACK = new IskaraElementType("HASH_LBRACK");
    public static final IElementType COMMA = new IskaraElementType("COMMA");
    public static final IElementType DOT = new IskaraElementType("DOT");
    public static final IElementType COLON = new IskaraElementType("COLON");
    public static final IElementType PIPE = new IskaraElementType("PIPE");
    public static final IElementType TEMPLATE_STRING_DELIM = new IskaraElementType("TEMPLATE_STRING_DELIM");
    public static final IElementType TEMPLATE_TEXT = new IskaraElementType("TEMPLATE_TEXT");
    public static final IElementType TEMPLATE_EXPR_START = new IskaraElementType("TEMPLATE_EXPR_START");
    public static final IElementType NEWLINE = new IskaraElementType("NEWLINE");
    public static final IElementType WHITE_SPACE = new IskaraElementType("WHITE_SPACE");
    public static final IElementType BAD_CHARACTER = new IskaraElementType("BAD_CHARACTER");
    public static final IElementType BOOLEAN = new IskaraElementType("BOOLEAN");
    public static final IElementType NULL = new IskaraElementType("NULL");

    public static final TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(STRING, TEMPLATE_TEXT, TEMPLATE_STRING_DELIM);

    private static final Map<Integer, IElementType> TOKEN_MAP = new HashMap<>();

    static {
        // Keywords
        TOKEN_MAP.put(IskaraLexer.MODULE, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.IMPORTS, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.FACTS, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.GLOBALS, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.OUTPUTS, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.RULE, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.TEMPLATE, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.DECISION, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.TABLE, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.DATA, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.WHEN, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.THEN, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.ELSE, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.END, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.WHERE, KEYWORD);
        TOKEN_MAP.put(IskaraLexer.LET, KEYWORD);

        // Boolean and null literals
        TOKEN_MAP.put(IskaraLexer.TRUE, BOOLEAN);
        TOKEN_MAP.put(IskaraLexer.FALSE, BOOLEAN);
        TOKEN_MAP.put(IskaraLexer.NULL, NULL);

        // Operators
        TOKEN_MAP.put(IskaraLexer.ASSIGN, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.EQUALS, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.NOT_EQUALS, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.GREATER_EQ, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.LESS_EQ, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.GREATER, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.LESS, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.PLUS, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.MINUS, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.STAR, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.SLASH, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.DOTDOT, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.BANG, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.AT, OPERATOR);
        TOKEN_MAP.put(IskaraLexer.HASH, OPERATOR);

        // Strings and numbers
        TOKEN_MAP.put(IskaraLexer.STRING, STRING);
        TOKEN_MAP.put(IskaraLexer.NUMBER, NUMBER);
        TOKEN_MAP.put(IskaraLexer.QUOTED_ID, QUOTED_IDENTIFIER);
        TOKEN_MAP.put(IskaraLexer.IDENTIFIER, IDENTIFIER);

        // Delimiters
        TOKEN_MAP.put(IskaraLexer.LBRACE, LBRACE);
        TOKEN_MAP.put(IskaraLexer.RBRACE, RBRACE);
        TOKEN_MAP.put(IskaraLexer.LBRACK, LBRACK);
        TOKEN_MAP.put(IskaraLexer.RBRACK, RBRACK);
        TOKEN_MAP.put(IskaraLexer.LPAREN, LPAREN);
        TOKEN_MAP.put(IskaraLexer.RPAREN, RPAREN);
        TOKEN_MAP.put(IskaraLexer.HASH_LPAREN, HASH_LPAREN);
        TOKEN_MAP.put(IskaraLexer.HASH_LBRACE, HASH_LBRACE);
        TOKEN_MAP.put(IskaraLexer.HASH_LBRACK, HASH_LBRACK);
        TOKEN_MAP.put(IskaraLexer.COMMA, COMMA);
        TOKEN_MAP.put(IskaraLexer.DOT, DOT);
        TOKEN_MAP.put(IskaraLexer.COLON, COLON);
        TOKEN_MAP.put(IskaraLexer.PIPE, PIPE);

        // Template strings
        TOKEN_MAP.put(IskaraLexer.TEMPLATE_STRING_START, TEMPLATE_STRING_DELIM);
        TOKEN_MAP.put(IskaraLexer.TEMPLATE_STRING_END, TEMPLATE_STRING_DELIM);
        TOKEN_MAP.put(IskaraLexer.TEMPLATE_TEXT, TEMPLATE_TEXT);
        TOKEN_MAP.put(IskaraLexer.TEMPLATE_EXPR_START, TEMPLATE_EXPR_START);

        // Whitespace and newlines
        TOKEN_MAP.put(IskaraLexer.NEWLINE, NEWLINE);
        TOKEN_MAP.put(IskaraLexer.WS, WHITE_SPACE);

        // Comments
        TOKEN_MAP.put(IskaraLexer.LINE_COMMENT, LINE_COMMENT);
        TOKEN_MAP.put(IskaraLexer.BLOCK_COMMENT, BLOCK_COMMENT);
    }

    public static IElementType map(int antlrTokenType) {
        return TOKEN_MAP.getOrDefault(antlrTokenType, BAD_CHARACTER);
    }

    private IskaraTokenTypes() {
    }

    private static final class IskaraElementType extends IElementType {
        IskaraElementType(String debugName) {
            super(debugName, IskaraLanguage.INSTANCE);
        }
    }
}
