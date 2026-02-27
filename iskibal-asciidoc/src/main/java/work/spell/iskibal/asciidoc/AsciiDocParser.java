package work.spell.iskibal.asciidoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.RuleModuleMerger;
import work.spell.iskibal.parser.diagnostic.Diagnostic;
import work.spell.iskibal.parser.diagnostic.SourceLocation;

/// Main entry point for parsing AsciiDoc documents containing Iskara rules.
///
/// This parser uses AsciidoctorJ to parse AsciiDoc documents and extract Iskara
/// rules from annotated blocks. It handles:
/// - Include directives (via AsciidoctorJ)
/// - Rule blocks ([source,iskara,.rule])
/// - Import definition lists ([.imports])
/// - Fact/Global/Output tables
/// - Data tables
/// - Decision tables with aliases
///
///
/// Example usage:
///
/// ```
/// AsciiDocParser parser = new AsciiDocParser();
/// ParseResult result = parser.parseFile(Path.of("rules.adoc"));
/// if (result.isSuccess()) {
///     RuleModule module = result.module();
///     // process module...
/// }
/// ```
public class AsciiDocParser implements AutoCloseable {

    private final Asciidoctor asciidoctor;
    private final AsciiDocRuleModuleBuilder builder;
    private final RuleModuleMerger merger;
    private final Locale locale;

    /// Creates a new AsciiDocParser with the default locale.
    public AsciiDocParser() {
        this(Locale.getDefault());
    }

    /// Creates a new AsciiDocParser with the specified locale.
    ///
    /// @param locale
    ///            the locale for number parsing
    public AsciiDocParser(Locale locale) {
        this(work.spell.iskibal.parser.api.Parser.load(), locale);
    }

    /// Creates a new AsciiDocParser with the specified parser and locale.
    ///
    /// @param parser
    ///            the parser to use
    /// @param locale
    ///            the locale for number parsing
    public AsciiDocParser(work.spell.iskibal.parser.api.Parser parser, Locale locale) {
        this.locale = locale;
        this.asciidoctor = Asciidoctor.Factory.create();
        this.builder = new AsciiDocRuleModuleBuilder(parser, locale);
        this.merger = new RuleModuleMerger();
    }

    /// Parses an AsciiDoc document from a string.
    ///
    /// @param content
    ///            the AsciiDoc content
    /// @return the parse result
    public ParseResult parse(String content) {
        return parse(content, Options.builder().build());
    }

    /// Parses an AsciiDoc document from a string with custom options.
    ///
    /// @param content
    ///            the AsciiDoc content
    /// @param options
    ///            AsciidoctorJ options
    /// @return the parse result
    public ParseResult parse(String content, Options options) {
        try {
            var document = asciidoctor.load(content, options);
            RuleModule module = builder.build(document);
            return new ParseResult(module, java.util.List.of());
        } catch (Exception e) {
            return new ParseResult(null,
                    java.util.List.of(Diagnostic.error(e.getMessage(), SourceLocation.at("<string>", 0, 0))));
        }
    }

    /// Parses an AsciiDoc document from a file.
    ///
    /// The file's directory is automatically set as the base directory for
    /// resolving include directives.
    ///
    /// @param file
    ///            the path to the AsciiDoc file
    /// @return the parse result
    public ParseResult parseFile(Path file) {
        try {
            String content = Files.readString(file);
            Options options = Options.builder()
                    .baseDir(file.getParent() != null ? file.getParent().toFile() : null)
                    .safe(SafeMode.UNSAFE) // Allow includes
                    .build();
            return parse(content, options);
        } catch (IOException e) {
            return new ParseResult(null,
                    java.util.List.of(Diagnostic.error("Failed to read file: " + e.getMessage(),
                            SourceLocation.at(file.toString(), 0, 0))));
        }
    }

    /// Parses multiple AsciiDoc files and merges them into a single RuleModule.
    ///
    /// @param files
    ///            the paths to the AsciiDoc files
    /// @return the parse result with merged module
    public ParseResult parseFiles(java.util.List<Path> files) {
        java.util.List<RuleModule> modules = new ArrayList<>();
        java.util.List<Diagnostic> allDiagnostics = new ArrayList<>();

        for (Path file : files) {
            ParseResult result = parseFile(file);
            if (result.isSuccess()) {
                modules.add(result.module());
            }
            allDiagnostics.addAll(result.diagnostics());
        }

        if (modules.isEmpty()) {
            return new ParseResult(null, allDiagnostics);
        }

        try {
            RuleModule merged = merger.merge(modules);
            return new ParseResult(merged, allDiagnostics);
        } catch (IllegalArgumentException e) {
            allDiagnostics.add(Diagnostic.error("Merge conflict: " + e.getMessage(),
                    SourceLocation.at("<merged>", 0, 0)));
            return new ParseResult(null, allDiagnostics);
        }
    }

    /// Returns the underlying Asciidoctor instance.
    ///
    /// This can be used to register extensions or configure additional options.
    ///
    /// @return the Asciidoctor instance
    public Asciidoctor getAsciidoctor() {
        return asciidoctor;
    }

    @Override
    public void close() {
        asciidoctor.close();
    }

    /// Result of parsing an AsciiDoc document.
    ///
    /// @param module
    ///            the parsed RuleModule, or null if parsing failed
    /// @param diagnostics
    ///            any warnings or errors encountered during parsing
    public record ParseResult(RuleModule module, java.util.List<Diagnostic> diagnostics) {
        /// Returns true if parsing was successful and a module was produced.
        public boolean isSuccess() {
            return module != null;
        }

        /// Returns true if there are any errors in the diagnostics.
        public boolean hasErrors() {
            return diagnostics.stream()
                    .anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }
}
