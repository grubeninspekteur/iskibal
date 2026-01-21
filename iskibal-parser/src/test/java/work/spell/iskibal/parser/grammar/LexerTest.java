package work.spell.iskibal.parser.grammar;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import work.spell.iskibal.parser.IskaraLexer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests for the Iskara lexer.
 */
class LexerTest {

    @Nested
    class Keywords {
        @Test
        void tokenizesAllKeywords() {
            var tokens = tokenize("module imports facts globals outputs rule template decision table data when then else end where let");

            assertThat(tokens)
                    .extracting(Token::getType)
                    .containsExactly(
                            IskaraLexer.MODULE,
                            IskaraLexer.IMPORTS,
                            IskaraLexer.FACTS,
                            IskaraLexer.GLOBALS,
                            IskaraLexer.OUTPUTS,
                            IskaraLexer.RULE,
                            IskaraLexer.TEMPLATE,
                            IskaraLexer.DECISION,
                            IskaraLexer.TABLE,
                            IskaraLexer.DATA,
                            IskaraLexer.WHEN,
                            IskaraLexer.THEN,
                            IskaraLexer.ELSE,
                            IskaraLexer.END,
                            IskaraLexer.WHERE,
                            IskaraLexer.LET
                    );
        }

        @Test
        void tokenizesBooleanLiterals() {
            var tokens = tokenize("true false");

            assertThat(tokens)
                    .extracting(Token::getType)
                    .containsExactly(IskaraLexer.TRUE, IskaraLexer.FALSE);
        }

        @Test
        void tokenizesNullLiteral() {
            var tokens = tokenize("null");

            assertThat(tokens)
                    .extracting(Token::getType)
                    .containsExactly(IskaraLexer.NULL);
        }
    }

    @Nested
    class Operators {
        @Test
        void tokenizesAllOperators() {
            var tokens = tokenize(":= = ~= >= <= > < + - * / , . .. ! : @");

            assertThat(tokens)
                    .extracting(Token::getType)
                    .containsExactly(
                            IskaraLexer.ASSIGN,
                            IskaraLexer.EQUALS,
                            IskaraLexer.NOT_EQUALS,
                            IskaraLexer.GREATER_EQ,
                            IskaraLexer.LESS_EQ,
                            IskaraLexer.GREATER,
                            IskaraLexer.LESS,
                            IskaraLexer.PLUS,
                            IskaraLexer.MINUS,
                            IskaraLexer.STAR,
                            IskaraLexer.SLASH,
                            IskaraLexer.COMMA,
                            IskaraLexer.DOT,
                            IskaraLexer.DOTDOT,
                            IskaraLexer.BANG,
                            IskaraLexer.COLON,
                            IskaraLexer.AT
                    );
        }

        @Test
        void tokenizesDelimiters() {
            var tokens = tokenize("{ } [ ] ( ) |");

            assertThat(tokens)
                    .extracting(Token::getType)
                    .containsExactly(
                            IskaraLexer.LBRACE,
                            IskaraLexer.RBRACE,
                            IskaraLexer.LBRACK,
                            IskaraLexer.RBRACK,
                            IskaraLexer.LPAREN,
                            IskaraLexer.RPAREN,
                            IskaraLexer.PIPE
                    );
        }
    }

    @Nested
    class Literals {
        @Test
        void tokenizesDoubleQuotedString() {
            var tokens = tokenize("\"hello world\"");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.STRING, "\"hello world\""));
        }

        @Test
        void tokenizesSingleQuotedString() {
            var tokens = tokenize("'single quotes'");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.STRING, "'single quotes'"));
        }

        @Test
        void tokenizesIntegerNumber() {
            var tokens = tokenize("42");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.NUMBER, "42"));
        }

        @Test
        void tokenizesDecimalNumber() {
            var tokens = tokenize("3.14");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.NUMBER, "3.14"));
        }

        @Test
        void tokenizesMultipleNumbers() {
            var tokens = tokenize("42 3.14 0 100.50");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(
                            tuple(IskaraLexer.NUMBER, "42"),
                            tuple(IskaraLexer.NUMBER, "3.14"),
                            tuple(IskaraLexer.NUMBER, "0"),
                            tuple(IskaraLexer.NUMBER, "100.50")
                    );
        }
    }

    @Nested
    class Identifiers {
        @Test
        void tokenizesSimpleIdentifier() {
            var tokens = tokenize("foo");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.IDENTIFIER, "foo"));
        }

        @Test
        void tokenizesIdentifierWithNumbers() {
            var tokens = tokenize("bar123");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.IDENTIFIER, "bar123"));
        }

        @Test
        void tokenizesCamelCaseIdentifier() {
            var tokens = tokenize("CamelCase");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.IDENTIFIER, "CamelCase"));
        }

        @Test
        void tokenizesQuotedIdentifierWithSpaces() {
            var tokens = tokenize("`has spaces`");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(tuple(IskaraLexer.QUOTED_ID, "`has spaces`"));
        }

        @Test
        void tokenizesMultipleQuotedIdentifiers() {
            var tokens = tokenize("`Given Name` `Family Name`");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(
                            tuple(IskaraLexer.QUOTED_ID, "`Given Name`"),
                            tuple(IskaraLexer.QUOTED_ID, "`Family Name`")
                    );
        }
    }

    @Nested
    class Comments {
        @Test
        void skipsLineCommentAndPreservesNewline() {
            var tokens = tokenize("foo // this is a comment\nbar");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(
                            tuple(IskaraLexer.IDENTIFIER, "foo"),
                            tuple(IskaraLexer.NEWLINE, "\n"),
                            tuple(IskaraLexer.IDENTIFIER, "bar")
                    );
        }

        @Test
        void skipsBlockComment() {
            var tokens = tokenize("foo /* multi\nline\ncomment */ bar");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(
                            tuple(IskaraLexer.IDENTIFIER, "foo"),
                            tuple(IskaraLexer.IDENTIFIER, "bar")
                    );
        }

        @Test
        void skipsEmptyBlockComment() {
            var tokens = tokenize("foo /**/ bar");

            assertThat(tokens)
                    .extracting(Token::getType, Token::getText)
                    .containsExactly(
                            tuple(IskaraLexer.IDENTIFIER, "foo"),
                            tuple(IskaraLexer.IDENTIFIER, "bar")
                    );
        }
    }

    @Nested
    class TemplateStrings {
        @Test
        void tokenizesTemplateStringStart() {
            var tokens = tokenizeAll("$\"hello ${name}\"");

            assertThat(tokens.get(0).getType()).isEqualTo(IskaraLexer.TEMPLATE_STRING_START);
            assertThat(tokens.get(0).getText()).isEqualTo("$\"");
        }

        @Test
        void tokenizesCompleteTemplateString() {
            var tokens = tokenizeAll("$\"hello ${name}!\"");

            assertThat(tokens)
                    .extracting(Token::getType)
                    .startsWith(
                            IskaraLexer.TEMPLATE_STRING_START,
                            IskaraLexer.TEMPLATE_TEXT,
                            IskaraLexer.TEMPLATE_EXPR_START,
                            IskaraLexer.IDENTIFIER,
                            IskaraLexer.RBRACE
                    );
        }
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

    private List<Token> tokenizeAll(String input) {
        IskaraLexer lexer = new IskaraLexer(CharStreams.fromString(input));
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            tokens.add(token);
        }
        return tokens;
    }
}
