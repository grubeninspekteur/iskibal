package work.spell.iskibal.asciidoc;

import java.util.List;
import java.util.Locale;

import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Statement;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.Parser;
import work.spell.iskibal.parser.api.SourceType;

/// Helper for parsing Iskara expressions and statements from strings.
///
/// This wraps the ANTLR-based parser to provide convenient methods for parsing
/// individual expressions, statements, and blocks without requiring full module
/// syntax.
public class ExpressionParser {

    private final Parser parser;
    private final Locale locale;

    /// Creates an ExpressionParser with the default locale.
    public ExpressionParser() {
        this(Locale.getDefault());
    }

    /// Creates an ExpressionParser with the specified locale.
    ///
    /// @param locale
    ///            the locale for number parsing
    public ExpressionParser(Locale locale) {
        this(Parser.load(), locale);
    }

    /// Creates an ExpressionParser with the specified parser and locale.
    ///
    /// @param parser
    ///            the parser to use
    /// @param locale
    ///            the locale for number parsing
    public ExpressionParser(Parser parser, Locale locale) {
        this.parser = parser;
        this.locale = locale;
    }

    /// Parses a single expression from a string.
    ///
    /// @param source
    ///            the expression source
    /// @return the parsed Expression, or null if parsing fails
    public Expression parseExpression(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        // Wrap expression in a minimal rule structure to parse it
        String wrapped = wrapInRule(source.trim());
        ParseResult<RuleModule> result = parser.parse(wrapped, parseOptions());
        if (result.isSuccess() && !result.getValue().get().rules().isEmpty()) {
            var rule = result.getValue().get().rules().getFirst();
            if (rule instanceof work.spell.iskibal.model.Rule.SimpleRule simple && !simple.when().isEmpty()) {
                Statement stmt = simple.when().getFirst();
                if (stmt instanceof Statement.ExpressionStatement expr) {
                    return expr.expression();
                }
            }
        }
        return null;
    }

    /// Parses a list of statements from a string.
    ///
    /// @param source
    ///            the statements source (newline or comma separated)
    /// @return the parsed statements, or empty list if parsing fails
    public List<Statement> parseStatements(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        String wrapped = wrapInRule(source.trim());
        ParseResult<RuleModule> result = parser.parse(wrapped, parseOptions());
        if (result.isSuccess() && !result.getValue().get().rules().isEmpty()) {
            var rule = result.getValue().get().rules().getFirst();
            if (rule instanceof work.spell.iskibal.model.Rule.SimpleRule simple) {
                return simple.when();
            }
        }
        return List.of();
    }

    /// Parses a block expression from a string.
    ///
    /// The string should include the brackets, e.g., "[x > 0]" or "[:param |
    /// param * 2]".
    ///
    /// @param source
    ///            the block source including brackets
    /// @return the parsed Block, or null if parsing fails
    public Expression.Block parseBlock(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        Expression expr = parseExpression(source.trim());
        if (expr instanceof Expression.Block block) {
            return block;
        }
        return null;
    }

    /// Attempts to parse the given source as an expression.
    ///
    /// @param source
    ///            the source to parse
    /// @return a ParseResult with the Expression or errors
    public ParseResult<Expression> tryParseExpression(String source) {
        if (source == null || source.isBlank()) {
            return new ParseResult.Success<>(null);
        }
        String wrapped = wrapInRule(source.trim());
        ParseResult<RuleModule> result = parser.parse(wrapped, parseOptions());
        return result.map(module -> {
            if (!module.rules().isEmpty()) {
                var rule = module.rules().getFirst();
                if (rule instanceof work.spell.iskibal.model.Rule.SimpleRule simple && !simple.when().isEmpty()) {
                    Statement stmt = simple.when().getFirst();
                    if (stmt instanceof Statement.ExpressionStatement expr) {
                        return expr.expression();
                    }
                }
            }
            return null;
        });
    }

    private String wrapInRule(String source) {
        return """
                rule exprWrapper
                when:
                    %s
                then:
                end
                """.formatted(source);
    }

    /// Parses a full module from source.
    ///
    /// @param source
    ///            the source to parse
    /// @return the parse result
    public ParseResult<RuleModule> parseModule(String source) {
        return parser.parse(source, parseOptions());
    }

    private ParseOptions parseOptions() {
        return new ParseOptions(locale, SourceType.ISKARA, "<expression>");
    }
}
