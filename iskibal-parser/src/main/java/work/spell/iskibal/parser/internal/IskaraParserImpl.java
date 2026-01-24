package work.spell.iskibal.parser.internal;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.IskaraLexer;
import work.spell.iskibal.parser.IskaraParser;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.Parser;
import work.spell.iskibal.parser.api.SourceType;
import work.spell.iskibal.parser.asciidoc.AsciiDocExtractor;
import work.spell.iskibal.parser.diagnostic.Diagnostic;
import work.spell.iskibal.parser.diagnostic.IskaraDiagnosticListener;
import work.spell.iskibal.parser.visitor.ExpressionVisitor;
import work.spell.iskibal.parser.visitor.RuleModuleVisitor;

import java.util.List;
import java.util.Set;

/**
 * Implementation of the Iskara parser using ANTLR4.
 */
public class IskaraParserImpl implements Parser {

	private static final Set<SourceType> SUPPORTED_SOURCE_TYPES = Set.of(SourceType.ISKARA, SourceType.ASCIIDOC);

	@Override
	public ParseResult<RuleModule> parse(String source, ParseOptions options) {
		// Handle AsciiDoc extraction if needed
		String iskaraSource = source;
		if (options.sourceType() == SourceType.ASCIIDOC) {
			AsciiDocExtractor extractor = new AsciiDocExtractor();
			iskaraSource = extractor.extractIskara(source);
		}

		return parseIskara(iskaraSource, options);
	}

	private ParseResult<RuleModule> parseIskara(String source, ParseOptions options) {
		// Create diagnostic listener
		IskaraDiagnosticListener diagnosticListener = new IskaraDiagnosticListener(options.sourceName());

		// Create lexer
		IskaraLexer lexer = new IskaraLexer(CharStreams.fromString(source));
		lexer.removeErrorListeners();
		lexer.addErrorListener(diagnosticListener);

		// Create token stream
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		// Create parser
		IskaraParser parser = new IskaraParser(tokens);
		parser.removeErrorListeners();
		parser.addErrorListener(diagnosticListener);

		// Parse the source
		IskaraParser.RuleModuleContext parseTree;
		try {
			parseTree = parser.ruleModule();
		} catch (Exception e) {
			diagnosticListener.addError("Parse error: " + e.getMessage(), 1, 1);
			return new ParseResult.Failure<>(diagnosticListener.getDiagnostics());
		}

		// Check for syntax errors
		if (diagnosticListener.hasErrors()) {
			return new ParseResult.Failure<>(diagnosticListener.getDiagnostics());
		}

		// Build AST using visitors
		try {
			ExpressionVisitor expressionVisitor = new ExpressionVisitor(diagnosticListener, options.locale());
			RuleModuleVisitor ruleModuleVisitor = new RuleModuleVisitor(expressionVisitor, diagnosticListener);
			RuleModule ruleModule = ruleModuleVisitor.visit(parseTree);

			// Return result with any warnings
			List<Diagnostic> warnings = diagnosticListener.getDiagnostics().stream()
					.filter(d -> d.severity() != Diagnostic.Severity.ERROR).toList();

			return new ParseResult.Success<>(ruleModule, warnings);
		} catch (Exception e) {
			diagnosticListener.addError("AST construction error: " + e.getMessage(), 1, 1);
			return new ParseResult.Failure<>(diagnosticListener.getDiagnostics());
		}
	}

	@Override
	public Set<SourceType> supportedSourceTypes() {
		return SUPPORTED_SOURCE_TYPES;
	}
}
