package work.spell.iskibal.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import work.spell.iskibal.compiler.common.api.AnalysisResult;
import work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
import work.spell.iskibal.compiler.common.internal.SemanticAnalyzerImpl;
import work.spell.iskibal.compiler.java.api.CompilationResult;
import work.spell.iskibal.compiler.java.api.JavaCompiler;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.compiler.java.internal.JavaCompilerImpl;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.Parser;
import work.spell.iskibal.parser.internal.IskaraParserImpl;

/**
 * Fluent builder for end-to-end rule tests.
 * <p>
 * Orchestrates the full pipeline: Parse → Analyze → Generate → Compile →
 * Instantiate
 *
 * <pre>
 * var result = RuleTestBuilder.forSource(source).withPackage("test.rules").withFact(item).build();
 * </pre>
 */
public class RuleTestBuilder {

	private final String source;
	private String packageName = "";
	private String className = "GeneratedRules";
	private final List<Object> facts = new ArrayList<>();
	private final List<Object> globals = new ArrayList<>();

	private RuleTestBuilder(String source) {
		this.source = source;
	}

	/**
	 * Creates a new builder for the given Iskara source code.
	 *
	 * @param source
	 *            the Iskara rule source code
	 * @return a new builder
	 */
	public static RuleTestBuilder forSource(String source) {
		return new RuleTestBuilder(source);
	}

	/**
	 * Sets the package name for generated code.
	 *
	 * @param packageName
	 *            the package name
	 * @return this builder
	 */
	public RuleTestBuilder withPackage(String packageName) {
		this.packageName = packageName;
		return this;
	}

	/**
	 * Sets the class name for generated code.
	 *
	 * @param className
	 *            the class name
	 * @return this builder
	 */
	public RuleTestBuilder withClassName(String className) {
		this.className = className;
		return this;
	}

	/**
	 * Adds a fact to be passed to the generated rules constructor.
	 *
	 * @param fact
	 *            the fact instance
	 * @return this builder
	 */
	public RuleTestBuilder withFact(Object fact) {
		this.facts.add(fact);
		return this;
	}

	/**
	 * Adds multiple facts to be passed to the generated rules constructor.
	 *
	 * @param facts
	 *            the fact instances
	 * @return this builder
	 */
	public RuleTestBuilder withFacts(Object... facts) {
		for (Object fact : facts) {
			this.facts.add(fact);
		}
		return this;
	}

	/**
	 * Adds a global to be passed to the generated rules constructor.
	 *
	 * @param global
	 *            the global instance
	 * @return this builder
	 */
	public RuleTestBuilder withGlobal(Object global) {
		this.globals.add(global);
		return this;
	}

	/**
	 * Adds multiple globals to be passed to the generated rules constructor.
	 *
	 * @param globals
	 *            the global instances
	 * @return this builder
	 */
	public RuleTestBuilder withGlobals(Object... globals) {
		for (Object global : globals) {
			this.globals.add(global);
		}
		return this;
	}

	/**
	 * Compiles the rules without instantiation, returning a reusable template.
	 * <p>
	 * This allows tests to compile once and instantiate multiple times with
	 * different arguments, improving performance for parameterized tests.
	 * <p>
	 * This method orchestrates stages 1-4 of the pipeline:
	 * <ol>
	 * <li>Parse the Iskara source</li>
	 * <li>Run semantic analysis</li>
	 * <li>Generate Java source code</li>
	 * <li>Compile Java source to bytecode</li>
	 * </ol>
	 *
	 * @return a template that can be instantiated multiple times
	 */
	public CompiledRuleTemplate compile() {
		// Stage 1: Parse
		Parser parser = new IskaraParserImpl();
		ParseResult<RuleModule> parseResult = parser.parse(source);

		if (!parseResult.isSuccess()) {
			List<String> errors = parseResult.getDiagnostics().stream().map(d -> d.message()).toList();
			return new CompiledRuleTemplate.Failure(new RuleTestResult.ParseFailure(errors));
		}

		RuleModule module = parseResult.getValue().orElseThrow();

		// Stage 2: Semantic analysis
		SemanticAnalyzer analyzer = new SemanticAnalyzerImpl();
		AnalysisResult analysisResult = analyzer.analyze(module);

		if (!analysisResult.isSuccess()) {
			List<String> errors = analysisResult.getDiagnostics().stream().map(d -> d.message()).toList();
			return new CompiledRuleTemplate.Failure(new RuleTestResult.AnalysisFailure(errors));
		}

		// Stage 3: Generate Java code
		JavaCompiler javaCompiler = new JavaCompilerImpl();
		JavaCompilerOptions options = new JavaCompilerOptions(packageName, className, true);
		CompilationResult codegenResult = javaCompiler.compile(module, options);

		if (!codegenResult.isSuccess()) {
			return new CompiledRuleTemplate.Failure(new RuleTestResult.CodegenFailure(codegenResult.getErrors()));
		}

		Map<String, String> sourceFiles = codegenResult.getSourceFiles().orElseThrow();
		String generatedSource = sourceFiles.values().iterator().next();
		String fullyQualifiedName = options.fullyQualifiedClassName();

		// Stage 4: Compile Java to bytecode
		InMemoryCompiler memCompiler = new InMemoryCompiler();
		InMemoryCompilationResult compileResult = memCompiler.compile(fullyQualifiedName, generatedSource);

		if (!compileResult.isSuccess()) {
			return new CompiledRuleTemplate.Failure(
					new RuleTestResult.CompilationFailure(compileResult.getDiagnostics(), generatedSource));
		}

		Class<?> compiledClass = compileResult.getCompiledClass().orElseThrow();
		return new CompiledRuleTemplate.Success(compiledClass, generatedSource);
	}

