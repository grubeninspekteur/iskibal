package work.spell.iskibal.asciidoc;

import module java.base;

import org.asciidoctor.ast.Document;

import module iskibal.parser;
import module iskibal.rule.model;

/// Builds a [RuleModule] from an AsciidoctorJ [Document].
///
/// Translates the AsciiDoc document to Iskara source text, then parses it
/// with the Iskara parser.
public class AsciiDocRuleModuleBuilder {

    private final IskaraSourceTranslator translator;
    private final Parser iskaraParser;
    private final Locale locale;

    /// Creates an AsciiDocRuleModuleBuilder with the default locale.
    public AsciiDocRuleModuleBuilder() {
        this(Locale.getDefault());
    }

    /// Creates an AsciiDocRuleModuleBuilder with the specified locale.
    ///
    /// @param locale
    ///            the locale for number parsing
    public AsciiDocRuleModuleBuilder(Locale locale) {
        this(Parser.load(), locale);
    }

    /// Creates an AsciiDocRuleModuleBuilder with the specified parser and
    /// locale.
    ///
    /// @param parser
    ///            the parser to use
    /// @param locale
    ///            the locale for number parsing
    public AsciiDocRuleModuleBuilder(Parser parser, Locale locale) {
        this.locale = locale;
        this.iskaraParser = parser;
        this.translator = new IskaraSourceTranslator();
    }

    /// Result of building a [RuleModule] from an AsciiDoc document.
    ///
    /// @param module
    ///            the built RuleModule
    /// @param diagnostics
    ///            any warnings or errors encountered during building
    public record BuildResult(RuleModule module, java.util.List<Diagnostic> diagnostics) {
        /// Returns true if there are any errors.
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }

    /// Builds a RuleModule from an AsciiDoc document.
    ///
    /// @param document
    ///            the parsed AsciiDoc document
    /// @return the build result containing the module and any diagnostics
    public BuildResult build(Document document) {
        // Step 1: Translate AsciiDoc DOM to Iskara source text
        IskaraSourceTranslator.TranslationResult translation = translator.translate(document);

        if (translation.hasErrors()) {
            return new BuildResult(null, translation.diagnostics());
        }

        String iskaraSource = translation.source();
        if (iskaraSource.isBlank()) {
            // Empty document — return empty module
            RuleModule emptyModule = new RuleModule.Default(
                    java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.List.of());
            return new BuildResult(emptyModule, translation.diagnostics());
        }

        // Step 2: Parse the Iskara source
        ParseResult<RuleModule> parseResult = iskaraParser.parse(iskaraSource, parseOptions());

        // Step 3: Merge diagnostics
        java.util.List<Diagnostic> allDiagnostics = new ArrayList<>();
        allDiagnostics.addAll(translation.diagnostics());
        allDiagnostics.addAll(parseResult.getDiagnostics());

        if (parseResult.isSuccess() && parseResult.getValue().isPresent()) {
            return new BuildResult(parseResult.getValue().get(), allDiagnostics);
        }

        return new BuildResult(null, allDiagnostics);
    }

    private ParseOptions parseOptions() {
        return new ParseOptions(locale, SourceType.ISKARA, "<asciidoc>");
    }
}
