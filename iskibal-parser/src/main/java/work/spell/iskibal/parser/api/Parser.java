package work.spell.iskibal.parser.api;

import work.spell.iskibal.model.RuleModule;

import java.util.ServiceLoader;
import java.util.Set;

/// Parser interface for parsing Iskara rule sources into a [RuleModule].
///
/// Implementations are loaded via [ServiceLoader]. Use [load()] to
/// obtain a parser instance.
public interface Parser {

    /// Parses the given source code into a [RuleModule].
    ///
    /// @param source
    ///            the source code to parse
    /// @param options
    ///            parsing options
    /// @return the parse result, either success with a RuleModule or failure with
    ///         errors
    ParseResult<RuleModule> parse(String source, ParseOptions options);

    /// Parses the given source code using default options.
    ///
    /// @param source
    ///            the source code to parse
    /// @return the parse result
    default ParseResult<RuleModule> parse(String source) {
        return parse(source, ParseOptions.defaults());
    }

    /// Returns the set of source types this parser supports.
    Set<SourceType> supportedSourceTypes();

    /// Loads a parser using the ServiceLoader mechanism.
    ///
    /// @return a parser instance
    /// @throws IllegalStateException
    ///             if no parser implementation is found
    static Parser load() {
        return ServiceLoader.load(Parser.class).findFirst().orElseThrow(() -> new IllegalStateException(
                "No Parser implementation found. Ensure iskibal-parser is on the module path."));
    }
}