	/**
	 * Builds and compiles the rules, returning the result.
	 * <p>
	 * This method orchestrates the full pipeline:
	 * <ol>
	 * <li>Parse the Iskara source</li>
	 * <li>Run semantic analysis</li>
	 * <li>Generate Java source code</li>
	 * <li>Compile Java source to bytecode</li>
	 * <li>Instantiate the compiled class</li>
	 * </ol>
	 *
	 * @return the test result
	 */
	public RuleTestResult build() {
		// Stage 1: Parse
		Parser parser = new IskaraParserImpl();
		ParseResult<RuleModule> parseResult = parser.parse(source);

		if (!parseResult.isSuccess()) {
			List<String> errors = parseResult.getDiagnostics().stream().map(d -> d.message()).toList();
			return new RuleTestResult.ParseFailure(errors);
		}

		RuleModule module = parseResult.getValue().orElseThrow();

		// Stage 2: Semantic analysis
		SemanticAnalyzer analyzer = new SemanticAnalyzerImpl();
		AnalysisResult analysisResult = analyzer.analyze(module);

		if (!analysisResult.isSuccess()) {
			List<String> errors = analysisResult.getDiagnostics().stream().map(d -> d.message()).toList();
			return new RuleTestResult.AnalysisFailure(errors);
		}

		// Stage 3: Generate Java code
		JavaCompiler javaCompiler = new JavaCompilerImpl();
		JavaCompilerOptions options = new JavaCompilerOptions(packageName, className, true);
		CompilationResult codegenResult = javaCompiler.compile(module, options);

		if (!codegenResult.isSuccess()) {
			return new RuleTestResult.CodegenFailure(codegenResult.getErrors());
		}

		Map<String, String> sourceFiles = codegenResult.getSourceFiles().orElseThrow();
		String generatedSource = sourceFiles.values().iterator().next();
		String fullyQualifiedName = options.fullyQualifiedClassName();

		// Stage 4: Compile Java to bytecode
		InMemoryCompiler memCompiler = new InMemoryCompiler();
		InMemoryCompilationResult compileResult = memCompiler.compile(fullyQualifiedName, generatedSource);

		if (!compileResult.isSuccess()) {
			return new RuleTestResult.CompilationFailure(compileResult.getDiagnostics(), generatedSource);
		}

		// Stage 5: Instantiate
		try {
			Class<?> compiledClass = compileResult.getCompiledClass().orElseThrow();
			Object[] constructorArgs = buildConstructorArgs();
			CompiledRules rules = CompiledRules.instantiate(compiledClass, constructorArgs);
			return new RuleTestResult.Success(rules, generatedSource);
		} catch (ReflectiveOperationException e) {
			return new RuleTestResult.CompilationFailure(List.of("Failed to instantiate rules: " + e.getMessage()),
					generatedSource);
		}
	}

	private Object[] buildConstructorArgs() {
		List<Object> args = new ArrayList<>();
		args.addAll(facts);
		args.addAll(globals);
		return args.toArray();
	}
}
