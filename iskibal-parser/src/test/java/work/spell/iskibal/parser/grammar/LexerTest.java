package work.spell.iskibal.parser.grammar;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import work.spell.iskibal.parser.IskaraLexer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Iskara lexer.
 */
class LexerTest {

    @Test
    void tokenizesKeywords() {
        String input = "module imports facts globals outputs rule template decision table data when then else end where let";
        List<Token> tokens = tokenize(input);

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.MODULE);
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.IMPORTS);
        assertThat(tokens.get(2).getType()).isEqualTo(IskaraLexer.FACTS);
        assertThat(tokens.get(3).getType()).isEqualTo(IskaraLexer.GLOBALS);
        assertThat(tokens.get(4).getType()).isEqualTo(IskaraLexer.OUTPUTS);
        assertThat(tokens.get(5).getType()).isEqualTo(IskaraLexer.RULE);
        assertThat(tokens.get(6).getType()).isEqualTo(IskaraLexer.TEMPLATE);
        assertThat(tokens.get(7).getType()).isEqualTo(IskaraLexer.DECISION);
        assertThat(tokens.get(8).getType()).isEqualTo(IskaraLexer.TABLE);
        assertThat(tokens.get(9).getType()).isEqualTo(IskaraLexer.DATA);
        assertThat(tokens.get(10).getType()).isEqualTo(IskaraLexer.WHEN);
        assertThat(tokens.get(11).getType()).isEqualTo(IskaraLexer.THEN);
        assertThat(tokens.get(12).getType()).isEqualTo(IskaraLexer.ELSE);
        assertThat(tokens.get(13).getType()).isEqualTo(IskaraLexer.END);
        assertThat(tokens.get(14).getType()).isEqualTo(IskaraLexer.WHERE);
        assertThat(tokens.get(15).getType()).isEqualTo(IskaraLexer.LET);
    }

    @Test
    void tokenizesBooleanLiterals() {
        List<Token> tokens = tokenize("true false");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.TRUE);
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.FALSE);
    }

    @Test
    void tokenizesNullLiteral() {
        List<Token> tokens = tokenize("null");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.NULL);
    }

    @Test
    void tokenizesOperators() {
        List<Token> tokens = tokenize(":= = ~= >= <= > < + - * / , . .. ! : @");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.ASSIGN);
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.EQUALS);
        assertThat(tokens.get(2).getType()).isEqualTo(IskaraLexer.NOT_EQUALS);
        assertThat(tokens.get(3).getType()).isEqualTo(IskaraLexer.GREATER_EQ);
        assertThat(tokens.get(4).getType()).isEqualTo(IskaraLexer.LESS_EQ);
        assertThat(tokens.get(5).getType()).isEqualTo(IskaraLexer.GREATER);
        assertThat(tokens.get(6).getType()).isEqualTo(IskaraLexer.LESS);
        assertThat(tokens.get(7).getType()).isEqualTo(IskaraLexer.PLUS);
        assertThat(tokens.get(8).getType()).isEqualTo(IskaraLexer.MINUS);
        assertThat(tokens.get(9).getType()).isEqualTo(IskaraLexer.STAR);
        assertThat(tokens.get(10).getType()).isEqualTo(IskaraLexer.SLASH);
        assertThat(tokens.get(11).getType()).isEqualTo(IskaraLexer.COMMA);
        assertThat(tokens.get(12).getType()).isEqualTo(IskaraLexer.DOT);
        assertThat(tokens.get(13).getType()).isEqualTo(IskaraLexer.DOTDOT);
        assertThat(tokens.get(14).getType()).isEqualTo(IskaraLexer.BANG);
        assertThat(tokens.get(15).getType()).isEqualTo(IskaraLexer.COLON);
        assertThat(tokens.get(16).getType()).isEqualTo(IskaraLexer.AT);
    }

    @Test
    void tokenizesDelimiters() {
        List<Token> tokens = tokenize("{ } [ ] ( ) |");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.LBRACE);
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.RBRACE);
        assertThat(tokens.get(2).getType()).isEqualTo(IskaraLexer.LBRACK);
        assertThat(tokens.get(3).getType()).isEqualTo(IskaraLexer.RBRACK);
        assertThat(tokens.get(4).getType()).isEqualTo(IskaraLexer.LPAREN);
        assertThat(tokens.get(5).getType()).isEqualTo(IskaraLexer.RPAREN);
        assertThat(tokens.get(6).getType()).isEqualTo(IskaraLexer.PIPE);
    }

    @Test
    void tokenizesStrings() {
        List<Token> tokens = tokenize("\"hello world\" 'single quotes'");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.STRING);
        assertThat(tokens.get(0).getText()).isEqualTo("\"hello world\"");
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.STRING);
        assertThat(tokens.get(1).getText()).isEqualTo("'single quotes'");
    }

    @Test
    void tokenizesNumbers() {
        List<Token> tokens = tokenize("42 3.14 0 100.50");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.NUMBER);
        assertThat(tokens.get(0).getText()).isEqualTo("42");
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.NUMBER);
        assertThat(tokens.get(1).getText()).isEqualTo("3.14");
        assertThat(tokens.get(2).getType()).isEqualTo(IskaraLexer.NUMBER);
        assertThat(tokens.get(3).getType()).isEqualTo(IskaraLexer.NUMBER);
    }

    @Test
    void tokenizesIdentifiers() {
        List<Token> tokens = tokenize("foo bar123 CamelCase");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.IDENTIFIER);
        assertThat(tokens.get(0).getText()).isEqualTo("foo");
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.IDENTIFIER);
        assertThat(tokens.get(1).getText()).isEqualTo("bar123");
        assertThat(tokens.get(2).getType()).isEqualTo(IskaraLexer.IDENTIFIER);
        assertThat(tokens.get(2).getText()).isEqualTo("CamelCase");
    }

    @Test
    void tokenizesQuotedIdentifiers() {
        List<Token> tokens = tokenize("`has spaces` `Given Name`");

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.QUOTED_ID);
        assertThat(tokens.get(0).getText()).isEqualTo("`has spaces`");
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.QUOTED_ID);
        assertThat(tokens.get(1).getText()).isEqualTo("`Given Name`");
    }

    @Test
    void skipsLineComments() {
        List<Token> tokens = tokenize("foo // this is a comment\nbar");

        // 3 tokens: foo, NEWLINE, bar (NEWLINE is now visible for statement separation)
        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0).getText()).isEqualTo("foo");
        assertThat(tokens.get(1).getType()).isEqualTo(IskaraLexer.NEWLINE);
        assertThat(tokens.get(2).getText()).isEqualTo("bar");
    }

    @Test
    void skipsBlockComments() {
        List<Token> tokens = tokenize("foo /* multi\nline\ncomment */ bar");

        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0).getText()).isEqualTo("foo");
        assertThat(tokens.get(1).getText()).isEqualTo("bar");
    }

    @Test
    void tokenizesTemplateStringStart() {
        IskaraLexer lexer = new IskaraLexer(CharStreams.fromString("$\"hello ${name}\""));
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            tokens.add(token);
        }

        assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.TEMPLATE_STRING_START);
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
