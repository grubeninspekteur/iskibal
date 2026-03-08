package work.spell.iskibal.intellij.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import work.spell.iskibal.intellij.IskaraTokenTypes;
import work.spell.iskibal.intellij.lexer.IskaraLexerAdapter;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class IskaraSyntaxHighlighter extends SyntaxHighlighterBase {

    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("ISKARA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("ISKARA_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("ISKARA_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("ISKARA_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("ISKARA_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("ISKARA_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("ISKARA_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey QUOTED_IDENTIFIER =
            createTextAttributesKey("ISKARA_QUOTED_IDENTIFIER", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey BRACES =
            createTextAttributesKey("ISKARA_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("ISKARA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("ISKARA_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey COMMA =
            createTextAttributesKey("ISKARA_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey DOT =
            createTextAttributesKey("ISKARA_DOT", DefaultLanguageHighlighterColors.DOT);
    public static final TextAttributesKey BOOLEAN =
            createTextAttributesKey("ISKARA_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey NULL =
            createTextAttributesKey("ISKARA_NULL", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey TEMPLATE_STRING =
            createTextAttributesKey("ISKARA_TEMPLATE_STRING", DefaultLanguageHighlighterColors.STRING);

    private static final Map<IElementType, TextAttributesKey[]> ATTRIBUTES = new HashMap<>();

    static {
        ATTRIBUTES.put(IskaraTokenTypes.KEYWORD, pack(KEYWORD));
        ATTRIBUTES.put(IskaraTokenTypes.BOOLEAN, pack(BOOLEAN));
        ATTRIBUTES.put(IskaraTokenTypes.NULL, pack(NULL));
        ATTRIBUTES.put(IskaraTokenTypes.STRING, pack(STRING));
        ATTRIBUTES.put(IskaraTokenTypes.NUMBER, pack(NUMBER));
        ATTRIBUTES.put(IskaraTokenTypes.IDENTIFIER, pack(IDENTIFIER));
        ATTRIBUTES.put(IskaraTokenTypes.QUOTED_IDENTIFIER, pack(QUOTED_IDENTIFIER));
        ATTRIBUTES.put(IskaraTokenTypes.OPERATOR, pack(OPERATOR));
        ATTRIBUTES.put(IskaraTokenTypes.LINE_COMMENT, pack(LINE_COMMENT));
        ATTRIBUTES.put(IskaraTokenTypes.BLOCK_COMMENT, pack(BLOCK_COMMENT));
        ATTRIBUTES.put(IskaraTokenTypes.LBRACE, pack(BRACES));
        ATTRIBUTES.put(IskaraTokenTypes.RBRACE, pack(BRACES));
        ATTRIBUTES.put(IskaraTokenTypes.HASH_LBRACE, pack(BRACES));
        ATTRIBUTES.put(IskaraTokenTypes.LBRACK, pack(BRACKETS));
        ATTRIBUTES.put(IskaraTokenTypes.RBRACK, pack(BRACKETS));
        ATTRIBUTES.put(IskaraTokenTypes.HASH_LBRACK, pack(BRACKETS));
        ATTRIBUTES.put(IskaraTokenTypes.LPAREN, pack(PARENTHESES));
        ATTRIBUTES.put(IskaraTokenTypes.RPAREN, pack(PARENTHESES));
        ATTRIBUTES.put(IskaraTokenTypes.HASH_LPAREN, pack(PARENTHESES));
        ATTRIBUTES.put(IskaraTokenTypes.COMMA, pack(COMMA));
        ATTRIBUTES.put(IskaraTokenTypes.DOT, pack(DOT));
        ATTRIBUTES.put(IskaraTokenTypes.TEMPLATE_STRING_DELIM, pack(TEMPLATE_STRING));
        ATTRIBUTES.put(IskaraTokenTypes.TEMPLATE_TEXT, pack(TEMPLATE_STRING));
        ATTRIBUTES.put(IskaraTokenTypes.TEMPLATE_EXPR_START, pack(TEMPLATE_STRING));
    }

    @Override
    public Lexer getHighlightingLexer() {
        return new IskaraLexerAdapter();
    }

    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return ATTRIBUTES.getOrDefault(tokenType, TextAttributesKey.EMPTY_ARRAY);
    }
}
