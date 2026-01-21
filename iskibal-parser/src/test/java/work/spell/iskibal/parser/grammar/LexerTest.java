package work.spell.iskibal.parser.grammar;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import work.spell.iskibal.parser.IskaraLexer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Iskara lexer.
 */
class LexerTest {

    @Test
    void tokenizesKeywords() {
        String input = "module imports facts globals outputs rule template decision table data when then else end where let";
        List<Token> tokens = tokenize(input);

        assertEquals(IskaraLexer.MODULE, tokens.get(0).getType());
        assertEquals(IskaraLexer.IMPORTS, tokens.get(1).getType());
        assertEquals(IskaraLexer.FACTS, tokens.get(2).getType());
        assertEquals(IskaraLexer.GLOBALS, tokens.get(3).getType());
        assertEquals(IskaraLexer.OUTPUTS, tokens.get(4).getType());
        assertEquals(IskaraLexer.RULE, tokens.get(5).getType());
        assertEquals(IskaraLexer.TEMPLATE, tokens.get(6).getType());
        assertEquals(IskaraLexer.DECISION, tokens.get(7).getType());
        assertEquals(IskaraLexer.TABLE, tokens.get(8).getType());
        assertEquals(IskaraLexer.DATA, tokens.get(9).getType());
        assertEquals(IskaraLexer.WHEN, tokens.get(10).getType());
        assertEquals(IskaraLexer.THEN, tokens.get(11).getType());
        assertEquals(IskaraLexer.ELSE, tokens.get(12).getType());
        assertEquals(IskaraLexer.END, tokens.get(13).getType());
        assertEquals(IskaraLexer.WHERE, tokens.get(14).getType());
        assertEquals(IskaraLexer.LET, tokens.get(15).getType());
    }

    @Test
    void tokenizesBooleanLiterals() {
        List<Token> tokens = tokenize("true false");

        assertEquals(IskaraLexer.TRUE, tokens.get(0).getType());
        assertEquals(IskaraLexer.FALSE, tokens.get(1).getType());
    }

    @Test
    void tokenizesNullLiteral() {
        List<Token> tokens = tokenize("null");

        assertEquals(IskaraLexer.NULL, tokens.get(0).getType());
    }

    @Test
    void tokenizesOperators() {
        List<Token> tokens = tokenize(":= = ~= >= <= > < + - * / , . .. ! : @");

        assertEquals(IskaraLexer.ASSIGN, tokens.get(0).getType());
        assertEquals(IskaraLexer.EQUALS, tokens.get(1).getType());
        assertEquals(IskaraLexer.NOT_EQUALS, tokens.get(2).getType());
        assertEquals(IskaraLexer.GREATER_EQ, tokens.get(3).getType());
        assertEquals(IskaraLexer.LESS_EQ, tokens.get(4).getType());
        assertEquals(IskaraLexer.GREATER, tokens.get(5).getType());
        assertEquals(IskaraLexer.LESS, tokens.get(6).getType());
        assertEquals(IskaraLexer.PLUS, tokens.get(7).getType());
        assertEquals(IskaraLexer.MINUS, tokens.get(8).getType());
        assertEquals(IskaraLexer.STAR, tokens.get(9).getType());
        assertEquals(IskaraLexer.SLASH, tokens.get(10).getType());
        assertEquals(IskaraLexer.COMMA, tokens.get(11).getType());
        assertEquals(IskaraLexer.DOT, tokens.get(12).getType());
        assertEquals(IskaraLexer.DOTDOT, tokens.get(13).getType());
        assertEquals(IskaraLexer.BANG, tokens.get(14).getType());
        assertEquals(IskaraLexer.COLON, tokens.get(15).getType());
        assertEquals(IskaraLexer.AT, tokens.get(16).getType());
    }

    @Test
    void tokenizesDelimiters() {
        List<Token> tokens = tokenize("{ } [ ] ( ) |");

        assertEquals(IskaraLexer.LBRACE, tokens.get(0).getType());
        assertEquals(IskaraLexer.RBRACE, tokens.get(1).getType());
        assertEquals(IskaraLexer.LBRACK, tokens.get(2).getType());
        assertEquals(IskaraLexer.RBRACK, tokens.get(3).getType());
        assertEquals(IskaraLexer.LPAREN, tokens.get(4).getType());
        assertEquals(IskaraLexer.RPAREN, tokens.get(5).getType());
        assertEquals(IskaraLexer.PIPE, tokens.get(6).getType());
    }

    @Test
    void tokenizesStrings() {
        List<Token> tokens = tokenize("\"hello world\" 'single quotes'");

        assertEquals(IskaraLexer.STRING, tokens.get(0).getType());
        assertEquals("\"hello world\"", tokens.get(0).getText());
        assertEquals(IskaraLexer.STRING, tokens.get(1).getType());
        assertEquals("'single quotes'", tokens.get(1).getText());
    }

    @Test
    void tokenizesNumbers() {
        List<Token> tokens = tokenize("42 3.14 0 100.50");

        assertEquals(IskaraLexer.NUMBER, tokens.get(0).getType());
        assertEquals("42", tokens.get(0).getText());
        assertEquals(IskaraLexer.NUMBER, tokens.get(1).getType());
        assertEquals("3.14", tokens.get(1).getText());
        assertEquals(IskaraLexer.NUMBER, tokens.get(2).getType());
        assertEquals(IskaraLexer.NUMBER, tokens.get(3).getType());
    }

    @Test
    void tokenizesIdentifiers() {
        List<Token> tokens = tokenize("foo bar123 CamelCase");

        assertEquals(IskaraLexer.IDENTIFIER, tokens.get(0).getType());
        assertEquals("foo", tokens.get(0).getText());
        assertEquals(IskaraLexer.IDENTIFIER, tokens.get(1).getType());
        assertEquals("bar123", tokens.get(1).getText());
        assertEquals(IskaraLexer.IDENTIFIER, tokens.get(2).getType());
        assertEquals("CamelCase", tokens.get(2).getText());
    }

    @Test
    void tokenizesQuotedIdentifiers() {
        List<Token> tokens = tokenize("`has spaces` `Given Name`");

        assertEquals(IskaraLexer.QUOTED_ID, tokens.get(0).getType());
        assertEquals("`has spaces`", tokens.get(0).getText());
        assertEquals(IskaraLexer.QUOTED_ID, tokens.get(1).getType());
        assertEquals("`Given Name`", tokens.get(1).getText());
    }

    @Test
    void skipsLineComments() {
        List<Token> tokens = tokenize("foo // this is a comment\nbar");

        // 3 tokens: foo, NEWLINE, bar (NEWLINE is now visible for statement separation)
        assertEquals(3, tokens.size());
        assertEquals("foo", tokens.get(0).getText());
        assertEquals(IskaraLexer.NEWLINE, tokens.get(1).getType());
        assertEquals("bar", tokens.get(2).getText());
    }

    @Test
    void skipsBlockComments() {
        List<Token> tokens = tokenize("foo /* multi\nline\ncomment */ bar");

        assertEquals(2, tokens.size());
        assertEquals("foo", tokens.get(0).getText());
        assertEquals("bar", tokens.get(1).getText());
    }

    @Test
    void tokenizesTemplateStringStart() {
        IskaraLexer lexer = new IskaraLexer(CharStreams.fromString("$\"hello ${name}\""));
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            tokens.add(token);
        }

        assertEquals(IskaraLexer.TEMPLATE_STRING_START, tokens.get(0).getType());
    }

    private List<Token> tokenize(String input) {
        IskaraLexer lexer = new IskaraLexer(CharStreams.fromString(input));
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
