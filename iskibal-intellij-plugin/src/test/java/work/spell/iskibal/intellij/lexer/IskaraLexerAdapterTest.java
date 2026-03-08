package work.spell.iskibal.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;
import work.spell.iskibal.intellij.IskaraTokenTypes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IskaraLexerAdapterTest {

    @Test
    void tokenizesSimpleRule() {
        var lexer = new IskaraLexerAdapter();
        lexer.start("rule VIP\nwhen\n    x >= 1\nthen\n    y := 2\nend", 0, 44, 0);

        var types = collectTokenTypes(lexer);

        assertTrue(types.contains(IskaraTokenTypes.KEYWORD), "should contain KEYWORD tokens");
        assertTrue(types.contains(IskaraTokenTypes.IDENTIFIER), "should contain IDENTIFIER tokens");
        assertTrue(types.contains(IskaraTokenTypes.NUMBER), "should contain NUMBER tokens");
        assertTrue(types.contains(IskaraTokenTypes.OPERATOR), "should contain OPERATOR tokens");
    }

    @Test
    void tokenizesComments() {
        var lexer = new IskaraLexerAdapter();
        var source = "// line comment\n/* block */";
        lexer.start(source, 0, source.length(), 0);

        var types = collectTokenTypes(lexer);

        assertTrue(types.contains(IskaraTokenTypes.LINE_COMMENT), "should contain line comment");
        assertTrue(types.contains(IskaraTokenTypes.BLOCK_COMMENT), "should contain block comment");
    }

    @Test
    void tokenizesWhitespace() {
        var lexer = new IskaraLexerAdapter();
        var source = "rule  VIP";
        lexer.start(source, 0, source.length(), 0);

        var types = collectTokenTypes(lexer);

        assertTrue(types.contains(IskaraTokenTypes.WHITE_SPACE), "should preserve whitespace tokens");
    }

    @Test
    void coversEntireInput() {
        var lexer = new IskaraLexerAdapter();
        var source = "facts {\n    x: Integer\n}";
        lexer.start(source, 0, source.length(), 0);

        int lastEnd = 0;
        while (lexer.getTokenType() != null) {
            assertEquals(lastEnd, lexer.getTokenStart(),
                    "token start should match previous token end (no gaps)");
            lastEnd = lexer.getTokenEnd();
            lexer.advance();
        }
        assertEquals(source.length(), lastEnd, "tokens should cover entire input");
    }

    @Test
    void tokenizesStringLiterals() {
        var lexer = new IskaraLexerAdapter();
        var source = "tier := \"vip\"";
        lexer.start(source, 0, source.length(), 0);

        var types = collectTokenTypes(lexer);

        assertTrue(types.contains(IskaraTokenTypes.STRING), "should contain STRING token");
        assertTrue(types.contains(IskaraTokenTypes.OPERATOR), "should contain OPERATOR for :=");
    }

    @Test
    void tokenizesQuotedIdentifier() {
        var lexer = new IskaraLexerAdapter();
        var source = "`is active`";
        lexer.start(source, 0, source.length(), 0);

        var types = collectTokenTypes(lexer);

        assertTrue(types.contains(IskaraTokenTypes.QUOTED_IDENTIFIER));
    }

    @Test
    void badCharactersProduceBadCharacterTokens() {
        var lexer = new IskaraLexerAdapter();
        var source = "rule § VIP";
        lexer.start(source, 0, source.length(), 0);

        var types = collectTokenTypes(lexer);

        assertTrue(types.contains(IskaraTokenTypes.BAD_CHARACTER),
                "unrecognized characters should produce BAD_CHARACTER tokens");
    }

    @Test
    void badCharactersCoverEntireInput() {
        var lexer = new IskaraLexerAdapter();
        var source = "x § y";
        lexer.start(source, 0, source.length(), 0);

        int lastEnd = 0;
        while (lexer.getTokenType() != null) {
            assertEquals(lastEnd, lexer.getTokenStart(),
                    "no gaps even with bad characters");
            lastEnd = lexer.getTokenEnd();
            lexer.advance();
        }
        assertEquals(source.length(), lastEnd, "tokens should cover entire input");
    }

    private List<IElementType> collectTokenTypes(IskaraLexerAdapter lexer) {
        var types = new ArrayList<IElementType>();
        while (lexer.getTokenType() != null) {
            types.add(lexer.getTokenType());
            lexer.advance();
        }
        return types;
    }
}
